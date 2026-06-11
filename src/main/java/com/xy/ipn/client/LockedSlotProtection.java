package com.xy.ipn.client;

import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * CLIENT-SIDE — prevents item movement out of locked player inventory slots.
 *
 * Intercepts mouse clicks (shift-click) and keyboard drops (Q)
 * when the player hovers over a locked inventory slot (0-35).
 *
 * Register on MinecraftForge.EVENT_BUS.
 */
@SideOnly(Side.CLIENT)
public class LockedSlotProtection {

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();
        Slot slotUnderMouse = gui.getSlotUnderMouse();
        if (slotUnderMouse == null) {
            return;
        }

        // Only protect player inventory storage slots (0-35)
        if (!ContainerUtils.isPlayerSlot(slotUnderMouse)) {
            return;
        }
        int invIndex = ContainerUtils.getPlayerInvIndex(slotUnderMouse);
        if (invIndex < 0 || invIndex >= 36) {
            return;
        }
        int mouseButton = Mouse.getEventButton();
        // No button pressed — not a click event we care about
        if (mouseButton < 0) {
            return;
        }

        // Alt+click toggles the lock (original IPN marking UX), on button press
        // only. Must run BEFORE the isLocked guard or unlocked slots could
        // never be locked by click.
        if (mouseButton == 0 && Mouse.getEventButtonState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))) {
            LockedSlotHandler.toggleSlot(invIndex);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                String msgKey = LockedSlotHandler.isLocked(invIndex)
                        ? "ipn.msg.slot_locked" : "ipn.msg.slot_unlocked";
                mc.player.sendStatusMessage(
                        new net.minecraft.util.text.TextComponentTranslation(msgKey, invIndex), true);
            }
            event.setCanceled(true);
            return;
        }

        if (!LockedSlotHandler.isLocked(invIndex)) {
            return;
        }

        // Prevent shift-click from moving items out of locked slots
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GuiContainer gui = (GuiContainer) event.getGui();

        int eventKey = Keyboard.getEventKey();
        int dropKeyCode = mc.gameSettings.keyBindDrop.getKeyCode();

        // Hotbar number key (1-9) swap: blocked if either the hovered slot
        // or the target hotbar slot is locked
        int hotbarTarget = hotbarKeyIndex(mc, eventKey);

        if (eventKey != dropKeyCode && hotbarTarget < 0) {
            return;
        }

        Slot slotUnderMouse = gui.getSlotUnderMouse();
        if (slotUnderMouse == null) {
            return;
        }

        // Only protect player inventory storage slots (0-35)
        if (!ContainerUtils.isPlayerSlot(slotUnderMouse)) {
            return;
        }
        int invIndex = ContainerUtils.getPlayerInvIndex(slotUnderMouse);
        if (invIndex < 0 || invIndex >= 36) {
            return;
        }

        if (LockedSlotHandler.isLocked(invIndex)
                || (hotbarTarget >= 0 && LockedSlotHandler.isLocked(hotbarTarget))) {
            event.setCanceled(true);
        }
    }

    /**
     * If the key is a vanilla hotbar keybinding, return the hotbar index 0-8,
     * else -1.
     */
    private static int hotbarKeyIndex(Minecraft mc, int eventKey) {
        for (int i = 0; i < 9; i++) {
            if (eventKey == mc.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                return i;
            }
        }
        return -1;
    }
}
