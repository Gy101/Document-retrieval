package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.NumberingMeta;
import com.doccheck.parser.model.ParagraphMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 编号与项目符号策略：多级编号样式、层级、起始值、前后缀与连续性。 */
@Service
public class NumberingFormatStrategy implements FormatCheckStrategy {

    @Override
    public String name() {
        return "numbering-format";
    }

    @Override
    public String dimension() {
        return "编号序号";
    }

    @Override
    public String promptGuidance() {
        return "比对多级编号的 numId/层级/编号格式/前后缀/起始值，检查同级编号连续性与项目符号字符；"
                + "模板有编号而目标丢失编号的，按高危处理。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<ParagraphAligner.Pair> pairs =
                ParagraphAligner.align(context.getTemplateMeta(), context.getTargetMeta());
        int limit = Math.min(150, pairs.size());
        for (int i = 0; i < limit; i++) {
            NumberingMeta tn = pairs.get(i).template().getNumbering();
            if (tn == null) {
                continue;
            }
            ParagraphMeta dp = pairs.get(i).target();
            NumberingMeta dn = dp.getNumbering();
            String anchor = DiffSupport.anchorOfParagraph(dp.getIndex());
            String location = "第 " + (dp.getIndex() + 1) + " 段";
            if (dn == null) {
                result.add(DiffSupport.of("编号", Severity.HIGH, "paragraph", anchor, location,
                        describe(tn), "无编号", dp.getText(), "恢复模板中的多级编号"));
                continue;
            }
            if (!Objects.equals(tn.getNumFmt(), dn.getNumFmt())) {
                result.add(DiffSupport.of("编号", Severity.MIDDLE, "paragraph", anchor, location,
                        "编号格式 " + tn.getNumFmt(), "编号格式 " + dn.getNumFmt(), dp.getText(),
                        "将编号格式调整为 " + tn.getNumFmt()));
            }
            if (!Objects.equals(tn.getIlvl(), dn.getIlvl())) {
                result.add(DiffSupport.of("编号", Severity.MIDDLE, "paragraph", anchor, location,
                        "编号层级 " + tn.getIlvl(), "编号层级 " + dn.getIlvl(), dp.getText(),
                        "将编号层级调整为第 " + (tn.getIlvl() == null ? 1 : tn.getIlvl() + 1) + " 级"));
            }
            if (tn.getLevelText() != null && dn.getLevelText() != null
                    && !Objects.equals(tn.getLevelText(), dn.getLevelText())) {
                result.add(DiffSupport.of("编号", Severity.LOW, "paragraph", anchor, location,
                        "编号文本 " + tn.getLevelText(), "编号文本 " + dn.getLevelText(), dp.getText(),
                        "按模板修正编号前后缀"));
            }
        }
        return result;
    }

    private String describe(NumberingMeta numbering) {
        return "编号 " + (numbering.getNumFmt() == null ? "有序" : numbering.getNumFmt())
                + "，第 " + (numbering.getIlvl() == null ? 1 : numbering.getIlvl() + 1) + " 级";
    }
}
