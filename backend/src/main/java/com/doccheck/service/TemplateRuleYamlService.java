package com.doccheck.service;

import com.doccheck.common.BizException;
import com.doccheck.domain.entity.DocTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据模板的目录/章节结构生成一份可直接使用的 YAML 检索规则。
 *
 * <p>生成结果只包含规则文件本身，不修改当前生效规则。默认所有规则都关闭，
 * 避免用户直接启用后对历史模板产生大量“缺少章节”的误报；用户下载后按需打开
 * {@code enabled} 即可。</p>
 */
@Service
@RequiredArgsConstructor
public class TemplateRuleYamlService {

    private static final int MAX_SECTION_RULES = 40;
    private static final String SPACE = "[\\s\\u3000]*";

    private static final Pattern CHAPTER = Pattern.compile("^第[一二三四五六七八九十百千万0-9]+章\\s*(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+){0,2})\\s+(.+)$");
    private static final Pattern SPECIAL = Pattern.compile("^(摘\\s*要|ABSTRACT|参考文献|致\\s*谢|附\\s*录.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_NO = Pattern.compile("^[0-9IVXLCivxlc]+$");

    private final TemplateService templateService;

    public GeneratedRuleYaml generate(Long templateId) {
        DocTemplate template = templateService.requireVisible(templateId);
        JsonNode metadata = templateService.metadata(templateId);
        List<SectionSeed> sections = extractSections(metadata);
        String yaml = buildYaml(template, metadata, sections);
        return new GeneratedRuleYaml(fileName(template), yaml);
    }

    private List<SectionSeed> extractSections(JsonNode metadata) {
        Map<String, SectionSeed> unique = new LinkedHashMap<>();
        for (JsonNode section : metadata.path("sections")) {
            for (JsonNode block : section.path("blocks")) {
                JsonNode paragraph = block.path("paragraph");
                if (paragraph.isMissingNode()) {
                    continue;
                }
                String text = paragraph.path("text").asText("");
                SectionSeed seed = parseTocLine(text);
                if (seed != null && !seed.title().isBlank()) {
                    unique.putIfAbsent(normalizeKey(seed.title()), seed);
                }
            }
        }
        if (!unique.isEmpty()) {
            List<SectionSeed> all = new ArrayList<>(unique.values());
            if (all.size() <= MAX_SECTION_RULES) {
                return all;
            }
            // 规则太多时优先保留章和二级节，避免生成文件难以维护。
            List<SectionSeed> shallow = all.stream().filter(s -> s.depth() <= 2).toList();
            return (shallow.size() > MAX_SECTION_RULES ? shallow : all).subList(0, MAX_SECTION_RULES);
        }
        return fallbackSpecialSections(metadata);
    }

    /**
     * 解析目录行，例如：
     * <ul>
     *   <li>{@code 第1章 绪论\t1}</li>
     *   <li>{@code 1.2 研究目的和意义\t1}</li>
     *   <li>{@code 摘要\tI}</li>
     * </ul>
     * 只接受带制表符且末尾为页码的目录行，避免把正文段落误识别为章节。
     */
    private SectionSeed parseTocLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int tab = raw.indexOf('\t');
        if (tab < 0) {
            return null;
        }
        String left = raw.substring(0, tab).trim();
        String right = raw.substring(tab + 1).trim();
        if (!PAGE_NO.matcher(right).matches() || left.isBlank()) {
            return null;
        }
        String display = normalizeDisplay(left);
        if (display.equals("目录")) {
            return null;
        }

        Matcher chapter = CHAPTER.matcher(display);
        if (chapter.matches()) {
            return new SectionSeed(normalizeDisplay(chapter.group(1)), 1);
        }
        Matcher numbered = NUMBERED.matcher(display);
        if (numbered.matches()) {
            int depth = (int) numbered.group(1).chars().filter(c -> c == '.').count() + 1;
            return new SectionSeed(normalizeDisplay(numbered.group(2)), Math.min(depth, 3));
        }
        Matcher special = SPECIAL.matcher(display);
        if (special.matches()) {
            return new SectionSeed(normalizeDisplay(special.group(1)), 1);
        }
        return null;
    }

