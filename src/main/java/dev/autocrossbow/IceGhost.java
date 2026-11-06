package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Ice Ghost.
 *
 * When enabled and you right-click while holding ice in your main hand, the offhand-swap
 * packet (what pressing F sends) goes out BEFORE your placement packet in the same tick.
 * The server has already moved the ice to your offhand when it handles the placement, so it
 * refuses the block, while your client has already drawn it. That is the ghost block.
 *
 * A few ticks later the swap is sent again so the ice comes back to your main hand.
 * This must run at the START of the client tick, before the game handles your right-click.
 */
public final class IceGhost {
    public static final IceGhost INSTANCE = new IceGhost();

    /** Ticks to wait before swapping the ice back. 20 ticks = 1 second. */
    private static final int RESTORE_TICKS = 8;

    private boolean lastUseDown;
    private int restoreTimer;

    public void tickStart(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.getNetworkHandler() == null) {
            restoreTimer = 0;
            return;
        }

        boolean useDown = mc.options.useKey.isPressed();
        boolean clicked = useDown && !lastUseDown;
        lastUseDown = useDown;

        // Swap the ice back, but only if it really is sitting in the offhand and the main
        // hand is not holding something else we would be moving around.
        if (restoreTimer > 0) {
            restoreTimer--;
            if (restoreTimer == 0 && isIce(player.getOffHandStack())
                    && !isIce(player.getMainHandStack()) && mc.currentScreen == null) {
                sendSwap(mc);
            }
        }

        if (!Features.iceGhost || !clicked || mc.currentScreen != null || player.isSpectator()) {
            return;
        }
        if (!isIce(player.getMainHandStack())) {
            return;
        }

        // Offhand swap first; the game then sends your placement right after it.
        sendSwap(mc);
        restoreTimer = RESTORE_TICKS;
    }

    private static void sendSwap(MinecraftClient mc) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    private static boolean isIce(ItemStack s) {
        return s.isOf(Items.BLUE_ICE) || s.isOf(Items.PACKED_ICE) || s.isOf(Items.ICE);
    }
}
