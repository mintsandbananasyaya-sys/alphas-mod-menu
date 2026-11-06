package dev.autocrossbow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoCrossbowLoaderClient implements ClientModInitializer {
    public static final String MOD_ID = "autocrossbowloader";

    // '#' on a UK layout is the key left of Enter, which GLFW reports as BACKSLASH.
    // Both keys can be rebound in Options > Controls > Key Binds.
    public static KeyBinding startKey;
    public static KeyBinding stopKey;

    @Override
    public void onInitializeClient() {
        Features.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocrossbowloader.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, category));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocrossbowloader.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, category));

        // Ice Ghost runs at the START of the tick so its packet goes out before your right-click.
        ClientTickEvents.START_CLIENT_TICK.register(client -> IceGhost.INSTANCE.tickStart(client));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Stop is checked first and on every tick so it always wins.
            while (stopKey.wasPressed()) {
                LoaderController.INSTANCE.stop(client, "Stopped by user");
            }
            while (startKey.wasPressed()) {
                // '#' opens the Alpha PvP menu; the crossbow loader is started from it.
                if (client.currentScreen == null && client.player != null
                        && !LoaderController.INSTANCE.isRunning()) {
                    client.setScreen(new PvpMenuScreen());
                }
            }
            LoaderController.INSTANCE.tick(client);
            SwordTech.INSTANCE.tick(client);
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(MOD_ID, "progress"),
                (context, tickCounter) -> LoaderHud.render(context));
    }
}
