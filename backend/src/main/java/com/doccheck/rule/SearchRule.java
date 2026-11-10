package com.doccheck.rule;

import lombok.Data;

/**
 * 一条 YAML 文本检索规则。
 *
 * <p>规则只读取文档的纯文本元数据，不修改文档；命中/缺失会转换成标准 FormatDiff，
 * 与本地格式规则、DSH 结果一起合并展示。</p>
 */
@Data
public class SearchRule {

    /** 规则唯一标识，便于排查和去重。 */
    private String id;

    /** 展示名称，例如「未替换占位符」。 */
    private String name;

    /** 关键词（contains）或正则（regex）。 */
    private String pattern;

    /** contains | regex，默认 contains。 */
    private String match = "contains";

    /** required（必须出现）| forbidden（禁止出现），默认 forbidden。 */
    private String mode = "forbidden";

    /** body | table | header | footer | all，默认 all。 */
    private String scope = "all";

    /** high | middle | low，默认 middle。 */
    private String severity = "middle";

    /** 命中时的补充说明。 */
    private String message;

    /** 修复建议。 */
    private String suggestion;

    /** 是否区分大小写，默认 false。 */
    private boolean caseSensitive;

    /** 同一条规则在一个文本单元内最多返回的命中数，默认 5。 */
    private int maxHits = 5;

    /** 是否启用该条规则，默认 true。 */
    private boolean enabled = true;
}
