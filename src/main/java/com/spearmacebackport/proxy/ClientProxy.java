package com.spearmacebackport.proxy;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ItemMace;
import com.spearmacebackport.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

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

    private static final Map<Integer, AxisAlignedBB> TRUE_BOXES = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            TRUE_BOXES.clear();
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            for (Entity entity : mc.world.loadedEntityList) {
                AxisAlignedBB orig = TRUE_BOXES.remove(entity.getEntityId());
                if (orig != null) {
                    entity.setEntityBoundingBox(orig);
                }
            }
            return;
        }
        boolean mace = mc.player.getHeldItemMainhand().getItem() instanceof ItemMace
                || mc.player.getHeldItemOffhand().getItem() instanceof ItemMace;
        if (!mace) {
            return;
        }
        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.player) {
                continue;
            }
            AxisAlignedBB box = entity.getEntityBoundingBox();
            TRUE_BOXES.put(entity.getEntityId(), box);
            double w = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);
            double h = box.maxY - box.minY;
            entity.setEntityBoundingBox(box.grow(w * 0.5D, h * 0.5D, w * 0.5D));
        }
    }
}
