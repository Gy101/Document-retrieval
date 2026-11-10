package com.doccheck.service.storage;

import com.doccheck.common.BizException;
import com.doccheck.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘存储实现。
 * 目录结构：{root}/{category}/yyyy/MM/dd/{uuid}.docx
 * 安全要点：所有相对路径在 resolve 时做 normalize + 前缀校验，防止 ../ 路径穿越。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties properties;
    private Path root;

    @PostConstruct
    public void init() throws IOException {
        this.root = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("本地文件存储根目录: {}", root);
    }

    @Override
    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件为空");
        }
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed.docx" : file.getOriginalFilename());
        String ext = extensionOf(originalName);
        String relative = "%s/%s/%s%s".formatted(
                sanitizeCategory(category), LocalDate.now().format(DATE_DIR), UUID.randomUUID(), ext);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(500, "文件保存失败: " + e.getMessage(), e);
        }
        String sha256;
        try (InputStream in = Files.newInputStream(target)) {
            sha256 = digestSha256(in);
        } catch (IOException e) {
            throw new BizException(500, "文件校验失败: " + e.getMessage(), e);
        }
        return new StoredFile(relative, originalName, file.getSize(), sha256,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType());
    }

    @Override
    public Path resolve(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new BizException(400, "文件路径为空");
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            throw new BizException(400, "非法文件路径");
        }
        return candidate;
    }

    @Override
    public void delete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", relativePath, e);
        }
    }

    private String sanitizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "common";
        }
        String cleaned = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isBlank() ? "common" : cleaned;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".bin";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : ".bin";
    }

    private String digestSha256(InputStream in) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
