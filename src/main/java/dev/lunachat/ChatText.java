package dev.lunachat;

import net.minecraft.text.*;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ChatText {
    private static final Pattern PREFIX = Pattern.compile("^(?:(?:\\[[^]\\r\\n]{1,40}\\]\\s*)*<[^>\\r\\n]{1,64}>\\s*|(?:\\[[^]\\r\\n]{1,40}\\]\\s*)*[A-Za-z0-9_]{1,16}\\s*[:»]\\s+)");

    public static int bodyStart(Text text) {
        if (text.getContent() instanceof TranslatableTextContent t
                && t.getKey().equals("chat.type.text") && t.getArgs().length >= 2) {
            String body = t.getArgs()[1] instanceof Text b ? b.getString() : String.valueOf(t.getArgs()[1]);
            int index = text.getString().lastIndexOf(body);
            if (index >= 0) return index;
        }
        var match = PREFIX.matcher(text.getString());
        return match.find() ? match.end() : 0;
    }

    public static MutableText slice(Text source, int start, int end) {
        MutableText result = Text.empty();
        int[] offset = {0};
        source.visit((style, part) -> {
            int a = Math.max(0, start - offset[0]), b = Math.min(part.length(), end - offset[0]);
            if (a < b) result.append(Text.literal(part.substring(a, b)).setStyle(style));
            offset[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static Text replace(Text source, int start, int end, String translated) {
        MutableText body = slice(source, start, end);
        Style style = body.getSiblings().isEmpty() ? source.getStyle() : body.getSiblings().getFirst().getStyle();
        return slice(source, 0, start)
                .append(Text.literal(translated).setStyle(style.withClickEvent(null)
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(source.getString().substring(start, end))))))
                .append(slice(source, end, source.getString().length()));
    }
}
