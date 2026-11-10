package com.doccheck.controller;

import com.doccheck.common.ApiResponse;
import com.doccheck.common.BizException;
import com.doccheck.common.UserContext;
import com.doccheck.domain.dto.SearchRuleSnapshotVO;
import com.doccheck.rule.SearchRule;
import com.doccheck.service.SearchRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * YAML 检索规则管理接口。仅管理员可查看和热加载，普通用户只会在差异列表中看到结果。
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final SearchRuleService searchRuleService;

    @GetMapping("/search")
    public ApiResponse<SearchRuleSnapshotVO> current() {
        requireAdmin();
        return ApiResponse.ok(view(searchRuleService.current()));
    }

    @PostMapping("/search/reload")
    public ApiResponse<SearchRuleSnapshotVO> reload() {
        requireAdmin();
        return ApiResponse.ok(view(searchRuleService.reload()));
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BizException(403, "仅管理员可以管理 YAML 检索规则");
        }
    }

    private SearchRuleSnapshotVO view(SearchRuleService.Snapshot snapshot) {
        List<SearchRule> rules = snapshot.rules().stream()
                .map(SearchRuleService.CompiledRule::rule)
                .toList();
        return SearchRuleSnapshotVO.builder()
                .source(snapshot.source())
                .version(snapshot.version())
                .enabled(snapshot.enabled())
                .loadedAt(snapshot.loadedAt())
                .rules(rules)
                .errors(snapshot.errors())
                .build();
    }
}
