package com.xy.ipn.sort;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Comparator;

/**
 * Comparator for sorting ItemStacks.
 *
 * Sort methods:
 * 0 (ID): compare by Item.getIdFromItem, then metadata, then NBT existence
 * 1 (NAME): compare by getDisplayName (case-insensitive), then by ID
 * 2 (MOD): compare by registry namespace, then registry path, then metadata
 *
 * Empty stacks always sort last.
 */
public class ItemSortComparator implements Comparator<ItemStack> {

    private final int sortMethod;

    public ItemSortComparator(int sortMethod) {
        this.sortMethod = sortMethod;
    }

    @Override
    public int compare(ItemStack a, ItemStack b) {
        // Empty stacks sort to the end
        boolean aEmpty = a.isEmpty();
        boolean bEmpty = b.isEmpty();
        if (aEmpty && bEmpty) {
            return 0;
        }
        if (aEmpty) {
            return 1;
        }
        if (bEmpty) {
            return -1;
        }

        switch (sortMethod) {
            case 0:
                return compareById(a, b);
            case 1:
                return compareByName(a, b);
            case 2:
                return compareByMod(a, b);
            default:
                return compareById(a, b);
        }
    }

    /**
     * Sort by item ID, then metadata, then NBT existence (no NBT first).
     */
    private int compareById(ItemStack a, ItemStack b) {
        // Compare by item ID
        int idA = Item.getIdFromItem(a.getItem());
        int idB = Item.getIdFromItem(b.getItem());
        int cmp = Integer.compare(idA, idB);
        if (cmp != 0) {
            return cmp;
        }

        // Compare by metadata
        cmp = Integer.compare(a.getMetadata(), b.getMetadata());
        if (cmp != 0) {
            return cmp;
        }

        // Compare by NBT existence (no NBT sorts first)
        boolean aHasNbt = a.hasTagCompound();
        boolean bHasNbt = b.hasTagCompound();
        return Boolean.compare(aHasNbt, bHasNbt);
    }

    /**
     * Sort by display name (case-insensitive), then by item ID.
     */
    private int compareByName(ItemStack a, ItemStack b) {
        // Compare by display name
        String nameA = a.getDisplayName();
        String nameB = b.getDisplayName();
        if (nameA != null && nameB != null) {
            int cmp = nameA.compareToIgnoreCase(nameB);
            if (cmp != 0) {
                return cmp;
            }
        } else if (nameA == null && nameB != null) {
            return -1;
        } else if (nameA != null) {
            return 1;
        }

        // Fallback: compare by item ID
        int idA = Item.getIdFromItem(a.getItem());
        int idB = Item.getIdFromItem(b.getItem());
        return Integer.compare(idA, idB);
    }

    /**
     * Sort by mod namespace, then registry path, then metadata.
     */
    private int compareByMod(ItemStack a, ItemStack b) {
        ResourceLocation regA = a.getItem().getRegistryName();
        ResourceLocation regB = b.getItem().getRegistryName();

        // Items without registry names sort last
        if (regA == null && regB == null) {
            return 0;
        }
        if (regA == null) {
            return 1;
        }
        if (regB == null) {
            return -1;
        }

        // Compare by namespace (mod ID)
        int cmp = regA.getNamespace().compareTo(regB.getNamespace());
        if (cmp != 0) {
            return cmp;
        }

        // Compare by path (item name)
        cmp = regA.getPath().compareTo(regB.getPath());
        if (cmp != 0) {
            return cmp;
        }

        // Compare by metadata
        return Integer.compare(a.getMetadata(), b.getMetadata());
    }
}
