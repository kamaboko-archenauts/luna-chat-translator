package dev.lunachat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class LunaSettingsScreen extends Screen {
    private final Screen parent;
    public LunaSettingsScreen(Screen parent) { super(Text.literal("Luna チャット翻訳")); this.parent = parent; }
    @Override protected void init() {
        LunaChatClient mod = LunaChatClient.instance();
        int x = width / 2 - 140, y = 38;
        addDrawableChild(ButtonWidget.builder(Text.literal("自動翻訳: " + (mod.automatic() ? "ON" : "OFF")), b -> {
            mod.toggleAutomatic(); b.setMessage(Text.literal("自動翻訳: " + (mod.automatic() ? "ON" : "OFF")));
        }).dimensions(x, y, 280, 20).build()).active = client.world != null;
        addDrawableChild(ButtonWidget.builder(Text.literal("選択翻訳: " + (mod.clicking() ? "ON" : "OFF")), b -> {
            mod.toggleClick(); b.setMessage(Text.literal("選択翻訳: " + (mod.clicking() ? "ON" : "OFF")));
        }).dimensions(x, y + 24, 280, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("EarthMC mode: " + (mod.earthMc() ? "ON" : "OFF")), b -> {
            mod.toggleEarth(); b.setMessage(Text.literal("EarthMC mode: " + (mod.earthMc() ? "ON" : "OFF")));
        }).dimensions(x, y + 48, 280, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("チャットを開いて選択・翻訳"), b -> mod.openPicker())
                .dimensions(x, y + 72, 280, 20).build()).active = client.world != null;
        addDrawableChild(ButtonWidget.builder(Text.literal("原文に戻す"), b -> mod.restoreOriginal())
                .dimensions(x, y + 96, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Codexログイン"), b -> mod.beginLogin())
                .dimensions(x + 142, y + 96, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("戻る"), b -> close()).dimensions(x, height - 27, 280, 20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFFFF);
        int y = 158;
        for (String line : LunaChatClient.instance().usageLines()) {
            for (var wrapped : textRenderer.wrapLines(Text.literal(line), Math.min(width - 24, 520))) {
                if (y < height - 35) context.drawTextWithShadow(textRenderer, wrapped, Math.max(12, width / 2 - 260), y, 0xFFCCCCCC);
                y += 11;
            }
        }
    }
    @Override public void close() { client.setScreen(parent); }
}
