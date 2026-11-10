package com.doccheck.domain.dto;

import com.doccheck.rule.SearchRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** YAML 检索规则管理接口的只读视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRuleSnapshotVO {

    /** 实际来源：file:... 或 classpath:... */
    private String source;

    private int version;

    /** 全局开关。 */
    private boolean enabled;

    private LocalDateTime loadedAt;

    private List<SearchRule> rules;

    /** 被跳过的无效规则说明，便于排查 YAML 配置。 */
    private List<String> errors;
}
