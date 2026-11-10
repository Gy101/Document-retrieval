package com.doccheck.service;

/**
 * 从 DOCX 包中按需读取的一张图片。
 * 不把图片二进制塞进元数据 JSON，避免任务详情和模板缓存被大图片撑爆。
 */
public record DocumentImage(byte[] bytes, String contentType, String fileName) {
}
