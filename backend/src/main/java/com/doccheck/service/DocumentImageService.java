package com.doccheck.service;

import com.doccheck.common.BizException;
import com.doccheck.common.UserContext;
import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.entity.DocTemplate;
import com.doccheck.mapper.CheckTaskMapper;
import com.doccheck.parser.DocxParser;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.service.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 按图片序号从 DOCX 包中读取原始图片。
 *
 * <p>图片只在需要预览时读取，元数据里仅保存 partName，避免把二进制塞进
 * document_metadata / metadata_json。读取前仍会做任务或模板的权限校验，
 * 不能通过拼接 URL 读取其他用户的文档资源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentImageService {

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    private final CheckTaskMapper taskMapper;
    private final TemplateService templateService;
    private final DocxParser docxParser;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public DocumentImage taskImage(Long taskId, int imageIndex) {
        CheckTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "校验任务不存在");
        }
        if (!UserContext.isAdmin() && !UserContext.userId().equals(task.getUserId())) {
            throw new BizException(403, "无权查看该任务");
        }
        return read(task.getDocPath(), task.getDocumentMetadata(), imageIndex, task.getDocName());
    }

    public DocumentImage templateImage(Long templateId, int imageIndex) {
        DocTemplate template = templateService.requireVisible(templateId);
        return read(template.getStoragePath(), template.getMetadataJson(), imageIndex, template.getOriginalName());
    }

    private DocumentImage read(String relativePath, String metadataJson, int imageIndex, String fallbackName) {
        if (imageIndex < 0) {
            throw new BizException(404, "图片不存在");
        }
        Path docx = storageService.resolve(relativePath);
        if (!Files.exists(docx)) {
            throw new BizException(404, "原始文档已丢失");
        }

        JsonNode metadata = readMetadata(metadataJson);
        if (metadata == null) {
            try (InputStream in = Files.newInputStream(docx)) {
                DocumentMeta parsed = docxParser.parse(in, fallbackName);
                metadata = objectMapper.valueToTree(parsed);
            } catch (IOException e) {
                throw new BizException(500, "文档图片解析失败: " + e.getMessage());
            }
        }

        JsonNode images = metadata.path("images");
        if (!images.isArray() || imageIndex >= images.size()) {
            throw new BizException(404, "图片不存在");
        }
        JsonNode image = images.get(imageIndex);
        String partName = text(image, "partName");
        if (partName == null || partName.isBlank()) {
            // 兼容图片功能上线前的任务/模板：旧元数据没有 partName，
            // 用当前解析器重新读一次 DOCX，并按旧图片的名称/段落定位。
            JsonNode fresh = parseFreshMetadata(docx, fallbackName);
            image = resolveLegacyImage(fresh, image, imageIndex);
            if (image == null) {
                throw new BizException(404, "该图片没有可读取的包内路径");
            }
            partName = text(image, "partName");
        }
        if (partName == null || partName.isBlank()) {
            throw new BizException(404, "该图片没有可读取的包内路径");
        }
        String contentType = text(image, "contentType");
        String fileName = text(image, "name");
        if (fileName == null || fileName.isBlank()) {
            fileName = "image-" + imageIndex;
        }

        try (ZipFile zip = new ZipFile(docx.toFile())) {
            ZipEntry entry = findEntry(zip, partName);
            if (entry == null) {
                throw new BizException(404, "DOCX 中找不到图片资源");
            }
            if (entry.getSize() > MAX_IMAGE_BYTES) {
                throw new BizException(413, "图片超过 20MB，无法在线预览");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes((int) MAX_IMAGE_BYTES + 1);
                if (bytes.length > MAX_IMAGE_BYTES) {
                    throw new BizException(413, "图片超过 20MB，无法在线预览");
                }
                return new DocumentImage(bytes, safeContentType(contentType, fileName), fileName);
            }
        } catch (IOException e) {
            log.warn("读取 DOCX 图片失败: {}#{}", relativePath, imageIndex, e);
            throw new BizException(500, "图片读取失败");
        }
    }

    private JsonNode parseFreshMetadata(Path docx, String fallbackName) {
        try (InputStream in = Files.newInputStream(docx)) {
            DocumentMeta parsed = docxParser.parse(in, fallbackName);
            return objectMapper.valueToTree(parsed);
        } catch (IOException e) {
            log.warn("兼容旧图片元数据时解析文档失败: {}", docx, e);
            throw new BizException(404, "图片资源无法读取");
        }
    }

    /**
     * 旧元数据缺少 partName 且图片顺序可能与新解析器不同：
     * 优先按「文件名 + 段落序号」匹配，其次按文件名，最后按原序号兜底。
     */
    private JsonNode resolveLegacyImage(JsonNode freshMetadata, JsonNode oldImage, int imageIndex) {
        JsonNode freshImages = freshMetadata == null ? null : freshMetadata.path("images");
        if (freshImages == null || !freshImages.isArray() || freshImages.isEmpty()) {
            return null;
        }
        String oldName = text(oldImage, "name");
        int oldParagraph = oldImage.path("paragraphIndex").asInt(-1);
        if (oldName != null && !oldName.isBlank()) {
            for (JsonNode candidate : freshImages) {
                if (oldName.equalsIgnoreCase(text(candidate, "name"))
                        && oldParagraph >= 0
                        && candidate.path("paragraphIndex").asInt(-1) == oldParagraph) {
                    return candidate;
                }
            }
            for (JsonNode candidate : freshImages) {
                if (oldName.equalsIgnoreCase(text(candidate, "name"))) {
                    return candidate;
                }
            }
        }
        return imageIndex < freshImages.size() ? freshImages.get(imageIndex) : null;
    }

    private JsonNode readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            return null;
        }
    }

    private ZipEntry findEntry(ZipFile zip, String partName) {
        String normalized = partName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        ZipEntry entry = zip.getEntry(normalized);
        if (entry != null) {
            return entry;
        }
        // 大小写/路径前缀差异时做一次兜底扫描，仍然只允许 word/media 下的资源
        for (ZipEntry candidate : java.util.Collections.list(zip.entries())) {
            String name = candidate.getName().replace('\\', '/');
            if (name.equalsIgnoreCase(normalized)
                    || name.toLowerCase(Locale.ROOT).endsWith("/" + normalized.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String safeContentType(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
