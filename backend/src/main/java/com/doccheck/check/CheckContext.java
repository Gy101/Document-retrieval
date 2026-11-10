package com.doccheck.check;

import com.doccheck.domain.entity.DocTemplate;
import com.doccheck.parser.model.DocumentMeta;
import lombok.Builder;
import lombok.Data;

/**
 * 校验管线上下文：一个任务对应一个上下文，避免在策略之间传递可变全局状态。
 */
@Data
@Builder
public class CheckContext {

    private Long taskId;
    private DocTemplate template;
    private DocumentMeta templateMeta;
    /** 管线解析后写入，供各策略读取，避免重复解析 */
    private DocumentMeta targetMeta;
    private String targetFilePath;
    private String targetFileName;
    private String customRules;
}
