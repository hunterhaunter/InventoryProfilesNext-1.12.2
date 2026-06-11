package com.xy.ipn.client;

import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages locked player inventory slots (indices 0-35) and renders
 * semi-transparent red overlays on locked slots in GUIs.
 *
 * Locked slots are saved to/loaded from a plain text file in the config
 * directory. Pure client-side: all enforcement happens in the client click
 * executors, so no server sync is needed.
 */
@SideOnly(Side.CLIENT)
public class LockedSlotHandler {

    private static final String LOCK_FILE_NAME = "inventoryprofilesnext_locked_slots.txt";
    private static final Set<Integer> lockedSlots = new HashSet<Integer>();

    /**
     * Toggle the locked state of a player inventory slot index (0-35).
     */
    public static void toggleSlot(int slotIndex) {
        if (lockedSlots.contains(Integer.valueOf(slotIndex))) {
            lockedSlots.remove(Integer.valueOf(slotIndex));
        } else {
            lockedSlots.add(Integer.valueOf(slotIndex));
        }
        saveTo(Loader.instance().getConfigDir());
    }

    /**
     * Check if a player inventory slot index is locked.
     */
    public static boolean isLocked(int slotIndex) {
        return lockedSlots.contains(Integer.valueOf(slotIndex));
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }
        if (!IPNConfig.enableLockedSlots) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();
        int guiLeft = gui.getGuiLeft();
        int guiTop = gui.getGuiTop();

        for (Integer lockedIdxObj : lockedSlots) {
            int lockedIdx = lockedIdxObj.intValue();
            // Find the Slot in the container matching this player inventory index
            Slot slot = findPlayerSlot(gui, lockedIdx);
            if (slot != null) {
                int sx = guiLeft + slot.xPos;
                int sy = guiTop + slot.yPos;
                // Semi-transparent red overlay: 0x80 = 128 alpha, FF = red, 0000 = green/blue
                net.minecraft.client.gui.GuiScreen.drawRect(sx, sy, sx + 16, sy + 16, 0x80FF0000);
            }
        }
    }

    /**
     * Find the Slot in the GuiContainer that corresponds to the given
     * player inventory index (0-35 in InventoryPlayer).
     */
    private static Slot findPlayerSlot(GuiContainer gui, int playerInvIndex) {
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer) {
                int slotIndex = slot.getSlotIndex();
                if (slotIndex == playerInvIndex && slotIndex >= 0 && slotIndex < 36) {
                    return slot;
                }
            }
        }
        return null;
    }

    /**
     * Save locked slots to a plain text file. One slot index per line.
     */
    public static void saveTo(File configDir) {
        File lockFile = new File(configDir, LOCK_FILE_NAME);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(lockFile));
            for (Integer lockedIdx : lockedSlots) {
                writer.write(lockedIdx.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            // Silently fail — locked slots are non-critical
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Load locked slots from a plain text file. One slot index per line.
     */
    public static void loadFrom(File configDir) {
        lockedSlots.clear();
        File lockFile = new File(configDir, LOCK_FILE_NAME);
        if (!lockFile.exists()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(lockFile));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    int idx = Integer.parseInt(line);
                    if (idx >= 0 && idx < 36) {
                        lockedSlots.add(Integer.valueOf(idx));
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed lines
                }
            }
        } catch (IOException e) {
            // Silently fail — locked slots are non-critical
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
