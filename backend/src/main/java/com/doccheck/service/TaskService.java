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
import com.doccheck.service.storage.FileHashes;
import com.doccheck.service.storage.StorageService;
import com.doccheck.service.storage.StoredFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final DocxSecurityValidator securityValidator;

    @Value("${check.queue.max-retries:2}")
    private int maxRetries;

    public CreateTaskResponse create(MultipartFile file, Long templateId, String customRules) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择待校验的 Word 文档");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new BizException(415, "仅支持 .docx 格式（不支持旧版 .doc）");
        }
        DocTemplate template = templateService.requireVisible(templateId);
        securityValidator.validate(file);
        Long userId = UserContext.userId();

        // 先算指纹：相同内容只落盘一次，并复用历史解析缓存。
        String hash = FileHashes.sha256(file);
        StoredFile stored = reuseOrStore(file, hash, originalName);

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
        task.setRetryCount(0);
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

    private StoredFile reuseOrStore(MultipartFile file, String hash, String originalName) {
        CheckTask reusable = taskMapper.selectReusableFile(hash);
        if (reusable != null && StringUtils.hasText(reusable.getDocPath())) {
            try {
                Path existing = storageService.resolve(reusable.getDocPath());
                if (Files.exists(existing)) {
                    log.info("复用同指纹任务文件: task={}, hash={}", reusable.getId(), hash);
                    String contentType = file.getContentType() == null
                            ? "application/octet-stream" : file.getContentType();
                    return new StoredFile(reusable.getDocPath(), StringUtils.cleanPath(originalName),
                            file.getSize(), hash, contentType);
                }
            } catch (RuntimeException e) {
                log.warn("历史文件不可复用，重新落盘: {}", e.getMessage());
            }
        }
        return storageService.store(file, "document");
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
        return detail(id, false);
    }

    /** light=true 时只返回任务基础信息，元数据与差异走独立接口，避免大任务详情响应过重。 */
    public TaskDetailVO detail(Long id, boolean light) {
        CheckTask task = requireOwned(id);
        TaskDetailVO vo = new TaskDetailVO();
        copyBase(task, vo);
        vo.setCustomRules(task.getCustomRules());
        if (!light) {
            vo.setDocument(loadDocument(task));
            vo.setDiffs(loadDiffs(id, null, null, null).stream().map(this::toVO).toList());
        }
        return vo;
    }

    /** 文档格式元数据（含批注/图片锚点），按任务懒加载。 */
    public JsonNode documentMetadata(Long id) {
        return loadDocument(requireOwned(id));
    }

    /**
     * 差异分页：兼容旧版全量详情，同时支持按严重等级/类型/关键词过滤。
     * 关键词搜索覆盖位置、片段、模板值、实际值与建议。
     */
    public PageResult<DiffVO> diffPage(Long id, long page, long size, String severity,
                                       String type, String keyword) {
        requireOwned(id);
        long current = Math.max(1, page);
        long limit = Math.min(Math.max(1, size), 200);
        List<CheckDiff> rows = loadDiffs(id, severity, type, keyword);
        int from = (int) Math.min((current - 1) * limit, rows.size());
        int to = (int) Math.min(from + limit, rows.size());
        List<DiffVO> items = rows.subList(from, to).stream().map(this::toVO).toList();
        return PageResult.of(items, rows.size(), current, limit);
    }

    /** 过滤下拉可用的差异类型。 */
    public List<String> diffTypes(Long id) {
        requireOwned(id);
        return diffMapper.selectList(Wrappers.<CheckDiff>lambdaQuery()
                        .eq(CheckDiff::getTaskId, id)
                        .select(CheckDiff::getDiffType))
                .stream().map(CheckDiff::getDiffType).filter(StringUtils::hasText)
                .distinct().sorted().toList();
    }

    private JsonNode loadDocument(CheckTask task) {
        JsonNode document = null;
        if (StringUtils.hasText(task.getDocumentMetadata())) {
            try {
                document = objectMapper.readTree(task.getDocumentMetadata());
            } catch (Exception e) {
                log.warn("任务 {} 文档元数据 JSON 损坏", task.getId(), e);
            }
        }
        if (document != null && document.path("metadataVersion").asInt(0) < 6) {
            document = refreshDocumentMetadata(task, document);
        }
        return document;
    }

    private List<CheckDiff> loadDiffs(Long id, String severity, String type, String keyword) {
        var query = Wrappers.<CheckDiff>lambdaQuery().eq(CheckDiff::getTaskId, id);
        if (StringUtils.hasText(severity)) {
            String value = severity.trim().toLowerCase();
            if (Severity.HIGH.equals(value) || Severity.MIDDLE.equals(value) || Severity.LOW.equals(value)) {
                query.eq(CheckDiff::getSeverity, value);
            }
        }
        if (StringUtils.hasText(type)) {
            query.eq(CheckDiff::getDiffType, type.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(w -> w.like(CheckDiff::getLocation, value)
                    .or().like(CheckDiff::getContentSnippet, value)
                    .or().like(CheckDiff::getTemplateFormat, value)
                    .or().like(CheckDiff::getActualFormat, value)
                    .or().like(CheckDiff::getSuggestion, value));
        }
        query.orderByAsc(CheckDiff::getSeq);
        return diffMapper.selectList(query);
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
            CheckTask current = taskMapper.selectById(taskId);
            CheckTask effective = current == null ? task : current;
            int retried = effective.getRetryCount() == null ? 0 : effective.getRetryCount();
            boolean retryable = isRetryable(e);
            if (retryable && retried < maxRetries) {
                int next = retried + 1;
                CheckTask retry = new CheckTask();
                retry.setId(taskId);
                retry.setStatus(TaskStatus.QUEUED.name());
                retry.setProgress(0);
                retry.setStage("第 " + next + " 次重试排队");
                retry.setRetryCount(next);
                retry.setErrorMessage(truncate(e.getMessage(), 1800));
                retry.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(retry);
                try {
                    queueService.enqueue(taskId);
                    queueService.markStatus(taskId, TaskStatus.QUEUED.name(), 0, retry.getStage());
                    log.warn("任务 {} 第 {} 次重试入队: {}", taskId, next, e.getMessage());
                } catch (RuntimeException enqueueError) {
                    log.error("任务 {} 重试入队失败", taskId, enqueueError);
                    failTask(effective, "重试入队失败: " + enqueueError.getMessage(), 100, "重试入队失败", TaskStatus.DEAD);
                    queueService.deadLetter(taskId, "重试入队失败: " + enqueueError.getMessage());
                }
            } else {
                boolean dead = retryable && retried >= maxRetries;
                String message = dead
                        ? "任务重试 " + retried + " 次后仍失败: " + e.getMessage()
                        : truncate(e.getMessage(), 1800);
                failTask(effective, truncate(message, 1800), 100, dead ? "重试耗尽" : "校验失败",
                        dead ? TaskStatus.DEAD : TaskStatus.FAILED);
                if (dead) {
                    queueService.deadLetter(taskId, message);
                }
                queueService.markStatus(taskId, dead ? TaskStatus.DEAD.name() : TaskStatus.FAILED.name(),
                        100, dead ? "重试耗尽" : "校验失败");
            }
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
        DocumentMeta cachedTargetMeta = loadCachedTargetMeta(task.getDocHash());
        CheckContext context = CheckContext.builder()
                .taskId(taskId)
                .template(template)
                .templateMeta(templateMeta)
                .targetMeta(cachedTargetMeta)
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

    /** 同指纹的成功任务已有版本 6 元数据时，直接复用，跳过 POI 解析。 */
    private DocumentMeta loadCachedTargetMeta(String docHash) {
        if (!StringUtils.hasText(docHash)) {
            return null;
        }
        for (CheckTask candidate : taskMapper.selectMetadataCandidates(docHash)) {
            if (!StringUtils.hasText(candidate.getDocumentMetadata())) {
                continue;
            }
            try {
                DocumentMeta meta = objectMapper.readValue(candidate.getDocumentMetadata(), DocumentMeta.class);
                if (meta.getMetadataVersion() != null && meta.getMetadataVersion() >= 6) {
                    log.info("复用任务 {} 的解析缓存", candidate.getId());
                    return meta;
                }
            } catch (Exception e) {
                log.warn("任务 {} 解析缓存损坏，忽略", candidate.getId());
            }
        }
        return null;
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

    /** 只重试基础设施类故障；参数/权限/文件不存在等业务错误不应重试。 */
    private boolean isRetryable(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof BizException biz) {
                if (biz.getCode() == 502 || biz.getCode() == 503 || biz.getCode() == 504) {
                    return true;
                }
                if (biz.getCode() >= 400 && biz.getCode() < 500) {
                    return false;
                }
            }
            if (current instanceof java.io.IOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("timeout") || lower.contains("timed out")
                        || lower.contains("429") || lower.contains("502")
                        || lower.contains("503") || lower.contains("504")
                        || lower.contains("connection reset") || lower.contains("temporarily")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void failTask(CheckTask task, String message, int progress, String stage) {
        failTask(task, message, progress, stage, TaskStatus.FAILED);
    }

    private void failTask(CheckTask task, String message, int progress, String stage, TaskStatus status) {
        CheckTask failed = new CheckTask();
        failed.setId(task.getId());
        failed.setStatus(status.name());
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
