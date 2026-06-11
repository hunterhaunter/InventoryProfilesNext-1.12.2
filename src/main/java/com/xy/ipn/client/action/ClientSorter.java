package com.xy.ipn.client.action;

import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.config.IPNConfig;
import com.xy.ipn.sort.ItemSortComparator;
import com.xy.ipn.sort.SlotGroup;
import com.xy.ipn.sort.SortingContext;
import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side sort executed entirely through vanilla window-click packets.
 *
 * Follows the same behavioural contract as {@link com.xy.ipn.sort.SortHandler#sort}
 * but realises the target layout via merge-then-permutation click passes
 * (see {@code CONTEXT-CLICKSIM.md}).
 */
@SideOnly(Side.CLIENT)
public final class ClientSorter {

    private ClientSorter() {
    }

    // ---- public entry point ------------------------------------------------

    /**
     * Sort the slot group that contains {@code slotId} inside the currently
     * open container GUI.
     *
     * @param gui          the open container screen
     * @param slotId       a slot number belonging to the group to sort
     * @param layoutMethod 0 = left-to-right fill, 1 = grouped columns, 2 = grouped rows
     */
    public static void sort(GuiContainer gui, int slotId, int layoutMethod) {
        if (!IPNConfig.enableSorting) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Container container = gui.inventorySlots;

        // 1. Build the slot group containing slotId
        SortingContext ctx = SortingContext.create(container, mc.player);
        SlotGroup group = ctx.getSlotGroup(slotId);
        if (group == null || group.isEmpty()) {
            return;
        }

        // 2. Filter sortable slots
        List<Slot> sortableSlots = getSortableSlots(group);
        if (sortableSlots.isEmpty()) {
            return;
        }

        // 3. Compute target layout on copies (do not touch real slots)
        List<ItemStack> merged = gatherAndMergeOnCopies(sortableSlots);
        if (merged.isEmpty()) {
            return;
        }

        int comparatorIdx = comparatorFromConfig();
        merged.sort(new ItemSortComparator(comparatorIdx));

        int numCols = detectNumCols(sortableSlots);
        List<ItemStack> overflow;
        ItemStack[] target;
        if (layoutMethod == 1) {
            target = buildGroupedTarget(sortableSlots, merged, numCols, true);
        } else if (layoutMethod == 2) {
            target = buildGroupedTarget(sortableSlots, merged, numCols, false);
        } else {
            target = buildFlatTarget(sortableSlots, merged);
        }

        // 4. Realize the target via clicks
        realizeTarget(gui, sortableSlots, target);

        // 5. Restock hotbar if enabled and sorting a player-inventory group
        if (IPNConfig.restockHotbar && group.isPlayerInventory()) {
            restockHotbarViaClicks(gui, ctx);
        }
    }

    // ---- slot filtering ----------------------------------------------------

    /**
     * Return the subset of group slots that can be sorted.
     * Excludes locked player-inventory slots and slots the player cannot take from.
     */
    private static List<Slot> getSortableSlots(SlotGroup group) {
        Minecraft mc = Minecraft.getMinecraft();
        List<Slot> result = new ArrayList<Slot>();
        for (Slot slot : group.getSlots()) {
            if (slot.inventory instanceof InventoryPlayer
                    && LockedSlotHandler.isLocked(slot.getSlotIndex())) {
                continue;
            }
            if (!slot.canTakeStack(mc.player)) {
                continue;
            }
            // Must be able to insert as well (empty or valid for own stack)
            if (!slot.getStack().isEmpty() && !slot.isItemValid(slot.getStack())) {
                continue;
            }
            result.add(slot);
        }
        return result;
    }

    // ---- gather + merge on copies ------------------------------------------

    /**
     * Build a merged list from copies of every non-empty sortable slot.
     * Identical-type stacks (item + meta + NBT) are combined up to max stack
     * size.  The real slots are NOT modified.
     */
    private static List<ItemStack> gatherAndMergeOnCopies(List<Slot> slots) {
        List<ItemStack> merged = new ArrayList<ItemStack>();

        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack taken = stack.copy();

            // Try to merge into existing entries
            for (ItemStack existing : merged) {
                if (taken.isEmpty()) break;
                if (ItemStack.areItemsEqual(existing, taken)
                        && ItemStack.areItemStackTagsEqual(existing, taken)) {
                    int maxSize = existing.getMaxStackSize();
                    int space = maxSize - existing.getCount();
                    if (space > 0) {
                        int toAdd = Math.min(space, taken.getCount());
                        existing.grow(toAdd);
                        taken.shrink(toAdd);
                    }
                }
            }

            if (!taken.isEmpty()) {
                merged.add(taken);
            }
        }
        return merged;
    }

    // ---- target builders ---------------------------------------------------

    /** Fill target array left-to-right, splitting stacks at max size. */
    private static ItemStack[] buildFlatTarget(List<Slot> sortableSlots,
                                                List<ItemStack> sorted) {
        ItemStack[] target = emptyTarget(sortableSlots.size());
        int slotIdx = 0;
        for (ItemStack stack : sorted) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            while (!remaining.isEmpty() && slotIdx < target.length) {
                int maxSize = remaining.getMaxStackSize();
                int toPut = Math.min(maxSize, remaining.getCount());
                target[slotIdx] = remaining.copy();
                target[slotIdx].setCount(toPut);
                remaining.shrink(toPut);
                slotIdx++;
            }
        }
        return target;
    }

    /**
     * Build a target array using grouped-in-columns or grouped-in-rows layout.
     * Falls back to flat placement when the slot grid is not rectangular.
     */
    private static ItemStack[] buildGroupedTarget(List<Slot> sortableSlots,
                                                   List<ItemStack> sorted,
                                                   int numCols,
                                                   boolean columns) {
        int numRows = sortableSlots.size() / numCols;
        if (numRows * numCols != sortableSlots.size()) {
            return buildFlatTarget(sortableSlots, sorted);
        }

        List<List<ItemStack>> groups = groupStacksByType(sorted);
        List<Integer> groupSizes = new ArrayList<Integer>();
        for (List<ItemStack> g : groups) {
            groupSizes.add(g.size());
        }

        GroupInColumnsCalculator calc;
        if (columns) {
            calc = new GroupInColumnsCalculator(groupSizes, numRows, numCols);
        } else {
            calc = new GroupInColumnsCalculator(groupSizes, numCols, numRows);
        }

        List<List<Integer>> slotIndicesPerGroup = calc.calc();
        if (slotIndicesPerGroup == null) {
            return buildFlatTarget(sortableSlots, sorted);
        }

        if (columns) {
            for (List<Integer> indices : slotIndicesPerGroup) {
                for (int j = 0; j < indices.size(); j++) {
                    indices.set(j,
                            GroupInColumnsCalculator.transposedIndex(numRows, numCols, indices.get(j)));
                }
            }
        }

        return applyGroupTarget(sortableSlots.size(), groups, slotIndicesPerGroup);
    }

    /** Place groups at their calculated indices; excess goes into overflow slots. */
    private static ItemStack[] applyGroupTarget(int totalSlots,
                                                 List<List<ItemStack>> groups,
                                                 List<List<Integer>> slotIndicesPerGroup) {
        ItemStack[] target = emptyTarget(totalSlots);

        List<ItemStack> overflow = new ArrayList<ItemStack>();
        for (int g = 0; g < groups.size(); g++) {
            List<ItemStack> group = groups.get(g);
            List<Integer> indices = slotIndicesPerGroup.get(g);
            for (int i = 0; i < group.size(); i++) {
                if (i < indices.size() && indices.get(i) < totalSlots) {
                    target[indices.get(i)] = group.get(i).copy();
                } else {
                    overflow.add(group.get(i).copy());
                }
            }
        }

        // Append overflow into remaining empty target slots
        int oi = 0;
        for (int i = 0; i < target.length && oi < overflow.size(); i++) {
            if (target[i].isEmpty()) {
                target[i] = overflow.get(oi++);
            }
        }

        return target;
    }

    private static ItemStack[] emptyTarget(int size) {
        ItemStack[] t = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            t[i] = ItemStack.EMPTY;
        }
        return t;
    }

    // ---- layout helpers (duplicated from SortHandler / GroupInColumnsCalc) ---

    private static int detectNumCols(List<Slot> slots) {
        if (slots.size() <= 1) return slots.size();
        int firstY = slots.get(0).yPos;
        for (int i = 1; i < slots.size(); i++) {
            if (slots.get(i).yPos != firstY) return i;
        }
        return slots.size();
    }

    private static List<List<ItemStack>> groupStacksByType(List<ItemStack> sorted) {
        List<List<ItemStack>> groups = new ArrayList<List<ItemStack>>();
        List<ItemStack> current = null;
        ItemStack lastType = null;
        for (ItemStack stack : sorted) {
            if (stack.isEmpty()) continue;
            boolean same = lastType != null
                    && ItemStack.areItemsEqual(lastType, stack)
                    && ItemStack.areItemStackTagsEqual(lastType, stack);
            if (!same) {
                current = new ArrayList<ItemStack>();
                groups.add(current);
            }
            current.add(stack);
            lastType = stack;
        }
        return groups;
    }

    // ---- click-based realisation -------------------------------------------

    /**
     * Realise the {@code target} layout through two client-side passes:
     * <ol>
     *   <li><b>Merge pass</b> — combine partial stacks of the same type.</li>
     *   <li><b>Permutation pass</b> — selection-sort the slots to match target.</li>
     * </ol>
     */
    private static void realizeTarget(GuiContainer gui, List<Slot> sortableSlots,
                                       ItemStack[] target) {
        // Merge pass
        mergePass(gui, sortableSlots);

        // Permutation pass
        permutationPass(gui, sortableSlots, target);
    }

    /**
     * For each slot i with a partial stack, scan later slots j for the same
     * item type and merge j into i via clicks (PICKUP j → PICKUP i → put
     * remainder back at j).
     */
    private static void mergePass(GuiContainer gui, List<Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slotI = slots.get(i);
            ItemStack stackI = slotI.getStack();
            if (stackI.isEmpty() || stackI.getCount() >= stackI.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < slots.size(); j++) {
                // Re-read slotI — may have been modified by a previous merge
                stackI = slotI.getStack();
                if (stackI.isEmpty() || stackI.getCount() >= stackI.getMaxStackSize()) {
                    break;
                }
                Slot slotJ = slots.get(j);
                ItemStack stackJ = slotJ.getStack();
                if (stackJ.isEmpty()) continue;
                if (!ItemStack.areItemsEqual(stackI, stackJ)
                        || !ItemStack.areItemStackTagsEqual(stackI, stackJ)) {
                    continue;
                }

                ContainerClicker.leftClick(slotJ.slotNumber);   // lift from j
                ContainerClicker.leftClick(slotI.slotNumber);   // merge onto i

                // If cursor still holds items, put remainder back at j
                if (!Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty()) {
                    ContainerClicker.leftClick(slotJ.slotNumber);
                }
            }
        }
    }

    /**
     * Selection-sort the slots via click swaps so every sortable slot ends up
     * with the stack described by {@code target[i]}.
     */
    private static void permutationPass(GuiContainer gui, List<Slot> slots,
                                         ItemStack[] target) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slotI = slots.get(i);
            ItemStack cur = slotI.getStack();
            ItemStack want = target[i];

            // Already correct?
            if (stacksEqual(cur, want)) {
                continue;
            }

            // Find a later slot j whose current stack matches target[i]
            int found = -1;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stacksEqual(slots.get(j).getStack(), want)) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                continue; // no matching stack available — leave as-is
            }

            Slot slotJ = slots.get(found);
            ContainerClicker.leftClick(slotJ.slotNumber);   // pick up target stack
            ContainerClicker.leftClick(slotI.slotNumber);   // place at i (displaces i's old stack onto cursor)

            // Put any cursor remainder back at j
            if (!Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty()) {
                ContainerClicker.leftClick(slotJ.slotNumber);
            }
        }
    }

    /** True when both stacks are equal in item, meta, NBT AND count. */
    private static boolean stacksEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.areItemsEqual(a, b)
                && ItemStack.areItemStackTagsEqual(a, b)
                && a.getCount() == b.getCount();
    }

    // ---- restock hotbar via clicks -----------------------------------------

    /**
     * Top up partial hotbar stacks from the main inventory using clicks.
     * Skips locked main-inventory source slots.
     */
    private static void restockHotbarViaClicks(GuiContainer gui, SortingContext ctx) {
        SlotGroup hotbar = ctx.getPlayerHotbar();
        SlotGroup mainInv = ctx.getPlayerMainInv();
        if (hotbar == null || mainInv == null) {
            return;
        }

        for (Slot hotbarSlot : hotbar.getSlots()) {
            ItemStack hotbarStack = hotbarSlot.getStack();
            if (hotbarStack.isEmpty() || hotbarStack.getCount() >= hotbarStack.getMaxStackSize()) {
                continue;
            }

            for (Slot mainSlot : mainInv.getSlots()) {
                // Re-read hotbar stack — may have been topped up by a previous source
                hotbarStack = hotbarSlot.getStack();
                if (hotbarStack.isEmpty() || hotbarStack.getCount() >= hotbarStack.getMaxStackSize()) {
                    break;
                }

                // Skip locked main-inventory source slots
                if (mainSlot.inventory instanceof InventoryPlayer
                        && LockedSlotHandler.isLocked(mainSlot.getSlotIndex())) {
                    continue;
                }

                ItemStack mainStack = mainSlot.getStack();
                if (mainStack.isEmpty()) continue;
                if (!ItemStack.areItemsEqual(hotbarStack, mainStack)) continue;
                if (!ItemStack.areItemStackTagsEqual(hotbarStack, mainStack)) continue;

                // PICKUP main source → PICKUP onto hotbar (merges up to max) → PICKUP remainder back
                ContainerClicker.leftClick(mainSlot.slotNumber);
                ContainerClicker.leftClick(hotbarSlot.slotNumber);
                if (!Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty()) {
                    ContainerClicker.leftClick(mainSlot.slotNumber);
                }
            }
        }
    }

    // ---- config mapping ----------------------------------------------------

    private static int comparatorFromConfig() {
        String method = IPNConfig.defaultSortMethod;
        if ("name".equalsIgnoreCase(method)) return 1;
        if ("mod".equalsIgnoreCase(method)) return 2;
        return 0;
    }

    // ========================================================================
    // Duplicated GroupInColumnsCalculator (package-private in com.xy.ipn.sort)
    // ========================================================================

    /**
     * Internal copy of {@code com.xy.ipn.sort.GroupInColumnsCalculator}.
     * Duplicated here so we do not modify the visibility of the original.
     */
    private static final class GroupInColumnsCalculator {

        private final List<Integer> groupSizes;
        private final int width;
        private final int height;

        GroupInColumnsCalculator(List<Integer> groupSizes, int width, int height) {
            this.groupSizes = groupSizes;
            this.width = width;
            this.height = height;
        }

        List<List<Integer>> calc() {
            int n = groupSizes.size();
            if (n == 0) return null;

            List<ColumnsCandidate> candidates = new ArrayList<ColumnsCandidate>();
            for (int c = 1; c <= width; c++) {
                if (n > height * c) continue;
                int[] colWidths = distribute(width, c);
                ColumnsCandidate candidate = new ColumnsCandidate(groupSizes, colWidths, height, width);
                if (!candidate.succeeded) continue;
                if (candidate.brokenGroups == 0) {
                    return candidate.apply();
                }
                candidates.add(candidate);
            }

            ColumnsCandidate best = null;
            for (ColumnsCandidate cand : candidates) {
                if (best == null || cand.brokenGroups < best.brokenGroups) {
                    best = cand;
                }
            }
            return best != null ? best.apply() : null;
        }

        static int[] distribute(int total, int parts) {
            int[] result = new int[parts];
            for (int i = 0; i < parts; i++) {
                result[i] = total * (i + 1) / parts - total * i / parts;
            }
            return result;
        }

        static int transposedIndex(int n, int n2, int i) {
            return i % n * n2 + i / n;
        }

        private static class Cell {
            final int room;
            final int rowIndex;
            final int columnIndex;
            boolean occupied;
            final int slotX;
            final int totalWidth;

            Cell(int room, int rowIndex, int columnIndex, int slotX, int totalWidth) {
                this.room = room;
                this.rowIndex = rowIndex;
                this.columnIndex = columnIndex;
                this.slotX = slotX;
                this.totalWidth = totalWidth;
            }

            int slotIndex() {
                return rowIndex * totalWidth + slotX;
            }

            List<Integer> slotIndices() {
                List<Integer> indices = new ArrayList<Integer>(room);
                int base = slotIndex();
                for (int i = 0; i < room; i++) {
                    indices.add(base + i);
                }
                return indices;
            }
        }

        private static class ColumnsCandidate {
            final List<Integer> groupSizes;
            final int[] columnWidths;
            final int height;
            final int totalWidth;
            int brokenGroups;
            boolean succeeded;
            final List<Cell> cells;
            int cellIndex;
            boolean allowBroken;
            final List<List<Cell>> eachCellsList;

            ColumnsCandidate(List<Integer> groupSizes, int[] columnWidths, int height, int totalWidth) {
                this.groupSizes = groupSizes;
                this.columnWidths = columnWidths;
                this.height = height;
                this.totalWidth = totalWidth;

                cells = new ArrayList<Cell>();
                int slotX = 0;
                for (int col = 0; col < columnWidths.length; col++) {
                    for (int row = 0; row < height; row++) {
                        cells.add(new Cell(columnWidths[col], row, col, slotX, totalWidth));
                    }
                    slotX += columnWidths[col];
                }

                eachCellsList = new ArrayList<List<Cell>>();

                boolean success = true;
                for (int i = 0; i < groupSizes.size(); i++) {
                    if (!addCellsForIndex(i)) {
                        success = false;
                        break;
                    }
                }
                this.succeeded = success;
            }

            boolean addCellsForIndex(int groupIdx) {
                int count = groupSizes.get(groupIdx);
                List<Cell> found = findCellsForRoom(count);
                if (found.isEmpty()) return false;

                for (Cell cell : found) {
                    cell.occupied = true;
                }
                eachCellsList.add(found);

                if (!connected(found)) {
                    brokenGroups++;
                }
                return true;
            }

            List<Cell> findCellsForRoom(int count) {
                if (!findEmptyCell()) return Collections.emptyList();

                if (!allowBroken) {
                    int startCol = cells.get(cellIndex).columnIndex;
                    int totalRoom = 0;
                    int cellCount = 0;
                    for (int i = cellIndex; i < cells.size(); i++) {
                        cellCount++;
                        totalRoom += cells.get(i).room;
                        if (totalRoom >= count) {
                            if (cellCount > height || cells.get(i).columnIndex == startCol) {
                                return new ArrayList<Cell>(cells.subList(cellIndex, cellIndex + cellCount));
                            }
                            cellIndex = (startCol + 1) * height;
                            break;
                        }
                    }
                }

                List<Cell> result = new ArrayList<Cell>();
                int remaining = count;
                while (remaining > 0) {
                    if (!findEmptyCell()) return Collections.emptyList();
                    Cell cell = cells.get(cellIndex);
                    result.add(cell);
                    cell.occupied = true;
                    remaining -= cell.room;
                }
                return result;
            }

            boolean findEmptyCell() {
                while (true) {
                    if (cellIndex < cells.size()) {
                        if (!cells.get(cellIndex).occupied) return true;
                        cellIndex++;
                        continue;
                    }
                    if (allowBroken) return false;
                    allowBroken = true;
                    cellIndex = 0;
                }
            }

            List<List<Integer>> apply() {
                List<List<Integer>> result = new ArrayList<List<Integer>>();
                for (int i = 0; i < groupSizes.size(); i++) {
                    List<Cell> groupCells = eachCellsList.get(i);
                    List<Integer> indices = new ArrayList<Integer>();
                    for (Cell cell : groupCells) {
                        indices.addAll(cell.slotIndices());
                    }
                    result.add(indices);
                }
                return result;
            }

            boolean connected(List<Cell> cellList) {
                if (cellList.size() <= 1) return true;

                List<int[]> remaining = new ArrayList<int[]>();
                for (Cell c : cellList) {
                    remaining.add(new int[]{c.rowIndex, c.columnIndex});
                }

                List<int[]> queue = new ArrayList<int[]>();
                queue.add(remaining.remove(0));

                while (!queue.isEmpty()) {
                    int[] current = queue.remove(0);
                    for (int i = remaining.size() - 1; i >= 0; i--) {
                        int[] other = remaining.get(i);
                        int dist = Math.abs(current[0] - other[0]) + Math.abs(current[1] - other[1]);
                        if (dist == 1) {
                            queue.add(remaining.remove(i));
                        }
                    }
                }
                return remaining.isEmpty();
            }
        }
    }
}
