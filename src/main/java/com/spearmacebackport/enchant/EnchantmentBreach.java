package com.spearmacebackport.enchant;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.item.ItemMace;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/** Modern Breach: -15% armor effectiveness per level. Max 4, table-available. */
public class EnchantmentBreach extends Enchantment {
    public EnchantmentBreach() {
        super(Rarity.RARE, EnumEnchantmentType.WEAPON, ModEnchants.hand());
        setRegistryName(SpearMaceBackportMod.MOD_ID, "breach");
        setName(SpearMaceBackportMod.MOD_ID + ".breach");
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
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
