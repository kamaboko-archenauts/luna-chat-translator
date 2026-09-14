package dev.lunachat.mixin;

import dev.lunachat.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.*;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatAccess {
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow @Final private MinecraftClient client;
    @Shadow private int scrolledLines;
    @Shadow private boolean hasUnreadNewMessages;
    @Shadow protected abstract int getWidth();
    @Shadow protected abstract double getChatScale();
    @Shadow public abstract int getVisibleLineCount();
    @Shadow public abstract boolean isChatFocused();
    @Shadow protected abstract boolean isChatHidden();
    @Shadow protected abstract void refresh();
    @Shadow public abstract void scroll(int amount);

    @Redirect(method = "addVisibleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine;breakLines(Lnet/minecraft/client/font/TextRenderer;I)Ljava/util/List;"))
    private List<OrderedText> luna$renderTranslation(ChatHudLine line, TextRenderer renderer, int width) {
        return LunaChatClient.displayLine(line).breakLines(renderer, width);
    }

    @Redirect(method = "addVisibleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine;indicator()Lnet/minecraft/client/gui/hud/MessageIndicator;"))
    private MessageIndicator luna$translationIndicator(ChatHudLine line) {
        return LunaChatClient.displayLine(line).indicator();
    }

    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("TAIL"))
    private void luna$received(ChatHudLine line, CallbackInfo ci) { LunaChatClient.received(line); }

    @Inject(method = "clear", at = @At("HEAD"))
    private void luna$clear(boolean history, CallbackInfo ci) { LunaChatClient.clearTranslations(); }

    @Override public List<ChatHudLine> luna$messages() { return List.copyOf(messages); }

    @Override public List<Hover> luna$highlights(List<Selection> selections) {
        if (selections.isEmpty() || !LunaChatClient.canClick() || !isChatFocused() || isChatHidden() || client.options.hudHidden) return List.of();
        double scale = getChatScale();
        int width = (int) Math.floor(getWidth() / scale);
        int lineHeight = (int) (9 * (client.options.getChatLineSpacing().getValue() + 1));
        int baseline = (int) Math.floor((client.getWindow().getScaledHeight() - 40) / scale);
        int offset = 0, top = scrolledLines + getVisibleLineCount();
        List<Hover> result = new ArrayList<>();
        for (ChatHudLine line : messages) {
            int count = LunaChatClient.displayLine(line).breakLines(client.textRenderer, width).size();
            Selection selected = selections.stream().filter(s -> s.line() == line).findFirst().orElse(null);
            if (selected != null) {
                List<Rectangle> boxes = new ArrayList<>();
                for (int row = Math.max(offset, scrolledLines); row < Math.min(offset + count, top); row++) {
                    int bottom = baseline - (row - scrolledLines) * lineHeight;
                    boxes.add(new Rectangle((int)(2 * scale), (int)((bottom - lineHeight) * scale),
                            (int)((width + 6) * scale), (int)(bottom * scale)));
                }
                result.add(new Hover(selected, boxes));
            }
            offset += count;
            if (offset >= top) break;
        }
        return result;
    }

    @Override public Hover luna$hover(double mouseX, double mouseY) {
        if (!LunaChatClient.canClick() || !isChatFocused()) return null;
        double scale = getChatScale();
        int lineHeight = (int) (9 * (client.options.getChatLineSpacing().getValue() + 1));
        int baseline = (int) Math.floor((client.getWindow().getScaledHeight() - 40) / scale);
        double x = mouseX / scale - 4;
        int row = (int) Math.floor((baseline - mouseY / scale) / lineHeight);
        int width = (int) Math.floor(getWidth() / scale);
        if (x < -4 || x > width + 4 || row < 0 || row >= getVisibleLineCount()) return null;
        int target = scrolledLines + row, offset = 0;
        for (ChatHudLine line : messages) {
            int count = LunaChatClient.displayLine(line).breakLines(client.textRenderer, width).size();
            if (target >= offset && target < offset + count) {
                if (!LunaChatClient.eligible(line)) return null;
                Selection selection = luna$visible().stream().filter(s -> s.line() == line).findFirst().orElse(null);
                if (selection == null) return null;
                List<Rectangle> boxes = new ArrayList<>();
                int first = Math.max(offset, scrolledLines), last = Math.min(offset + count, scrolledLines + getVisibleLineCount());
                for (int index = first; index < last; index++) {
                    int bottom = baseline - (index - scrolledLines) * lineHeight;
                    boxes.add(new Rectangle((int) (2 * scale), (int) ((bottom - lineHeight) * scale),
                            (int) ((width + 6) * scale), (int) (bottom * scale)));
                }
                return new Hover(selection, boxes);
            }
            offset += count;
        }
        return null;
    }

    @Override public List<Selection> luna$visible() {
        if (isChatHidden() || client.options.hudHidden || client.world == null) return List.of();
        int bottom = scrolledLines;
        int top = Math.min(visibleMessages.size(), bottom + getVisibleLineCount());
        int width = (int) Math.floor(getWidth() / getChatScale());
        int offset = 0, now = client.inGameHud.getTicks();
        List<Selection> result = new ArrayList<>();
        for (ChatHudLine original : messages) {
            ChatHudLine shown = LunaChatClient.displayLine(original);
            List<OrderedText> lines = shown.breakLines(client.textRenderer, width);
            int count = lines.size();
            int lo = Math.max(offset, bottom), hi = Math.min(offset + count, top);
            if (lo < hi && (isChatFocused() || now - original.creationTick() < 200)) {
                // Already translated entries are not sent again.
                if (shown == original) {
                    int first = count - (hi - offset), last = count - (lo - offset);
                    int[] range = luna$textRange(original.content().getString(), lines, first, last);
                    if (range != null) {
                        int start = Math.max(range[0], ChatText.bodyStart(original.content()));
                        if (start < range[1]) result.add(new Selection(original, start, range[1]));
                    }
                }
            }
            offset += count;
            if (offset >= top) break;
        }
        return result;
    }

    @Unique private static int[] luna$textRange(String text, List<OrderedText> lines, int first, int last) {
        List<String> rows = new ArrayList<>();
        for (OrderedText line : lines) {
            StringBuilder part = new StringBuilder();
            line.accept((index, style, codePoint) -> { part.appendCodePoint(codePoint); return true; });
            rows.add(part.toString());
        }
        return VisibleRange.find(text, rows, first, last);
    }

    @Override public void luna$refreshKeepingScroll() {
        int oldScroll = scrolledLines;
        boolean unread = hasUnreadNewMessages;
        // Anchor the bottom-most visible entry, even when translations change its line count.
        int width = (int) Math.floor(getWidth() / getChatScale());
        int entry = -1;
        for (int i = 0; i <= oldScroll && i < visibleMessages.size(); i++)
            if (visibleMessages.get(i).endOfEntry()) entry++;
        int priorStart = Math.min(oldScroll, Math.max(0, visibleMessages.size() - 1));
        while (priorStart > 0 && !visibleMessages.get(priorStart).endOfEntry()) priorStart--;
        scrolledLines = 0;
        refresh();
        if (oldScroll > 0 && entry >= 0 && entry < messages.size()) {
            int before = 0;
            for (int i = 0; i < entry; i++) before += LunaChatClient.displayLine(messages.get(i)).breakLines(client.textRenderer, width).size();
            int count = LunaChatClient.displayLine(messages.get(entry)).breakLines(client.textRenderer, width).size();
            scrolledLines = before + Math.min(oldScroll - priorStart, count - 1);
        }
        scroll(0);
        hasUnreadNewMessages = unread;
    }
}
