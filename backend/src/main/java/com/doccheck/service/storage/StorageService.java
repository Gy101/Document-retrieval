package com.doccheck.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 文件存储抽象。本地磁盘是默认实现；接入 MinIO 时新增实现类并设置 storage.type=minio 即可，
 * 业务层只依赖本接口，不感知物理存储介质。
 */
public interface StorageService {

    /**
     * 保存上传文件。
     *
     * @param file     上传文件
     * @param category 逻辑分类，用于组织目录，如 template / document
     */
    StoredFile store(MultipartFile file, String category);

    /** 解析相对路径为可读的本地文件；接口语义限定为「可供当前节点读取」。 */
    Path resolve(String relativePath);

    /** 删除文件，不存在时静默返回。 */
    void delete(String relativePath);
}
