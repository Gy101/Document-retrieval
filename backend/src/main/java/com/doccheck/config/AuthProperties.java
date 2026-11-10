package com.doccheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private int tokenTtlHours = 8;

    private String demoUsername = "admin";

    private String demoPassword = "admin123";
}
