package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * .docx 文档的全量格式元数据。该对象会被序列化为 JSON：
 * 1）作为 DSH 比对的输入；2）作为前端高亮预览的数据源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMeta {

    /**
     * 元数据结构版本。2 起 image 节点带 containerType / partName 等字段；
     * 3 起附带 Word 批注 comments，旧任务可在读取详情时按需重新解析并回写。
     */
    private Integer metadataVersion;

    private String fileName;

    private LocalDateTime parsedAt;

    private DocumentStatistics statistics;

    /** 文档节列表（页面设置、分栏、该节内的段落/表格块） */
    private List<SectionMeta> sections;

    private List<HeaderFooterMeta> headers;

    private List<HeaderFooterMeta> footers;

    private List<ImageMeta> images;

    /** 文档默认样式与段落/字符样式摘要，帮助模型判断继承后的有效格式 */
    private DocumentStylesMeta styles;

    /** Word 批注（模板批注通常就是该章节的格式/内容要求） */
    private List<CommentMeta> comments;

    /** 解析提示，例如“含 1 个不支持的内容控件” */
    private String notes;
}
