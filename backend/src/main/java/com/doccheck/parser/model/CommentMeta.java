package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Word 批注元数据。批注往往承载模板作者对某一章节的格式/内容要求，
 * 因此既用于前端展示，也会被注入 DSH 提示词并由本地批注策略解析数值约束。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentMeta {

    /** Word 内部的批注 ID（w:comment/@w:id）。 */
    private String id;

    private String author;

    private String initials;

    private LocalDateTime date;

    /** 批注正文（保留换行，前端按空白折叠展示）。 */
    private String text;

    /** 批注范围起始段落，使用与差异锚点一致的全局段落序号。 */
    private Integer paragraphIndex;

    private Integer sectionIndex;

    /** 便于前端直接定位的锚点，例如 body.p[3]。 */
    private String anchorPath;
}
