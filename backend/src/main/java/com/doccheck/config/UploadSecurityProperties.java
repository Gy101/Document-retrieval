package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DOCX 上传安全阈值。默认值面向普通论文/报告；大文件可在配置中放宽。
 */
@Data
@ConfigurationProperties(prefix = "validation.upload")
public class UploadSecurityProperties {

    /** 解压后总大小上限（MB）。 */
    private long maxUncompressedMb = 200;

    /** 单个 entry 解压后上限（MB）。 */
    private long maxEntryMb = 80;

    /** ZIP entry 数量上限。 */
    private long maxEntries = 5000;

    /** 总解压大小 / 压缩包大小 的最大比例。 */
    private long maxCompressionRatio = 120;
}
