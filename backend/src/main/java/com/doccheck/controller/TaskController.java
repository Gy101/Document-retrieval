package com.doccheck.controller;

import com.doccheck.common.ApiResponse;
import com.doccheck.common.PageResult;
import com.doccheck.domain.dto.CreateTaskResponse;
import com.doccheck.domain.dto.TaskDetailVO;
import com.doccheck.domain.dto.TaskVO;
import com.doccheck.service.TaskDownload;
import com.doccheck.service.TaskService;
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
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreateTaskResponse> create(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long templateId,
            @RequestParam(required = false) String customRules) {
        return ApiResponse.ok(taskService.create(file, templateId, customRules));
    }

    @GetMapping
    public ApiResponse<PageResult<TaskVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(taskService.list(page, size, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.detail(id));
    }

    /**
     * 返回任务原始 DOCX，供前端按 WPS 版式渲染。
     * 使用 inline 是为了浏览器/预览器能直接识别文档类型，权限仍由 TaskService 校验。
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> document(@PathVariable Long id) {
        TaskDownload download = taskService.document(id);
        Resource resource = new FileSystemResource(download.path());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<TaskVO> progress(@PathVariable Long id) {
        return ApiResponse.ok(taskService.progress(id));
    }
}
