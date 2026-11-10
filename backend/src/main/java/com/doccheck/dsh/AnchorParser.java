package com.doccheck.dsh;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析模型返回的 location / anchor_path，统一为前端可用的定位信息。
 * 支持：body.p[3]、body.table[1].r[2].c[0]、header[0].p[1]、footer[0].p[1]。
 */
public final class AnchorParser {

    private static final Pattern BODY_P = Pattern.compile("body\\.p\\[(\\d+)]");
    private static final Pattern BODY_TABLE = Pattern.compile("body\\.table\\[(\\d+)]");
    private static final Pattern ROW = Pattern.compile("\\.r\\[(\\d+)]");
    private static final Pattern COL = Pattern.compile("\\.c\\[(\\d+)]");
    private static final Pattern HEADER_P = Pattern.compile("header\\[(\\d+)]\\.p\\[(\\d+)]");
    private static final Pattern FOOTER_P = Pattern.compile("footer\\[(\\d+)]\\.p\\[(\\d+)]");

    private AnchorParser() {
    }

    public record Anchor(String anchorPath, Integer sectionIndex, Integer paragraphIndex,
                         Integer tableIndex, Integer rowIndex, Integer colIndex,
                         Integer headerIndex, Integer footerIndex) {
    }

    public static Anchor parse(String text) {
        if (text == null) {
            return new Anchor(null, null, null, null, null, null, null, null);
        }
        String value = text.trim();
        Matcher header = HEADER_P.matcher(value);
        if (header.find()) {
            return new Anchor(value, 0, Integer.parseInt(header.group(2)), null, null, null,
                    Integer.parseInt(header.group(1)), null);
        }
        Matcher footer = FOOTER_P.matcher(value);
        if (footer.find()) {
            return new Anchor(value, 0, Integer.parseInt(footer.group(2)), null, null, null,
                    null, Integer.parseInt(footer.group(1)));
        }
        Matcher table = BODY_TABLE.matcher(value);
        if (table.find()) {
            Matcher row = ROW.matcher(value);
            Matcher col = COL.matcher(value);
            return new Anchor(value, null, null, Integer.parseInt(table.group(1)),
                    row.find() ? Integer.parseInt(row.group(1)) : null,
                    col.find() ? Integer.parseInt(col.group(1)) : null, null, null);
        }
        Matcher p = BODY_P.matcher(value);
        if (p.find()) {
            return new Anchor(value, null, Integer.parseInt(p.group(1)), null, null, null, null, null);
        }
        return new Anchor(value, null, null, null, null, null, null, null);
    }
}
