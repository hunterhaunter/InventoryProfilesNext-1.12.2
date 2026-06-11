package com.xy.ipn.client;

import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * CLIENT-SIDE — highlights all slots containing the same item type
 * as the currently hovered slot.
 *
 * Draws a semi-transparent colored overlay on all matching slots.
 * Draws AFTER the locked slot overlay so highlights layer on top.
 *
 * Register on MinecraftForge.EVENT_BUS.
 */
@SideOnly(Side.CLIENT)
public class ItemHighlightHandler {

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!IPNConfig.enableItemHighlight) {
            return;
        }

        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();
        int guiLeft = gui.getGuiLeft();
        int guiTop = gui.getGuiTop();

        Slot hoveredSlot = gui.getSlotUnderMouse();
        if (hoveredSlot == null) {
            return;
        }

        ItemStack hoveredStack = hoveredSlot.getStack();
        if (hoveredStack.isEmpty()) {
            return;
        }

        int highlightColor = IPNConfig.highlightColor;

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            // Skip the hovered slot itself
            if (slot.slotNumber == hoveredSlot.slotNumber) {
                continue;
            }

            ItemStack slotStack = slot.getStack();
            if (slotStack.isEmpty()) {
                continue;
            }

            // Match: same Item and same metadata
            if (hoveredStack.getItem() == slotStack.getItem()
                    && hoveredStack.getMetadata() == slotStack.getMetadata()) {
                int sx = guiLeft + slot.xPos;
                int sy = guiTop + slot.yPos;
                Gui.drawRect(sx, sy, sx + 16, sy + 16, highlightColor);
            }
        }
    }
}
