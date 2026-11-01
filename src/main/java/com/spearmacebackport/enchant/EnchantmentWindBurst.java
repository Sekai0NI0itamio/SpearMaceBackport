package com.spearmacebackport.enchant;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.item.ItemMace;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/** Modern Wind Burst: smash launches the attacker upward for chained smashes. Max 3, treasure-only. */
public class EnchantmentWindBurst extends Enchantment {
    public EnchantmentWindBurst() {
        super(Rarity.VERY_RARE, EnumEnchantmentType.WEAPON, ModEnchants.hand());
        setRegistryName(SpearMaceBackportMod.MOD_ID, "wind_burst");
        setName(SpearMaceBackportMod.MOD_ID + ".wind_burst");
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 20 + (level - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 15;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof ItemMace;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof ItemMace;
    }
}
