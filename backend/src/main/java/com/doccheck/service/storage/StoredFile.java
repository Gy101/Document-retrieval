package com.doccheck.service.storage;

/**
 * 一次文件落盘的结果。relativePath 是相对存储根目录的路径，禁止直接暴露给前端。
 */
public record StoredFile(String relativePath, String originalName, long size, String sha256, String contentType) {
}
