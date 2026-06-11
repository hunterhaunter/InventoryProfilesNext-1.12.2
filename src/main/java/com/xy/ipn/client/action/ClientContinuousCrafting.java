package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side continuous crafting refill.
 *
 * When the player crafts, this snapshots the crafting grid ingredients.
 * One tick later it refills any emptied grid slots from the player's main
 * inventory using ContainerClicker window-click packets, so it works on
 * vanilla servers.
 *
 * The director registers this instance on the Forge event bus.
 */
@SideOnly(Side.CLIENT)
public class ClientContinuousCrafting {

    /** Ticks to keep a pending snapshot alive while the cursor is occupied. */
    private static final int PENDING_TIMEOUT_TICKS = 100;

    /** Pending snapshot from the last ItemCraftedEvent. */
    private ItemStack[] pendingSnapshot;
    /** The container window ID the snapshot was taken from. */
    private int snapshotWindowId = -1;
    /** Age of the pending snapshot in ticks. */
    private int pendingAge;

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.player.world.isRemote) return;
        if (!IPNConfig.enableContinuousCrafting) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        Container container = mc.player.openContainer;
        if (container == null) return;

        // Find all crafting-grid slots
        List<Slot> gridSlots = new ArrayList<Slot>();
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryCrafting) {
                gridSlots.add(slot);
            }
        }
        if (gridSlots.isEmpty()) return;

        pendingSnapshot = new ItemStack[gridSlots.size()];
        for (int i = 0; i < gridSlots.size(); i++) {
            ItemStack stack = gridSlots.get(i).getStack();
            pendingSnapshot[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        snapshotWindowId = container.windowId;
        pendingAge = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingSnapshot == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            pendingSnapshot = null;
            return;
        }

        Container container = mc.player.openContainer;
        if (container == null || container.windowId != snapshotWindowId) {
            pendingSnapshot = null;
            return;
        }

        // The player usually still holds the crafted result on the cursor this
        // tick (normal click on the result slot). Refilling now would place the
        // crafted item into the inventory with our first leftClick. Wait until
        // the cursor is free, with a timeout so a stale snapshot can't linger.
        if (!mc.player.inventory.getItemStack().isEmpty()) {
            pendingAge++;
            if (pendingAge > PENDING_TIMEOUT_TICKS) {
                pendingSnapshot = null;
            }
            return;
        }

        // Gather current crafting-grid slots
        List<Slot> gridSlots = new ArrayList<Slot>();
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryCrafting) {
                gridSlots.add(slot);
            }
        }
        if (gridSlots.isEmpty()) {
            pendingSnapshot = null;
            return;
        }

        int size = Math.min(pendingSnapshot.length, gridSlots.size());

        // Group slots needing refill by item type from snapshot.
        // A slot needs refill only if it was non-empty in the snapshot
        // and is now empty.
        Map<String, List<Integer>> typeGroups = new LinkedHashMap<String, List<Integer>>();
        Map<String, ItemStack> typeTemplates = new LinkedHashMap<String, ItemStack>();

        for (int i = 0; i < size; i++) {
            ItemStack before = pendingSnapshot[i];
            if (before.isEmpty()) continue;
            ItemStack current = gridSlots.get(i).getStack();
            if (!current.isEmpty()) continue;

            String key = itemKey(before);
            if (!typeGroups.containsKey(key)) {
                typeGroups.put(key, new ArrayList<Integer>());
                typeTemplates.put(key, before.copy());
            }
            typeGroups.get(key).add(i);
        }

        if (typeGroups.isEmpty()) {
            pendingSnapshot = null;
            return;
        }

        // Gather player inventory slots (invIdx 0-35, hotbar included — the
        // original counts all 36 main-inventory slots as refill sources)
        List<Slot> playerMainSlots = new ArrayList<Slot>();
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer) {
                int invIdx = slot.getSlotIndex();
                if (invIdx >= 0 && invIdx <= 35) {
                    playerMainSlots.add(slot);
                }
            }
        }

        for (Map.Entry<String, List<Integer>> entry : typeGroups.entrySet()) {
            ItemStack template = typeTemplates.get(entry.getKey());
            List<Integer> groupIndices = entry.getValue();
            int numSlots = groupIndices.size();
            int maxPerSlot = template.getMaxStackSize();

            // Count matching items in player main inventory (not locked)
            int total = 0;
            for (Slot playerSlot : playerMainSlots) {
                int invIdx = playerSlot.getSlotIndex();
                if (LockedSlotHandler.isLocked(invIdx)) continue;
                ItemStack s = playerSlot.getStack();
                if (matchesType(s, template)) {
                    total += s.getCount();
                }
            }

            // Target per slot: integer division, capped at maxStackSize
            int perSlot = total / numSlots;
            if (perSlot > maxPerSlot) {
                perSlot = maxPerSlot;
            }
            if (perSlot == 0) continue;

            // Build list of grid slotIds for this group
            List<Integer> groupSlotIds = new ArrayList<Integer>();
            for (int gi : groupIndices) {
                groupSlotIds.add(gridSlots.get(gi).slotNumber);
            }

            // While any group slot is below target and sources remain, refill.
            // Progress guard: vanilla even-split gives floor(cursor/slots) per
            // slot — 0 when the cursor stack is smaller than the drag set, so
            // an iteration can be a complete no-op. Bail when grid counts stop
            // changing instead of looping forever.
            int prevGridTotal = -1;
            while (true) {
                // Build list of group slots still below target
                List<Integer> belowSlots = new ArrayList<Integer>();
                int gridTotal = 0;
                for (int gsId : groupSlotIds) {
                    Slot gs = container.getSlot(gsId);
                    if (gs == null) continue;
                    gridTotal += gs.getStack().getCount();
                    if (gs.getStack().getCount() < perSlot) {
                        belowSlots.add(gsId);
                    }
                }
                if (belowSlots.isEmpty()) break; // all satisfied
                if (gridTotal == prevGridTotal) break; // no progress — bail
                prevGridTotal = gridTotal;

                // Find a source slot with matching items
                int sourceSlotId = -1;
                for (Slot playerSlot : playerMainSlots) {
                    int invIdx = playerSlot.getSlotIndex();
                    if (LockedSlotHandler.isLocked(invIdx)) continue;
                    ItemStack s = playerSlot.getStack();
                    if (matchesType(s, template)) {
                        sourceSlotId = playerSlot.slotNumber;
                        break;
                    }
                }
                if (sourceSlotId < 0) break; // no more sources

                // Lift from source, drag-split-even to below-target grid slots,
                // return remainder
                ContainerClicker.leftClick(sourceSlotId);
                ContainerClicker.dragSplitEven(belowSlots);

                if (!mc.player.inventory.getItemStack().isEmpty()) {
                    ContainerClicker.leftClick(sourceSlotId);
                }
            }
        }

        pendingSnapshot = null;
    }

    // ---- Matching helpers (ported from ContinuousCraftingHandler) ----

    private static boolean matchesType(ItemStack a, ItemStack b) {
        return !a.isEmpty() && a.getItem() == b.getItem()
                && a.getMetadata() == b.getMetadata()
                && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static String itemKey(ItemStack stack) {
        int id = Item.getIdFromItem(stack.getItem());
        int meta = stack.getMetadata();
        int nbtHash = stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0;
        return id + ":" + meta + ":" + nbtHash;
    }
}
