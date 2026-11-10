package com.doccheck.dsh;

import com.doccheck.domain.model.FormatDiff;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DshCompareResult {

    private String summary;
    private int totalDiffCount;
    private int highCount;
    private int middleCount;
    private int lowCount;
    private List<FormatDiff> diffs;

    /** 原始响应 JSON，用于审计；Mock 模式下为本地生成结果 */
    private String rawJson;
}
