package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DSH 大模型 HTTP 调用配置。
 */
@Data
@ConfigurationProperties(prefix = "dsh")
public class DshProperties {

    /** 演示模式：不调用真实 DSH，使用本地确定性结果，便于零配置跑通全流程 */
    private boolean mock = true;

    private String baseUrl = "https://api.dsh.example.com";

    private String apiKey = "";

    private String model = "dsh-chat";

    /** OpenAI 兼容的对话接口路径 */
    private String chatPath = "/v1/chat/completions";

    private int connectTimeoutSeconds = 10;

    private int timeoutSeconds = 120;

    /** 失败重试次数（不含首次请求） */
    private int maxRetries = 2;

    private long retryBackoffMs = 1000;

    private double temperature = 0.1;

    private int maxTokens = 8192;

    /** 是否发送 response_format={"type":"json_object"}；部分网关不支持时可关闭 */
    private boolean jsonMode = true;

    /** 送入模型的元数据 JSON 字符上限 */
    private int maxPromptChars = 120000;
}
