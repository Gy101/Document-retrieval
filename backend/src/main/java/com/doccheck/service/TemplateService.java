package com.doccheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.doccheck.common.BizException;
import com.doccheck.common.PageResult;
import com.doccheck.common.UserContext;
import com.doccheck.domain.dto.TemplateVO;
import com.doccheck.domain.entity.DocTemplate;
import com.doccheck.domain.enums.TemplateScope;
import com.doccheck.mapper.DocTemplateMapper;
import com.doccheck.parser.DocxParser;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.service.storage.StorageService;
import com.doccheck.service.storage.StoredFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 模板库服务：公共模板 + 个人模板。
 *
 * <p>上传时同步做一次「预解析」，把 POI 提取的格式元数据缓存到 metadata_json。
 * 校验任务启动时优先读缓存，模板被反复使用时无需重复解析，显著缩短任务耗时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final DocTemplateMapper templateMapper;
    private final StorageService storageService;
    private final DocxParser docxParser;
    private final ObjectMapper objectMapper;

    public PageResult<TemplateVO> list(long page, long size, String scope, String keyword) {
        LambdaQueryWrapper<DocTemplate> query = Wrappers.<DocTemplate>lambdaQuery();
        if (!UserContext.isAdmin()) {
            Long userId = UserContext.userId();
            if (StringUtils.hasText(scope) && "PERSONAL".equalsIgnoreCase(scope)) {
                query.eq(DocTemplate::getOwnerId, userId);
            } else if (StringUtils.hasText(scope) && "PUBLIC".equalsIgnoreCase(scope)) {
                query.eq(DocTemplate::getScope, "PUBLIC");
            } else {
                query.and(w -> w.eq(DocTemplate::getScope, "PUBLIC").or().eq(DocTemplate::getOwnerId, userId));
            }
        } else if (StringUtils.hasText(scope) && TemplateScope.isValid(scope)) {
            query.eq(DocTemplate::getScope, scope.toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(DocTemplate::getName, keyword.trim())
                    .or().like(DocTemplate::getDescription, keyword.trim())
                    .or().like(DocTemplate::getOriginalName, keyword.trim()));
        }
        query.orderByDesc(DocTemplate::getCreatedAt);
        Page<DocTemplate> result = templateMapper.selectPage(new Page<>(page, size), query);
        List<TemplateVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /**
     * 上传并预解析模板。解析失败不回滚上传，而是记录 FAILED 与原因，
     * 用户可以在模板列表中看到状态并在校验时触发重新解析。
     */
    public TemplateVO create(MultipartFile file, String name, String description, String category, String scope) {
        validateDocx(file);
        String actualScope = StringUtils.hasText(scope) ? scope.toUpperCase(Locale.ROOT) : "PERSONAL";
        if (!TemplateScope.isValid(actualScope)) {
            throw new BizException(400, "模板范围只能是 PUBLIC 或 PERSONAL");
        }
        if ("PUBLIC".equals(actualScope) && !UserContext.isAdmin()) {
            throw new BizException(403, "只有管理员可以上传公共模板");
        }
        Long userId = UserContext.userId();
        StoredFile stored = storageService.store(file, "template");

        String displayName = StringUtils.hasText(name) ? name.trim()
                : stripExtension(stored.originalName());
        if (displayName.length() > 120) {
            throw new BizException(400, "模板名称不能超过 120 个字符");
        }

        DocTemplate template = new DocTemplate();
        template.setName(displayName);
        template.setDescription(description);
        template.setCategory(StringUtils.hasText(category) ? category.trim() : "通用");
        template.setScope(actualScope);
        template.setOwnerId(userId);
        template.setOriginalName(stored.originalName());
        template.setStoragePath(stored.relativePath());
        template.setFileSize(stored.size());
        template.setFileHash(stored.sha256());
        template.setContentType(stored.contentType());
        template.setParseStatus("PENDING");
        template.setMetadataVersion(3);
        template.setDownloadCount(0);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template.setDeleted(0);

        try {
            DocumentMeta meta = docxParser.parse(storageService.resolve(stored.relativePath()), stored.originalName());
            template.setMetadataJson(objectMapper.writeValueAsString(meta));
            template.setParseStatus("SUCCESS");
            template.setParseMessage("预解析完成");
        } catch (Exception e) {
            log.warn("模板预解析失败: {}", stored.originalName(), e);
            template.setParseStatus("FAILED");
            template.setParseMessage(truncate("预解析失败: " + e.getMessage(), 500));
        }
        templateMapper.insert(template);
        return toVO(template);
    }

    /** 返回可见模板实体；用于校验任务创建与权限判断。 */
    public DocTemplate requireVisible(Long id) {
        DocTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(404, "模板不存在或已删除");
        }
        if (!UserContext.isAdmin()
                && !"PUBLIC".equalsIgnoreCase(template.getScope())
                && !UserContext.userId().equals(template.getOwnerId())) {
            throw new BizException(403, "无权访问该模板");
        }
        return template;
    }

    /**
     * 任务线程内按 ID 读取模板，不做用户上下文校验。
     * 任务创建阶段已经校验过模板可见性，异步线程没有 ThreadLocal 用户，不能再走 requireVisible。
     */
    public DocTemplate requireById(Long id) {
        DocTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(404, "模板不存在或已删除");
        }
        return template;
    }

    /** 回写任务执行时重新解析出的模板元数据缓存。 */
    public void saveParseCache(DocTemplate patch) {
        templateMapper.updateById(patch);
    }

    /** 获取模板元数据，缓存缺失或解析失败时自动重新解析并回写缓存。 */
    public JsonNode metadata(Long id) {
        DocTemplate template = requireVisible(id);
        if (!StringUtils.hasText(template.getMetadataJson())
                || "FAILED".equals(template.getParseStatus())
                || template.getMetadataVersion() == null
                || template.getMetadataVersion() < 6) {
            refreshMetadata(template);
        }
        try {
            return objectMapper.readTree(template.getMetadataJson());
        } catch (Exception e) {
            throw new BizException(500, "模板元数据损坏，请重新上传模板");
        }
    }

    /**
     * 预览用：读取原始 DOCX，不增加下载次数。
     */
    public TemplateDownload document(Long id) {
        DocTemplate template = requireVisible(id);
        Path path = storageService.resolve(template.getStoragePath());
        if (!Files.exists(path)) {
            throw new BizException(404, "模板文件已丢失，请重新上传");
        }
        return new TemplateDownload(path, template.getOriginalName());
    }

    public TemplateDownload download(Long id) {
        DocTemplate template = requireVisible(id);
        Path path = storageService.resolve(template.getStoragePath());
        if (!Files.exists(path)) {
            throw new BizException(404, "模板文件已丢失，请重新上传");
        }
        templateMapper.update(null, Wrappers.<DocTemplate>lambdaUpdate()
                .eq(DocTemplate::getId, id)
                .setSql("download_count = download_count + 1"));
        return new TemplateDownload(path, template.getOriginalName());
    }

    public void delete(Long id) {
        DocTemplate template = requireVisible(id);
        boolean admin = UserContext.isAdmin();
        if (!admin && !UserContext.userId().equals(template.getOwnerId())) {
            throw new BizException(403, "只能删除自己上传的个人模板");
        }
        if ("PUBLIC".equalsIgnoreCase(template.getScope()) && !admin) {
            throw new BizException(403, "只有管理员可以删除公共模板");
        }
        templateMapper.deleteById(id);
        storageService.delete(template.getStoragePath());
    }

    private void refreshMetadata(DocTemplate template) {
        try {
            DocumentMeta meta = docxParser.parse(storageService.resolve(template.getStoragePath()),
                    template.getOriginalName());
            template.setMetadataJson(objectMapper.writeValueAsString(meta));
            template.setParseStatus("SUCCESS");
            template.setParseMessage("重新解析完成");
            template.setMetadataVersion(meta.getMetadataVersion() == null ? 3 : meta.getMetadataVersion());
        } catch (Exception e) {
            template.setParseStatus("FAILED");
            template.setParseMessage(truncate("解析失败: " + e.getMessage(), 500));
            throw new BizException(422, "模板解析失败，请确认文件是有效的 .docx: " + e.getMessage());
        } finally {
            template.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(template);
        }
    }

    private TemplateVO toVO(DocTemplate template) {
        TemplateVO vo = new TemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setDescription(template.getDescription());
        vo.setCategory(template.getCategory());
        vo.setScope(template.getScope());
        vo.setOwnerId(template.getOwnerId());
        vo.setOriginalName(template.getOriginalName());
        vo.setFileSize(template.getFileSize());
        vo.setParseStatus(template.getParseStatus());
        vo.setParseMessage(template.getParseMessage());
        vo.setMetadataVersion(template.getMetadataVersion());
        vo.setDownloadCount(template.getDownloadCount());
        vo.setCreatedAt(template.getCreatedAt());
        return vo;
    }

    private void validateDocx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的 Word 文档");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".docx")) {
            throw new BizException(415, "仅支持 .docx 格式（不支持旧版 .doc）");
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : "未命名模板";
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
