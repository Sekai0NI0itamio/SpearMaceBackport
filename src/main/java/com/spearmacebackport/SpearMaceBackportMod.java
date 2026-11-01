package com.spearmacebackport;

import com.spearmacebackport.enchant.ModEnchants;
import com.spearmacebackport.item.ModItems;
import com.spearmacebackport.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = SpearMaceBackportMod.MOD_ID, name = SpearMaceBackportMod.NAME,
        version = SpearMaceBackportMod.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class SpearMaceBackportMod {
    public static final String MOD_ID = "spearmacebackport";
    public static final String NAME = "SpearMaceBackport";
    public static final String VERSION = "1.1.5";

    @Mod.Instance(MOD_ID)
    public static SpearMaceBackportMod instance;

    @SidedProxy(clientSide = "com.spearmacebackport.proxy.ClientProxy",
            serverSide = "com.spearmacebackport.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModEnchants.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
