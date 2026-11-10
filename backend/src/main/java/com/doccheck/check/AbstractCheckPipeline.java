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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("parseExecutor")
    private ThreadPoolTaskExecutor parseExecutor;

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("dshExecutor")
    private ThreadPoolTaskExecutor dshExecutor;

    @Value("${check.parse-timeout-seconds:120}")
    private int parseTimeoutSeconds;

    @Value("${check.model-timeout-seconds:180}")
    private int modelTimeoutSeconds;

    public final CheckOutcome execute(CheckContext context, ProgressCallback progress) {
        progress.update(3, "准备校验");
        boolean cached = context.getTargetMeta() != null;
        DocumentMeta targetMeta = cached ? context.getTargetMeta() : parseTarget(context);
        progress.update(35, cached ? "复用历史解析结果" : "待校验文档解析完成");

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

        DshCompareResult dsh = compareWithModelAsync(context, targetMeta);
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

    /**
     * 解析放入独立线程池执行。任务编排线程只等待结果，不直接做 CPU/IO 密集解析。
     */
    private DocumentMeta parseTarget(CheckContext context) {
        try {
            return await(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return docxParser.parse(Path.of(context.getTargetFilePath()), context.getTargetFileName());
                        } catch (Exception e) {
                            throw new BizException(422, "待校验文档解析失败，请确认是有效的 .docx 文件: "
                                    + e.getMessage(), e);
                        }
                    }, parseExecutor), parseTimeoutSeconds, "文档解析超时");
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(422, "待校验文档解析失败: " + rootMessage(e), e);
        }
    }

    /** DSH 调用放入独立线程池，避免模型网络抖动占满解析线程。 */
    private DshCompareResult compareWithModelAsync(CheckContext context, DocumentMeta targetMeta) {
        try {
            return await(CompletableFuture.supplyAsync(
                    () -> compareWithModel(context, targetMeta), dshExecutor),
                    modelTimeoutSeconds, "DSH 模型调用超时");
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(502, "DSH 模型调用失败: " + rootMessage(e), e);
        }
    }

    private <T> T await(CompletableFuture<T> future, int timeoutSeconds, String timeoutMessage) {
        try {
            return future.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BizException(504, timeoutMessage + "（" + timeoutSeconds + "s）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(503, "校验线程被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new BizException(500, "校验执行失败: " + (cause == null ? e.getMessage() : cause.getMessage()), cause);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
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
