package dev.autocrossbow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

/**
 * Holding a sword and left-clicking triggers:
 *   tick 0: swap to spear
 *   tick 1: look at what the crosshair targets while the spear is held;
 *           if it is blocking with a shield and an axe is in the hotbar, swap to the axe,
 *           otherwise swap back to the sword
 *   tick 2: attack the target
 *   tick 3: swap back to the sword if the axe was used
 * One tick per step so the server receives each hotbar change before the next action.
 * Whether the server accepts the longer reach is up to the server; nothing here can force it.
 */
public final class SwordTech {
    public static final SwordTech INSTANCE = new SwordTech();

    private enum State { IDLE, SPEAR_HELD, WEAPON_SET, RESTORE }

    private State state = State.IDLE;
    private int swordSlot;
    private Entity target;
    private boolean usedAxe;
    private boolean lastAttackDown;

    public void tick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        boolean attackDown = mc.options.attackKey.isPressed();
        boolean justClicked = attackDown && !lastAttackDown;
        lastAttackDown = attackDown;

        // Safety: abandon if the user opens a screen mid-sequence.
        if (state != State.IDLE && mc.currentScreen != null) {
            restore(player);
            state = State.IDLE;
            return;
        }

        switch (state) {
            case IDLE -> {
                if (!Features.swordTech || !justClicked || mc.currentScreen != null) return;
                ItemStack held = player.getMainHandStack();
                if (!held.isIn(ItemTags.SWORDS)) return;
                int spear = findHotbar(player, true, false);
                if (spear < 0) return;
                swordSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(spear);
                usedAxe = false;
                target = null;
                state = State.SPEAR_HELD;
            }
            case SPEAR_HELD -> {
                // Crosshair target was recomputed with the spear in hand.
                target = mc.targetedEntity;
                int slot = swordSlot;
                if (target instanceof LivingEntity le && le.isBlocking()) {
                    int axe = findHotbar(player, false, true);
                    if (axe >= 0) {
                        slot = axe;
                        usedAxe = true;
                    }
                }
                player.getInventory().setSelectedSlot(slot);
                state = State.WEAPON_SET;
            }
            case WEAPON_SET -> {
                if (target != null && target.isAlive()) {
                    mc.interactionManager.attackEntity(player, target);
                    player.swingHand(Hand.MAIN_HAND);
                }
                if (usedAxe) {
                    player.getInventory().setSelectedSlot(swordSlot);
                }
                state = State.RESTORE;
            }
            case RESTORE -> {
                state = State.IDLE;
                target = null;
            }
        }
    }

    private void restore(ClientPlayerEntity player) {
        player.getInventory().setSelectedSlot(swordSlot);
    }

    /** Finds a spear (spear=true) or axe (axe=true) in the hotbar, or -1. */
    private static int findHotbar(ClientPlayerEntity player, boolean spear, boolean axe) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (spear && s.isIn(ItemTags.SPEARS)) return i;
            if (axe && s.isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }
}
