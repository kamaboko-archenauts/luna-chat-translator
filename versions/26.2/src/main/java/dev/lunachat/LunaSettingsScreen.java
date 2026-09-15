package dev.lunachat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class LunaSettingsScreen extends Screen {
    private final Screen parent;
    public LunaSettingsScreen(Screen parent) { super(Component.literal("Luna チャット翻訳")); this.parent = parent; }
    @Override protected void init() {
        LunaChatClient mod = LunaChatClient.instance();
        int x = width / 2 - 140, y = 38;
        addRenderableWidget(Button.builder(Component.literal("自動翻訳: " + (mod.automatic() ? "ON" : "OFF")), b -> {
            mod.toggleAutomatic(); b.setMessage(Component.literal("自動翻訳: " + (mod.automatic() ? "ON" : "OFF")));
        }).bounds(x, y, 280, 20).build()).active = minecraft.level != null;
        addRenderableWidget(Button.builder(Component.literal("選択翻訳: " + (mod.clicking() ? "ON" : "OFF")), b -> {
            mod.toggleClick(); b.setMessage(Component.literal("選択翻訳: " + (mod.clicking() ? "ON" : "OFF")));
        }).bounds(x, y + 24, 280, 20).build());
        addRenderableWidget(Button.builder(Component.literal("EarthMC mode: " + (mod.earthMc() ? "ON" : "OFF")), b -> {
            mod.toggleEarth(); b.setMessage(Component.literal("EarthMC mode: " + (mod.earthMc() ? "ON" : "OFF")));
        }).bounds(x, y + 48, 280, 20).build());
        addRenderableWidget(Button.builder(Component.literal("チャットを開いて選択・翻訳"), b -> mod.openPicker())
                .bounds(x, y + 72, 280, 20).build()).active = minecraft.level != null;
        addRenderableWidget(Button.builder(Component.literal("原文に戻す"), b -> mod.restoreOriginal())
                .bounds(x, y + 96, 138, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Codexログイン"), b -> mod.beginLogin())
                .bounds(x + 142, y + 96, 138, 20).build());
        addRenderableWidget(Button.builder(Component.literal("戻る"), b -> onClose()).bounds(x, height - 27, 280, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        int y = 158;
        for (String line : LunaChatClient.instance().usageLines()) {
            for (var wrapped : font.split(Component.literal(line), Math.min(width - 24, 520))) {
                if (y < height - 35) context.text(font, wrapped, Math.max(12, width / 2 - 260), y, 0xFFCCCCCC);
                y += 11;
            }
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
