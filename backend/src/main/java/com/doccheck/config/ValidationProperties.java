package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 校验管线配置。
 */
@Data
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    /** 是否启用本地确定性规则（页面/段落/表格等硬指标），与 DSH 结果合并 */
    private boolean localRulesEnabled = true;

    private int maxDiffs = 500;

    private int maxParagraphs = 800;

    private int maxRunsPerParagraph = 30;

    private int maxTableRows = 60;

    /** 外部 YAML 检索规则文件；默认相对启动目录的 config/search-rules.yml。 */
    private String searchRulesFile = "./config/search-rules.yml";

    /** 外部规则文件热加载轮询间隔（毫秒）。 */
    private long searchRulesWatchMs = 15000;
}
