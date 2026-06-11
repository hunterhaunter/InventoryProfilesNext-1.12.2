package com.xy.ipn.util;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerUtils {

    /**
     * Check if a slot belongs to a player's inventory.
     */
    public static boolean isPlayerSlot(Slot slot) {
        return slot.inventory instanceof InventoryPlayer;
    }

    /**
     * Check if a slot is in the player's main inventory (slots 9-35 in InventoryPlayer).
     */
    public static boolean isPlayerMainInvSlot(Slot slot) {
        if (!(slot.inventory instanceof InventoryPlayer)) {
            return false;
        }
        int index = slot.getSlotIndex();
        return index >= 9 && index < 36;
    }

    /**
     * Check if a slot is in the player's hotbar (slots 0-8 in InventoryPlayer).
     */
    public static boolean isPlayerHotbarSlot(Slot slot) {
        if (!(slot.inventory instanceof InventoryPlayer)) {
            return false;
        }
        int index = slot.getSlotIndex();
        return index >= 0 && index < 9;
    }

    /**
     * Get the InventoryPlayer index for this slot.
     * Returns -1 if the slot is not a player slot.
     */
    public static int getPlayerInvIndex(Slot slot) {
        if (!(slot.inventory instanceof InventoryPlayer)) {
            return -1;
        }
        return slot.getSlotIndex();
    }

    /**
     * Try to merge a stack into a slot. Returns the remainder that could not fit.
     * If the slot is empty, puts the stack into the slot.
     * If the slot has a matching item, adds as much as possible.
     */
    public static ItemStack mergeIntoSlot(Slot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getStack();

        // Slot is empty — put the whole stack
        if (slotStack.isEmpty()) {
            int toPut = Math.min(stack.getCount(), Math.min(slot.getItemStackLimit(stack), stack.getMaxStackSize()));
            ItemStack put = stack.copy();
            put.setCount(toPut);
            slot.putStack(put);
            slot.onSlotChanged();

            if (toPut >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - toPut);
            return remainder;
        }

        // Slot has a matching item — try to grow it
        if (ItemStack.areItemsEqual(stack, slotStack)
                && ItemStack.areItemStackTagsEqual(stack, slotStack)) {
            int maxSize = Math.min(slot.getItemStackLimit(slotStack), slotStack.getMaxStackSize());
            int space = maxSize - slotStack.getCount();
            if (space > 0) {
                int toAdd = Math.min(space, stack.getCount());
                slotStack.grow(toAdd);
                slot.onSlotChanged();

                if (toAdd >= stack.getCount()) {
                    return ItemStack.EMPTY;
                }
                ItemStack remainder = stack.copy();
                remainder.setCount(stack.getCount() - toAdd);
                return remainder;
            }
        }

        // Cannot merge — return entire stack as remainder
        return stack;
    }
}
