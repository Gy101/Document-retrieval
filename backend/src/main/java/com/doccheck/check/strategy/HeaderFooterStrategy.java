package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.HeaderFooterMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 页眉页脚策略：内容、奇偶页/首页规则、页码格式与起始值。 */
@Service
public class HeaderFooterStrategy implements FormatCheckStrategy {

    @Override
    public String name() {
        return "header-footer";
    }

    @Override
    public String dimension() {
        return "页眉页脚页码";
    }

    @Override
    public String promptGuidance() {
        return "比对页眉页脚文本、字体字号、奇偶页/首页不同规则、页码格式与起始值；"
                + "模板有页码而目标缺失页码字段时按高危处理。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        compareGroup("页眉", context.getTemplateMeta() == null ? List.of() : context.getTemplateMeta().getHeaders(),
                context.getTargetMeta() == null ? List.of() : context.getTargetMeta().getHeaders(),
                "header", result);
        compareGroup("页脚", context.getTemplateMeta() == null ? List.of() : context.getTemplateMeta().getFooters(),
                context.getTargetMeta() == null ? List.of() : context.getTargetMeta().getFooters(),
                "footer", result);
        return result;
    }

    private void compareGroup(String label, List<HeaderFooterMeta> template, List<HeaderFooterMeta> target,
                              String anchorPrefix, List<FormatDiff> result) {
        // 页眉/页脚数量随分节数变化，不作为格式差异；按 default/first/even 类型配对。
        // 同一类型可能在多个节重复出现，优先取有实际内容的那一份，避免把空页眉/空页脚误判为缺失。
        List<String> types = template.stream()
                .map(HeaderFooterMeta::getHeaderFooterType)
                .distinct()
                .toList();
        for (String type : types) {
            HeaderFooterMeta th = template.stream()
                    .filter(t -> Objects.equals(type, t.getHeaderFooterType()))
                    .filter(t -> !normalize(t.getText()).isEmpty() || t.isHasPageNumberField())
                    .findFirst()
                    .orElseGet(() -> template.stream()
                            .filter(t -> Objects.equals(type, t.getHeaderFooterType()))
                            .findFirst()
                            .orElse(null));
            if (th == null) {
                continue;
            }
            List<HeaderFooterMeta> candidates = target.stream()
                    .filter(t -> Objects.equals(type, t.getHeaderFooterType()))
                    .toList();
            HeaderFooterMeta dh = candidates.stream()
                    .filter(t -> !normalize(t.getText()).isEmpty())
                    .findFirst()
                    .orElseGet(() -> candidates.stream()
                            .filter(HeaderFooterMeta::isHasPageNumberField)
                            .findFirst()
                            .orElse(candidates.isEmpty() ? null : candidates.get(0)));
            if (dh == null) {
                continue;
            }
            int index = target.indexOf(dh);
            String anchor = anchorPrefix + "[" + index + "].p[0]";
            String location = "第 " + (index + 1) + " 个" + label;
            if (!normalize(th.getText()).equals(normalize(dh.getText()))) {
                result.add(DiffSupport.of("页眉页脚", Severity.MIDDLE, anchorPrefix, anchor, location,
                        normalize(th.getText()), normalize(dh.getText()), "",
                        "按模板修正" + label + "文字"));
            }
            if (th.isHasPageNumberField() && !dh.isHasPageNumberField()) {
                result.add(DiffSupport.of("页码", Severity.HIGH, anchorPrefix, anchor, location,
                        "含 PAGE 页码字段", "无页码字段", "",
                        "插入 PAGE 域并设置页码格式"));
            }
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
