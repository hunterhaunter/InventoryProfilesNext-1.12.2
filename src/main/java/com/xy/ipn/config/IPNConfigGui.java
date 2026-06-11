package com.xy.ipn.config;

import com.xy.ipn.InventoryProfilesNext;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiConfig;

public class IPNConfigGui extends GuiConfig {

    public IPNConfigGui(GuiScreen parentScreen) {
        super(parentScreen,
                IPNConfig.getConfigElements(),
                InventoryProfilesNext.MODID,
                false,
                false,
                "Inventory Profiles Next Config");
    }
}
