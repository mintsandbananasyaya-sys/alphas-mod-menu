package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

public class PvpMenuScreen extends Screen {

    public PvpMenuScreen() {
        super(Text.literal("Alpha's PvP Menu"));
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 24;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 40;

        // Ice Ghost: toggle
        this.addDrawableChild(new ModuleButton(x, y, w, h, "Ice Ghost",
                () -> Features.iceGhost,
                () -> {
                    Features.iceGhost = !Features.iceGhost;
                    Features.save();
                }));

        // Sword Spear: toggle
        this.addDrawableChild(new ModuleButton(x, y + 32, w, h, "Sword Spear",
                () -> Features.swordTech,
                () -> {
                    Features.swordTech = !Features.swordTech;
                    Features.save();
                }));

        // Auto Crossbow: runs the loader once (blue while it is running)
        this.addDrawableChild(new ModuleButton(x, y + 64, w, h, "Auto Crossbow",
                () -> LoaderController.INSTANCE.isRunning(),
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(null);
                    LoaderController.INSTANCE.start(mc);
                }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 66, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "MODULES", this.width / 2, this.height / 2 - 54, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Blue when enabled, transparent when disabled. */
    private static final class ModuleButton extends ButtonWidget {
        private final String name;
        private final BooleanSupplier enabled;

        ModuleButton(int x, int y, int w, int h, String name, BooleanSupplier enabled, Runnable onClick) {
            // Fully qualified: inside a ButtonWidget the short name "Text" means a nested class.
            super(x, y, w, h, net.minecraft.text.Text.literal(name), b -> onClick.run(), DEFAULT_NARRATION_SUPPLIER);
            this.name = name;
            this.enabled = enabled;
            // Hide the vanilla grey button texture; we draw everything ourselves in drawIcon.
            this.setAlpha(0.0F);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean on = enabled.getAsBoolean();
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            int fill = on ? 0xCC2E7DFF : (this.isHovered() ? 0x50FFFFFF : 0x30000000);
            context.fill(x, y, x + w, y + h, fill);
            int border = on ? 0xFF8FC1FF : 0x80FFFFFF;
            context.fill(x, y, x + w, y + 1, border);
            context.fill(x, y + h - 1, x + w, y + h, border);
            context.fill(x, y, x + 1, y + h, border);
            context.fill(x + w - 1, y, x + w, y + h, border);

            MinecraftClient mc = MinecraftClient.getInstance();
            String label = name + "  -  " + (on ? "ENABLED" : "DISABLED");
            context.drawCenteredTextWithShadow(mc.textRenderer, label, x + w / 2, y + (h - 8) / 2,
                    on ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }
}
