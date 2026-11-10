package com.doccheck.check;

import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.DocumentMeta;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CheckOutcome {

    private DocumentMeta targetMeta;
    private List<FormatDiff> diffs;
    private String summary;
    private int highCount;
    private int middleCount;
    private int lowCount;
    private String rawResultJson;
}
