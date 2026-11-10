package com.doccheck.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends TaskVO {

    /** 待校验文档的完整格式元数据，前端据此绘制预览并高亮 anchorPath */
    private JsonNode document;
    private List<DiffVO> diffs;
    private String customRules;
}
