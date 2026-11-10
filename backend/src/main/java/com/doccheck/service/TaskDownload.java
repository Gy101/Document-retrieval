package com.doccheck.service;

import java.nio.file.Path;

/** 任务原始文档下载描述。 */
public record TaskDownload(Path path, String fileName) {
}
