package com.doccheck.check.strategy;

import com.doccheck.check.CheckContext;
import com.doccheck.check.DiffSupport;
import com.doccheck.check.FormatCheckStrategy;
import com.doccheck.check.MetaIndex;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.HeaderFooterMeta;
import com.doccheck.parser.model.ParagraphMeta;
import com.doccheck.parser.model.TableCellMeta;
import com.doccheck.parser.model.TableMeta;
import com.doccheck.parser.model.TableRowMeta;
import com.doccheck.rule.SearchRule;
import com.doccheck.service.SearchRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * YAML 自定义文本检索策略。
 *
 * <p>支持「必须出现 / 禁止出现」两种模式，作用范围覆盖正文段落、表格单元格、
 * 页眉与页脚；规则文件由 {@link SearchRuleService} 加载，命中结果会作为
 * “规则检索”类型进入统一差异列表。</p>
 */
@Service
@RequiredArgsConstructor
public class YamlSearchStrategy implements FormatCheckStrategy {

    private final SearchRuleService searchRuleService;

    @Override
    public String name() {
        return "yaml-search";
    }

    @Override
    public String dimension() {
        return "规则检索";
    }

    @Override
    public String promptGuidance() {
        return "自定义 YAML 检索规则已由本地引擎执行，关键词/正则的命中与缺失会单独输出为“规则检索”；"
                + "不要重复报告相同文本规则结果，只比较文档格式。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<SearchRuleService.CompiledRule> rules = searchRuleService.activeRules();
        if (rules.isEmpty() || context.getTargetMeta() == null) {
            return List.of();
        }
        List<TextUnit> units = collectTextUnits(context.getTargetMeta());
        if (units.isEmpty()) {
            return List.of();
        }

        List<FormatDiff> result = new ArrayList<>();
        for (SearchRuleService.CompiledRule compiled : rules) {
            SearchRule rule = compiled.rule();
            List<TextUnit> scoped = units.stream()
                    .filter(u -> "all".equals(rule.getScope()) || rule.getScope().equals(u.scope()))
                    .toList();
            if (scoped.isEmpty()) {
                continue;
            }
            if ("required".equals(rule.getMode())) {
                addRequiredIfMissing(scoped, rule, compiled, result);
            } else {
                addForbiddenHits(scoped, rule, compiled, result);
            }
        }
        return result;
    }

    private void addRequiredIfMissing(List<TextUnit> units, SearchRule rule,
                                      SearchRuleService.CompiledRule compiled, List<FormatDiff> result) {
        for (TextUnit unit : units) {
            if (compiled.pattern().matcher(unit.text()).find()) {
                return;
            }
        }
        TextUnit first = units.get(0);
        result.add(DiffSupport.of("规则检索", Severity.normalize(rule.getSeverity()), "text",
                first.anchor(), "全文 · " + rule.getName(),
                "必须包含「" + rule.getPattern() + "」", "未检索到", "",
                suggestion(rule, "文档中缺少规则要求的文本")));
    }

    private void addForbiddenHits(List<TextUnit> units, SearchRule rule,
                                  SearchRuleService.CompiledRule compiled, List<FormatDiff> result) {
        int returned = 0;
        for (TextUnit unit : units) {
            if (returned >= rule.getMaxHits()) {
                break;
            }
            Matcher matcher = compiled.pattern().matcher(unit.text());
            if (!matcher.find()) {
                continue;
            }
            String matched = matcher.group();
            if (matched == null || matched.isEmpty()) {
                continue;
            }
            result.add(DiffSupport.of("规则检索", Severity.normalize(rule.getSeverity()), "text",
                    unit.anchor(), unit.location() + " · " + rule.getName(),
                    "禁止出现「" + rule.getPattern() + "」", "命中「" + matched + "」",
                    snippet(unit.text(), matcher.start(), matcher.end()),
                    suggestion(rule, "删除或替换命中的文本")));
            returned++;
        }
    }

    private String suggestion(SearchRule rule, String fallback) {
        String suggestion = rule.getSuggestion();
        if (suggestion == null || suggestion.isBlank()) {
            suggestion = fallback;
        }
        if (rule.getMessage() != null && !rule.getMessage().isBlank()) {
            return rule.getMessage() + "；" + suggestion;
        }
        return suggestion;
    }

    private String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 30);
        int to = Math.min(text.length(), end + 50);
        return (from > 0 ? "…" : "") + text.substring(from, to).replaceAll("\\s+", " ").trim()
                + (to < text.length() ? "…" : "");
    }

    private List<TextUnit> collectTextUnits(DocumentMeta meta) {
        List<TextUnit> units = new ArrayList<>();

        for (ParagraphMeta paragraph : MetaIndex.paragraphs(meta)) {
            if (hasText(paragraph.getText())) {
                units.add(new TextUnit("body", paragraph.getText(),
                        "body.p[" + paragraph.getIndex() + "]",
                        "正文第 " + (paragraph.getIndex() + 1) + " 段"));
            }
        }

        for (TableMeta table : MetaIndex.tables(meta)) {
            if (table.getRows() == null) {
                continue;
            }
            List<TableRowMeta> rows = table.getRows();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                TableRowMeta row = rows.get(rowIndex);
                if (row == null || row.getCells() == null) {
                    continue;
                }
                List<TableCellMeta> cells = row.getCells();
                for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                    TableCellMeta cell = cells.get(cellIndex);
                    if (cell == null || !hasText(cell.getText())) {
                        continue;
                    }
                    units.add(new TextUnit("table", cell.getText(),
                            "body.table[" + table.getIndex() + "].r[" + rowIndex + "].c[" + cellIndex + "]",
                            "表格 " + (table.getIndex() + 1) + " 第 " + (rowIndex + 1)
                                    + " 行第 " + (cellIndex + 1) + " 列"));
                }
            }
        }

        addHeaderFooterUnits(units, meta.getHeaders(), "header", "页眉");
        addHeaderFooterUnits(units, meta.getFooters(), "footer", "页脚");
        return units;
    }

    private void addHeaderFooterUnits(List<TextUnit> units, List<HeaderFooterMeta> items,
                                      String scope, String label) {
        if (items == null) {
            return;
        }
        for (HeaderFooterMeta item : items) {
            if (item == null) {
                continue;
            }
            int containerIndex = item.getIndex();
            List<ParagraphMeta> paragraphs = item.getParagraphs();
            if (paragraphs != null && !paragraphs.isEmpty()) {
                for (int i = 0; i < paragraphs.size(); i++) {
                    ParagraphMeta paragraph = paragraphs.get(i);
                    if (paragraph != null && hasText(paragraph.getText())) {
                        units.add(new TextUnit(scope, paragraph.getText(),
                                scope + "[" + containerIndex + "].p[" + i + "]",
                                label + " " + (containerIndex + 1) + " 第 " + (i + 1) + " 段"));
                    }
                }
            } else if (hasText(item.getText())) {
                units.add(new TextUnit(scope, item.getText(),
                        scope + "[" + containerIndex + "].p[0]",
                        label + " " + (containerIndex + 1)));
            }
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private record TextUnit(String scope, String text, String anchor, String location) {
    }
}
