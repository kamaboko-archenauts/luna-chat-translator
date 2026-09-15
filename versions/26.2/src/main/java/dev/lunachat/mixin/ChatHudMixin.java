package dev.lunachat.mixin;

import dev.lunachat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements ChatAccess {
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;
    @Shadow private java.util.function.Predicate<GuiMessage> visibleMessageFilter;
    @Shadow protected abstract int getWidth();
    @Shadow protected abstract double getScale();
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract boolean isChatFocused();
    @Unique private boolean isChatHidden() { return minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN; }
    @Shadow protected abstract void refreshTrimmedMessages();
    @Shadow public abstract void scrollChat(int amount);

    @ModifyVariable(method = "addMessageToDisplayQueue", at = @At("HEAD"), argsOnly = true)
    private GuiMessage luna$renderTranslation(GuiMessage line) { return LunaChatClient.displayLine(line); }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void luna$received(GuiMessage line, CallbackInfo ci) { LunaChatClient.received(line); }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void luna$clear(boolean history, CallbackInfo ci) { LunaChatClient.clearTranslations(); }

    @Override public List<GuiMessage> luna$messages() { return List.copyOf(allMessages); }

    @Unique private List<GuiMessage> luna$displayedMessages() { return allMessages.stream().filter(visibleMessageFilter).toList(); }

    @Override public List<Hover> luna$highlights(List<Selection> selections) {
        if (selections.isEmpty() || !LunaChatClient.canClick() || !isChatFocused() || isChatHidden() || minecraft.gui.hud.isHidden()) return List.of();
        double scale = getScale();
        int width = (int) Math.floor(getWidth() / scale);
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1));
        int baseline = (int) Math.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / scale);
        int offset = 0, top = chatScrollbarPos + getLinesPerPage();
        List<Hover> result = new ArrayList<>();
        for (GuiMessage line : luna$displayedMessages()) {
            int count = LunaChatClient.displayLine(line).splitLines(minecraft.font, width).size();
            Selection selected = selections.stream().filter(s -> s.line() == line).findFirst().orElse(null);
            if (selected != null) {
                List<Rectangle> boxes = new ArrayList<>();
                for (int row = Math.max(offset, chatScrollbarPos); row < Math.min(offset + count, top); row++) {
                    int bottom = baseline - (row - chatScrollbarPos) * lineHeight;
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
        double scale = getScale();
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1));
        int baseline = (int) Math.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / scale);
        double x = mouseX / scale - 4;
        int row = (int) Math.floor((baseline - mouseY / scale) / lineHeight);
        int width = (int) Math.floor(getWidth() / scale);
        if (x < -4 || x > width + 4 || row < 0 || row >= getLinesPerPage()) return null;
        int target = chatScrollbarPos + row, offset = 0;
        for (GuiMessage line : luna$displayedMessages()) {
            int count = LunaChatClient.displayLine(line).splitLines(minecraft.font, width).size();
            if (target >= offset && target < offset + count) {
                if (!LunaChatClient.eligible(line)) return null;
                Selection selection = luna$visible().stream().filter(s -> s.line() == line).findFirst().orElse(null);
                if (selection == null) return null;
                List<Rectangle> boxes = new ArrayList<>();
                int first = Math.max(offset, chatScrollbarPos), last = Math.min(offset + count, chatScrollbarPos + getLinesPerPage());
                for (int index = first; index < last; index++) {
                    int bottom = baseline - (index - chatScrollbarPos) * lineHeight;
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
        if (isChatHidden() || minecraft.gui.hud.isHidden() || minecraft.level == null) return List.of();
        int bottom = chatScrollbarPos;
        int top = Math.min(trimmedMessages.size(), bottom + getLinesPerPage());
        int width = (int) Math.floor(getWidth() / getScale());
        int offset = 0, now = minecraft.gui.hud.getGuiTicks();
        List<Selection> result = new ArrayList<>();
        for (GuiMessage original : luna$displayedMessages()) {
            GuiMessage shown = LunaChatClient.displayLine(original);
            List<FormattedCharSequence> lines = shown.splitLines(minecraft.font, width);
            int count = lines.size();
            int lo = Math.max(offset, bottom), hi = Math.min(offset + count, top);
            if (lo < hi && (isChatFocused() || now - original.addedTime() < 200)) {
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

    @Unique private static int[] luna$textRange(String text, List<FormattedCharSequence> lines, int first, int last) {
        List<String> rows = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            StringBuilder part = new StringBuilder();
            line.accept((index, style, codePoint) -> { part.appendCodePoint(codePoint); return true; });
            rows.add(part.toString());
        }
        return VisibleRange.find(text, rows, first, last);
    }

    @Override public void luna$refreshKeepingScroll() {
        int oldScroll = chatScrollbarPos;
        boolean unread = newMessageSinceScroll;
        // Anchor the bottom-most visible entry, even when translations change its line count.
        int width = (int) Math.floor(getWidth() / getScale());
        int entry = -1;
        for (int i = 0; i <= oldScroll && i < trimmedMessages.size(); i++)
            if (trimmedMessages.get(i).endOfEntry()) entry++;
        int priorStart = Math.min(oldScroll, Math.max(0, trimmedMessages.size() - 1));
        while (priorStart > 0 && !trimmedMessages.get(priorStart).endOfEntry()) priorStart--;
        chatScrollbarPos = 0;
        refreshTrimmedMessages();
        if (oldScroll > 0 && entry >= 0 && entry < luna$displayedMessages().size()) {
            int before = 0;
            for (int i = 0; i < entry; i++) before += LunaChatClient.displayLine(luna$displayedMessages().get(i)).splitLines(minecraft.font, width).size();
            int count = LunaChatClient.displayLine(luna$displayedMessages().get(entry)).splitLines(minecraft.font, width).size();
            chatScrollbarPos = before + Math.min(oldScroll - priorStart, count - 1);
        }
        scrollChat(0);
        newMessageSinceScroll = unread;
    }
}
