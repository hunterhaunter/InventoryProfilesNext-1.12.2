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

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side "move all" — QUICK_MOVE every non-empty, non-locked source slot.
 *
 * Direction:
 * <ul>
 *   <li>{@code toContainer = true}  → player hotbar + main → container</li>
 *   <li>{@code toContainer = false} → container → player</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class ClientMoveAll {

    private ClientMoveAll() {
    }

    /**
     * Move all items between the player inventory and the container.
     *
     * @param gui         the open container screen
     * @param slotId      a slot number used to identify the side (player vs container)
     * @param toContainer {@code true} to move player → container, {@code false} for container → player
     */
    public static void moveAll(GuiContainer gui, int slotId, boolean toContainer) {
        Minecraft mc = Minecraft.getMinecraft();
        Container container = gui.inventorySlots;

        SortingContext ctx = SortingContext.create(container, mc.player);

        // Without a real container (plain player inventory), QUICK_MOVE just
        // shuffles items main<->hotbar. Original MOVE_ALL is a no-op there.
        boolean hasContainerSlots = false;
        for (SlotGroup group : ctx.getGroups()) {
            if (!group.isPlayerInventory() && !group.isEmpty()) {
                hasContainerSlots = true;
                break;
            }
        }
        if (!hasContainerSlots) {
            return;
        }

        List<Slot> sourceSlots = new ArrayList<Slot>();

        if (toContainer) {
            // Player → Container: hotbar first, then main inventory
            addPlayerSlots(sourceSlots, ctx.getPlayerHotbar());
            addPlayerSlots(sourceSlots, ctx.getPlayerMainInv());
        } else {
            // Container → Player: all container slots
            for (SlotGroup group : ctx.getGroups()) {
                if (!group.isPlayerInventory()) {
                    sourceSlots.addAll(group.getSlots());
                }
            }
        }

        for (Slot slot : sourceSlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (!slot.canTakeStack(mc.player)) {
                continue;
            }
            // Skip locked player-inventory source slots
            if (slot.inventory instanceof InventoryPlayer
                    && LockedSlotHandler.isLocked(slot.getSlotIndex())) {
                continue;
            }

            if (toContainer) {
                ContainerClicker.shiftClick(slot.slotNumber);
            } else if (!ClientQuickMoveRouter.quickMoveToPlayer(container, slot.slotNumber, false)) {
                // no locked slots (or router declined) — vanilla is identical
                ContainerClicker.shiftClick(slot.slotNumber);
            }
        }
    }

    private static void addPlayerSlots(List<Slot> dest, SlotGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }
        dest.addAll(group.getSlots());
    }
}
