package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Keeps dropped-item pickups out of locked slots.
 *
 * Item pickup is decided server-side (vanilla addItemStackToInventory knows
 * nothing about locked slots), so a client-side mod cannot prevent the
 * placement — instead this guard watches locked slots every tick during
 * gameplay and immediately moves any stack that appeared in an empty locked
 * slot to a free unlocked slot (or merges it into matching stacks).
 *
 * Only empty→non-empty transitions with no GUI open are touched:
 * <ul>
 *   <li>manual placement into locked slots with the inventory open stays
 *       allowed (deliberate action)</li>
 *   <li>pickup top-ups of a matching stack already in a locked slot stay
 *       allowed (same rule as quick-move merging)</li>
 *   <li>the active hotbar slot is exempt so auto-refill and deliberate
 *       hand-pickup keep working</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class ClientLockedSlotPickupGuard {

    /** Previous tick's contents of each player inventory slot (copies). */
    private final ItemStack[] prevStacks = new ItemStack[36];
    private boolean snapshotValid = false;

    public ClientLockedSlotPickupGuard() {
        for (int i = 0; i < 36; i++) {
            prevStacks[i] = ItemStack.EMPTY;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null
                || !IPNConfig.enableLockedSlots
                || !LockedSlotHandler.hasAnyLocked()) {
            snapshotValid = false;
            return;
        }

        // With a GUI open the player moves items deliberately — just track
        if (mc.currentScreen != null
                || mc.player.openContainer != mc.player.inventoryContainer) {
            takeSnapshot(mc);
            return;
        }

        if (snapshotValid && mc.player.inventory.getItemStack().isEmpty()) {
            int held = mc.player.inventory.currentItem;
            for (int invIdx = 0; invIdx < 36; invIdx++) {
                if (invIdx == held) continue;
                if (!LockedSlotHandler.isLocked(invIdx)) continue;
                ItemStack cur = mc.player.inventory.mainInventory.get(invIdx);
                if (prevStacks[invIdx].isEmpty() && !cur.isEmpty()) {
                    moveOut(mc, invIdx);
                }
            }
        }

        takeSnapshot(mc);
    }

    private void takeSnapshot(Minecraft mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.inventory.mainInventory.get(i);
            prevStacks[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        snapshotValid = true;
    }

    /**
     * Move the stack out of locked slot {@code sourceInvIdx}: first empty
     * unlocked slot wins; otherwise merge into matching unlocked stacks.
     * If the inventory has no legal room the stack stays where it is.
     */
    private static void moveOut(Minecraft mc, int sourceInvIdx) {
        int sourceSlotId = invIdxToSlotId(sourceInvIdx);
        ItemStack moving = mc.player.inventory.mainInventory.get(sourceInvIdx);
        if (sourceSlotId < 0 || moving.isEmpty()) return;

        // Pass 1: first empty unlocked slot (vanilla pickup scan order)
        for (int invIdx = 0; invIdx < 36; invIdx++) {
            if (invIdx == sourceInvIdx) continue;
            if (LockedSlotHandler.isLocked(invIdx)) continue;
            if (!mc.player.inventory.mainInventory.get(invIdx).isEmpty()) continue;

            if (invIdx <= 8) {
                // hotbar target: single-packet SWAP
                ContainerClicker.swap(sourceSlotId, invIdx);
            } else {
                ContainerClicker.leftClick(sourceSlotId);
                ContainerClicker.leftClick(invIdxToSlotId(invIdx));
            }
            return;
        }

        // Pass 2: merge into matching non-full unlocked stacks
        boolean lifted = false;
        for (int invIdx = 0; invIdx < 36 && !cursorAndSourceDone(mc, sourceInvIdx, lifted); invIdx++) {
            if (invIdx == sourceInvIdx) continue;
            if (LockedSlotHandler.isLocked(invIdx)) continue;
            ItemStack dest = mc.player.inventory.mainInventory.get(invIdx);
            if (dest.isEmpty()) continue;
            if (dest.getCount() >= dest.getMaxStackSize()) continue;
            if (!ItemStack.areItemsEqual(dest, moving)
                    || !ItemStack.areItemStackTagsEqual(dest, moving)) continue;

            if (!lifted) {
                ContainerClicker.leftClick(sourceSlotId);
                lifted = true;
            }
            ContainerClicker.leftClick(invIdxToSlotId(invIdx));
        }
        // Remainder (if any) goes back to the locked slot — no legal room left
        if (lifted && !mc.player.inventory.getItemStack().isEmpty()) {
            ContainerClicker.leftClick(sourceSlotId);
        }
    }

    private static boolean cursorAndSourceDone(Minecraft mc, int sourceInvIdx, boolean lifted) {
        return lifted && mc.player.inventory.getItemStack().isEmpty();
    }

    /** InventoryPlayer index → ContainerPlayer slotId (hotbar i→36+i, main i→i). */
    private static int invIdxToSlotId(int invIdx) {
        if (invIdx >= 0 && invIdx <= 8) return 36 + invIdx;
        if (invIdx >= 9 && invIdx <= 35) return invIdx;
        return -1;
    }
}
