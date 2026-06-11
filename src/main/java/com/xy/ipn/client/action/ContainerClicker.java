package com.xy.ipn.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Thin static wrappers around
 * {@code mc.playerController.windowClick(windowId, slotId, button, type, player)}.
 *
 * Every inventory operation in IPN flows through these methods.  They work on
 * vanilla servers because they send ordinary window-click packets.
 */
@SideOnly(Side.CLIENT)
public final class ContainerClicker {

    private ContainerClicker() {
    }

    // ---- helpers -----------------------------------------------------------

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static int winId() {
        return mc().player.openContainer.windowId;
    }

    // ---- public API --------------------------------------------------------

    /** Left-click (PICKUP, button 0) — pick up or place a full stack. */
    public static void leftClick(int slotId) {
        mc().playerController.windowClick(winId(), slotId, 0, ClickType.PICKUP, mc().player);
    }

    /** Right-click (PICKUP, button 1) — pick up half / place one. */
    public static void rightClick(int slotId) {
        mc().playerController.windowClick(winId(), slotId, 1, ClickType.PICKUP, mc().player);
    }

    /** Shift-click (QUICK_MOVE, button 0) — vanilla transfer to other section. */
    public static void shiftClick(int slotId) {
        mc().playerController.windowClick(winId(), slotId, 0, ClickType.QUICK_MOVE, mc().player);
    }

    /**
     * Swap with a hotbar slot (SWAP, button = hotbar index 0-8).
     * No cursor involvement — instant single-packet swap.
     */
    public static void swap(int slotId, int hotbarIdx) {
        mc().playerController.windowClick(winId(), slotId, hotbarIdx, ClickType.SWAP, mc().player);
    }

    /** Throw one item from the slot (THROW, button 0). */
    public static void throwOne(int slotId) {
        mc().playerController.windowClick(winId(), slotId, 0, ClickType.THROW, mc().player);
    }

    /** Throw the whole stack from the slot (THROW, button 1). */
    public static void throwStack(int slotId) {
        mc().playerController.windowClick(winId(), slotId, 1, ClickType.THROW, mc().player);
    }

    /**
     * Evenly distribute the cursor stack across {@code slotIds}.
     *
     * Sequence: QUICK_CRAFT button 0 with slotId -999 (start),
     * QUICK_CRAFT button 1 for each slot (add to drag set),
     * QUICK_CRAFT button 2 with slotId -999 (end / distribute).
     */
    public static void dragSplitEven(List<Integer> slotIds) {
        int win = winId();
        mc().playerController.windowClick(win, -999, 0, ClickType.QUICK_CRAFT, mc().player);
        for (int id : slotIds) {
            mc().playerController.windowClick(win, id, 1, ClickType.QUICK_CRAFT, mc().player);
        }
        mc().playerController.windowClick(win, -999, 2, ClickType.QUICK_CRAFT, mc().player);
    }
}
