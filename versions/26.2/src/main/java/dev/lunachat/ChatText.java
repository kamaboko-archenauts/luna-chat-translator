package dev.lunachat;

import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ChatText {
    private static final Pattern PREFIX = Pattern.compile("^(?:(?:\\[[^]\\r\\n]{1,40}\\]\\s*)*<[^>\\r\\n]{1,64}>\\s*|(?:\\[[^]\\r\\n]{1,40}\\]\\s*)*[A-Za-z0-9_]{1,16}\\s*[:»]\\s+)");

    public static int bodyStart(Component text) {
        if (text.getContents() instanceof TranslatableContents t
                && t.getKey().equals("chat.type.text") && t.getArgs().length >= 2) {
            String body = t.getArgs()[1] instanceof Component b ? b.getString() : String.valueOf(t.getArgs()[1]);
            int index = text.getString().lastIndexOf(body);
            if (index >= 0) return index;
        }
        var match = PREFIX.matcher(text.getString());
        return match.find() ? match.end() : 0;
    }

    public static MutableComponent slice(Component source, int start, int end) {
        MutableComponent result = Component.empty();
        int[] offset = {0};
        source.visit((style, part) -> {
            int a = Math.max(0, start - offset[0]), b = Math.min(part.length(), end - offset[0]);
            if (a < b) result.append(Component.literal(part.substring(a, b)).setStyle(style));
            offset[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static Component replace(Component source, int start, int end, String translated) {
        MutableComponent body = slice(source, start, end);
        Style style = body.getSiblings().isEmpty() ? source.getStyle() : body.getSiblings().getFirst().getStyle();
        return slice(source, 0, start)
                .append(Component.literal(translated).setStyle(style.withClickEvent(null)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(source.getString().substring(start, end))))))
                .append(slice(source, end, source.getString().length()));
    }
}
