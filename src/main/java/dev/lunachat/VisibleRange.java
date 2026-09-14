package dev.lunachat;

import java.util.List;

/** Map wrapped rows back to logical text; fail closed for ambiguous bidi/custom layouts. */
public final class VisibleRange {
    public static int[] find(String text, List<String> rows, int first, int last) {
        if (first < 0 || last > rows.size() || first >= last) return null;
        if (first == 0 && last == rows.size()) return new int[]{0, text.length()};
        int cursor = 0, start = -1, end = -1;
        for (int i = 0; i < last; i++) {
            String part = rows.get(i);
            int position = text.indexOf(part, cursor);
            if (position < 0) return null;
            if (i == first) start = position;
            cursor = position + part.length();
            end = cursor;
        }
        return start >= 0 ? new int[]{start, end} : null;
    }
}
