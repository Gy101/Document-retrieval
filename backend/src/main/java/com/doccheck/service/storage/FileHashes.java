package com.doccheck.service.storage;

import com.doccheck.common.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 上传文件指纹工具。创建任务前先算 SHA-256，既可用于文件去重，也可复用历史解析结果。
 */
public final class FileHashes {

    private FileHashes() {
    }

    public static String sha256(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件为空");
        }
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(500, "文件指纹计算失败: " + e.getMessage(), e);
        }
    }
}
