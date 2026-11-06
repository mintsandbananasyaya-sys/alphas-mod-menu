package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class LoaderHud {
    private LoaderHud() {}

    public static void render(DrawContext context) {
        LoaderController c = LoaderController.INSTANCE;
        String line1 = c.hudTitle();
        if (line1 == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int x = 8;
        int y = 8;
        int w = Math.max(mc.textRenderer.getWidth(line1), mc.textRenderer.getWidth(c.hudDetail())) + 8;
        context.fill(x - 4, y - 4, x - 4 + w, y + 24, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, line1, x, y, 0xFFFFFFFF);
        context.drawTextWithShadow(mc.textRenderer, c.hudDetail(), x, y + 12, c.hudColor());
    }
}
