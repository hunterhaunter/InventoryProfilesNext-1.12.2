package com.xy.ipn.config;

import com.xy.ipn.InventoryProfilesNext;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IPNConfig {

    private static Configuration config;

    // Sorting
    public static boolean enableSorting = true;
    public static boolean showSortButtons = true;
    public static String defaultSortMethod = "id";

    // Auto-refill
    public static boolean enableAutoRefill = true;
    public static int autoRefillDamageThreshold = 5;

    // Locked slots
    public static boolean enableLockedSlots = true;

    // GUI
    public static int buttonSize = 10;
    public static boolean showMoveAllButtons = true;
    public static boolean playSortSound = true;

    // Item highlight
    public static boolean enableItemHighlight = true;
    public static int highlightColor = 0x80FFFF00;

    // Continuous crafting
    public static boolean enableContinuousCrafting = true;

    // Restock hotbar
    public static boolean restockHotbar = true;

    // Profiles
    public static boolean enableProfiles = true;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        config.load();

        String sortingCategory = "sorting";
        config.addCustomCategoryComment(sortingCategory, "Inventory sorting settings");
        enableSorting = config.getBoolean("enableSorting", sortingCategory, true,
                "Enable inventory sorting");
        showSortButtons = config.getBoolean("showSortButtons", sortingCategory, true,
                "Show sort buttons in container GUIs");
        defaultSortMethod = config.getString("defaultSortMethod", sortingCategory, "id",
                "Default sort method: 'id', 'name', or 'mod'",
                new String[]{"id", "name", "mod"});

        String refillCategory = "autorefill";
        config.addCustomCategoryComment(refillCategory, "Auto-refill settings");
        enableAutoRefill = config.getBoolean("enableAutoRefill", refillCategory, true,
                "Enable auto-refill of item stacks");
        autoRefillDamageThreshold = config.getInt("autoRefillDamageThreshold", refillCategory, 5, 0, 100,
                "Durability threshold for auto-refill (break tool when below this)");

        String lockedCategory = "lockedslots";
        config.addCustomCategoryComment(lockedCategory, "Locked slots settings");
        enableLockedSlots = config.getBoolean("enableLockedSlots", lockedCategory, true,
                "Enable slot locking");

        String guiCategory = "gui";
        config.addCustomCategoryComment(guiCategory, "GUI settings");
        buttonSize = config.getInt("buttonSize", guiCategory, 10, 5, 20,
                "Size of sort buttons in pixels");
        showMoveAllButtons = config.getBoolean("showMoveAllButtons", guiCategory, true,
                "Show move-all buttons in container GUIs");
        playSortSound = config.getBoolean("playSortSound", guiCategory, true,
                "Play a sound when sorting");
        enableItemHighlight = config.getBoolean("enableItemHighlight", guiCategory, true,
                "Highlight all matching items when hovering over one");
        // ARGB colors with alpha >= 0x80 are negative ints — a 0..0xFFFFFFFF
        // bound range breaks Forge's min/max clamping (0xFFFFFFFF == -1)
        highlightColor = config.getInt("highlightColor", guiCategory, 0x80FFFF00,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                "Highlight overlay color in ARGB format (hex)");

        String craftingCategory = "crafting";
        config.addCustomCategoryComment(craftingCategory, "Continuous crafting settings");
        enableContinuousCrafting = config.getBoolean("enableContinuousCrafting", craftingCategory, true,
                "Auto-refill crafting grid ingredients from player inventory");

        restockHotbar = config.getBoolean("restockHotbar", sortingCategory, true,
                "Restock partial hotbar stacks from main inventory after sorting");

        String profilesCategory = "profiles";
        config.addCustomCategoryComment(profilesCategory, "Inventory profile settings");
        enableProfiles = config.getBoolean("enableProfiles", profilesCategory, true,
                "Enable inventory profiles (save/load gear sets)");

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void save() {
        if (config != null) {
            config.save();
        }
    }

    /**
     * Persist the continuous-crafting toggle (flipped by the GUI checkbox)
     * to the config file so it survives restarts.
     */
    public static void setContinuousCrafting(boolean enabled) {
        enableContinuousCrafting = enabled;
        if (config != null) {
            config.get("crafting", "enableContinuousCrafting", true).set(enabled);
            config.save();
        }
    }

    public static Configuration getConfig() {
        return config;
    }

    public static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();
        if (config != null) {
            elements.add(new ConfigElement(config.getCategory("sorting")));
            elements.add(new ConfigElement(config.getCategory("autorefill")));
            elements.add(new ConfigElement(config.getCategory("lockedslots")));
            elements.add(new ConfigElement(config.getCategory("gui")));
            elements.add(new ConfigElement(config.getCategory("crafting")));
            elements.add(new ConfigElement(config.getCategory("profiles")));
        }
        return elements;
    }

    @Mod.EventBusSubscriber(modid = InventoryProfilesNext.MODID)
    public static class ConfigEventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(InventoryProfilesNext.MODID)) {
                load();
                save();
            }
        }
    }
}
