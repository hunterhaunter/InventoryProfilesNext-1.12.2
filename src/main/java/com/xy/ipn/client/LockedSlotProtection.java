package com.xy.ipn.client;

import com.xy.ipn.client.action.ClientQuickMoveRouter;
import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
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

        // ---- Locked slots must not RECEIVE items (original IPN behavior) ----
        // Take over shift-clicks whose destination is the player inventory and
        // route them around empty locked slots. Vanilla quick-move picks its
        // own destinations and would fill them.
        boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shiftHeld && Mouse.getEventButton() == 0 && Mouse.getEventButtonState()
                && !(gui instanceof GuiContainerCreative)
                && !(slotUnderMouse instanceof SlotCrafting)) {
            if (!ContainerUtils.isPlayerSlot(slotUnderMouse)) {
                // container slot → player inventory
                if (ClientQuickMoveRouter.quickMoveToPlayer(
                        gui.inventorySlots, slotUnderMouse.slotNumber, false)) {
                    event.setCanceled(true);
                    return;
                }
            } else if (gui instanceof GuiInventory
                    && !isEquipmentRouted(slotUnderMouse.getStack())) {
                // main ↔ hotbar move inside the player GUI (skip items vanilla
                // routes to armor/offhand so shift-click-to-equip keeps working)
                int srcIdx = ContainerUtils.getPlayerInvIndex(slotUnderMouse);
                if (srcIdx >= 0 && srcIdx < 36 && !LockedSlotHandler.isLocked(srcIdx)
                        && ClientQuickMoveRouter.quickMoveToPlayer(
                                gui.inventorySlots, slotUnderMouse.slotNumber, true)) {
                    event.setCanceled(true);
                    return;
                }
            }
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
     * True when vanilla would route this stack to an armor/offhand slot on
     * shift-click in the player GUI (shift-click-to-equip). Those moves are
     * left to vanilla — armor and offhand slots cannot be locked anyway.
     */
    private static boolean isEquipmentRouted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(stack);
        return slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR
                || slot == EntityEquipmentSlot.OFFHAND;
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
