package com.xy.ipn.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProfileClientState {
    public static String currentProfileName = "";   // "" = none
    public static int profileCount = 0;
}
