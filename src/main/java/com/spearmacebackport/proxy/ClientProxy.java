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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
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
        // Exact replica of modern's display_context select (gui/ground/fixed
        // -> short icon, everything else -> long held art): the rendered
        // entity is only ever a player when a player holds/wears the spear,
        // and null in GUIs. No stack comparison (the renderer may hold a
        // value-equal copy) and no world check (the hand path passes a null
        // world in 1.12.2, which is why the world-based predicate failed).
        ModItems.DIAMOND_SPEAR.addPropertyOverride(
                new ResourceLocation(SpearMaceBackportMod.MOD_ID, "in_hand"),
                (stack, world, entity) -> entity instanceof EntityPlayer ? 1.0F : 0.0F);
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
        // REQUIRED in 1.12.2: models referenced only from an override block
        // are never loaded unless registered as item variants (this is how
        // vanilla loads bow_pulling_0/1/2). Without this line the override
        // is silently dead and the base model shows everywhere.
        ModelLoader.registerItemVariants(ModItems.DIAMOND_SPEAR,
                new ResourceLocation(SpearMaceBackportMod.MOD_ID, "item/diamond_spear_in_hand"));
    }

    private static final Map<Integer, AxisAlignedBB> TRUE_BOXES = new HashMap<>();

    /**
     * True client-side hitbox doubling: while the player holds a mace, every
     * mob's box is exactly 2x in all three dimensions for click detection
     * (grow by half-size on each side). True boxes are restored at tick start
     * so movement and collision always use real sizes; the grown boxes only
     * exist for render/input afterwards.
     */
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
