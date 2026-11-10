package com.doccheck.check;

import com.doccheck.domain.model.FormatDiff;

import java.util.List;

/**
 * 多维度校验策略。新增规则只需实现本接口并注册为 Spring Bean，无需修改管线。
 * promptGuidance 会注入 DSH 提示词，precheck 用于确定性的硬指标校验（可返回空列表）。
 */
public interface FormatCheckStrategy {

    /** 唯一名称，用于日志与排查。 */
    String name();

    /** 维度名称，如 字符/段落/编号/表格/图片/页面/页眉页脚。 */
    String dimension();

    /** 注入 DSH 的补充校验要求。 */
    String promptGuidance();

    /** 本地确定性预校验；默认不执行本地规则。 */
    default List<FormatDiff> precheck(CheckContext context) {
        return List.of();
    }
}
