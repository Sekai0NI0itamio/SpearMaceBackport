package com.spearmacebackport.enchant;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.item.ItemDiamondSpear;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

/** Modern Lunge: boosts spear charge (sprint/velocity) damage. Max 3, table-available on spears. */
public class EnchantmentLunge extends Enchantment {
    public EnchantmentLunge() {
        super(Rarity.UNCOMMON, EnumEnchantmentType.WEAPON, ModEnchants.hand());
        setRegistryName(SpearMaceBackportMod.MOD_ID, "lunge");
        setName(SpearMaceBackportMod.MOD_ID + ".lunge");
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 5 + (level - 1) * 8;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof ItemDiamondSpear;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof ItemDiamondSpear;
    }
}
