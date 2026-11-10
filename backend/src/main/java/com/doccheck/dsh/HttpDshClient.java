package com.doccheck.dsh;

import com.doccheck.common.BizException;
import com.doccheck.config.DshProperties;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DSH OpenAI 兼容 HTTP 客户端。
 *
 * <p>职责边界：
 * <ul>
 *   <li>固定系统提示词从 classpath 加载，动态用户提示词由 DshPromptBuilder 组装；</li>
 *   <li>超时、重试、HTTP 错误码、响应非 JSON、JSON 结构不合法都在这里收敛；</li>
 *   <li>模型响应只做「结构解析 + 归一化」，不在这里写业务规则。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dsh.mock", havingValue = "false")
public class HttpDshClient implements DshClient {

    private final DshProperties properties;
    private final DshPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public DshCompareResult compare(DshCompareRequest request) {
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("max_tokens", properties.getMaxTokens());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (properties.isJsonMode()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        String raw;
        try {
            String requestJson = objectMapper.writeValueAsString(payload);
            raw = callWithRetry(requestJson);
        } catch (IOException e) {
            throw new BizException(502, "DSH 请求序列化失败: " + e.getMessage(), e);
        }
        return parseResponse(raw);
    }

    @Override
    public String mode() {
        return "http";
    }

    private String callWithRetry(String body) {
        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = send(body);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                String message = "DSH 返回 HTTP " + status + ": " + abbreviate(response.body(), 300);
                if (status == 429 || status >= 500) {
                    last = new BizException(502, message);
                    log.warn("DSH 第 {}/{} 次调用失败，准备重试: {}", attempt, attempts, message);
                } else {
                    throw new BizException(502, message);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                last = new BizException(502, "DSH 网络调用失败: " + e.getMessage(), e);
                log.warn("DSH 第 {}/{} 次调用异常: {}", attempt, attempts, e.getMessage());
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(Math.max(0, properties.getRetryBackoffMs()) * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException(502, "DSH 调用被中断", e);
                }
            }
        }
        throw last == null ? new BizException(502, "DSH 调用失败") : last;
    }

    private HttpResponse<String> send(String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        String url = trimSlash(properties.getBaseUrl()) + normalizePath(properties.getChatPath());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private DshCompareResult parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(502, "DSH 返回空响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new BizException(502, "DSH 返回不是合法 JSON", e);
        }
        String content = extractContent(root);
        JsonNode result = parseModelJson(content);
        if (!result.has("diff_list") || !result.get("diff_list").isArray()) {
            throw new BizException(502, "DSH 返回 JSON 缺少 diff_list 数组");
        }

        List<FormatDiff> diffs = new ArrayList<>();
        for (JsonNode node : result.get("diff_list")) {
            diffs.add(toDiff(node));
        }
        int high = (int) diffs.stream().filter(d -> Severity.HIGH.equals(d.getSeverity())).count();
        int middle = (int) diffs.stream().filter(d -> Severity.MIDDLE.equals(d.getSeverity())).count();
        int low = diffs.size() - high - middle;
        JsonNode stats = result.path("diff_statistics");
        String summary = result.path("summary").asText("已完成格式比对");
        return DshCompareResult.builder()
                .summary(summary)
                .totalDiffCount(result.path("total_diff_count").asInt(diffs.size()))
                .highCount(stats.path("high_count").asInt(high))
                .middleCount(stats.path("middle_count").asInt(middle))
                .lowCount(stats.path("low_count").asInt(low))
                .diffs(diffs)
                .rawJson(raw)
                .build();
    }

    private String extractContent(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText("");
        if (content.isBlank()) {
            content = choice.path("text").asText("");
        }
        if (content.isBlank()) {
            content = root.path("output_text").asText("");
        }
        if (content.isBlank()) {
            throw new BizException(502, "DSH 响应缺少 choices[0].message.content");
        }
        return content;
    }

    private JsonNode parseModelJson(String content) {
        String text = stripCodeFence(content).trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException(502, "DSH 返回内容中未找到 JSON 对象");
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (IOException e) {
            throw new BizException(502, "DSH 返回的 JSON 无法解析: " + abbreviate(text, 200), e);
        }
    }

    private FormatDiff toDiff(JsonNode node) {
        String anchor = text(node, "anchor_path");
        String location = text(node, "location");
        if (anchor.isBlank()) {
            anchor = location;
        }
        AnchorParser.Anchor parsed = AnchorParser.parse(anchor);
        String diffType = text(node, "diff_type");
        if (diffType.isBlank()) {
            diffType = "其他";
        }
        String elementType = text(node, "element_type");
        if (elementType.isBlank()) {
            elementType = deriveElementType(anchor);
        }
        return FormatDiff.builder()
                .diffId(text(node, "diff_id"))
                .location(location.isBlank() ? anchor : location)
                .anchorPath(anchor)
                .sectionIndex(intOrNull(node, "section_index", parsed.sectionIndex()))
                .paragraphIndex(intOrNull(node, "paragraph_index", parsed.paragraphIndex()))
                .tableIndex(intOrNull(node, "table_index", parsed.tableIndex()))
                .rowIndex(intOrNull(node, "row_index", parsed.rowIndex()))
                .colIndex(intOrNull(node, "col_index", parsed.colIndex()))
                .charStart(intOrNull(node, "char_start", null))
                .charEnd(intOrNull(node, "char_end", null))
                .diffType(diffType)
                .severity(Severity.normalize(text(node, "severity")))
                .elementType(elementType)
                .templateFormat(text(node, "template_format"))
                .actualFormat(text(node, "actual_format"))
                .contentSnippet(abbreviate(text(node, "content_snippet"), 500))
                .suggestion(text(node, "suggestion"))
                .build();
    }

    private String deriveElementType(String anchor) {
        if (anchor == null) {
            return "text";
        }
        if (anchor.startsWith("header")) {
            return "header";
        }
        if (anchor.startsWith("footer")) {
            return "footer";
        }
        if (anchor.contains(".table")) {
            return "table";
        }
        return "text";
    }

    private Integer intOrNull(JsonNode node, String field, Integer fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        String text = value.asText();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String stripCodeFence(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine > 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text;
    }

    private String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/v1/chat/completions";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
