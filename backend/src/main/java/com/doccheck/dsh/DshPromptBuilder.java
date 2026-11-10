package com.doccheck.dsh;

import com.doccheck.config.DshProperties;
import com.doccheck.parser.model.DocumentMeta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * DSH 提示词组装器。
 * 系统提示词固定在 resources/prompts/format-check-system.txt，运行时只读加载，避免模型行为漂移；
 * 用户提示词动态注入模板元数据、待校验元数据、自定义规则与策略扩展要求。
 */
@Component
@RequiredArgsConstructor
public class DshPromptBuilder {

    private final ObjectMapper objectMapper;
    private final DshProperties properties;

    public String systemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/format-check-system.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载系统提示词 prompts/format-check-system.txt", e);
        }
    }

    public String userPrompt(DshCompareRequest request) {
        String templateJson = toJson(request.template());
        String targetJson = toJson(request.target());
        String guidance = request.promptGuidance() == null || request.promptGuidance().isEmpty()
                ? "按系统提示词的七个维度执行全量比对。"
                : String.join("\n", request.promptGuidance());
        String comments = formatComments(request.template());

        return """
                【任务】请以标准模板为唯一基准，对下面的待校验文档执行全量格式比对。
                【标准模板结构化元数据】
                %s
                【待校验文档结构化元数据】
                %s
                【模板批注（模板作者对相应章节的明确要求，优先级高于通用格式规则）】
                %s
                【本系统启用的扩展校验策略】
                %s
                【用户附加规则】
                %s

                【章节对齐要求】模板与目标文档中的“摘要/Abstract/第1章/1.1/参考文献/致谢”等章节，
                应按章节标题语义对齐后再比较，禁止仅凭全文段落序号把不同章节强行配对。
                若批注给出字数、关键词数量等数值要求，必须按目标文档的对应章节统计并判断。

                【数量不是格式】模板中的小标题、段落、表格、图片数量只是模板示例，目标文档可根据实际内容增加或减少。
                不得因“小标题数量超出/少于模板”“段落数量不同”“缺少某个示例小节”而判定格式错误；
                也不得输出仅以数量差异为依据的 diff。模板明确批注要求固定数量时除外。

                【定位路径强制约定】diff_list[].location 必须使用以下可解析格式之一，禁止只写自然语言：
                - 正文段落：body.p[段落全局序号]
                - 正文表格单元：body.table[表格全局序号].r[行号].c[列号]
                - 页眉段落：header[页眉序号].p[段内序号]
                - 页脚段落：footer[页脚序号].p[段内序号]
                段落与表格序号均从 0 开始。

                【输出】严格输出一个 JSON 对象，不要 Markdown 代码块，不要额外解释，结构必须为：
                {"summary":"...","total_diff_count":0,"diff_statistics":{"high_count":0,"middle_count":0,"low_count":0},
                 "diff_list":[{"diff_id":"D001","location":"body.p[0]","diff_type":"字体","severity":"high",
                 "element_type":"text","template_format":"宋体 小四","actual_format":"仿宋 五号",
                 "content_snippet":"...","suggestion":"..."}]}
                """.formatted(truncate(templateJson, properties.getMaxPromptChars() / 2),
                truncate(targetJson, properties.getMaxPromptChars() / 2),
                comments,
                guidance,
                request.customRules() == null || request.customRules().isBlank() ? "无" : request.customRules());
    }

    private String formatComments(DocumentMeta template) {
        if (template == null || template.getComments() == null || template.getComments().isEmpty()) {
            return "无";
        }
        StringBuilder value = new StringBuilder();
        int count = 0;
        for (var comment : template.getComments()) {
            if (comment.getText() == null || comment.getText().isBlank()) {
                continue;
            }
            if (count++ >= 40) {
                value.append("- ...（批注过多已截断）\n");
                break;
            }
            value.append("- ").append(comment.getAnchorPath() == null ? "" : comment.getAnchorPath());
            if (comment.getAuthor() != null && !comment.getAuthor().isBlank()) {
                value.append("（").append(comment.getAuthor()).append("）");
            }
            value.append("：")
                    .append(comment.getText().replaceAll("\\s+", " ").trim())
                    .append('\n');
        }
        return value.isEmpty() ? "无" : value.toString();
    }

    private String toJson(DocumentMeta meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("文档元数据序列化失败", e);
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)) + "\n...[元数据过长已截断，请基于已提供部分判断]";
    }
}
