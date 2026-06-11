package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.sort.SlotGroup;
import com.xy.ipn.sort.SortingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side "throw all" — THROW(1) every slot in the same group whose stack
 * matches the type of the stack at {@code slotId}.  Locked player slots are
 * skipped.
 */
@SideOnly(Side.CLIENT)
public final class ClientThrowAll {

    private ClientThrowAll() {
    }

    /**
     * Throw the entire contents of every slot in the same group as
     * {@code slotId} whose item type matches the stack at {@code slotId}.
     *
     * @param gui    the open container screen
     * @param slotId a slot number whose item type determines what to throw
     */
    public static void throwAll(GuiContainer gui, int slotId) {
        Minecraft mc = Minecraft.getMinecraft();
        Container container = gui.inventorySlots;

        SortingContext ctx = SortingContext.create(container, mc.player);
        SlotGroup group = slotId >= 0 ? ctx.getSlotGroup(slotId) : null;
        if (group == null || group.isEmpty()) {
            return;
        }

        // Determine the type to match
        Slot referenceSlot = container.getSlot(slotId);
        if (referenceSlot == null) {
            return;
        }
        ItemStack reference = referenceSlot.getStack();
        if (reference.isEmpty()) {
            return; // nothing to throw — slot is empty
        }

        for (Slot slot : group.getSlots()) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemStack.areItemsEqual(reference, stack)
                    || !ItemStack.areItemStackTagsEqual(reference, stack)) {
                continue;
            }
            // Skip locked player-inventory slots
            if (slot.inventory instanceof InventoryPlayer
                    && LockedSlotHandler.isLocked(slot.getSlotIndex())) {
                continue;
            }

            ContainerClicker.throwStack(slot.slotNumber);
        }
    }
}
