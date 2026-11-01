package com.spearmacebackport.proxy;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.client.SpearPerspectiveModel;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
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

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
            event.getMap().registerSprite(new ResourceLocation(
                    SpearMaceBackportMod.MOD_ID, "items/diamond_spear_in_hand"));
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        try {
            ModelResourceLocation inv = new ModelResourceLocation(
                    ModItems.DIAMOND_SPEAR.getRegistryName(), "inventory");
            IBakedModel base = event.getModelRegistry().getObject(inv);
            if (base == null) {
                return;
            }
            net.minecraftforge.client.model.IModel handModel =
                    net.minecraftforge.client.model.ModelLoaderRegistry.getModelOrMissing(
                            new ResourceLocation(SpearMaceBackportMod.MOD_ID,
                                    "item/diamond_spear_hand"));
            IBakedModel hand = handModel.bake(handModel.getDefaultState(),
                    DefaultVertexFormats.ITEM,
                    net.minecraftforge.client.model.ModelLoader.defaultTextureGetter());
            event.getModelRegistry().putObject(inv, new SpearPerspectiveModel(base, hand));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
