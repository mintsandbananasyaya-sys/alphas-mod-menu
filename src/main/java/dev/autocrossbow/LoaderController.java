package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Automates the "open inventory while charging" crossbow glitch.
 *
 * Sequence (one state per phase, advanced from the client tick):
 *  CHARGE    hold the use key on the initial crossbow, then after CHARGE_DELAY_TICKS
 *            open the inventory screen in the same tick (the "press E" step).
 *  SETTLE    wait a couple of ticks with the inventory open.
 *  SWAP_IN   SWAP-click the next target crossbow into the held hotbar slot.
 *  SWAP_BACK SWAP-click it back to where it came from.
 *  VERIFY    check the target is now charged; retry a limited number of times, else mark failed.
 *  FINISH    close the inventory and report.
 *
 * Only SWAP clicks against the held hotbar slot are ever sent, so nothing can be thrown,
 * quick-moved or placed on the cursor. Every swap is preceded by sanity checks.
 */
public final class LoaderController {
    public static final LoaderController INSTANCE = new LoaderController();

    // Tunables
    private static final int CHARGE_DELAY_TICKS = 1;   // ticks of charging before E is "pressed"
    private static final int SETTLE_TICKS = 2;
    private static final int STEP_WAIT_TICKS = 2;      // wait after each click for server sync
    private static final int MAX_RETRIES = 2;

    private enum State { IDLE, CHARGE, SETTLE, SWAP_IN, SWAP_BACK, VERIFY, FINISH }

    private State state = State.IDLE;
    private int wait;
    private int heldInvIndex;                 // player inventory index (0-8) of the held slot
    private int initialIndex;                 // where the first crossbow started (== heldInvIndex)
    private final List<Integer> targets = new ArrayList<>(); // inventory indexes still to process
    private int currentTarget = -1;
    private int retries;
    private int total;
    private int loaded;
    private int failed;
    private int skippedLoaded;
    private String message = "";
    private int messageColor = 0xFFFFFFFF;
    private long finishedAtMs;
    private boolean weOpenedScreen;

    public boolean isRunning() {
        return state != State.IDLE;
    }

    // ---------------------------------------------------------------- start / stop

    public void start(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || mc.currentScreen != null) {
            return;
        }
        resetCounters();

        if (countAmmo(player) <= 0) {
            finish("No arrows or fireworks in inventory", 0xFFFF5555);
            return;
        }

