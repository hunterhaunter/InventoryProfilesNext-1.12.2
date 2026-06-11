package com.xy.ipn.client.action;

import com.xy.ipn.InventoryProfilesNext;
import com.xy.ipn.client.ProfileClientState;
import com.xy.ipn.proxy.ClientProxy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure client-side inventory profile management.
 *
 * Port of ProfileHandler using ContainerClicker for swaps, so it works on
 * vanilla servers. Profiles are saved as JSON files in
 * config/inventoryprofilesnext/profiles/.
 */
@SideOnly(Side.CLIENT)
public class ClientProfiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SLOTS_TYPE = new TypeToken<Map<Integer, String>>() {}.getType();

    /** Client-side current profile name, used for cycling. */
    private static String currentProfileName = "";

    /**
     * Initialize ProfileClientState from disk so the profile bar shows the
     * right count before any profile operation. Called from ClientProxy.preInit.
     */
    public static void refreshState() {
        ProfileClientState.profileCount = getProfileNames().size();
    }

    // ---- File I/O ----

    public static File getProfilesDir() {
        File dir = new File("config", InventoryProfilesNext.MODID + "/profiles");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<String> getProfileNames() {
        List<String> names = new ArrayList<String>();
        File dir = getProfilesDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".json")) {
                    names.add(name.substring(0, name.length() - 5));
                }
            }
        }
        return names;
    }

    public static Profile loadProfile(String name) {
        File file = new File(getProfilesDir(), name + ".json");
        if (!file.exists()) {
            return null;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            return GSON.fromJson(reader, Profile.class);
        } catch (IOException e) {
            InventoryProfilesNext.LOGGER.error("Failed to load profile: " + name, e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ---- Save ----

    /**
     * Save the player's current equipment loadout as a new profile with the
     * first unused name in the sequence profile1, profile2, ...
     */
    public static void saveNew() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Find first unused profileN name
        int n = 1;
        String name;
        while (true) {
            name = "profile" + n;
            File file = new File(getProfilesDir(), name + ".json");
            if (!file.exists()) break;
            n++;
        }

        if (!writeProfile(buildCurrentLoadout(name))) return;

        currentProfileName = name;
        ProfileClientState.currentProfileName = name;
        ProfileClientState.profileCount = getProfileNames().size();

        mc.player.sendMessage(new TextComponentTranslation("ipn.msg.profile_saved", name));
    }

    /**
     * Overwrite the active profile with the player's current loadout.
     */
    public static void overwriteCurrent() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        String name = currentProfileName;
        if (name == null || name.isEmpty()) {
            mc.player.sendMessage(new TextComponentTranslation("ipn.msg.no_active_profile"));
            return;
        }

        if (!writeProfile(buildCurrentLoadout(name))) return;

        ProfileClientState.currentProfileName = name;
        ProfileClientState.profileCount = getProfileNames().size();

        mc.player.sendMessage(new TextComponentTranslation("ipn.msg.profile_overwritten", name));
    }

    /**
     * Delete the active profile's JSON file and clear the active selection.
     */
    public static void deleteCurrent() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        String name = currentProfileName;
        if (name == null || name.isEmpty()) {
            mc.player.sendMessage(new TextComponentTranslation("ipn.msg.no_active_profile"));
            return;
        }

        File file = new File(getProfilesDir(), name + ".json");
        if (file.exists() && !file.delete()) {
            InventoryProfilesNext.LOGGER.error("Failed to delete profile file: {}", file);
            return;
        }

        currentProfileName = "";
        ProfileClientState.currentProfileName = "";
        ProfileClientState.profileCount = getProfileNames().size();

        mc.player.sendMessage(new TextComponentTranslation("ipn.msg.profile_deleted", name));
    }

    /**
     * Snapshot the player's current hotbar/armor/offhand loadout as a Profile.
     */
    private static Profile buildCurrentLoadout(String name) {
        Minecraft mc = Minecraft.getMinecraft();

        Profile profile = new Profile();
        profile.name = name;
        profile.slots = new HashMap<Integer, String>();

        // Hotbar: InventoryPlayer indices 0-8
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.mainInventory.get(i);
            if (!stack.isEmpty()) {
                profile.slots.put(i, itemToKey(stack));
            }
        }

        // Armor: InventoryPlayer indices 36-39
        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player.inventory.armorInventory.get(i);
            if (!stack.isEmpty()) {
                profile.slots.put(36 + i, itemToKey(stack));
            }
        }

        // Offhand: InventoryPlayer index 40
        ItemStack offhand = mc.player.inventory.offHandInventory.get(0);
        if (!offhand.isEmpty()) {
            profile.slots.put(40, itemToKey(offhand));
        }

        return profile;
    }

    /** Write a profile to its JSON file. Returns false on I/O failure. */
    private static boolean writeProfile(Profile profile) {
        File file = new File(getProfilesDir(), profile.name + ".json");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            GSON.toJson(profile, writer);
            return true;
        } catch (IOException e) {
            InventoryProfilesNext.LOGGER.error("Failed to save profile: " + profile.name, e);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ---- Apply ----

    /**
     * Apply a named profile: rearrange inventory so that items matching the
     * profile end up in the correct slots. Uses ContainerClicker for swaps.
     */
    public static void apply(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Only operate when the player container is open (including in-game)
        if (mc.player.openContainer != mc.player.inventoryContainer) {
            return;
        }

        Profile profile = loadProfile(name);
        if (profile == null || profile.slots == null) return;

        Container container = mc.player.openContainer;

        for (Map.Entry<Integer, String> entry : profile.slots.entrySet()) {
            int targetInvIdx = entry.getKey();
            String requiredKey = entry.getValue();

            int targetSlotId = invIdxToSlotId(targetInvIdx);
            if (targetSlotId < 0) continue;

            // Check if target already has the right item
            ItemStack targetStack = getStackInSlot(container, targetSlotId);
            if (!targetStack.isEmpty() && itemToKey(targetStack).equals(requiredKey)) {
                continue;
            }

            // Find source slot with matching item
            int sourceSlotId = findMatchingSlot(container, requiredKey, targetSlotId);
            if (sourceSlotId < 0) continue;

            // Realize the swap via clicks
            if (targetInvIdx >= 0 && targetInvIdx <= 8) {
                // Hotbar target: swap clicks
                ContainerClicker.swap(sourceSlotId, targetInvIdx);
            } else {
                // Armor or offhand: pick-up, place, return-displaced
                ContainerClicker.leftClick(sourceSlotId);
                ContainerClicker.leftClick(targetSlotId);

                // If cursor has items (displaced or rejected), put them back
                if (!mc.player.inventory.getItemStack().isEmpty()) {
                    ContainerClicker.leftClick(sourceSlotId);

                    // If cursor still non-empty (source was occupied), find
                    // first empty main inventory slot
                    if (!mc.player.inventory.getItemStack().isEmpty()) {
                        int emptySlot = findEmptyMainSlot(container);
                        if (emptySlot >= 0) {
                            ContainerClicker.leftClick(emptySlot);
                        }
                    }
                }
            }
        }

        currentProfileName = name;
        ProfileClientState.currentProfileName = name;
        ProfileClientState.profileCount = getProfileNames().size();

        mc.player.sendMessage(new TextComponentTranslation("ipn.msg.profile_applied", name));
    }

    // ---- Cycle ----

    public static void applyNext() {
        Minecraft mc = Minecraft.getMinecraft();
        List<String> names = getProfileNames();
        if (names.isEmpty()) {
            if (mc.player != null) {
                String keyName = ClientProxy.KEY_PROFILE_SAVE.getDisplayName();
                mc.player.sendMessage(new TextComponentTranslation("ipn.msg.no_profiles", keyName));
            }
            return;
        }
        java.util.Collections.sort(names);

        String current = currentProfileName;
        int idx = names.indexOf(current); // -1 if absent
        int nextIdx = (idx + 1) % names.size();
        apply(names.get(nextIdx));
    }

    public static void applyPrev() {
        Minecraft mc = Minecraft.getMinecraft();
        List<String> names = getProfileNames();
        if (names.isEmpty()) {
            if (mc.player != null) {
                String keyName = ClientProxy.KEY_PROFILE_SAVE.getDisplayName();
                mc.player.sendMessage(new TextComponentTranslation("ipn.msg.no_profiles", keyName));
            }
            return;
        }
        java.util.Collections.sort(names);

        String current = currentProfileName;
        int idx = names.indexOf(current); // -1 if absent
        // First press with no current profile: -1 would land on size()-2;
        // treat "no current" as wrapping to the last profile.
        int prevIdx = idx < 0 ? names.size() - 1 : (idx - 1 + names.size()) % names.size();
        apply(names.get(prevIdx));
    }

    public static void applyByIndex(int index) {
        Minecraft mc = Minecraft.getMinecraft();
        List<String> names = getProfileNames();
        java.util.Collections.sort(names);

        if (index >= 0 && index < names.size()) {
            apply(names.get(index));
        } else if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("ipn.msg.profile_missing", index + 1));
        }
    }

    // ---- Slot-id mapping (ContainerPlayer, windowId 0) ----

    /**
     * Convert InventoryPlayer index to Container slotId.
     * From CONTEXT-CLICKSIM: hotbar i→36+i, main 9-35→9-35,
     * armor boots(36)=8, legs(37)=7, chest(38)=6, helm(39)=5, offhand 40→45.
     */
    static int invIdxToSlotId(int invIdx) {
        if (invIdx >= 0 && invIdx <= 8) return 36 + invIdx;
        if (invIdx >= 9 && invIdx <= 35) return invIdx;
        if (invIdx >= 36 && invIdx <= 39) return 44 - invIdx; // 36→8, 37→7, 38→6, 39→5
        if (invIdx == 40) return 45;
        return -1;
    }

    /**
     * Convert Container slotId to InventoryPlayer index.
     */
    static int slotIdToInvIdx(int slotId) {
        if (slotId >= 5 && slotId <= 8) return 44 - slotId; // armor
        if (slotId >= 9 && slotId <= 35) return slotId;     // main
        if (slotId >= 36 && slotId <= 44) return slotId - 36; // hotbar
        if (slotId == 45) return 40;                          // offhand
        return -1;
    }

    // ---- Internal helpers ----

    static String itemToKey(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null || item.getRegistryName() == null) {
            return "minecraft:air:0";
        }
        return item.getRegistryName().toString() + ":" + stack.getMetadata();
    }

    private static ItemStack getStackInSlot(Container container, int slotId) {
        if (slotId < 0 || slotId >= container.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }
        return container.inventorySlots.get(slotId).getStack();
    }

    /**
     * Search the player container for a slot whose stack matches key.
     * Skips excludeSlotId. Searches ALL player slots including armor/offhand.
     */
    private static int findMatchingSlot(Container container, String key, int excludeSlotId) {
        for (Slot slot : container.inventorySlots) {
            if (slot.slotNumber == excludeSlotId) continue;
            if (!(slot.inventory instanceof InventoryPlayer)) continue;
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && itemToKey(stack).equals(key)) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /**
     * Find the first empty main inventory slot (slotIndex 9-35) in the
     * player container. Returns slotId or -1.
     */
    private static int findEmptyMainSlot(Container container) {
        for (Slot slot : container.inventorySlots) {
            if (!(slot.inventory instanceof InventoryPlayer)) continue;
            int invIdx = slot.getSlotIndex();
            if (invIdx >= 9 && invIdx <= 35 && !slot.getHasStack()) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    // ---- Profile data class ----

    public static class Profile {
        public String name;
        public Map<Integer, String> slots; // InventoryPlayer index → "registryName:metadata"
    }
}
