package com.spearmacebackport.item;

import com.google.common.collect.Multimap;
import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.combat.CombatHandler;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * Diamond Spear backport (diamond tier only, per request).
 * Modern diamond spear: jab 4 damage, 0.95 attack speed, 1561 durability,
 * 2-4.5 block reach with a sub-2-block dead zone, plus a sprint/velocity
 * charge attack. Reach/charge/dead-zone handled in CombatHandler because
 * 1.12.2 has no attack_range attribute.
 */
public class ItemDiamondSpear extends Item {
    public static final double BASE_DAMAGE = 4.0D;
    public static final double ATTACK_SPEED_MOD = -3.05D;
    public static final double MIN_REACH = 2.0D;
    public static final double MAX_REACH = 4.5D;

    public ItemDiamondSpear() {
        setRegistryName(SpearMaceBackportMod.MOD_ID, "diamond_spear");
        setTranslationKey(SpearMaceBackportMod.MOD_ID + ".diamond_spear");
        setMaxStackSize(1);
        setMaxDamage(1561);
        setCreativeTab(CreativeTabs.COMBAT);
        ModItems.ALL.add(this);
    }

    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        Multimap<String, AttributeModifier> map = super.getItemAttributeModifiers(slot);
        if (ModItems.isMainHand(slot)) {
            // Modifier stacks on the player's base 1.0 attack damage: 3.0 + 1.0 = 4.0.
            map = ModItems.weaponModifiers(BASE_DAMAGE - 1.0D, ATTACK_SPEED_MOD);
        }
        return map;
    }

    @Override
    public int getItemEnchantability() {
        return 10;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            player.swingArm(hand);
        } else {
            CombatHandler.performSpearJab(player, stack, hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean hitEntity(ItemStack stack, net.minecraft.entity.EntityLivingBase target,
                            net.minecraft.entity.EntityLivingBase attacker) {
        stack.damageItem(1, attacker);
        return true;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return ModItems.repairableWith(toRepair, repair, Items.DIAMOND);
    }
}
