package dev.lunachat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VisibleRangeTest {
    @Test void fullEntryKeepsWhitespace() {
        assertArrayEquals(new int[]{0, 11}, VisibleRange.find("hello world", List.of("hello", "world"), 0, 2));
    }
    @Test void partialEntryDoesNotSendOffscreenText() {
        String text = "secret visible hidden";
        int[] range = VisibleRange.find(text, List.of("secret", "visible", "hidden"), 1, 2);
        assertNotNull(range);
        assertEquals("visible", text.substring(range[0], range[1]));
    }
    @Test void repeatedLinesSelectCorrectOccurrence() {
        assertArrayEquals(new int[]{5, 9}, VisibleRange.find("same same", List.of("same", "same"), 1, 2));
    }
    @Test void unicodeOffsetsAreSafe() {
        assertArrayEquals(new int[]{3, 6}, VisibleRange.find("😀 日本語", List.of("😀", "日本語"), 1, 2));
    }
    @Test void reorderedOrCustomTextIsNotGuessed() {
        assertNull(VisibleRange.find("abc def", List.of("cba", "fed"), 1, 2));
    }
    @Test void invalidViewportIsRejected() {
        assertNull(VisibleRange.find("abc", List.of("abc"), 1, 1));
    }
}
