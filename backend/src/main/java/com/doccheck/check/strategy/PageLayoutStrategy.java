package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.PageSetupMeta;
import com.doccheck.parser.model.SectionMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 页面布局策略：纸张、方向、页边距、分栏、页眉页脚距离。 */
@Service
public class PageLayoutStrategy implements FormatCheckStrategy {

    /** 0.2cm ≈ 5.67pt，按需求中的边距容差 */
    private static final double MARGIN_TOLERANCE = 5.67;
    private static final double SIZE_TOLERANCE = 2.0;

    @Override
    public String name() {
        return "page-layout";
    }

    @Override
    public String dimension() {
        return "页面布局";
    }

    @Override
    public String promptGuidance() {
        return "比对纸张大小、纵横方向、上下左右页边距、页眉页脚距离、分栏数与分隔符类型；"
                + "页边距容差 0.2 厘米，纸张尺寸与方向必须严格一致。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<SectionMeta> template = MetaIndex.sections(context.getTemplateMeta());
        List<SectionMeta> target = MetaIndex.sections(context.getTargetMeta());
        if (template.isEmpty() || target.isEmpty()) {
            return result;
        }
        int limit = Math.min(5, Math.min(template.size(), target.size()));
        for (int i = 0; i < limit; i++) {
            PageSetupMeta tp = template.get(i).getPageSetup();
            PageSetupMeta dp = target.get(i).getPageSetup();
            if (tp == null || dp == null) {
                continue;
            }
            String location = "第 " + (i + 1) + " 节页面设置";
            String anchor = "body.p[0]";
            if (!Objects.equals(tp.getOrientation(), dp.getOrientation())) {
                result.add(DiffSupport.of("页面", Severity.HIGH, "page", anchor, location,
                        "方向 " + tp.getOrientation(), "方向 " + dp.getOrientation(), "",
                        "将页面方向调整为 " + tp.getOrientation()));
            }
            if (Math.abs(tp.getWidthPt() - dp.getWidthPt()) > SIZE_TOLERANCE
                    || Math.abs(tp.getHeightPt() - dp.getHeightPt()) > SIZE_TOLERANCE) {
                result.add(DiffSupport.of("页面", Severity.HIGH, "page", anchor, location,
                        Math.round(tp.getWidthPt()) + "×" + Math.round(tp.getHeightPt()) + "pt",
                        Math.round(dp.getWidthPt()) + "×" + Math.round(dp.getHeightPt()) + "pt", "",
                        "将纸张大小调整为模板纸张"));
            }
            margin(result, anchor, location, "上边距", tp.getTopMarginPt(), dp.getTopMarginPt());
            margin(result, anchor, location, "下边距", tp.getBottomMarginPt(), dp.getBottomMarginPt());
            margin(result, anchor, location, "左边距", tp.getLeftMarginPt(), dp.getLeftMarginPt());
            margin(result, anchor, location, "右边距", tp.getRightMarginPt(), dp.getRightMarginPt());
            if (tp.getColumnCount() != dp.getColumnCount()) {
                result.add(DiffSupport.of("页面", Severity.MIDDLE, "page", anchor, location,
                        tp.getColumnCount() + " 栏", dp.getColumnCount() + " 栏", "",
                        "将分栏数调整为 " + tp.getColumnCount() + " 栏"));
            }
        }
        return result;
    }

    private void margin(List<FormatDiff> result, String anchor, String location, String name,
                        double expected, double actual) {
        if (Math.abs(expected - actual) > MARGIN_TOLERANCE) {
            result.add(DiffSupport.of("页面", Severity.MIDDLE, "page", anchor, location,
                    name + " " + round(expected) + "pt", name + " " + round(actual) + "pt", "",
                    "将" + name + "调整为 " + round(expected) + "pt"));
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
