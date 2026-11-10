package com.doccheck.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends TaskVO {

    /** 待校验文档的完整格式元数据；light 模式下为 null，需单独调用 metadata 接口 */
    private JsonNode document;
    /** 差异列表；light 模式下为 null，需分页调用 diffs 接口 */
    private List<DiffVO> diffs;
    private String customRules;
}
