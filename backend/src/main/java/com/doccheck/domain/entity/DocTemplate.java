package com.doccheck.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_template")
public class DocTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String category;

    /** PUBLIC / PERSONAL */
    private String scope;

    private Long ownerId;

    private String originalName;

    /** 相对于存储根目录的路径，绝不下发到前端 */
    private String storagePath;

    private Long fileSize;

    private String fileHash;

    private String contentType;

    /** PENDING / SUCCESS / FAILED */
    private String parseStatus;

    private Integer metadataVersion;

    private String metadataJson;

    private String parseMessage;

    private Integer downloadCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
