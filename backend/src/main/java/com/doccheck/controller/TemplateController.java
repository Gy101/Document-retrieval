package com.doccheck.controller;

import com.doccheck.common.ApiResponse;
import com.doccheck.common.PageResult;
import com.doccheck.domain.dto.TemplateVO;
import com.doccheck.service.GeneratedRuleYaml;
import com.doccheck.service.TemplateDownload;
import com.doccheck.service.TemplateRuleYamlService;
import com.doccheck.service.TemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateRuleYamlService templateRuleYamlService;

    @GetMapping
    public ApiResponse<PageResult<TemplateVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(templateService.list(page, size, scope, keyword));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(templateService.create(file, name, description, category, scope));
    }

    @GetMapping("/{id}/metadata")
    public ApiResponse<JsonNode> metadata(@PathVariable Long id) {
        return ApiResponse.ok(templateService.metadata(id));
    }

    /**
     * 模板库 WPS 风格预览：inline 返回原始 DOCX，不累加下载次数。
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> document(@PathVariable Long id) {
        TemplateDownload document = templateService.document(id);
        Resource resource = new FileSystemResource(document.path());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    /**
     * 根据模板目录生成 YAML 检索规则，以附件形式下载。
     * 不写入服务器配置，避免一次误点直接改变全局校验规则。
     */
    @GetMapping("/{id}/rules.yaml")
    public ResponseEntity<byte[]> rulesYaml(@PathVariable Long id) {
        GeneratedRuleYaml generated = templateRuleYamlService.generate(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(generated.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/yaml;charset=UTF-8"))
                .body(generated.content().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        TemplateDownload download = templateService.download(id);
        Resource resource = new FileSystemResource(download.path());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResponse.ok();
    }
}
