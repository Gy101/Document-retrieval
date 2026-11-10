package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。MINIO 为预留扩展，切换 storage.type=minio 后由自定义 StorageService 实现接管。
 */
@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** local / minio */
    private String type = "local";

    private Local local = new Local();

    private Minio minio = new Minio();

    @Data
    public static class Local {
        /** 本地存储根目录，支持相对路径（相对于启动目录） */
        private String root = "./data/uploads";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "doc-check";
    }
}
