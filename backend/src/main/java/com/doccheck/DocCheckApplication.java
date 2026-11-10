package com.doccheck;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通用文档格式智能校验系统启动类。
 */
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.doccheck.mapper")
@SpringBootApplication
public class DocCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocCheckApplication.class, args);
    }
}