    private List<SectionSeed> fallbackSpecialSections(JsonNode metadata) {
        Map<String, SectionSeed> unique = new LinkedHashMap<>();
        for (JsonNode section : metadata.path("sections")) {
            for (JsonNode block : section.path("blocks")) {
                JsonNode paragraph = block.path("paragraph");
                String text = normalizeDisplay(paragraph.path("text").asText(""));
                if (text.length() > 0 && text.length() <= 30 && SPECIAL.matcher(text).matches()) {
                    unique.putIfAbsent(normalizeKey(text), new SectionSeed(text, 1));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String buildYaml(DocTemplate template, JsonNode metadata, List<SectionSeed> sections) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("enabled", true);

        List<Map<String, Object>> rules = new ArrayList<>();
        int index = 1;
        for (SectionSeed seed : sections) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", "tpl-section-" + index++);
            rule.put("name", "必需章节：" + seed.title());
            rule.put("pattern", regexForTitle(seed.title()));
            rule.put("match", "regex");
            rule.put("mode", "required");
            rule.put("scope", "body");
            rule.put("severity", severity(seed));
            rule.put("message", "模板目录要求包含「" + seed.title() + "」章节");
            rule.put("suggestion", "请按模板补充「" + seed.title() + "」章节");
            rule.put("caseSensitive", false);
            rule.put("maxHits", 1);
            rule.put("enabled", false);
            rules.add(rule);
        }

        // 额外附带占位符检查。和章节规则一样默认关闭，用户可按需要启用。
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("id", "tpl-placeholder");
        placeholder.put("name", "未替换占位符");
        placeholder.put("pattern", "(X{3,}|TODO|待补充|请填写)");
        placeholder.put("match", "regex");
        placeholder.put("mode", "forbidden");
        placeholder.put("scope", "all");
        placeholder.put("severity", "middle");
        placeholder.put("message", "模板中仍存在未替换的占位符");
        placeholder.put("suggestion", "将占位符替换为实际内容后再提交");
        placeholder.put("caseSensitive", false);
        placeholder.put("maxHits", 10);
        placeholder.put("enabled", false);
        rules.add(placeholder);

        root.put("rules", rules);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setWidth(200);
        options.setAllowUnicode(true);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        String body = new Yaml(options).dump(root);
        return header(template, metadata, sections.size()) + body;
    }

    private String header(DocTemplate template, JsonNode metadata, int sectionRules) {
        JsonNode stats = metadata.path("statistics");
        JsonNode page = metadata.path("sections").path(0).path("pageSetup");
        String pageLine = "";
        if (!page.isMissingNode()) {
            pageLine = String.format(Locale.ROOT,
                    "# 页面：%s · %.1f × %.1f pt%n# 页边距：上 %.1f / 下 %.1f / 左 %.1f / 右 %.1f pt%n",
                    page.path("orientation").asText(""), page.path("widthPt").asDouble(),
                    page.path("heightPt").asDouble(), page.path("topMarginPt").asDouble(),
                    page.path("bottomMarginPt").asDouble(), page.path("leftMarginPt").asDouble(),
                    page.path("rightMarginPt").asDouble());
        }
        return "# ============================================================\n"
                + "# 由模板自动生成的检索规则\n"
                + "# 模板：" + template.getName() + "（ID: " + template.getId() + "）\n"
                + "# 原文件：" + template.getOriginalName() + "\n"
                + "# 生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n"
                + "# 结构：" + stats.path("sectionCount").asInt() + " 节 / "
                + stats.path("paragraphCount").asInt() + " 段落 / "
                + stats.path("tableCount").asInt() + " 表格 / "
                + stats.path("imageCount").asInt() + " 图片\n"
                + pageLine
                + "# 生成章节规则：" + sectionRules + " 条（全部默认关闭）\n"
                + "#\n"
                + "# 使用方式：\n"
                + "#   1. 将本文件保存为 backend/config/search-rules.yml；\n"
                + "#   2. 按需把规则的 enabled 改为 true；\n"
                + "#   3. 保存后约 15 秒热加载，或调用 POST /api/rules/search/reload。\n"
                + "# ============================================================\n";
    }

    private String severity(SectionSeed seed) {
        if (seed.depth() == 1) {
            return "middle";
        }
        return seed.depth() == 2 ? "low" : "low";
    }

    private String regexForTitle(String title) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.isWhitespace(c) || c == '\u3000') {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SPACE);
            }
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        if (sb.length() == 0) {
            throw new BizException(400, "模板目录中存在空章节标题，无法生成规则");
        }
        return sb.toString();
    }

    private String normalizeDisplay(String value) {
        return value == null ? "" : value.replace('\u3000', ' ')
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeKey(String title) {
        return title.replaceAll("[\\s\\u3000]", "").toLowerCase(Locale.ROOT);
    }

    private String fileName(DocTemplate template) {
        String name = template.getName() == null ? "template" : template.getName();
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (name.isBlank()) {
            name = "template-" + template.getId();
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name + "-检索规则.yml";
    }

    private record SectionSeed(String title, int depth) {
    }
}
