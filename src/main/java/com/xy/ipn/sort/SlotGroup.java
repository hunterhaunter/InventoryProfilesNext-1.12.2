package com.xy.ipn.sort;

import net.minecraft.inventory.Slot;

import java.util.List;

/**
 * Represents a group of contiguous slots in a container.
 */
public class SlotGroup {

    private final List<Slot> slots;
    private final boolean isPlayerInventory;

    public SlotGroup(List<Slot> slots, boolean isPlayerInventory) {
        this.slots = slots;
        this.isPlayerInventory = isPlayerInventory;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * Check if this group contains a slot with the given slot number
     * (index in the Container's inventorySlots list).
     */
    public boolean contains(int slotNumber) {
        for (Slot slot : slots) {
            if (slot.slotNumber == slotNumber) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlayerInventory() {
        return isPlayerInventory;
    }
}
