package dev.lunachat.mixin;

import dev.lunachat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;
    @Unique private final List<ChatAccess.Selection> luna$selected = new ArrayList<>();
    @Unique private Button luna$japanese, luna$english;
    @Unique private boolean luna$draftBusy;
    @Unique private long luna$editRevision;
    protected ChatScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void luna$buttons(CallbackInfo ci) {
        int x = Math.max(2, width - 244);
        luna$japanese = addRenderableWidget(Button.builder(Component.literal("日本語に翻訳 (0)"), b -> {
            LunaChatClient.instance().translateSelected(List.copyOf(luna$selected));
            luna$selected.clear(); setFocused(input);
        }).bounds(x, height - 39, 120, 18).build());
        luna$english = addRenderableWidget(Button.builder(Component.literal("入力文を英語に翻訳"), b -> luna$translateDraft())
                .bounds(x + 122, height - 39, 120, 18).build());
        luna$japanese.active = false;
    }

    @Unique private void luna$translateDraft() {
        if (luna$draftBusy) return;
        String original = input.getValue();
        if (original.isBlank() || original.startsWith("/")) return;
        luna$draftBusy = true;
        long revision = luna$editRevision;
        LunaChatClient.instance().translateDraft(original, result -> {
            luna$draftBusy = false;
            if (Minecraft.getInstance().gui.screen() != (Object) this
                    || revision != luna$editRevision || !input.getValue().equals(original)) return;
            // Never silently truncate a draft or turn translated data into a server command.
            if (result.length() > ((TextFieldAccessor) input).luna$maxLength() || result.stripLeading().startsWith("/")) {
                if (minecraft.player != null) minecraft.gui.hud.setOverlayMessage(Component.literal("[Luna] 英訳を入力欄に反映できないため原文を保持しました（文字数上限・コマンド形式）"), true);
                return;
            }
            input.setValue(result); setFocused(input);
        }, () -> luna$draftBusy = false);
        setFocused(input);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void luna$click(MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        if (luna$japanese != null && (luna$japanese.isMouseOver(click.x(), click.y()) || luna$english.isMouseOver(click.x(), click.y()))) {
            if (luna$japanese.isMouseOver(click.x(), click.y())) luna$japanese.mouseClicked(click, doubleClick);
            else luna$english.mouseClicked(click, doubleClick);
            cir.setReturnValue(true); return;
        }
        if ((click.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) return;
        ChatAccess access = (ChatAccess) Minecraft.getInstance().gui.hud.getChat();
        ChatAccess.Hover hover = access.luna$hover(click.x(), click.y());
        if (hover != null) {
            boolean removed = luna$selected.removeIf(s -> s.line() == hover.selection().line());
            if (!removed && luna$selected.size() < 100) luna$selected.add(hover.selection());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void luna$state(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatAccess access = (ChatAccess) Minecraft.getInstance().gui.hud.getChat();
        var alive = access.luna$messages();
        luna$selected.removeIf(s -> !LunaChatClient.canClick() || !alive.contains(s.line())
                || !LunaChatClient.eligible(s.line()) || LunaChatClient.displayLine(s.line()) != s.line());
        luna$japanese.setMessage(Component.literal("日本語に翻訳 (" + luna$selected.size() + ")"));
        luna$japanese.active = !luna$selected.isEmpty();
        luna$english.active = !luna$draftBusy && !input.getValue().isBlank() && !input.getValue().startsWith("/");
        luna$english.setMessage(Component.literal(luna$draftBusy ? "英訳中…" : "入力文を英語に翻訳"));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void luna$highlight(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatAccess access = (ChatAccess) Minecraft.getInstance().gui.hud.getChat();
        Set<Object> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var selected : access.luna$highlights(luna$selected)) {
            drawn.add(selected.selection().line());
            luna$paint(context, selected, 0x6655BB88);
        }
        if (luna$japanese.isMouseOver(mouseX, mouseY) || luna$english.isMouseOver(mouseX, mouseY)) return;
        var hover = access.luna$hover(mouseX, mouseY);
        if (hover != null && !drawn.contains(hover.selection().line())) luna$paint(context, hover, 0x554488CC);
    }
    @Unique private void luna$paint(GuiGraphicsExtractor context, ChatAccess.Hover hover, int color) {
        for (ChatAccess.Rectangle box : hover.rectangles()) {
            int bottom = Math.min(box.bottom(), height - 40);
            if (bottom <= box.top()) continue;
            context.fill(box.left(), box.top(), box.right(), bottom, color);
            context.fill(box.left(), box.top(), box.left() + 2, bottom, 0xFF8FCFFF);
        }
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void luna$keys(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
        if (LunaChatClient.handleChatKey(key)) cir.setReturnValue(true);
    }

    @Inject(method = "onEdited", at = @At("HEAD"))
    private void luna$edited(String text, CallbackInfo ci) { luna$editRevision++; }
}