        // Collect crossbows (hotbar 0-8, main inventory 9-35).
        List<Integer> unloaded = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(Items.CROSSBOW)) {
                if (CrossbowItem.isCharged(s)) skippedLoaded++;
                else unloaded.add(i);
            }
        }
        if (unloaded.isEmpty()) {
            finish("No unloaded crossbows found", 0xFFFFAA00);
            return;
        }

        // The initial crossbow must be in the hotbar so it can be held.
        int selected = player.getInventory().getSelectedSlot();
        int initial = -1;
        if (unloaded.contains(selected)) {
            initial = selected;
        } else {
            for (int idx : unloaded) {
                if (idx < 9) { initial = idx; break; }
            }
        }
        if (initial < 0) {
            finish("Put an unloaded crossbow in your hotbar first", 0xFFFFAA00);
            return;
        }
        player.getInventory().setSelectedSlot(initial);
        heldInvIndex = initial;
        initialIndex = initial;

        targets.clear();
        for (int idx : unloaded) {
            if (idx != initial) targets.add(idx);
        }
        total = unloaded.size();

        // Begin loading and arm the "E" step.
        mc.options.useKey.setPressed(true);
        wait = CHARGE_DELAY_TICKS;
        state = State.CHARGE;
        message = "Charging initial crossbow";
        messageColor = 0xFFFFFFFF;
    }

    public void stop(MinecraftClient mc, String reason) {
        if (state == State.IDLE) return;
        cleanup(mc);
        finish(reason, 0xFFFFAA00);
    }

    private void cleanup(MinecraftClient mc) {
        if (mc.options != null) mc.options.useKey.setPressed(false);
        if (weOpenedScreen && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }
        weOpenedScreen = false;
    }

    private void finish(String text, int color) {
        state = State.IDLE;
        message = text;
        messageColor = color;
        finishedAtMs = System.currentTimeMillis();
        targets.clear();
        currentTarget = -1;
    }

    private void resetCounters() {
        loaded = 0;
        failed = 0;
        skippedLoaded = 0;
        total = 0;
        retries = 0;
        currentTarget = -1;
        weOpenedScreen = false;
    }

    // ---------------------------------------------------------------- tick

    public void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || !player.isAlive()) {
            stop(mc, "Stopped: player unavailable");
            return;
        }
        // The user opened/closed something themselves in the middle of the inventory phase.
        if (state != State.CHARGE && state != State.FINISH) {
            if (!(mc.currentScreen instanceof InventoryScreen)) {
                stop(mc, "Stopped: inventory was closed");
                return;
            }
        }
        if (state == State.CHARGE && mc.currentScreen != null) {
            stop(mc, "Stopped: a screen opened");
            return;
        }

        if (wait > 0) {
            wait--;
            return;
        }

        switch (state) {
            case CHARGE -> {
                // "Press E" while the crossbow is mid-load.
                mc.setScreen(new InventoryScreen(player));
                weOpenedScreen = true;
                wait = SETTLE_TICKS;
                state = State.SETTLE;
                message = "Inventory opened";
            }
            case SETTLE -> nextTarget(mc, player);
            case SWAP_IN -> swapIn(mc, player);
            case SWAP_BACK -> swapBack(mc, player);
            case VERIFY -> verify(mc, player);
            case FINISH -> {
                cleanup(mc);
                boolean initialOk = CrossbowItem.isCharged(player.getInventory().getStack(initialIndex));
                String text = "Done: " + (loaded + (initialOk ? 1 : 0)) + "/" + total + " loaded"
                        + (failed > 0 ? ", " + failed + " failed" : "")
                        + (skippedLoaded > 0 ? ", " + skippedLoaded + " already loaded" : "");
                finish(text, failed > 0 ? 0xFFFFAA00 : 0xFF55FF55);
            }
            default -> {}
        }
    }

    private void nextTarget(MinecraftClient mc, ClientPlayerEntity player) {
        if (targets.isEmpty()) {
            state = State.FINISH;
            return;
        }
        if (countAmmo(player) <= 0) {
            cleanup(mc);
            finish("Stopped: out of ammunition (" + loaded + "/" + (total - 1) + " extra loaded)", 0xFFFF5555);
            return;
        }
        currentTarget = targets.remove(0);
        retries = 0;
        state = State.SWAP_IN;
    }

    private void swapIn(MinecraftClient mc, ClientPlayerEntity player) {
        if (!safeToClick(player, currentTarget)) {
            fail("Aborted: unexpected inventory state");
            return;
        }
        click(mc, player, currentTarget);
        wait = STEP_WAIT_TICKS;
        state = State.SWAP_BACK;
        message = "Loading crossbow " + (loaded + failed + 1) + " of " + (total - 1);
    }

    private void swapBack(MinecraftClient mc, ClientPlayerEntity player) {
        // Swapping the same two slots again restores the original positions.
        if (player.currentScreenHandler.getCursorStack().isEmpty()) {
            click(mc, player, currentTarget);
        } else {
            stop(mc, "Stopped: item on cursor, refusing to continue");
            return;
        }
        wait = STEP_WAIT_TICKS;
        state = State.VERIFY;
    }

    private void verify(MinecraftClient mc, ClientPlayerEntity player) {
        ItemStack s = player.getInventory().getStack(currentTarget);
        if (s.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(s)) {
            loaded++;
            state = State.SETTLE;
            wait = 0;
            return;
        }
        if (retries < MAX_RETRIES && s.isOf(Items.CROSSBOW)) {
            retries++;
            state = State.SWAP_IN;
            return;
        }
        fail(null);
    }

    private void fail(String abortReason) {
        if (abortReason != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            cleanup(mc);
            finish(abortReason, 0xFFFF5555);
            return;
        }
        failed++;
        state = State.SETTLE;
        wait = 0;
    }

    // ---------------------------------------------------------------- helpers

    /** Slot id in the player's screen handler for a player-inventory index. */
    private static int toSlotId(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    private boolean safeToClick(ClientPlayerEntity player, int invIndex) {
        if (!player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        ItemStack target = player.getInventory().getStack(invIndex);
        ItemStack held = player.getInventory().getStack(heldInvIndex);
        return target.isOf(Items.CROSSBOW) && held.isOf(Items.CROSSBOW);
    }

    /** SWAP click: exchanges the clicked slot with the hotbar slot (button = hotbar index). */
    private void click(MinecraftClient mc, ClientPlayerEntity player, int invIndex) {
        mc.interactionManager.clickSlot(
                player.currentScreenHandler.syncId,
                toSlotId(invIndex),
                heldInvIndex,
                SlotActionType.SWAP,
                player);
    }

    private static int countAmmo(ClientPlayerEntity player) {
        int n = 0;
        for (int i = 0; i < 36; i++) n += ammoCount(player.getInventory().getStack(i));
        n += ammoCount(player.getOffHandStack());
        return n;
    }

    private static int ammoCount(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.getItem() instanceof ArrowItem || s.isOf(Items.FIREWORK_ROCKET)) return s.getCount();
        return 0;
    }

    // ---------------------------------------------------------------- HUD data

    public String hudTitle() {
        if (state == State.IDLE) {
            if (System.currentTimeMillis() - finishedAtMs > 5000 || message.isEmpty()) return null;
            return "Auto Crossbow Loader";
        }
        return "Auto Crossbow Loader (] to stop)";
    }

    public String hudDetail() {
        if (state == State.IDLE) return message;
        int done = loaded + failed;
        return message + "  |  " + done + "/" + Math.max(total - 1, 0) + " extra  |  failed " + failed;
    }

    public int hudColor() {
        return state == State.IDLE ? messageColor : 0xFF55FFFF;
    }
}
