package com.spearmacebackport.proxy;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = "spearmacebackport", value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModItems.DIAMOND_SPEAR.addPropertyOverride(
                new ResourceLocation(SpearMaceBackportMod.MOD_ID, "in_hand"),
                (stack, world, entity) -> {
                    if (entity != null && (stack == entity.getHeldItemMainhand()
                            || stack == entity.getHeldItemOffhand())) {
                        return 1.0F;
                    }
                    return 0.0F;
                });
        RenderingRegistry.registerEntityRenderingHandler(EntityWindCharge.class,
                manager -> new RenderSnowball<EntityWindCharge>(manager,
                        ModItems.WIND_CHARGE, Minecraft.getMinecraft().getRenderItem()));
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent event) {
        for (Item item : ModItems.ALL) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
