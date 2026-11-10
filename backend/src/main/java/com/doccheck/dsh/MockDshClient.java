package com.doccheck.dsh;

import com.doccheck.check.ParagraphAligner;
import com.doccheck.config.DshProperties;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.ParagraphMeta;
import com.doccheck.parser.model.RunMeta;
import com.doccheck.parser.model.SectionMeta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 演示模式客户端：零密钥跑通全流程。
 *
 * <p>它并不「假装调用」远程模型，而是执行一套本地可解释的字符级比对，
 * 用于展示 DSH 返回结构、定位锚点与前端高亮；真实接入时设置 dsh.mock=false 即切换到 HttpDshClient。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dsh.mock", havingValue = "true", matchIfMissing = true)
public class MockDshClient implements DshClient {

    private final ObjectMapper objectMapper;
    private final DshProperties properties;

    @Override
    public DshCompareResult compare(DshCompareRequest request) {
        List<FormatDiff> diffs = new ArrayList<>();
        DocumentMeta template = request.template();
        DocumentMeta target = request.target();
        List<ParagraphAligner.Pair> pairs = ParagraphAligner.align(template, target);
        int limit = Math.min(60, pairs.size());

        for (int i = 0; i < limit && diffs.size() < 30; i++) {
            ParagraphMeta tp = pairs.get(i).template();
            ParagraphMeta dp = pairs.get(i).target();
            if (tp.getText() == null || tp.getText().isBlank()) {
                continue;
            }
            RunMeta tr = firstEffectiveRun(tp);
            RunMeta dr = firstEffectiveRun(dp);
            if (tr == null || dr == null) {
                continue;
            }
            if (!Objects.equals(tr.getFontEastAsia(), dr.getFontEastAsia())) {
                diffs.add(diff("字体", "high", "text", "body.p[" + dp.getIndex() + "]",
                        "第 " + (dp.getIndex() + 1) + " 段", tr.getFontEastAsia(), dr.getFontEastAsia(),
                        dp.getText(), "将中文字体调整为模板要求的「" + tr.getFontEastAsia() + "」"));
            }
            if (tr.getFontSizePt() != null && dr.getFontSizePt() != null
                    && Math.abs(tr.getFontSizePt() - dr.getFontSizePt()) > 0.5) {
                diffs.add(diff("字号", "high", "text", "body.p[" + dp.getIndex() + "]",
                        "第 " + (dp.getIndex() + 1) + " 段", tr.getFontSizePt() + "pt", dr.getFontSizePt() + "pt",
                        dp.getText(), "将字号调整为 " + tr.getFontSizePt() + "pt（约 "
                                + Math.round(tr.getFontSizePt() / 2) + " 号）"));
            }
            if (!Objects.equals(tr.getBold(), dr.getBold())) {
                diffs.add(diff("字重", "middle", "text", "body.p[" + dp.getIndex() + "]",
                        "第 " + (dp.getIndex() + 1) + " 段", boldName(tr.getBold()),
                        boldName(dr.getBold()), dp.getText(),
                        Boolean.TRUE.equals(tr.getBold()) ? "将该段文字加粗" : "取消该段文字加粗"));
            }
        }
        if (diffs.size() < 5 && template != null && target != null) {
            // 字符级差异不足时补充页面级提示，保证结果页有可演示的高亮目标
            SectionMeta ts = firstSection(template);
            SectionMeta ds = firstSection(target);
            if (ts != null && ds != null && ts.getPageSetup() != null && ds.getPageSetup() != null
                    && Math.abs(ts.getPageSetup().getTopMarginPt() - ds.getPageSetup().getTopMarginPt()) > 0.2) {
                diffs.add(diff("页边距", "middle", "page", "body.p[0]", "页面设置",
                        ts.getPageSetup().getTopMarginPt() + "pt", ds.getPageSetup().getTopMarginPt() + "pt", "",
                        "按模板调整上边距"));
            }
        }

        int high = (int) diffs.stream().filter(d -> Severity.HIGH.equals(d.getSeverity())).count();
        int middle = (int) diffs.stream().filter(d -> Severity.MIDDLE.equals(d.getSeverity())).count();
        int low = diffs.size() - high - middle;
        String summary = diffs.isEmpty()
                ? "未发现格式差异：待校验文档与模板的关键字符级格式一致。"
                : "演示模式共发现 " + diffs.size() + " 处格式差异，其中高危 " + high + " 处、中危 " + middle + " 处。";
        DshCompareResult result = DshCompareResult.builder()
                .summary(summary)
                .totalDiffCount(diffs.size())
                .highCount(high)
                .middleCount(middle)
                .lowCount(low)
                .diffs(diffs)
                .build();
        try {
            result.setRawJson(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            result.setRawJson("{}");
        }
        return result;
    }

    @Override
    public String mode() {
        return "mock";
    }

    private FormatDiff diff(String type, String severity, String element, String anchor, String location,
                            String templateValue, String actualValue, String snippet, String suggestion) {
        return FormatDiff.builder()
                .diffType(type)
                .severity(severity)
                .elementType(element)
                .anchorPath(anchor)
                .location(location)
                .templateFormat(templateValue == null ? "未设置" : templateValue)
                .actualFormat(actualValue == null ? "未设置" : actualValue)
                .contentSnippet(snippet == null ? "" : snippet.substring(0, Math.min(80, snippet.length())))
                .suggestion(suggestion)
                .build();
    }

    private String boldName(Boolean bold) {
        if (bold == null) {
            return "未设置";
        }
        return bold ? "加粗" : "未加粗";
    }

    private RunMeta firstEffectiveRun(ParagraphMeta paragraph) {
        if (paragraph.getRuns() == null) {
            return null;
        }
        return paragraph.getRuns().stream()
                .filter(r -> r.getText() != null && !r.getText().isBlank())
                .findFirst()
                .orElse(paragraph.getRuns().isEmpty() ? null : paragraph.getRuns().get(0));
    }

    private SectionMeta firstSection(DocumentMeta meta) {
        return meta == null || meta.getSections() == null || meta.getSections().isEmpty()
                ? null : meta.getSections().get(0);
    }
}
