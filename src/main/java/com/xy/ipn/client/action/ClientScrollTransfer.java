package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.sort.SlotGroup;
import com.xy.ipn.sort.SortingContext;
import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side scroll transfer — moves items one-at-a-time or by full stack
 * between the player inventory and the open container.
 *
 * @see com.xy.ipn.client.ScrollHandler for the scroll-wheel trigger
 */
@SideOnly(Side.CLIENT)
public final class ClientScrollTransfer {

    private ClientScrollTransfer() {
    }

    /**
     * Transfer the stack at {@code slotId} to the other section.
     *
     * @param gui         the open container screen
     * @param slotId      the slot to transfer from
     * @param toContainer {@code true} to move toward the container,
     *                    {@code false} to move toward the player inventory
     * @param fullStack   {@code true} = shift-click the whole stack;
     *                    {@code false} = move a single item via click sequence
     */
    public static void transfer(GuiContainer gui, int slotId,
                                 boolean toContainer, boolean fullStack) {
        Minecraft mc = Minecraft.getMinecraft();
        Container container = gui.inventorySlots;

        Slot sourceSlot = container.getSlot(slotId);
        if (sourceSlot == null) {
            return;
        }
        ItemStack sourceStack = sourceSlot.getStack();
        if (sourceStack.isEmpty()) {
            return;
        }

        // Skip if source is a locked player slot AND we are moving OUT of player inventory
        if (toContainer
                && sourceSlot.inventory instanceof InventoryPlayer
                && LockedSlotHandler.isLocked(sourceSlot.getSlotIndex())) {
            return;
        }

        SortingContext ctx = SortingContext.create(container, mc.player);

        // Build the destination slot list
        List<Slot> destSlots;
        if (toContainer) {
            destSlots = getContainerSlots(ctx);
        } else {
            destSlots = getPlayerSlots(ctx);
        }

        if (destSlots.isEmpty()) {
            return;
        }

        if (fullStack) {
            // Full stack: vanilla shift-click
            ContainerClicker.shiftClick(slotId);
            return;
        }

        // Single item: find best destination
        Slot bestDest = findBestDestination(destSlots, sourceStack, mc);
        if (bestDest == null) {
            return;
        }

        // PICKUP source → right-click destination (place one) → PICKUP remainder back at source
        ContainerClicker.leftClick(slotId);               // lift stack
        ContainerClicker.rightClick(bestDest.slotNumber); // place exactly one
        ContainerClicker.leftClick(slotId);               // return remainder
    }

    // ---- destination selection ---------------------------------------------

    /**
     * Find the best destination slot for a single-item transfer:
     * <ol>
     *   <li>First non-full slot with a matching stack (item + meta + NBT) that
     *       passes {@code isItemValid}.</li>
     *   <li>Otherwise the first empty slot that passes {@code isItemValid}.</li>
     * </ol>
     */
    private static Slot findBestDestination(List<Slot> destSlots, ItemStack sourceStack,
                                             Minecraft mc) {
        // Pass 1: matching non-full stack
        for (Slot slot : destSlots) {
            ItemStack destStack = slot.getStack();
            if (destStack.isEmpty()) continue;
            if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
            if (!ItemStack.areItemsEqual(sourceStack, destStack)) continue;
            if (!ItemStack.areItemStackTagsEqual(sourceStack, destStack)) continue;
            if (!slot.isItemValid(sourceStack)) continue;
            return slot;
        }

        // Pass 2: first empty valid slot
        for (Slot slot : destSlots) {
            if (!slot.getStack().isEmpty()) continue;
            if (!slot.isItemValid(sourceStack)) continue;
            return slot;
        }

        return null;
    }

    // ---- slot list builders ------------------------------------------------

    private static List<Slot> getContainerSlots(SortingContext ctx) {
        List<Slot> result = new ArrayList<Slot>();
        for (SlotGroup group : ctx.getGroups()) {
            if (!group.isPlayerInventory()) {
                result.addAll(group.getSlots());
            }
        }
        return result;
    }

    private static List<Slot> getPlayerSlots(SortingContext ctx) {
        List<Slot> result = new ArrayList<Slot>();
        SlotGroup mainInv = ctx.getPlayerMainInv();
        SlotGroup hotbar = ctx.getPlayerHotbar();
        if (mainInv != null && !mainInv.isEmpty()) {
            result.addAll(mainInv.getSlots());
        }
        if (hotbar != null && !hotbar.isEmpty()) {
            result.addAll(hotbar.getSlots());
        }
        return result;
    }
}
