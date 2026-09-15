package dev.lunachat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

/** Real Minecraft input/rendering, with a seeded cache: no online translation is performed. */
public final class ChatInteractionTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var mod = LunaChatClient.instance();
                if (!mod.clicking()) mod.toggleClick();
                if (!mod.earthMc()) mod.toggleEarth();
                var hud = client.gui.hud.getChat(); hud.clearMessages(false);
                hud.addClientSystemMessage(Component.literal("BPN voted and received a gold crate /vote"));
                hud.addClientSystemMessage(Component.literal("<Other> it can be rebuilt"));
                hud.addClientSystemMessage(Component.literal("<Andrew> it can be rebuilt"));
                client.gui.setScreen(new ChatScreen("", false));
                var access = (ChatAccess) hud;
                double y = client.getWindow().getGuiScaledHeight() - 44;
                var hover = access.luna$hover(20, y);
                check(hover != null && hover.selection().line().content().getString().contains("Andrew"), "hover targets the correct message");
                check(!hover.rectangles().isEmpty(), "highlight rectangles exist");
                check(access.luna$hover(20, y - 18) == null, "EarthMC vote notice excluded");
                hud.setVisibleMessageFilter(m -> !m.content().getString().contains("Other"));
                check(access.luna$visible().stream().noneMatch(v -> v.line().content().getString().contains("Other")), "filtered messages never selected");
                check(access.luna$hover(20, y - 9) == null, "filtered row does not shift hover onto a hidden message");
                hud.setVisibleMessageFilter(m -> true);
                var cacheField = LunaChatClient.class.getDeclaredField("cache"); cacheField.setAccessible(true);
                @SuppressWarnings("unchecked") var cache = (Map<String, String>) cacheField.get(mod);
                cache.put("Japanese\nit can be rebuilt", "再建できます");
                cache.put("English\nこんにちは", "Hello!");
            });
            double[] cursor = context.computeOnClient(client -> new double[]{20 * client.getWindow().getGuiScale(),
                    (client.getWindow().getGuiScaledHeight() - 44) * client.getWindow().getGuiScale()});
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.takeScreenshot("luna-highlight");
            context.runOnClient(client -> {
                double y = client.getWindow().getGuiScaledHeight() - 44;
                check(client.gui.screen().mouseClicked(new MouseButtonEvent(20, y, new MouseButtonInfo(0, 0)), false), "left click consumed");
                var line = ((ChatAccess) client.gui.hud.getChat()).luna$messages().getFirst();
                check(LunaChatClient.displayLine(line) == line, "selection does not translate");
                client.gui.screen().mouseClicked(new MouseButtonEvent(20, y - 9, new MouseButtonInfo(0, 0)), false);
            });
            context.waitTicks(3);
            context.takeScreenshot("luna-multi-selection");
            context.runOnClient(client -> {
                var button = client.gui.screen().children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().startsWith("日本語に翻訳")).map(c -> (Button)c).findFirst().orElseThrow();
                check(button.getMessage().getString().contains("(2)"), "two messages selected");
                check(LunaChatClient.instance().usageLines().getFirst().contains("まだ翻訳していません"), "selecting does not start model usage");
                client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX()+5, button.getY()+5, new MouseButtonInfo(0, 0)), false);
            });
            context.waitFor(client -> {
                var line = ((ChatAccess) client.gui.hud.getChat()).luna$messages().getFirst();
                return LunaChatClient.displayLine(line).content().getString().contains("再建できます");
            }, 600);
            context.runOnClient(client -> {
                var line = ((ChatAccess) client.gui.hud.getChat()).luna$messages().getFirst();
                check(line.content().getString().equals("<Andrew> it can be rebuilt"), "original retained");
                var other = ((ChatAccess) client.gui.hud.getChat()).luna$messages().get(1);
                check(LunaChatClient.displayLine(other).content().getString().equals("<Other> 再建できます"), "second selection translated");
                var field = (EditBox) client.gui.screen().children().stream().filter(c -> c instanceof EditBox).findFirst().orElseThrow();
                field.setValue("こんにちは");
                check(LunaChatClient.displayLine(line).content().getString().equals("<Andrew> 再建できます"), "name retained");
                check(LunaChatClient.instance().usageLines().get(0).contains("まだ翻訳していません"), "cache hit consumes no tokens");
                });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var button = client.gui.screen().children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals("入力文を英語に翻訳")).map(c -> (Button)c).findFirst().orElseThrow();
                client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX()+5, button.getY()+5, new MouseButtonInfo(0, 0)), false);
                var field = (EditBox) client.gui.screen().children().stream().filter(c -> c instanceof EditBox).findFirst().orElseThrow();
                check(field.getValue().equals("Hello!"), "English result replaces draft");
                check(client.gui.screen() instanceof ChatScreen, "translation does not send or close chat");
                check(((ChatAccess) client.gui.hud.getChat()).luna$messages().size() == 3, "no outgoing message posted");
            });
            context.getInput().setCursorPos(30, 30);
            context.takeScreenshot("luna-chat-click");
            context.setScreen(() -> new LunaSettingsScreen(null));
            context.waitTicks(2);
            context.takeScreenshot("luna-settings");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

