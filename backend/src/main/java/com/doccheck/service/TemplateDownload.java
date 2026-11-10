package com.doccheck.service;

import java.nio.file.Path;

/** 模板下载所需的物理文件与原始文件名。 */
public record TemplateDownload(Path path, String fileName) {
}
