package com.xy.ipn;

import com.xy.ipn.config.IPNConfig;
import com.xy.ipn.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

/**
 * Pure client-side mod (original IPN architecture): every inventory operation
 * is executed via vanilla window-click packets, so it works on vanilla and
 * modded servers alike. clientSideOnly keeps it from loading on dedicated servers.
 */
@Mod(modid = InventoryProfilesNext.MODID, name = InventoryProfilesNext.NAME,
        version = InventoryProfilesNext.VERSION, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "com.xy.ipn.config.IPNGuiFactory")
public class InventoryProfilesNext {

    public static final String MODID = "inventoryprofilesnext";
    public static final String NAME = "Inventory Profiles Next";
    public static final String VERSION = "1.0.1";

    public static Logger LOGGER;

    @Instance(MODID)
    public static InventoryProfilesNext INSTANCE;

    @SidedProxy(clientSide = "com.xy.ipn.proxy.ClientProxy", serverSide = "com.xy.ipn.proxy.CommonProxy")
    public static CommonProxy PROXY;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        IPNConfig.init(event.getSuggestedConfigurationFile());
        PROXY.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        PROXY.init(event);
    }
}
