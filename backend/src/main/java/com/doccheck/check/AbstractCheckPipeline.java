package com.doccheck.check;

import com.doccheck.common.BizException;
import com.doccheck.config.ValidationProperties;
import com.doccheck.dsh.AnchorParser;
import com.doccheck.dsh.DshCompareResult;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.DocxParser;
import com.doccheck.parser.model.DocumentMeta;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 校验主流程的模板方法。
 *
 * <p>固定编排：加载上下文 → 解析待校验文档 → 并行收集本地规则 → DSH 全量比对
 * → 归一化/去重/排序 → 统计与摘要。子类只负责「如何调用模型」，流程骨架不可被策略篡改。
 */
@RequiredArgsConstructor
public abstract class AbstractCheckPipeline {

    protected final DocxParser docxParser;
    protected final ValidationProperties validationProperties;

    public final CheckOutcome execute(CheckContext context, ProgressCallback progress) {
        progress.update(3, "准备校验");
        DocumentMeta targetMeta = parseTarget(context);
        progress.update(35, "待校验文档解析完成");

        List<FormatDiff> localDiffs = new ArrayList<>();
        if (validationProperties.isLocalRulesEnabled()) {
            for (FormatCheckStrategy strategy : strategies()) {
                try {
                    List<FormatDiff> result = strategy.precheck(contextWithTarget(context, targetMeta));
                    if (result != null) {
                        localDiffs.addAll(result);
                    }
                } catch (RuntimeException e) {
                    // 单个策略失败不阻断主流程，DSH 仍会兜底
                    progress.update(40, "策略 " + strategy.name() + " 跳过: " + e.getMessage());
                }
            }
        }
        progress.update(50, "本地规则校验完成");

        DshCompareResult dsh = compareWithModel(context, targetMeta);
        progress.update(82, "大模型比对完成");

        List<FormatDiff> merged = normalizeAndMerge(localDiffs, dsh.getDiffs());
        int high = count(merged, Severity.HIGH);
        int middle = count(merged, Severity.MIDDLE);
        int low = merged.size() - high - middle;
        // 统计口径必须与最终合并、去重后的差异列表一致；
        // DSH 返回的 summary 只覆盖模型自身结果，不能直接用于展示。
        String summary = "共发现 " + merged.size() + " 处格式差异，其中高危 " + high
                + " 处、中危 " + middle + " 处、低危 " + low + " 处。";
        progress.update(90, "差异结果归一化完成");
        return CheckOutcome.builder()
                .targetMeta(targetMeta)
                .diffs(merged)
                .summary(summary)
                .highCount(high)
                .middleCount(middle)
                .lowCount(low)
                .rawResultJson(dsh.getRawJson())
                .build();
    }

    /** 子类实现真实/Mock 模型调用。 */
    protected abstract DshCompareResult compareWithModel(CheckContext context, DocumentMeta targetMeta);

    /** 由子类注入全部策略 Bean。 */
    protected abstract List<FormatCheckStrategy> strategies();

    private DocumentMeta parseTarget(CheckContext context) {
        try {
            return docxParser.parse(Path.of(context.getTargetFilePath()), context.getTargetFileName());
        } catch (Exception e) {
            throw new BizException(422, "待校验文档解析失败，请确认是有效的 .docx 文件: " + e.getMessage(), e);
        }
    }

    private CheckContext contextWithTarget(CheckContext context, DocumentMeta targetMeta) {
        return CheckContext.builder()
                .taskId(context.getTaskId())
                .template(context.getTemplate())
                .templateMeta(context.getTemplateMeta())
                .targetMeta(targetMeta)
                .targetFilePath(context.getTargetFilePath())
                .targetFileName(context.getTargetFileName())
                .customRules(context.getCustomRules())
                .build();
    }

    private List<FormatDiff> normalizeAndMerge(List<FormatDiff> local, List<FormatDiff> model) {
        Map<String, FormatDiff> unique = new LinkedHashMap<>();
        for (FormatDiff diff : concat(local, model)) {
            if (diff == null || diff.getDiffType() == null) {
                continue;
            }
            diff.setSeverity(Severity.normalize(diff.getSeverity()));
            if (diff.getAnchorPath() == null || diff.getAnchorPath().isBlank()) {
                diff.setAnchorPath(diff.getLocation() == null ? "body.p[0]" : diff.getLocation());
            }
            AnchorParser.Anchor anchor = AnchorParser.parse(diff.getAnchorPath());
            if (diff.getParagraphIndex() == null) {
                diff.setParagraphIndex(anchor.paragraphIndex());
            }
            if (diff.getTableIndex() == null) {
                diff.setTableIndex(anchor.tableIndex());
            }
            if (diff.getRowIndex() == null) {
                diff.setRowIndex(anchor.rowIndex());
            }
            if (diff.getColIndex() == null) {
                diff.setColIndex(anchor.colIndex());
            }
            unique.putIfAbsent(diff.dedupeKey(), diff);
        }
        List<FormatDiff> result = new ArrayList<>(unique.values());
        result.sort(Comparator
                .comparingInt((FormatDiff d) -> anchorOrder(d.getAnchorPath()))
                .thenComparingInt(d -> Severity.rank(d.getSeverity()))
                .thenComparing(d -> d.getDiffType() == null ? "" : d.getDiffType()));
        if (result.size() > validationProperties.getMaxDiffs()) {
            result = new ArrayList<>(result.subList(0, validationProperties.getMaxDiffs()));
        }
        for (int i = 0; i < result.size(); i++) {
            FormatDiff diff = result.get(i);
            diff.setSeq(i + 1);
            if (diff.getDiffId() == null || diff.getDiffId().isBlank()) {
                diff.setDiffId(String.format("D%03d", i + 1));
            }
            if (diff.getLocation() == null || diff.getLocation().isBlank()) {
                diff.setLocation("第 " + (i + 1) + " 处");
            }
        }
        return result;
    }

    private List<FormatDiff> concat(List<FormatDiff> a, List<FormatDiff> b) {
        List<FormatDiff> result = new ArrayList<>();
        if (a != null) {
            result.addAll(a);
        }
        if (b != null) {
            result.addAll(b);
        }
        return result;
    }

    private int count(List<FormatDiff> diffs, String severity) {
        return (int) diffs.stream().filter(d -> severity.equals(d.getSeverity())).count();
    }

    private int anchorOrder(String anchor) {
        if (anchor == null) {
            return 900000;
        }
        if (anchor.startsWith("header")) {
            return 500000;
        }
        if (anchor.startsWith("footer")) {
            return 600000;
        }
        if (anchor.contains(".table[")) {
            AnchorParser.Anchor parsed = AnchorParser.parse(anchor);
            int table = parsed.tableIndex() == null ? 0 : parsed.tableIndex();
            int row = parsed.rowIndex() == null ? 0 : parsed.rowIndex();
            return 100000 + table * 1000 + row * 10;
        }
        AnchorParser.Anchor parsed = AnchorParser.parse(anchor);
        return parsed.paragraphIndex() == null ? 90000 : parsed.paragraphIndex() * 100;
    }
}
