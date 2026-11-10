package com.doccheck.check;

import com.doccheck.config.ValidationProperties;
import com.doccheck.dsh.DshClient;
import com.doccheck.dsh.DshCompareRequest;
import com.doccheck.dsh.DshCompareResult;
import com.doccheck.parser.DocxParser;
import com.doccheck.parser.model.DocumentMeta;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultCheckPipeline extends AbstractCheckPipeline {

    private final DshClient dshClient;
    private final List<FormatCheckStrategy> strategies;

    public DefaultCheckPipeline(DocxParser docxParser, ValidationProperties validationProperties,
                                DshClient dshClient, List<FormatCheckStrategy> strategies) {
        super(docxParser, validationProperties);
        this.dshClient = dshClient;
        this.strategies = strategies;
    }

    @Override
    protected DshCompareResult compareWithModel(CheckContext context, DocumentMeta targetMeta) {
        List<String> guidance = strategies.stream()
                .map(s -> "【" + s.dimension() + "】" + s.promptGuidance())
                .toList();
        return dshClient.compare(new DshCompareRequest(
                context.getTemplateMeta(), targetMeta, context.getCustomRules(), guidance));
    }

    @Override
    protected List<FormatCheckStrategy> strategies() {
        return strategies;
    }
}
