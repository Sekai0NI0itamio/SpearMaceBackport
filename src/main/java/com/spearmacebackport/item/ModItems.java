package com.spearmacebackport.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.spearmacebackport.SpearMaceBackportMod;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SpearMaceBackportMod.MOD_ID)
public class ModItems {
    public static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    public static final UUID ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public static final List<Item> ALL = new ArrayList<>();

    public static final ItemMace MACE = new ItemMace();
    public static final ItemDiamondSpear DIAMOND_SPEAR = new ItemDiamondSpear();
    public static final Item HEAVY_CORE = single("heavy_core", 64);
    public static final Item BREEZE_ROD = single("breeze_rod", 64);

    private static Item single(String name, int stack) {
        Item item = new Item().setMaxStackSize(stack).setCreativeTab(CreativeTabs.MISC);
        item.setRegistryName(SpearMaceBackportMod.MOD_ID, name);
        item.setTranslationKey(SpearMaceBackportMod.MOD_ID + "." + name);
        ALL.add(item);
        return item;
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        for (Item item : ALL) {
            event.getRegistry().register(item);
        }
    }

    static Multimap<String, AttributeModifier> weaponModifiers(double damage, double speed) {
        Multimap<String, AttributeModifier> map = HashMultimap.create();
        map.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),
                new AttributeModifier(ATTACK_DAMAGE_UUID, "Weapon modifier", damage, 0));
        map.put(SharedMonsterAttributes.ATTACK_SPEED.getName(),
                new AttributeModifier(ATTACK_SPEED_UUID, "Weapon modifier", speed, 0));
        return map;
    }

    static boolean isMainHand(EntityEquipmentSlot slot) {
        return slot == EntityEquipmentSlot.MAINHAND;
    }

    static boolean repairableWith(ItemStack toRepair, ItemStack material, Item repairItem) {
        return material.getItem() == repairItem;
    }
}
