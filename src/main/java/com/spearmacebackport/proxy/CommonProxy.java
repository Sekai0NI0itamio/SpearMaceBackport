package com.spearmacebackport.proxy;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ModItems;
import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorProjectileDispense;
import net.minecraft.dispenser.IPosition;
import net.minecraft.entity.IProjectile;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
        EntityRegistry.registerModEntity(
                new ResourceLocation(SpearMaceBackportMod.MOD_ID, "wind_charge"),
                EntityWindCharge.class, "wind_charge", 0, SpearMaceBackportMod.instance,
                64, 10, true);
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(ModItems.WIND_CHARGE,
                new BehaviorProjectileDispense() {
                    @Override
                    protected IProjectile getProjectileEntity(World world, IPosition position,
                            ItemStack stack) {
                        return new EntityWindCharge(world,
                                position.getX(), position.getY(), position.getZ());
                    }
                });
    }
}
