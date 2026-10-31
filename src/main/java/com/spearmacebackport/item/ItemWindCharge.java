package com.spearmacebackport.item;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.entity.EntityWindCharge;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/**
 * Modern Wind Charge backport: 1 breeze rod crafts into 4 in modern, but in
 * this mod wind charges are creative-only (no recipe), matching the owner's
 * call that mace, wind charges and breeze rods stay out of survival crafting.
 * Right-click throws the projectile with a half-second (10-tick) cooldown.
 */
public class ItemWindCharge extends Item {
    public ItemWindCharge() {
        setRegistryName(SpearMaceBackportMod.MOD_ID, "wind_charge");
        setTranslationKey(SpearMaceBackportMod.MOD_ID + ".wind_charge");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.MISC);
        ModItems.ALL.add(this);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.getCooldownTracker().setCooldown(this, 10);
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5F, 0.8F);
        if (!world.isRemote) {
            EntityWindCharge charge = new EntityWindCharge(world, player);
            charge.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, 1.5F, 1.0F);
            world.spawnEntity(charge);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.swingArm(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
