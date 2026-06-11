package com.xy.ipn.client;

import com.xy.ipn.config.IPNConfig;
import com.xy.ipn.config.IPNConfigGui;
import com.xy.ipn.client.action.ClientMoveAll;
import com.xy.ipn.client.action.ClientProfiles;
import com.xy.ipn.client.action.ClientSorter;
import com.xy.ipn.client.action.ClientThrowAll;
import com.xy.ipn.proxy.ClientProxy;
import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Client-side keybinding handler and GUI input interceptor.
 *
 * Processes IPN keybindings when a GuiContainer is open,
 * and prevents vanilla from handling those key events.
 */
@SideOnly(Side.CLIENT)
public class ClientEventHandler {

    /**
     * IMPORTANT 1.12.2 quirk: KeyBinding.isPressed() only works while no GuiScreen
     * is open (Minecraft.runTickKeyboard is skipped when a GUI is open), so all
     * IPN GUI hotkeys must be dispatched from KeyboardInputEvent instead.
     */
    @SubscribeEvent
    public void onGuiKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GuiContainer gui = (GuiContainer) event.getGui();
        if (mc.player == null || isTextFieldFocused(gui)) {
            return;
        }

        int eventKey = Keyboard.getEventKey();
        if (eventKey == Keyboard.KEY_NONE) {
            return;
        }

        boolean handled = false;

        if (matchesKey(ClientProxy.KEY_SORT, eventKey)) {
            ClientSorter.sort(gui, getHoveredSlotId(gui), 0);
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_SORT_COLUMNS, eventKey)) {
            ClientSorter.sort(gui, getHoveredSlotId(gui), 1);
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_SORT_ROWS, eventKey)) {
            ClientSorter.sort(gui, getHoveredSlotId(gui), 2);
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_MOVE_ALL, eventKey)) {
            int slotId = getHoveredSlotId(gui);
            ClientMoveAll.moveAll(gui, slotId, determineToContainer(gui, slotId));
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_LOCK_SLOT, eventKey)) {
            Slot slot = getHoveredSlot(gui);
            if (slot != null && (ContainerUtils.isPlayerHotbarSlot(slot) || ContainerUtils.isPlayerMainInvSlot(slot))) {
                int invIndex = ContainerUtils.getPlayerInvIndex(slot);
                LockedSlotHandler.toggleSlot(invIndex);
                String msgKey = LockedSlotHandler.isLocked(invIndex)
                        ? "ipn.msg.slot_locked" : "ipn.msg.slot_unlocked";
                mc.player.sendStatusMessage(
                        new net.minecraft.util.text.TextComponentTranslation(msgKey, invIndex), true);
            }
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_THROW_ALL, eventKey)) {
            ClientThrowAll.throwAll(gui, getHoveredSlotId(gui));
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_CONFIG, eventKey)) {
            mc.displayGuiScreen(new IPNConfigGui(mc.currentScreen));
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_NEXT, eventKey)) {
            ClientProfiles.applyNext();
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_PREV, eventKey)) {
            ClientProfiles.applyPrev();
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_SAVE, eventKey)) {
            ClientProfiles.saveNew();
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_1, eventKey)) {
            ClientProfiles.applyByIndex(0);
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_2, eventKey)) {
            ClientProfiles.applyByIndex(1);
            handled = true;
        } else if (matchesKey(ClientProxy.KEY_PROFILE_3, eventKey)) {
            ClientProfiles.applyByIndex(2);
            handled = true;
        }

        if (handled) {
            event.setCanceled(true);
        }
    }

    private static boolean matchesKey(net.minecraft.client.settings.KeyBinding binding, int eventKey) {
        int code = binding.getKeyCode();
        return code != Keyboard.KEY_NONE && code == eventKey;
    }

    /**
     * Get the slot number of the slot under the mouse, or -1 if none.
     */
    private static int getHoveredSlotId(GuiContainer gui) {
        Slot slot = gui.getSlotUnderMouse();
        return slot != null ? slot.slotNumber : -1;
    }

    /**
     * Get the Slot under the mouse, or null if none.
     */
    private static Slot getHoveredSlot(GuiContainer gui) {
        return gui.getSlotUnderMouse();
    }

    /**
     * Determine whether to send items to the container (true) or to the player (false)
     * based on which side the hovered slot is on.
     *
     * If the slot is in the player inventory, move TO the container.
     * If the slot is in the container, move TO the player inventory.
     * Default: true (to container).
     */
    private static boolean determineToContainer(GuiContainer gui, int slotId) {
        if (slotId < 0) {
            return true; // default: player → container
        }
        Slot slot = gui.inventorySlots.getSlot(slotId);
        if (slot == null) {
            return true;
        }
        // Player slot → move to container; container slot → move to player
        return ContainerUtils.isPlayerSlot(slot);
    }

    private static boolean isTextFieldFocused(GuiContainer gui) {
        if (gui instanceof GuiContainerCreative) {
            try {
                GuiTextField searchField = ObfuscationReflectionHelper.getPrivateValue(
                        GuiContainerCreative.class, (GuiContainerCreative) gui, "field_147062_A");
                return searchField != null && searchField.isFocused();
            } catch (Exception e) {
                return false;
            }
        }
        if (gui instanceof GuiRepair) {
            try {
                GuiTextField nameField = ObfuscationReflectionHelper.getPrivateValue(
                        GuiRepair.class, (GuiRepair) gui, "field_147091_a");
                return nameField != null && nameField.isFocused();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

}
