package dev.lunachat;

import net.minecraft.client.gui.hud.ChatHudLine;
import java.util.List;

public interface ChatAccess {
    record Selection(ChatHudLine line, int start, int end) { }
    record Rectangle(int left, int top, int right, int bottom) { }
    record Hover(Selection selection, List<Rectangle> rectangles) { }
    Hover luna$hover(double mouseX, double mouseY);
    List<Hover> luna$highlights(List<Selection> selections);
    List<Selection> luna$visible();
    List<ChatHudLine> luna$messages();
    void luna$refreshKeepingScroll();
}
