package com.spearmacebackport.enchant;

import com.spearmacebackport.SpearMaceBackportMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SpearMaceBackportMod.MOD_ID)
public class ModEnchants {
    public static final Enchantment DENSITY = new EnchantmentDensity();
    public static final Enchantment BREACH = new EnchantmentBreach();
    public static final Enchantment WIND_BURST = new EnchantmentWindBurst();
    public static final Enchantment LUNGE = new EnchantmentLunge();

    public static void init() {
    }

    @SubscribeEvent
    public static void onRegisterEnchants(RegistryEvent.Register<Enchantment> event) {
        event.getRegistry().registerAll(DENSITY, BREACH, WIND_BURST, LUNGE);
    }

    static EntityEquipmentSlot[] hand() {
        return new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND};
    }
}
