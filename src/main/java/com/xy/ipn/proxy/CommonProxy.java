package com.xy.ipn.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Empty base proxy. The mod is clientSideOnly — all behavior lives in
 * ClientProxy; nothing registers on a dedicated server.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }
}
