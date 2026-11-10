package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.ImageMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 图片策略：数量、大小、环绕方式、浮于文字上方/下方。 */
@Service
public class ImageFormatStrategy implements FormatCheckStrategy {

    @Override
    public String name() {
        return "image-format";
    }

    @Override
    public String dimension() {
        return "图表元素";
    }

    @Override
    public String promptGuidance() {
        return "比对图片宽高、环绕方式、是否衬于文字下方/浮于文字上方，以及图片所在容器"
                + "（正文段落、页眉页脚、表格单元格）；模板图片可能只是版式示例，"
                + "图片内容、文件名、图片数量对应的业务含义不应作为格式错误。"
                + "模板为嵌入式而目标为浮动式时至少按中危处理。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<ImageMeta> template = context.getTemplateMeta() == null || context.getTemplateMeta().getImages() == null
                ? List.of() : context.getTemplateMeta().getImages();
        List<ImageMeta> target = context.getTargetMeta() == null || context.getTargetMeta().getImages() == null
                ? List.of() : context.getTargetMeta().getImages();
        // 图片数量由文档内容决定，不作为格式差异；只比较双方都存在图片的版式属性。
        int limit = Math.min(20, Math.min(template.size(), target.size()));
        for (int i = 0; i < limit; i++) {
            ImageMeta ti = template.get(i);
            ImageMeta di = target.get(i);
            String anchor = anchor(di);
            String location = location(di, i);
            if (!Objects.equals(ti.getWrapType(), di.getWrapType())) {
                result.add(DiffSupport.of("图片", Severity.MIDDLE, "image", anchor, location,
                        "环绕方式 " + ti.getWrapType(), "环绕方式 " + di.getWrapType(), "",
                        "将图片环绕方式调整为 " + ti.getWrapType()));
            }
            if (ti.isBehindText() != di.isBehindText()) {
                result.add(DiffSupport.of("图片", Severity.MIDDLE, "image", anchor, location,
                        ti.isBehindText() ? "衬于文字下方" : "浮于文字上方",
                        di.isBehindText() ? "衬于文字下方" : "浮于文字上方", "",
                        ti.isBehindText() ? "将图片设置为衬于文字下方" : "将图片设置为浮于文字上方"));
            }
            if (Math.abs(ti.getWidthPt() - di.getWidthPt()) > 5
                    || Math.abs(ti.getHeightPt() - di.getHeightPt()) > 5) {
                result.add(DiffSupport.of("图片", Severity.MIDDLE, "image", anchor, location,
                        Math.round(ti.getWidthPt()) + "×" + Math.round(ti.getHeightPt()) + "pt",
                        Math.round(di.getWidthPt()) + "×" + Math.round(di.getHeightPt()) + "pt", "",
                        "按模板等比例缩放图片"));
            }
        }
        return result;
    }

    /** 前端和差异列表共用同一套 anchor，确保点击差异能定位到图片所在容器。 */
    private String anchor(ImageMeta image) {
        if ("tableCell".equals(image.getContainerType())) {
            return "body.table[" + n(image.getTableIndex()) + "].r[" + n(image.getRowIndex())
                    + "].c[" + n(image.getCellIndex()) + "]";
        }
        if ("header".equals(image.getContainerType())) {
            return "header[" + n(image.getHeaderIndex()) + "].p[" + Math.max(0, image.getParagraphIndex()) + "]";
        }
        if ("footer".equals(image.getContainerType())) {
            return "footer[" + n(image.getFooterIndex()) + "].p[" + Math.max(0, image.getParagraphIndex()) + "]";
        }
        return "body.p[" + Math.max(0, image.getParagraphIndex()) + "]";
    }

    private String location(ImageMeta image, int sequence) {
        if ("tableCell".equals(image.getContainerType())) {
            return "第 " + (sequence + 1) + " 张图片 · 表格 "
                    + (n(image.getTableIndex()) + 1) + " 第 " + (n(image.getRowIndex()) + 1)
                    + " 行第 " + (n(image.getCellIndex()) + 1) + " 列";
        }
        if ("header".equals(image.getContainerType())) {
            return "第 " + (sequence + 1) + " 张图片 · 页眉 " + (n(image.getHeaderIndex()) + 1);
        }
        if ("footer".equals(image.getContainerType())) {
            return "第 " + (sequence + 1) + " 张图片 · 页脚 " + (n(image.getFooterIndex()) + 1);
        }
        return "第 " + (sequence + 1) + " 张图片";
    }

    private int n(Integer value) {
        return value == null ? 0 : value;
    }
}
