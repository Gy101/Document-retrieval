package com.doccheck.service;

import com.doccheck.common.BizException;
import com.doccheck.config.ValidationProperties;
import com.doccheck.rule.SearchRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 加载用户自配置的 YAML 文本检索规则。
 *
 * <p>优先级：外部文件 {@code validation.search-rules-file}（默认 ./config/search-rules.yml）
 * → classpath 默认示例。外部文件修改后由定时任务自动热加载；管理接口也可手动 reload。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchRuleService {

    private static final String CLASSPATH_RULES = "rules/search-rules.yml";

    private final ValidationProperties validationProperties;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    private volatile long loadedStamp = Long.MIN_VALUE;

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.warn("YAML 检索规则初始化失败，当前不启用自定义规则: {}", e.getMessage());
        }
    }

    /** 当前生效快照，供策略与管理接口读取。 */
    public Snapshot current() {
        return snapshot.get();
    }

    public List<CompiledRule> activeRules() {
        Snapshot current = snapshot.get();
        return current.enabled() ? current.rules() : List.of();
    }

    /**
     * 重新读取并原子替换规则。解析失败时保留旧快照，并向调用方抛出 400。
     */
    public synchronized Snapshot reload() {
        try {
            LoadedConfig loaded = loadConfig();
            snapshot.set(loaded.snapshot());
            loadedStamp = loaded.stamp();
            log.info("YAML 检索规则已加载: source={}, enabled={}, rules={}, errors={}",
                    loaded.snapshot().source(), loaded.snapshot().enabled(),
                    loaded.snapshot().rules().size(), loaded.snapshot().errors().size());
            return loaded.snapshot();
        } catch (RuntimeException e) {
            throw e instanceof BizException biz ? biz
                    : new BizException(400, "YAML 检索规则加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 外部 YAML 修改后自动热加载。解析失败只记录日志并记住本次时间戳，
     * 避免同一个错误文件每 15 秒刷屏。
     */
    @Scheduled(fixedDelayString = "${validation.search-rules-watch-ms:15000}")
    public void watchExternalFile() {
        Path path = externalPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        long stamp;
        try {
            stamp = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return;
        }
        if (stamp == loadedStamp) {
            return;
        }
        try {
            LoadedConfig loaded = loadConfig();
            snapshot.set(loaded.snapshot());
            loadedStamp = loaded.stamp();
            log.info("检测到 YAML 规则文件变化，已热加载 {} 条规则", loaded.snapshot().rules().size());
        } catch (RuntimeException e) {
            loadedStamp = stamp;
            log.warn("YAML 规则文件变化后加载失败，继续使用旧规则: {}", e.getMessage());
        }
    }

    private LoadedConfig loadConfig() {
        Path external = externalPath();
        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                long stamp = Files.getLastModifiedTime(external).toMillis();
                return new LoadedConfig(parse(in, "file:" + external.toAbsolutePath().normalize()),
                        stamp);
            } catch (Exception e) {
                throw new IllegalArgumentException("无法读取 " + external + ": " + e.getMessage(), e);
            }
        }

        ClassPathResource resource = new ClassPathResource(CLASSPATH_RULES);
        if (!resource.exists()) {
            return new LoadedConfig(Snapshot.empty(), 0);
        }
        try (InputStream in = resource.getInputStream()) {
            return new LoadedConfig(parse(in, "classpath:" + CLASSPATH_RULES), 0);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取 classpath:" + CLASSPATH_RULES + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Snapshot parse(InputStream in, String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(in);
        if (loaded == null) {
            return new Snapshot(source, 1, true, LocalDateTime.now(), List.of(), List.of());
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("YAML 根节点必须是对象（包含 version/enabled/rules）");
        }

        int version = intValue(root.get("version"), 1);
        boolean enabled = boolValue(root.get("enabled"), true);
        Object rawRules = root.get("rules");
        if (rawRules != null && !(rawRules instanceof List<?>)) {
            throw new IllegalArgumentException("rules 必须是数组");
        }

        List<CompiledRule> rules = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (rawRules instanceof List<?> list) {
            int index = 0;
            for (Object item : list) {
                index++;
                if (!(item instanceof Map<?, ?> map)) {
                    errors.add("第 " + index + " 条规则不是对象，已跳过");
                    continue;
                }
                try {
                    CompiledRule compiled = compile((Map<String, Object>) map, index);
                    if (compiled != null) {
                        rules.add(compiled);
                    }
                } catch (RuntimeException e) {
                    errors.add("第 " + index + " 条规则无效: " + e.getMessage());
                }
            }
        }

        return new Snapshot(source, version, enabled, LocalDateTime.now(), List.copyOf(rules), List.copyOf(errors));
    }

    private CompiledRule compile(Map<String, Object> map, int index) {
        SearchRule rule = new SearchRule();
        rule.setId(text(map.get("id"), "rule-" + index));
        rule.setName(text(map.get("name"), rule.getId()));
        rule.setPattern(text(map.get("pattern"), ""));
        rule.setMatch(normalize(text(map.get("match"), "contains"), "contains"));
        rule.setMode(normalize(text(map.get("mode"), "forbidden"), "forbidden"));
        rule.setScope(normalize(text(map.get("scope"), "all"), "all"));
        rule.setSeverity(normalize(text(map.get("severity"), "middle"), "middle"));
        rule.setMessage(text(map.get("message"), ""));
        rule.setSuggestion(text(map.get("suggestion"), "请按规则要求调整文档"));
        rule.setCaseSensitive(boolValue(map.get("caseSensitive"), false));
        rule.setMaxHits(Math.max(1, Math.min(50, intValue(map.get("maxHits"), 5))));
        rule.setEnabled(boolValue(map.get("enabled"), true));

        if (!rule.isEnabled()) {
            return null;
        }
        if (rule.getPattern().isBlank()) {
            throw new IllegalArgumentException("pattern 不能为空");
        }
        if (!List.of("contains", "regex").contains(rule.getMatch())) {
            throw new IllegalArgumentException("match 只支持 contains / regex");
        }
        if (!List.of("required", "forbidden").contains(rule.getMode())) {
            throw new IllegalArgumentException("mode 只支持 required / forbidden");
        }
        if (!List.of("body", "table", "header", "footer", "all").contains(rule.getScope())) {
            throw new IllegalArgumentException("scope 只支持 body / table / header / footer / all");
        }

        int flags = Pattern.UNICODE_CASE;
        if (!rule.isCaseSensitive()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        try {
            String expression = "regex".equals(rule.getMatch())
                    ? rule.getPattern()
                    : Pattern.quote(rule.getPattern());
            return new CompiledRule(rule, Pattern.compile(expression, flags));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式无效: " + e.getDescription());
        }
    }

    private Path externalPath() {
        String configured = validationProperties.getSearchRulesFile();
        if (configured == null || configured.isBlank()) {
            return Path.of("config", "search-rules.yml").toAbsolutePath().normalize();
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 已编译规则。Pattern 只在后端使用，不参与 JSON 序列化。 */
    public record CompiledRule(SearchRule rule, Pattern pattern) {
    }

    public record Snapshot(String source, int version, boolean enabled, LocalDateTime loadedAt,
                           List<CompiledRule> rules, List<String> errors) {

        public static Snapshot empty() {
            return new Snapshot("none", 0, false, LocalDateTime.now(), List.of(), List.of());
        }
    }

    private record LoadedConfig(Snapshot snapshot, long stamp) {
    }
}
