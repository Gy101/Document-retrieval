package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private int tokenTtlHours = 8;

    /** 仅当应用位于可信反向代理之后时才信任 X-Forwarded-For / X-Real-IP。 */
    private boolean trustProxy = false;

    private String demoUsername = "admin";

    private String demoPassword = "admin123";
}
