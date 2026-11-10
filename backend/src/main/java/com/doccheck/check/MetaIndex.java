package com.doccheck.check;

import com.doccheck.parser.model.*;

import java.util.ArrayList;
import java.util.List;

/** 元数据索引工具：把按节组织的 blocks 摊平成全局顺序列表，供各策略复用。 */
public final class MetaIndex {

    private MetaIndex() {
    }

    public static List<ParagraphMeta> paragraphs(DocumentMeta meta) {
        List<ParagraphMeta> result = new ArrayList<>();
        if (meta == null || meta.getSections() == null) {
            return result;
        }
        for (SectionMeta section : meta.getSections()) {
            if (section.getBlocks() == null) {
                continue;
            }
            for (BlockMeta block : section.getBlocks()) {
                if (block.getParagraph() != null) {
                    result.add(block.getParagraph());
                }
            }
        }
        return result;
    }

    public static List<TableMeta> tables(DocumentMeta meta) {
        List<TableMeta> result = new ArrayList<>();
        if (meta == null || meta.getSections() == null) {
            return result;
        }
        for (SectionMeta section : meta.getSections()) {
            if (section.getBlocks() == null) {
                continue;
            }
            for (BlockMeta block : section.getBlocks()) {
                if (block.getTable() != null) {
                    result.add(block.getTable());
                }
            }
        }
        return result;
    }

    public static List<SectionMeta> sections(DocumentMeta meta) {
        return meta == null || meta.getSections() == null ? List.of() : meta.getSections();
    }
}
