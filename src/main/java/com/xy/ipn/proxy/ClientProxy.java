package com.xy.ipn.proxy;

import com.xy.ipn.client.ButtonHandler;
import com.xy.ipn.client.ClientEventHandler;
import com.xy.ipn.client.ItemHighlightHandler;
import com.xy.ipn.client.LockedSlotHandler;
import com.xy.ipn.client.LockedSlotProtection;
import com.xy.ipn.client.ScrollHandler;
import com.xy.ipn.client.action.ClientAutoRefill;
import com.xy.ipn.client.action.ClientContinuousCrafting;
import com.xy.ipn.client.action.ClientLockedSlotPickupGuard;
import com.xy.ipn.client.action.ClientProfiles;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    public static final KeyBinding KEY_SORT = new KeyBinding(
            "key.ipn.sort", KeyConflictContext.GUI, Keyboard.KEY_R, "key.categories.ipn");
    public static final KeyBinding KEY_SORT_COLUMNS = new KeyBinding(
            "key.ipn.sort_columns", KeyConflictContext.GUI, Keyboard.KEY_C, "key.categories.ipn");
    public static final KeyBinding KEY_SORT_ROWS = new KeyBinding(
            "key.ipn.sort_rows", KeyConflictContext.GUI, Keyboard.KEY_V, "key.categories.ipn");
    public static final KeyBinding KEY_MOVE_ALL = new KeyBinding(
            "key.ipn.move_all", KeyConflictContext.GUI, Keyboard.KEY_G, "key.categories.ipn");
    public static final KeyBinding KEY_CONFIG = new KeyBinding(
            "key.ipn.config", KeyConflictContext.GUI, Keyboard.KEY_O, "key.categories.ipn");
    // K, not L — L is the vanilla advancements key
    public static final KeyBinding KEY_LOCK_SLOT = new KeyBinding(
            "key.ipn.lock_slot", KeyConflictContext.GUI, Keyboard.KEY_K, "key.categories.ipn");
    public static final KeyBinding KEY_THROW_ALL = new KeyBinding(
            "key.ipn.throw_all", KeyConflictContext.GUI, Keyboard.KEY_T, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_NEXT = new KeyBinding(
            "key.ipn.profile_next", KeyConflictContext.GUI, Keyboard.KEY_PERIOD, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_PREV = new KeyBinding(
            "key.ipn.profile_prev", KeyConflictContext.GUI, Keyboard.KEY_COMMA, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_SAVE = new KeyBinding(
            "key.ipn.profile_save", KeyConflictContext.GUI, Keyboard.KEY_SEMICOLON, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_1 = new KeyBinding(
            "key.ipn.profile_1", KeyConflictContext.GUI, Keyboard.KEY_NONE, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_2 = new KeyBinding(
            "key.ipn.profile_2", KeyConflictContext.GUI, Keyboard.KEY_NONE, "key.categories.ipn");
    public static final KeyBinding KEY_PROFILE_3 = new KeyBinding(
            "key.ipn.profile_3", KeyConflictContext.GUI, Keyboard.KEY_NONE, "key.categories.ipn");

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientRegistry.registerKeyBinding(KEY_SORT);
        ClientRegistry.registerKeyBinding(KEY_SORT_COLUMNS);
        ClientRegistry.registerKeyBinding(KEY_SORT_ROWS);
        ClientRegistry.registerKeyBinding(KEY_MOVE_ALL);
        ClientRegistry.registerKeyBinding(KEY_CONFIG);
        ClientRegistry.registerKeyBinding(KEY_LOCK_SLOT);
        ClientRegistry.registerKeyBinding(KEY_THROW_ALL);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_NEXT);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_PREV);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_SAVE);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_1);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_2);
        ClientRegistry.registerKeyBinding(KEY_PROFILE_3);

        LockedSlotHandler.loadFrom(event.getModConfigurationDirectory());
        ClientProfiles.refreshState();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        MinecraftForge.EVENT_BUS.register(new ButtonHandler());
        MinecraftForge.EVENT_BUS.register(new LockedSlotHandler());
        MinecraftForge.EVENT_BUS.register(new LockedSlotProtection());
        MinecraftForge.EVENT_BUS.register(new ItemHighlightHandler());
        MinecraftForge.EVENT_BUS.register(new ScrollHandler());
        MinecraftForge.EVENT_BUS.register(new ClientContinuousCrafting());
        MinecraftForge.EVENT_BUS.register(new ClientAutoRefill());
        MinecraftForge.EVENT_BUS.register(new ClientLockedSlotPickupGuard());
    }
}
