package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionMeta {

    private int index;

    private PageSetupMeta pageSetup;

    /** 分节符类型：continuous / nextPage / evenPage / oddPage 等 */
    private String breakType;

    /** 该节按正文顺序排列的段落/表格块 */
    private List<BlockMeta> blocks;
}
