package com.xy.ipn.sort;

import com.xy.ipn.util.ContainerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes a Container's slot layout and groups slots by type.
 *
 * Produces:
 * - playerHotbar: the 9 hotbar slots (InventoryPlayer indices 0-8)
 * - playerMainInv: the 27 main inventory slots (InventoryPlayer indices 9-35)
 * - containerGroup: the first non-player slot group (chest/furnace/etc.)
 * - groups: all slot groups
 */
public class SortingContext {

    private final List<SlotGroup> groups;
    private final SlotGroup playerHotbar;
    private final SlotGroup playerMainInv;
    private final SlotGroup containerGroup;

    private SortingContext(List<SlotGroup> groups, SlotGroup playerHotbar,
                           SlotGroup playerMainInv, SlotGroup containerGroup) {
        this.groups = groups;
        this.playerHotbar = playerHotbar;
        this.playerMainInv = playerMainInv;
        this.containerGroup = containerGroup;
    }

    /**
     * Create a SortingContext by analyzing all slots in the container.
     */
    public static SortingContext create(Container container, EntityPlayer player) {
        List<Slot> allSlots = container.inventorySlots;

        List<Slot> hotbarSlots = new ArrayList<>();
        List<Slot> mainInvSlots = new ArrayList<>();
        List<SlotGroup> containerGroups = new ArrayList<>();

        List<Slot> currentRun = new ArrayList<>();
        IInventory currentInventory = null;

        for (Slot slot : allSlots) {
            if (ContainerUtils.isPlayerHotbarSlot(slot)) {
                // Flush current container run
                if (!currentRun.isEmpty()) {
                    containerGroups.add(new SlotGroup(new ArrayList<>(currentRun), false));
                    currentRun.clear();
                    currentInventory = null;
                }
                hotbarSlots.add(slot);
            } else if (ContainerUtils.isPlayerMainInvSlot(slot)) {
                // Flush current container run
                if (!currentRun.isEmpty()) {
                    containerGroups.add(new SlotGroup(new ArrayList<>(currentRun), false));
                    currentRun.clear();
                    currentInventory = null;
                }
                mainInvSlots.add(slot);
            } else {
                // Container slot — group by contiguous runs of same inventory
                IInventory slotInv = slot.inventory;
                if (currentInventory == null || currentInventory == slotInv) {
                    currentRun.add(slot);
                    currentInventory = slotInv;
                } else {
                    // Different inventory — flush and start new run
                    if (!currentRun.isEmpty()) {
                        containerGroups.add(new SlotGroup(new ArrayList<>(currentRun), false));
                    }
                    currentRun.clear();
                    currentRun.add(slot);
                    currentInventory = slotInv;
                }
            }
        }

        // Flush final container run
        if (!currentRun.isEmpty()) {
            containerGroups.add(new SlotGroup(new ArrayList<>(currentRun), false));
        }

        SlotGroup hotbar = new SlotGroup(hotbarSlots, true);
        SlotGroup mainInv = new SlotGroup(mainInvSlots, true);

        // Build combined groups list: hotbar, main inventory, then container groups
        List<SlotGroup> allGroups = new ArrayList<>();
        if (!hotbar.isEmpty()) {
            allGroups.add(hotbar);
        }
        if (!mainInv.isEmpty()) {
            allGroups.add(mainInv);
        }
        allGroups.addAll(containerGroups);

        SlotGroup firstContainer = containerGroups.isEmpty() ? null : containerGroups.get(0);

        return new SortingContext(allGroups, hotbar, mainInv, firstContainer);
    }

    /**
     * Find which SlotGroup contains the given slot number.
     */
    public SlotGroup getSlotGroup(int slotNumber) {
        for (SlotGroup group : groups) {
            if (group.contains(slotNumber)) {
                return group;
            }
        }
        return null;
    }

    public List<SlotGroup> getGroups() {
        return groups;
    }

    public SlotGroup getPlayerHotbar() {
        return playerHotbar;
    }

    public SlotGroup getPlayerMainInv() {
        return playerMainInv;
    }

    /**
     * Get the first non-player container slot group.
     * May be null if no container slots exist.
     */
    public SlotGroup getContainerGroup() {
        return containerGroup;
    }
}
