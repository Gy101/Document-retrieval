package com.doccheck.controller;

import com.doccheck.service.DocumentImage;
import com.doccheck.service.DocumentImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * DOCX 内图片的受保护读取接口。
 *
 * <p>前端不直接暴露磁盘路径，也不在任务详情 JSON 里内联 base64；
 * 先拿元数据中的图片序号，再用这里按需取图。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentImageController {

    private final DocumentImageService imageService;

    @GetMapping("/tasks/{taskId}/images/{index}")
    public ResponseEntity<Resource> taskImage(@PathVariable Long taskId, @PathVariable int index) {
        return toResponse(imageService.taskImage(taskId, index));
    }

    @GetMapping("/templates/{templateId}/images/{index}")
    public ResponseEntity<Resource> templateImage(@PathVariable Long templateId, @PathVariable int index) {
        return toResponse(imageService.templateImage(templateId, index));
    }

    private ResponseEntity<Resource> toResponse(DocumentImage image) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(image.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(image.bytes()));
    }
}
