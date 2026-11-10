package com.doccheck.service;

/**
 * 根据模板生成的 YAML 检索规则文件。
 *
 * @param fileName 下载文件名
 * @param content  YAML 文本
 */
public record GeneratedRuleYaml(String fileName, String content) {
}
