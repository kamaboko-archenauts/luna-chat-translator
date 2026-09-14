package dev.lunachat.mixin;

import dev.lunachat.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected TextFieldWidget chatField;
    @Unique private final List<ChatAccess.Selection> luna$selected = new ArrayList<>();
    @Unique private ButtonWidget luna$japanese, luna$english;
    @Unique private boolean luna$draftBusy;
    @Unique private long luna$editRevision;
    protected ChatScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void luna$buttons(CallbackInfo ci) {
        int x = Math.max(2, width - 244);
        luna$japanese = addDrawableChild(ButtonWidget.builder(Text.literal("日本語に翻訳 (0)"), b -> {
            LunaChatClient.instance().translateSelected(List.copyOf(luna$selected));
            luna$selected.clear(); setFocused(chatField);
        }).dimensions(x, height - 39, 120, 18).build());
        luna$english = addDrawableChild(ButtonWidget.builder(Text.literal("入力文を英語に翻訳"), b -> luna$translateDraft())
                .dimensions(x + 122, height - 39, 120, 18).build());
        luna$japanese.active = false;
    }

    @Unique private void luna$translateDraft() {
        if (luna$draftBusy) return;
        String original = chatField.getText();
        if (original.isBlank() || original.startsWith("/")) return;
        luna$draftBusy = true;
        long revision = luna$editRevision;
        LunaChatClient.instance().translateDraft(original, result -> {
            luna$draftBusy = false;
            if (MinecraftClient.getInstance().currentScreen != (Object) this
                    || revision != luna$editRevision || !chatField.getText().equals(original)) return;
            // Never silently truncate a draft or turn translated data into a server command.
            if (result.length() > ((TextFieldAccessor) chatField).luna$maxLength() || result.stripLeading().startsWith("/")) {
                if (client.player != null) client.player.sendMessage(Text.literal("[Luna] 英訳を入力欄に反映できないため原文を保持しました（文字数上限・コマンド形式）"), true);
                return;
            }
            chatField.setText(result); setFocused(chatField);
        }, () -> luna$draftBusy = false);
        setFocused(chatField);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void luna$click(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        if (luna$japanese != null && (luna$japanese.isMouseOver(click.x(), click.y()) || luna$english.isMouseOver(click.x(), click.y()))) {
            if (luna$japanese.isMouseOver(click.x(), click.y())) luna$japanese.mouseClicked(click, doubleClick);
            else luna$english.mouseClicked(click, doubleClick);
            cir.setReturnValue(true); return;
        }
        if ((click.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) return;
        ChatAccess access = (ChatAccess) MinecraftClient.getInstance().inGameHud.getChatHud();
        ChatAccess.Hover hover = access.luna$hover(click.x(), click.y());
        if (hover != null) {
            boolean removed = luna$selected.removeIf(s -> s.line() == hover.selection().line());
            if (!removed && luna$selected.size() < 100) luna$selected.add(hover.selection());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void luna$state(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatAccess access = (ChatAccess) MinecraftClient.getInstance().inGameHud.getChatHud();
        var alive = access.luna$messages();
        luna$selected.removeIf(s -> !LunaChatClient.canClick() || !alive.contains(s.line())
                || !LunaChatClient.eligible(s.line()) || LunaChatClient.displayLine(s.line()) != s.line());
        luna$japanese.setMessage(Text.literal("日本語に翻訳 (" + luna$selected.size() + ")"));
        luna$japanese.active = !luna$selected.isEmpty();
        luna$english.active = !luna$draftBusy && !chatField.getText().isBlank() && !chatField.getText().startsWith("/");
        luna$english.setMessage(Text.literal(luna$draftBusy ? "英訳中…" : "入力文を英語に翻訳"));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void luna$highlight(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatAccess access = (ChatAccess) MinecraftClient.getInstance().inGameHud.getChatHud();
        Set<Object> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var selected : access.luna$highlights(luna$selected)) {
            drawn.add(selected.selection().line());
            luna$paint(context, selected, 0x6655BB88);
        }
        if (luna$japanese.isMouseOver(mouseX, mouseY) || luna$english.isMouseOver(mouseX, mouseY)) return;
        var hover = access.luna$hover(mouseX, mouseY);
        if (hover != null && !drawn.contains(hover.selection().line())) luna$paint(context, hover, 0x554488CC);
    }
    @Unique private void luna$paint(DrawContext context, ChatAccess.Hover hover, int color) {
        for (ChatAccess.Rectangle box : hover.rectangles()) {
            int bottom = Math.min(box.bottom(), height - 40);
            if (bottom <= box.top()) continue;
            context.fill(box.left(), box.top(), box.right(), bottom, color);
            context.fill(box.left(), box.top(), box.left() + 2, bottom, 0xFF8FCFFF);
        }
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void luna$keys(KeyInput key, CallbackInfoReturnable<Boolean> cir) {
        if (LunaChatClient.handleChatKey(key)) cir.setReturnValue(true);
    }

    @Inject(method = "onChatFieldUpdate", at = @At("HEAD"))
    private void luna$edited(String text, CallbackInfo ci) { luna$editRevision++; }
}
