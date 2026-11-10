package com.doccheck.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("check_task")
public class CheckTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private Long userId;

    private Long templateId;

    /** 模板名称快照，模板删除后历史任务仍可读 */
    private String templateName;

    private String docName;

    private String docPath;

    private Long docSize;

    private String docHash;

    private String customRules;

    /** QUEUED / RUNNING / SUCCESS / FAILED / DEAD */
    private String status;

    private Integer progress;

    /** 已重试次数；达到上限后进入 DEAD 死信状态 */
    private Integer retryCount;

    private String stage;

    private String errorMessage;

    private String summary;

    private Integer totalDiffCount;

    private Integer highCount;

    private Integer middleCount;

    private Integer lowCount;

    /** 待校验文档的结构化格式元数据 JSON */
    private String documentMetadata;

    /** DSH 原始响应，保留审计能力 */
    private String resultJson;

    private Long durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
