package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMeta {

    private int index;

    private int sectionIndex;

    /** 正文/页眉页脚内的段落序号；表格单元格图片为 -1 */
    private int paragraphIndex;

    /** body / header / footer / tableCell */
    private String containerType;

    /** 页眉序号，仅 containerType=header 时存在 */
    private Integer headerIndex;

    /** 页脚序号，仅 containerType=footer 时存在 */
    private Integer footerIndex;

    /** 表格序号，仅 containerType=tableCell 时存在 */
    private Integer tableIndex;

    private Integer rowIndex;

    private Integer cellIndex;

    private double widthPt;
    private double heightPt;

    /** inline / square / tight / through / topAndBottom / none */
    private String wrapType;

    private boolean behindText;

    private String name;

    /** 图片 MIME 类型，例如 image/png */
    private String contentType;

    /** DOCX 包内稳定路径，例如 /word/media/image1.png */
    private String partName;

    private String altText;
}
