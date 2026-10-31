package com.spearmacebackport.item;

import com.google.common.collect.Multimap;
import com.spearmacebackport.SpearMaceBackportMod;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Modern Mace backport: base 6 damage (iron-sword tier) with a slow
 * ~0.6 attack speed (~1.6s recovery, matching the modern ~2s feel).
 * Smash scaling, Density/Breach/Wind Burst and fall negation live in
 * CombatHandler; the 1s easy-swap dual-hit lives there too.
 */
public class ItemMace extends Item {
    public static final double BASE_DAMAGE = 6.0D;
    public static final double ATTACK_SPEED_MOD = -3.4D;

    public ItemMace() {
        setRegistryName(SpearMaceBackportMod.MOD_ID, "mace");
        setTranslationKey(SpearMaceBackportMod.MOD_ID + ".mace");
        setMaxStackSize(1);
        setMaxDamage(500);
        setCreativeTab(CreativeTabs.COMBAT);
        ModItems.ALL.add(this);
    }

    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        Multimap<String, AttributeModifier> map = super.getItemAttributeModifiers(slot);
        if (ModItems.isMainHand(slot)) {
            // Modifier stacks on the player's base 1.0 attack damage: 5.0 + 1.0 = 6.0.
            map = ModItems.weaponModifiers(BASE_DAMAGE - 1.0D, ATTACK_SPEED_MOD);
        }
        return map;
    }

    @Override
    public boolean hitEntity(ItemStack stack, net.minecraft.entity.EntityLivingBase target,
                            net.minecraft.entity.EntityLivingBase attacker) {
        stack.damageItem(1, attacker);
        return true;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        // Modern maces repair with breeze rods (one rod = 25%); the backport
        // rod is the 1.12-obtainable stand-in.
        return ModItems.repairableWith(toRepair, repair, ModItems.BREEZE_ROD);
    }
}
