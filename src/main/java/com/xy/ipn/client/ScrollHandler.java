package com.xy.ipn.client;

import com.xy.ipn.client.action.ClientScrollTransfer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * CLIENT-SIDE — scroll wheel transfers items between container sections.
 *
 * Scroll up = move to container, scroll down = move to player inventory.
 * Hold Shift to move the full stack instead of one item.
 *
 * Register on MinecraftForge.EVENT_BUS.
 */
@SideOnly(Side.CLIENT)
public class ScrollHandler {

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }

        // Creative inventory uses the wheel to scroll its item list — don't hijack it
        if (event.getGui() instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) {
            return;
        }

        // Original IPN: holding Ctrl temporarily disables scroll transfer
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            return;
        }

        Slot slot = gui.getSlotUnderMouse();
        if (slot == null || !slot.getHasStack()) {
            return;
        }

        // scroll > 0 = up = to container, scroll < 0 = down = to player
        boolean toContainer = scroll > 0;
        boolean fullStack = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        ClientScrollTransfer.transfer(gui, slot.slotNumber, toContainer, fullStack);

        event.setCanceled(true);
    }
}
