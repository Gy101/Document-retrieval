package com.doccheck.dsh;

import com.doccheck.parser.model.DocumentMeta;

import java.util.List;

/**
 * 一次格式比对请求。元数据对象由后端解析器生成，DSH 只负责语义对齐与差异判断。
 */
public record DshCompareRequest(
        DocumentMeta template,
        DocumentMeta target,
        String customRules,
        List<String> promptGuidance
) {
}
