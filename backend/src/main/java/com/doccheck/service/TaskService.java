package com.doccheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.doccheck.check.CheckContext;
import com.doccheck.check.CheckOutcome;
import com.doccheck.common.BizException;
import com.doccheck.common.PageResult;
import com.doccheck.common.UserContext;
import com.doccheck.domain.dto.*;
import com.doccheck.domain.entity.CheckDiff;
import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.entity.DocTemplate;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.enums.TaskStatus;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.mapper.CheckDiffMapper;
import com.doccheck.mapper.CheckTaskMapper;
import com.doccheck.parser.DocxParser;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.service.storage.StorageService;
import com.doccheck.service.storage.StoredFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 校验任务服务：创建、分页查询、详情、进度、异步执行。
 *
 * <p>执行采用「模板方法管线 + 可靠队列」：Controller 只负责落库与入队，
 * 真正的解析/校验在 checkExecutor 中完成；任务状态与进度同时写 Redis 和 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter TASK_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CheckTaskMapper taskMapper;
    private final CheckDiffMapper diffMapper;
    private final TemplateService templateService;
    private final StorageService storageService;
    private final CheckQueueService queueService;
    private final com.doccheck.check.DefaultCheckPipeline pipeline;
    private final DocxParser docxParser;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CreateTaskResponse create(MultipartFile file, Long templateId, String customRules) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择待校验的 Word 文档");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new BizException(415, "仅支持 .docx 格式（不支持旧版 .doc）");
        }
        DocTemplate template = templateService.requireVisible(templateId);
        Long userId = UserContext.userId();

        StoredFile stored = storageService.store(file, "document");
        CheckTask task = new CheckTask();
        task.setTaskNo(generateTaskNo());
        task.setUserId(userId);
        task.setTemplateId(template.getId());
        task.setTemplateName(template.getName());
        task.setDocName(stored.originalName());
        task.setDocPath(stored.relativePath());
        task.setDocSize(stored.size());
        task.setDocHash(stored.sha256());
        task.setCustomRules(customRules);
        task.setStatus(TaskStatus.QUEUED.name());
        task.setProgress(0);
        task.setStage("排队中");
        task.setTotalDiffCount(0);
        task.setHighCount(0);
        task.setMiddleCount(0);
        task.setLowCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);
        taskMapper.insert(task);

        try {
            queueService.enqueue(task.getId());
            queueService.markStatus(task.getId(), TaskStatus.QUEUED.name(), 0, "排队中");
        } catch (RuntimeException e) {
            log.error("任务入队失败 {}", task.getId(), e);
            failTask(task, "任务入队失败，请确认 Redis 服务正常: " + e.getMessage(), 0, "入队失败");
            throw new BizException(503, "校验服务暂不可用，请稍后重试");
        }
        return new CreateTaskResponse(task.getId(), task.getTaskNo(), task.getStatus(), task.getProgress());
    }

    public PageResult<TaskVO> list(long page, long size, String status) {
        var query = Wrappers.<CheckTask>lambdaQuery();
        if (!UserContext.isAdmin()) {
            query.eq(CheckTask::getUserId, UserContext.userId());
        }
        if (StringUtils.hasText(status)) {
            try {
                query.eq(CheckTask::getStatus, TaskStatus.parse(status).name());
            } catch (IllegalArgumentException e) {
                throw new BizException(400, "未知任务状态: " + status);
            }
        }
        query.orderByDesc(CheckTask::getCreatedAt);
        Page<CheckTask> result = taskMapper.selectPage(new Page<>(page, size), query);
        List<TaskVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public TaskDetailVO detail(Long id) {
        CheckTask task = requireOwned(id);
        TaskDetailVO vo = new TaskDetailVO();
        copyBase(task, vo);
        JsonNode document = null;
        if (StringUtils.hasText(task.getDocumentMetadata())) {
            try {
                document = objectMapper.readTree(task.getDocumentMetadata());
            } catch (Exception e) {
                log.warn("任务 {} 文档元数据 JSON 损坏", id, e);
            }
        }
        // 旧任务的元数据没有图片容器/批注等字段，读取详情时补一次解析并回写，
        // 让历史任务无需重新校验也能正确显示图片/批注，后续请求直接复用新元数据。
        if (document != null && document.path("metadataVersion").asInt(0) < 6) {
            document = refreshDocumentMetadata(task, document);
        }
        vo.setDocument(document);
        List<CheckDiff> diffs = diffMapper.selectList(Wrappers.<CheckDiff>lambdaQuery()
                .eq(CheckDiff::getTaskId, id)
                .orderByAsc(CheckDiff::getSeq));
        vo.setDiffs(diffs.stream().map(this::toVO).toList());
        vo.setCustomRules(task.getCustomRules());
        return vo;
    }

    /** 兼容图片功能上线前生成的元数据：重新解析一次并持久化。失败则保留旧数据。 */
    private JsonNode refreshDocumentMetadata(CheckTask task, JsonNode fallback) {
        if (!StringUtils.hasText(task.getDocPath())) {
            return fallback;
        }
        try {
            Path path = storageService.resolve(task.getDocPath());
            if (!Files.exists(path)) {
                return fallback;
            }
            DocumentMeta meta = docxParser.parse(path, task.getDocName());
            JsonNode fresh = objectMapper.valueToTree(meta);
            CheckTask patch = new CheckTask();
            patch.setId(task.getId());
            patch.setDocumentMetadata(objectMapper.writeValueAsString(meta));
            taskMapper.updateById(patch);
            log.info("已升级任务 {} 的文档元数据到版本 {}", task.getId(), meta.getMetadataVersion());
            return fresh;
        } catch (Exception e) {
            log.warn("升级任务 {} 文档元数据失败，继续使用旧数据: {}", task.getId(), e.getMessage());
            return fallback;
        }
    }

    /** 轮询接口优先读 Redis，实时性更好；Redis 过期后回退数据库。 */
    public TaskVO progress(Long id) {
        CheckTask task = requireOwned(id);
        TaskVO vo = toVO(task);
        try {
            Map<Object, Object> progress = queueService.getProgress(id);
            if (progress != null && !progress.isEmpty()) {
                Object status = progress.get("status");
                Object value = progress.get("progress");
                Object stage = progress.get("stage");
                if (status != null) {
                    vo.setStatus(String.valueOf(status));
                }
                if (value != null) {
                    vo.setProgress(Integer.parseInt(String.valueOf(value)));
                }
                if (stage != null) {
                    vo.setStage(String.valueOf(stage));
                }
            }
        } catch (RuntimeException e) {
            log.debug("读取 Redis 进度失败，使用数据库状态: {}", e.getMessage());
        }
        return vo;
    }

    /**
     * 原始 DOCX：用于前端 docx-preview 做 WPS 风格版式预览。
     * 只有任务所有者或管理员能读取，避免通过任务 ID 越权下载他人论文。
     */
    public TaskDownload document(Long id) {
        CheckTask task = requireOwned(id);
        if (!StringUtils.hasText(task.getDocPath())) {
            throw new BizException(404, "任务原始文档不存在");
        }
        Path path = storageService.resolve(task.getDocPath());
        if (!Files.exists(path)) {
            throw new BizException(404, "任务原始文档已丢失");
        }
        String fileName = StringUtils.hasText(task.getDocName()) ? task.getDocName() : "document.docx";
        return new TaskDownload(path, fileName);
    }

    /**
     * 异步执行入口，必须自行吞掉异常并回写 FAILED，否则线程池只会留下日志。
     */
    public void execute(Long taskId) {
        CheckTask task = taskMapper.selectById(taskId);
        if (task == null) {
            queueService.ack(taskId);
            return;
        }
        if (TaskStatus.isTerminal(task.getStatus())) {
            queueService.ack(taskId);
            return;
        }
        long begin = System.currentTimeMillis();
        CheckTask running = new CheckTask();
        running.setId(taskId);
        running.setStatus(TaskStatus.RUNNING.name());
        running.setProgress(1);
        running.setStage("开始校验");
        running.setStartedAt(LocalDateTime.now());
        running.setUpdatedAt(LocalDateTime.now());
        running.setErrorMessage(null);
        taskMapper.updateById(running);
        queueService.markStatus(taskId, TaskStatus.RUNNING.name(), 1, "开始校验");
        try {
            CheckOutcome outcome = runPipeline(task, taskId);
            persistSuccess(task, outcome, begin);
            queueService.markStatus(taskId, TaskStatus.SUCCESS.name(), 100, "校验完成");
        } catch (Exception e) {
            log.error("校验任务失败 {}", taskId, e);
            CheckTask failed = taskMapper.selectById(taskId);
            failTask(failed == null ? task : failed, truncate(e.getMessage(), 1800), 100, "校验失败");
            queueService.markStatus(taskId, TaskStatus.FAILED.name(), 100, "校验失败");
        } finally {
            queueService.ack(taskId);
        }
    }

    private CheckOutcome runPipeline(CheckTask task, Long taskId) throws Exception {
        DocTemplate template = templateService.requireById(task.getTemplateId());
        DocumentMeta templateMeta = loadTemplateMeta(template);
        Path targetPath = storageService.resolve(task.getDocPath());
        if (!Files.exists(targetPath)) {
            throw new BizException(404, "待校验文件已丢失");
        }
        CheckContext context = CheckContext.builder()
                .taskId(taskId)
                .template(template)
                .templateMeta(templateMeta)
                .targetFilePath(targetPath.toString())
                .targetFileName(task.getDocName())
                .customRules(task.getCustomRules())
                .build();
        return pipeline.execute(context, (progress, stage) -> {
            int safe = Math.max(1, Math.min(99, progress));
            CheckTask patch = new CheckTask();
            patch.setId(taskId);
            patch.setProgress(safe);
            patch.setStage(stage);
            patch.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(patch);
            queueService.updateProgress(taskId, safe, stage);
        });
    }

    private DocumentMeta loadTemplateMeta(DocTemplate template) throws Exception {
        if (StringUtils.hasText(template.getMetadataJson()) && "SUCCESS".equals(template.getParseStatus())) {
            try {
                DocumentMeta cached = objectMapper.readValue(template.getMetadataJson(), DocumentMeta.class);
                // 解析器升级后必须强制刷新，否则任务会继续使用旧版元数据（例如缺少 Run 字体继承）。
                if (cached.getMetadataVersion() != null && cached.getMetadataVersion() >= 6) {
                    return cached;
                }
            } catch (Exception e) {
                log.warn("模板 {} 元数据缓存损坏，重新解析", template.getId(), e);
            }
        }
        DocumentMeta meta = docxParser.parse(storageService.resolve(template.getStoragePath()),
                template.getOriginalName());
        DocTemplate patch = new DocTemplate();
        patch.setId(template.getId());
        patch.setMetadataJson(objectMapper.writeValueAsString(meta));
        patch.setParseStatus("SUCCESS");
        patch.setParseMessage("任务执行时重新解析");
        patch.setUpdatedAt(LocalDateTime.now());
        templateService.saveParseCache(patch);
        return meta;
    }

    private void persistSuccess(CheckTask task, CheckOutcome outcome, long begin) throws Exception {
        String documentJson = objectMapper.writeValueAsString(outcome.getTargetMeta());
        String resultJson = outcome.getRawResultJson();
        List<FormatDiff> diffs = outcome.getDiffs() == null ? List.of() : outcome.getDiffs();
        transactionTemplate.executeWithoutResult(status -> {
            diffMapper.delete(Wrappers.<CheckDiff>lambdaQuery().eq(CheckDiff::getTaskId, task.getId()));
            for (FormatDiff diff : diffs) {
                CheckDiff entity = new CheckDiff();
                entity.setTaskId(task.getId());
                entity.setDiffNo(diff.getDiffId());
                entity.setSeq(diff.getSeq());
                entity.setLocation(truncate(diff.getLocation(), 255));
                entity.setAnchorPath(truncate(diff.getAnchorPath(), 255));
                entity.setSectionIndex(diff.getSectionIndex());
                entity.setParagraphIndex(diff.getParagraphIndex());
                entity.setTableIndex(diff.getTableIndex());
                entity.setRowIndex(diff.getRowIndex());
                entity.setColIndex(diff.getColIndex());
                entity.setCharStart(diff.getCharStart());
                entity.setCharEnd(diff.getCharEnd());
                entity.setDiffType(truncate(diff.getDiffType(), 64));
                entity.setSeverity(Severity.normalize(diff.getSeverity()));
                entity.setElementType(truncate(diff.getElementType(), 32));
                entity.setTemplateFormat(truncate(diff.getTemplateFormat(), 1000));
                entity.setActualFormat(truncate(diff.getActualFormat(), 1000));
                entity.setContentSnippet(truncate(diff.getContentSnippet(), 1000));
                entity.setSuggestion(truncate(diff.getSuggestion(), 1000));
                entity.setCreatedAt(LocalDateTime.now());
                diffMapper.insert(entity);
            }
            CheckTask success = new CheckTask();
            success.setId(task.getId());
            success.setStatus(TaskStatus.SUCCESS.name());
            success.setProgress(100);
            success.setStage("校验完成");
            success.setSummary(truncate(outcome.getSummary(), 2000));
            success.setTotalDiffCount(diffs.size());
            success.setHighCount(outcome.getHighCount());
            success.setMiddleCount(outcome.getMiddleCount());
            success.setLowCount(outcome.getLowCount());
            success.setDocumentMetadata(documentJson);
            success.setResultJson(resultJson);
            success.setDurationMs(System.currentTimeMillis() - begin);
            success.setFinishedAt(LocalDateTime.now());
            success.setUpdatedAt(LocalDateTime.now());
            success.setErrorMessage(null);
            taskMapper.updateById(success);
        });
    }

    private void failTask(CheckTask task, String message, int progress, String stage) {
        CheckTask failed = new CheckTask();
        failed.setId(task.getId());
        failed.setStatus(TaskStatus.FAILED.name());
        failed.setProgress(progress);
        failed.setStage(stage);
        failed.setErrorMessage(truncate(message, 2000));
        failed.setFinishedAt(LocalDateTime.now());
        failed.setUpdatedAt(LocalDateTime.now());
        failed.setDurationMs(task.getStartedAt() == null ? null
                : Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis());
        taskMapper.updateById(failed);
    }

    private CheckTask requireOwned(Long id) {
        CheckTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(404, "校验任务不存在");
        }
        if (!UserContext.isAdmin() && !UserContext.userId().equals(task.getUserId())) {
            throw new BizException(403, "无权查看该任务");
        }
        return task;
    }

    private TaskVO toVO(CheckTask task) {
        TaskVO vo = new TaskVO();
        copyBase(task, vo);
        return vo;
    }

    private void copyBase(CheckTask task, TaskVO vo) {
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTemplateId(task.getTemplateId());
        vo.setTemplateName(task.getTemplateName());
        vo.setDocName(task.getDocName());
        vo.setDocSize(task.getDocSize());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setStage(task.getStage());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setSummary(task.getSummary());
        vo.setTotalDiffCount(task.getTotalDiffCount());
        vo.setHighCount(task.getHighCount());
        vo.setMiddleCount(task.getMiddleCount());
        vo.setLowCount(task.getLowCount());
        vo.setDurationMs(task.getDurationMs());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
    }

    private DiffVO toVO(CheckDiff diff) {
        DiffVO vo = new DiffVO();
        vo.setId(diff.getId());
        vo.setDiffNo(diff.getDiffNo());
        vo.setSeq(diff.getSeq());
        vo.setLocation(diff.getLocation());
        vo.setAnchorPath(diff.getAnchorPath());
        vo.setSectionIndex(diff.getSectionIndex());
        vo.setParagraphIndex(diff.getParagraphIndex());
        vo.setTableIndex(diff.getTableIndex());
        vo.setRowIndex(diff.getRowIndex());
        vo.setColIndex(diff.getColIndex());
        vo.setCharStart(diff.getCharStart());
        vo.setCharEnd(diff.getCharEnd());
        vo.setDiffType(diff.getDiffType());
        vo.setSeverity(diff.getSeverity());
        vo.setElementType(diff.getElementType());
        vo.setTemplateFormat(diff.getTemplateFormat());
        vo.setActualFormat(diff.getActualFormat());
        vo.setContentSnippet(diff.getContentSnippet());
        vo.setSuggestion(diff.getSuggestion());
        return vo;
    }

    private String generateTaskNo() {
        return "DC" + LocalDateTime.now().format(TASK_NO_TIME)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
