package com.spearmacebackport.enchant;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.item.ItemMace;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/** Modern Density: +0.5 smash damage per fallen block per level. Max 5, table-available. */
public class EnchantmentDensity extends Enchantment {
    public EnchantmentDensity() {
        super(Rarity.COMMON, EnumEnchantmentType.WEAPON, ModEnchants.hand());
        setRegistryName(SpearMaceBackportMod.MOD_ID, "density");
        setName(SpearMaceBackportMod.MOD_ID + ".density");
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 1 + (level - 1) * 8;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof ItemMace;
    }
}
