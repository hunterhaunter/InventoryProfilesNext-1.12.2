package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Routed replacement for vanilla QUICK_MOVE (shift-click) when the
 * destination is the player inventory and locked slots exist.
 *
 * Vanilla quick-move picks destination slots itself and will happily fill an
 * empty locked slot. This router re-implements the move with explicit clicks:
 * <ul>
 *   <li>merging into existing matching stacks is allowed everywhere,
 *       INCLUDING locked slots (refilling a locked food/block stack is the
 *       point of locking it)</li>
 *   <li>empty LOCKED slots are never used as a destination</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class ClientQuickMoveRouter {

    private ClientQuickMoveRouter() {
    }

    /**
     * Quick-move the stack at {@code sourceSlotId} into the player inventory,
     * skipping empty locked slots.
     *
     * @param container       the open container
     * @param sourceSlotId    slot to move from (a non-player container slot,
     *                        or a player slot when {@code sectionMove})
     * @param sectionMove     {@code true} for player-GUI internal moves: the
     *                        destination is the OPPOSITE player section
     *                        (main &harr; hotbar) instead of the whole inventory
     * @return {@code true} if the router executed the move (caller must cancel
     *         the vanilla click), {@code false} to fall back to vanilla
     */
    public static boolean quickMoveToPlayer(Container container, int sourceSlotId,
                                            boolean sectionMove) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;

        // Nothing locked — vanilla produces an identical result with one packet
        if (!LockedSlotHandler.hasAnyLocked()) return false;

        // The click sequence needs a free cursor
        if (!mc.player.inventory.getItemStack().isEmpty()) return false;

        if (sourceSlotId < 0 || sourceSlotId >= container.inventorySlots.size()) return false;
        Slot source = container.getSlot(sourceSlotId);
        if (source == null || !source.getHasStack()) return false;
        ItemStack moving = source.getStack();
        if (!source.canTakeStack(mc.player)) return false;

        boolean sourceIsHotbar = isPlayerSlot(source) && source.getSlotIndex() < 9;

        // Collect candidate destination player slots in container order
        List<Slot> targets = new ArrayList<Slot>();
        for (Slot slot : container.inventorySlots) {
            if (!isPlayerSlot(slot)) continue;
            int invIdx = slot.getSlotIndex();
            if (invIdx < 0 || invIdx > 35) continue;     // storage slots only
            if (slot.slotNumber == sourceSlotId) continue;
            if (sectionMove) {
                boolean targetIsHotbar = invIdx < 9;
                if (targetIsHotbar == sourceIsHotbar) continue; // opposite section only
            }
            targets.add(slot);
        }
        if (targets.isEmpty()) return false;

        // Vanilla container->player merge runs in reverse slot order
        // (hotbar right-to-left first); section moves run forward
        if (!sectionMove) {
            Collections.reverse(targets);
        }

        // Capacity check: matching non-full stacks anywhere + empty UNLOCKED slots
        int capacity = 0;
        for (Slot slot : targets) {
            ItemStack dest = slot.getStack();
            if (dest.isEmpty()) {
                if (!LockedSlotHandler.isLocked(slot.getSlotIndex())) {
                    capacity += moving.getMaxStackSize();
                }
            } else if (matches(dest, moving) && dest.getCount() < dest.getMaxStackSize()) {
                capacity += dest.getMaxStackSize() - dest.getCount();
            }
        }
        // No legal room: handled (vanilla would have filled a locked slot)
        if (capacity <= 0) return true;

        // Output-style slots (furnace result etc.) reject placement, so a
        // partial move could strand the remainder on the cursor. Fall back to
        // vanilla unless everything fits.
        if (!source.isItemValid(moving) && capacity < moving.getCount()) {
            return false;
        }

        // Lift the source stack
        ContainerClicker.leftClick(sourceSlotId);

        // Merge pass: top up matching stacks (locked ones included)
        for (Slot slot : targets) {
            if (cursorEmpty(mc)) break;
            ItemStack dest = slot.getStack();
            if (dest.isEmpty()) continue;
            if (!matches(dest, mc.player.inventory.getItemStack())) continue;
            if (dest.getCount() >= dest.getMaxStackSize()) continue;
            ContainerClicker.leftClick(slot.slotNumber); // merge, remainder back on cursor
        }

        // Empty pass: first empty UNLOCKED slot takes the rest
        if (!cursorEmpty(mc)) {
            for (Slot slot : targets) {
                if (cursorEmpty(mc)) break;
                if (!slot.getStack().isEmpty()) continue;
                if (LockedSlotHandler.isLocked(slot.getSlotIndex())) continue;
                ContainerClicker.leftClick(slot.slotNumber);
            }
        }

        // Whatever could not be placed goes back to the source slot
        if (!cursorEmpty(mc)) {
            ContainerClicker.leftClick(sourceSlotId);
        }
        return true;
    }

    private static boolean isPlayerSlot(Slot slot) {
        return slot.inventory instanceof InventoryPlayer;
    }

    private static boolean cursorEmpty(Minecraft mc) {
        return mc.player.inventory.getItemStack().isEmpty();
    }

    private static boolean matches(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty()
                && ItemStack.areItemsEqual(a, b)
                && ItemStack.areItemStackTagsEqual(a, b);
    }
}
