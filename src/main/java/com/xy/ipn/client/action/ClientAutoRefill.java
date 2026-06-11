package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Set;

/**
 * Client-side auto-refill.
 *
 * When the player's held item is used up (goes from non-empty to empty
 * between ticks), searches the inventory for a replacement and swaps it
 * into the hotbar or offhand slot using ContainerClicker window-click
 * packets. Works on vanilla servers.
 *
 * The director registers this instance on the Forge event bus.
 */
@SideOnly(Side.CLIENT)
public class ClientAutoRefill {

    private static final int OFFHAND_INV_IDX = 40;

    /** Previous tick's main-hand stack copy. */
    private ItemStack prevMainHand = ItemStack.EMPTY;
    /** Previous tick's off-hand stack copy. */
    private ItemStack prevOffHand = ItemStack.EMPTY;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            prevMainHand = ItemStack.EMPTY;
            prevOffHand = ItemStack.EMPTY;
            return;
        }

        // Refill only acts during gameplay — with any GUI open it would fight
        // the player's own clicks (e.g. shift-clicking the held stack out of
        // the inventory makes the held slot empty and triggers a swap-back).
        if (mc.currentScreen != null) {
            prevMainHand = ItemStack.EMPTY;
            prevOffHand = ItemStack.EMPTY;
            return;
        }

        if (!IPNConfig.enableAutoRefill) {
            prevMainHand = ItemStack.EMPTY;
            prevOffHand = ItemStack.EMPTY;
            return;
        }

        // Only operate when in the player's own inventory view (or in-game)
        if (mc.player.openContainer != mc.player.inventoryContainer) {
            prevMainHand = ItemStack.EMPTY;
            prevOffHand = ItemStack.EMPTY;
            return;
        }

        int hotbarIdx = mc.player.inventory.currentItem;
        ItemStack curMainHand = mc.player.inventory.getCurrentItem();
        ItemStack curOffHand = mc.player.inventory.offHandInventory.get(0);

        // Check main hand
        if (!prevMainHand.isEmpty() && curMainHand.isEmpty()) {
            refill(containerInvIdxToSlotId(hotbarIdx), hotbarIdx, prevMainHand);
        }

        // Check offhand
        if (!prevOffHand.isEmpty() && curOffHand.isEmpty()) {
            refill(containerInvIdxToSlotId(OFFHAND_INV_IDX), OFFHAND_INV_IDX, prevOffHand);
        }

        // Store current stacks for next tick
        prevMainHand = curMainHand.isEmpty() ? ItemStack.EMPTY : curMainHand.copy();
        prevOffHand = curOffHand.isEmpty() ? ItemStack.EMPTY : curOffHand.copy();
    }

    /**
     * Find a replacement for the used-up item and swap it in via
     * ContainerClicker.
     *
     * @param targetSlotId container slotId of the hotbar/offhand slot
     * @param targetInvIdx InventoryPlayer index of the target (for search ordering)
     * @param usedUp       the item that was depleted
     */
    private void refill(int targetSlotId, int targetInvIdx, ItemStack usedUp) {
        Minecraft mc = Minecraft.getMinecraft();
        Container container = mc.player.openContainer;

        // Build search order: nearby slots first (InventoryPlayer indices 0-35)
        int[] searchOrder = buildSearchOrder(targetInvIdx);

        // 1. Try exact match (same item + metadata + NBT)
        int sourceInvIdx = findMatch(container, usedUp, searchOrder, true, true);
        if (sourceInvIdx < 0 && usedUp.isItemStackDamageable()) {
            // 2. For damageable items: try similar match (same item + meta, any NBT)
            sourceInvIdx = findMatch(container, usedUp, searchOrder, false, false);
        }
        if (sourceInvIdx < 0 && usedUp.isItemStackDamageable()) {
            // 3. For tools: try same tool class match
            sourceInvIdx = findToolMatch(container, usedUp, searchOrder);
        }
        if (sourceInvIdx < 0) return;

        int sourceSlotId = containerInvIdxToSlotId(sourceInvIdx);
        if (sourceSlotId < 0) return;

        // Swap replacement into the target slot
        if (targetInvIdx < 36) {
            // Hotbar or main inventory: swap with hotbar index
            int hotbarSwapIdx = targetInvIdx;
            if (targetInvIdx >= 9) {
                // If target is in main inventory, we still use SWAP with the
                // appropriate hotbar key. But auto-refill only targets hotbar
                // or offhand, so this branch is for hotbar.
                hotbarSwapIdx = targetInvIdx;
            }
            ContainerClicker.swap(sourceSlotId, hotbarSwapIdx);
        } else if (targetInvIdx == OFFHAND_INV_IDX) {
            ContainerClicker.swap(sourceSlotId, 40); // 40 = offhand SWAP key
        }

        // Play refill sound locally
        mc.player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
    }

    // ---- Matching logic (ported from RefillHandler) ----

    /**
     * Find a matching item in the container's player slots using the search order.
     *
     * @param container   the player container
     * @param target      the used-up item to match against
     * @param searchOrder InventoryPlayer main inventory indices in priority order
     * @param exactNbt    if true, require exact NBT match
     * @param exactOnly   if true, return first similar on exact fail
     * @return InventoryPlayer index of the match, or -1
     */
    private int findMatch(Container container, ItemStack target, int[] searchOrder,
                          boolean exactNbt, boolean exactOnly) {
        int firstSimilar = -1;

        for (int index : searchOrder) {
            // Skip locked slots
            if (LockedSlotHandler.isLocked(index)) continue;

            ItemStack candidate = getPlayerStackByInvIdx(container, index);
            if (candidate.isEmpty()) continue;

            // For damageable items, skip those below the damage threshold
            if (candidate.isItemStackDamageable()) {
                int maxDamage = candidate.getMaxDamage();
                int damage = candidate.getItemDamage();
                int remaining = maxDamage - damage;
                if (remaining <= IPNConfig.autoRefillDamageThreshold) continue;
            }

            // Must have same item and metadata
            if (candidate.getItem() != target.getItem()) continue;
            if (candidate.getMetadata() != target.getMetadata()) continue;

            boolean nbtMatch = ItemStack.areItemStackTagsEqual(candidate, target);
            if (nbtMatch && exactNbt) {
                return index;
            }
            if (!nbtMatch && exactNbt) {
                if (firstSimilar < 0) {
                    firstSimilar = index;
                }
            }
            if (nbtMatch && !exactNbt) {
                return index;
            }
        }

        if (exactOnly && firstSimilar >= 0) {
            return firstSimilar;
        }
        return -1;
    }

    /**
     * Find a tool with the same tool class set as the target.
     */
    private int findToolMatch(Container container, ItemStack target, int[] searchOrder) {
        Set<String> targetToolClasses = target.getItem().getToolClasses(target);
        if (targetToolClasses.isEmpty()) return -1;

        // Pass 1: exact tool class set match
        for (int index : searchOrder) {
            if (LockedSlotHandler.isLocked(index)) continue;

            ItemStack candidate = getPlayerStackByInvIdx(container, index);
            if (candidate.isEmpty()) continue;
            if (!candidate.isItemStackDamageable()) continue;

            int maxDamage = candidate.getMaxDamage();
            int damage = candidate.getItemDamage();
            int remaining = maxDamage - damage;
            if (remaining <= IPNConfig.autoRefillDamageThreshold) continue;

            Set<String> candidateToolClasses = candidate.getItem().getToolClasses(candidate);
            if (candidateToolClasses.equals(targetToolClasses)) {
                return index;
            }
        }

        // Pass 2: subset/superset matching
        for (int index : searchOrder) {
            if (LockedSlotHandler.isLocked(index)) continue;

            ItemStack candidate = getPlayerStackByInvIdx(container, index);
            if (candidate.isEmpty()) continue;
            if (!candidate.isItemStackDamageable()) continue;

            int maxDamage = candidate.getMaxDamage();
            int damage = candidate.getItemDamage();
            int remaining = maxDamage - damage;
            if (remaining <= IPNConfig.autoRefillDamageThreshold) continue;

            Set<String> candidateToolClasses = candidate.getItem().getToolClasses(candidate);
            if (candidateToolClasses.isEmpty()) continue;

            // If candidate has more tool classes but includes all target classes
            if (candidateToolClasses.size() > targetToolClasses.size()
                    && candidateToolClasses.containsAll(targetToolClasses)) {
                return index;
            }
            // If target has more tool classes but candidate has subset that matches
            if (candidateToolClasses.size() < targetToolClasses.size()
                    && targetToolClasses.containsAll(candidateToolClasses)) {
                return index;
            }
        }

        return -1;
    }

    // ---- Search order (ported from RefillHandler) ----

    /**
     * Build a search order array that prioritizes slots near the given index.
     * Order: other hotbar slots first (by distance), then main inventory rows
     * bottom-to-top.
     */
    private static int[] buildSearchOrder(int targetIndex) {
        int[] order = new int[35]; // 8 other hotbar + 27 main inventory
        int pos = 0;

        // Hotbar slots (0-8), sorted by distance from targetIndex
        for (int dist = 1; dist <= 8; dist++) {
            int slot = targetIndex + dist;
            if (slot < 9 && slot >= 0) order[pos++] = slot;
            slot = targetIndex - dist;
            if (slot >= 0 && slot < 9) order[pos++] = slot;
        }

        // For offhand target (40), search hotbar and main inventory
        // using a sensible order: hotbar first, then main rows bottom-up
        if (targetIndex == OFFHAND_INV_IDX) {
            // Fill remaining slots in hotbar order, then main inventory
            for (int i = 0; i < 9 && pos < 35; i++) {
                order[pos++] = i;
            }
            for (int rowStart = 27; rowStart >= 9 && pos < 35; rowStart -= 9) {
                for (int col = 0; col < 9 && pos < 35; col++) {
                    order[pos++] = rowStart + col;
                }
            }
            return order;
        }

        // Main inventory bottom row (27-35) — closest to hotbar
        int baseCol = targetIndex; // hotbar slot index = column
        pos = addRowByProximity(order, pos, baseCol, 27);
        pos = addRowByProximity(order, pos, baseCol, 18);
        addRowByProximity(order, pos, baseCol, 9);

        return order;
    }

    /**
     * Add a row of 9 slots to the order array, sorted by column distance from
     * baseCol. Returns the new write position.
     */
    private static int addRowByProximity(int[] order, int offset, int baseCol, int rowStart) {
        int pos = offset;
        boolean[] added = new boolean[9];

        for (int dist = 0; dist <= 8; dist++) {
            int col = baseCol + dist;
            if (col >= 0 && col < 9 && !added[col]) {
                order[pos++] = rowStart + col;
                added[col] = true;
            }
            col = baseCol - dist;
            if (col >= 0 && col < 9 && !added[col]) {
                order[pos++] = rowStart + col;
                added[col] = true;
            }
        }
        return pos;
    }

    // ---- Container helpers ----

    /**
     * Convert an InventoryPlayer index (0-35) to a Container slotId.
     * hotbar 0-8 → 36-44, main 9-35 → 9-35.
     */
    private static int containerInvIdxToSlotId(int invIdx) {
        if (invIdx >= 0 && invIdx <= 8) return 36 + invIdx;
        if (invIdx >= 9 && invIdx <= 35) return invIdx;
        if (invIdx == OFFHAND_INV_IDX) return 45;
        return -1;
    }

    /**
     * Get the ItemStack at an InventoryPlayer index by searching the container
     * for a matching player slot.
     */
    private static ItemStack getPlayerStackByInvIdx(Container container, int invIdx) {
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer) {
                if (slot.getSlotIndex() == invIdx) {
                    return slot.getStack();
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
