package com.spearmacebackport.combat;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.enchant.ModEnchants;
import com.spearmacebackport.item.ItemDiamondSpear;
import com.spearmacebackport.item.ItemMace;
import com.spearmacebackport.item.ModItems;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.DamageSource;
import net.minecraft.util.CombatRules;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.Item;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Modern combat backport core.
 * Mace smash: +3 damage per fallen block (scaled by 1.12 attack cooldown),
 * Density +1/block/level, Breach +15% smash damage/level (1.12 armor-model
 * approximation), Wind Burst vertical launch, smash negates accumulated fall.
 * Spear: 2-4.5 reach jab on right-click, sprint charge scaling, Lunge bonus,
 * soft sub-2-block dead zone. Easy-swap: a sword hit followed by a mace
 * smash within 1.0s applies BOTH hits without vanilla attribute swapping.
 */
@Mod.EventBusSubscriber(modid = SpearMaceBackportMod.MOD_ID)
public class CombatHandler {
    public static final long SWAP_WINDOW_MS = 1000L;
    private static final double SMASH_PER_BLOCK = 3.0D;
    private static final double DENSITY_PER_BLOCK = 1.0D;
    private static final double BREACH_FRACTION_PER_LEVEL = 0.15D;

    private static final Map<UUID, SwapMemory> LAST_SWORD_HIT = new HashMap<>();
    private static final Map<UUID, Long> LAST_SMASH_MS = new HashMap<>();

    private static final class SwapMemory {
        final long timeMs;
        final float damage;
        final int victimId;

        SwapMemory(long timeMs, float damage, int victimId) {
            this.timeMs = timeMs;
            this.damage = damage;
            this.victimId = victimId;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving() == null || event.getEntityLiving().world.isRemote) {
            return;
        }
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer attacker = (EntityPlayer) event.getSource().getTrueSource();
        ItemStack held = attacker.getHeldItemMainhand();
        if (held.isEmpty()) {
            return;
        }

        if (held.getItem() instanceof ItemMace) {
            handleMaceHit(event, attacker, held);
        } else if (held.getItem() instanceof ItemDiamondSpear) {
            handleSpearMelee(event, attacker);
        } else if (held.getItem() instanceof ItemSword) {
            LAST_SWORD_HIT.put(attacker.getUniqueID(),
                    new SwapMemory(System.currentTimeMillis(), event.getAmount(),
                            event.getEntityLiving().getEntityId()));
        }
    }

    private static void handleMaceHit(LivingHurtEvent event, EntityPlayer attacker, ItemStack held) {
        float strength = attacker.getCooledAttackStrength(0.5F);
        if (isSmash(attacker)) {
            float fallen = Math.max(0.0F, attacker.fallDistance - 1.5F);
            int density = EnchantmentHelper.getEnchantmentLevel(ModEnchants.DENSITY, held);
            int breach = EnchantmentHelper.getEnchantmentLevel(ModEnchants.BREACH, held);
            int windBurst = EnchantmentHelper.getEnchantmentLevel(ModEnchants.WIND_BURST, held);

            double bonus = fallen * (SMASH_PER_BLOCK + DENSITY_PER_BLOCK * density);
            bonus *= strength;
            event.setAmount(event.getAmount() + (float) bonus);
            if (breach > 0) {
                applyBreach(event, attacker, held, breach);
            }

            if (windBurst > 0) {
                attacker.motionY = 0.9D + 0.35D * windBurst;
                attacker.velocityChanged = true;
            }
            attacker.fallDistance = 0.0F;
            LAST_SMASH_MS.put(attacker.getUniqueID(), System.currentTimeMillis());

            SwapMemory memory = LAST_SWORD_HIT.remove(attacker.getUniqueID());
            if (memory != null
                    && System.currentTimeMillis() - memory.timeMs <= SWAP_WINDOW_MS
                    && memory.victimId == event.getEntityLiving().getEntityId()) {
                event.setAmount(event.getAmount() + memory.damage);
            }
        }
    }

    /**
     * True Breach semantics: shrink the victim's effective armor by 15% per
     * level, then top up the pre-armor amount so the post-armor result matches
     * a hit against that reduced armor (via vanilla CombatRules).
     */
    private static void applyBreach(LivingHurtEvent event, EntityPlayer attacker,
                                    ItemStack held, int breach) {
        EntityLivingBase victim = event.getEntityLiving();
        float armor = (float) victim.getTotalArmorValue();
        if (armor <= 0.0F) {
            return;
        }
        float toughness = (float) victim.getEntityAttribute(
                SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();
        float reduced = Math.max(0.0F, armor * (1.0F - (float) BREACH_FRACTION_PER_LEVEL * breach));
        float amount = event.getAmount();
        float want = CombatRules.getDamageAfterAbsorb(amount, reduced, toughness);
        float have = CombatRules.getDamageAfterAbsorb(amount, armor, toughness);
        if (want > have) {
            event.setAmount(amount + (want - have));
        }
    }

    private static boolean isSmash(EntityPlayer attacker) {
        return !attacker.onGround && attacker.fallDistance > 1.5F && attacker.motionY < 0.0D;
    }

    private static void handleSpearMelee(LivingHurtEvent event, EntityPlayer attacker) {
        double distance = attacker.getDistance(event.getEntityLiving());
        if (distance < ItemDiamondSpear.MIN_REACH) {
            event.setAmount(event.getAmount() * 0.25F);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntityLiving().world.isRemote
                || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        Long smashMs = LAST_SMASH_MS.get(player.getUniqueID());
        if (smashMs != null && System.currentTimeMillis() - smashMs < 1000L
                && event.getDistance() < 6.0F) {
            event.setCanceled(true);
            LAST_SMASH_MS.remove(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public static void onSpearJab(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ItemDiamondSpear)) {
            return;
        }
        if (player.getCooldownTracker().hasCooldown(held.getItem())) {
            return;
        }
        int lunge = EnchantmentHelper.getEnchantmentLevel(ModEnchants.LUNGE, held);
        double reach = ItemDiamondSpear.MAX_REACH + lunge * 0.25D;
        reach = Math.min(reach, blockLimitedReach(player, reach));
        EntityLivingBase target = raycastLiving(player, reach);
        if (target == null) {
            return;
        }
        double distance = player.getDistance(target);
        float damage = (float) ItemDiamondSpear.BASE_DAMAGE;
        if (distance < ItemDiamondSpear.MIN_REACH) {
            damage *= 0.25F;
        }
        double blocksPerSecond = Math.sqrt(player.motionX * player.motionX
                + player.motionZ * player.motionZ) * 20.0D;
        if (player.isSprinting() || blocksPerSecond > 3.0D) {
            damage += Math.min(6.0F, (float) (blocksPerSecond * 0.35D)) + lunge * 1.5F;
            target.knockBack(player, 0.6F + 0.2F * lunge,
                    player.posX - target.posX, player.posZ - target.posZ);
        }
        damage += EnchantmentHelper.getModifierForCreature(held, target.getCreatureAttribute());
        boolean hurt = target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
        if (hurt) {
            held.damageItem(1, player);
            player.getCooldownTracker().setCooldown(held.getItem(), 21);
            player.swingArm(event.getHand());
        }
        event.setCanceled(true);
    }

    private static double blockLimitedReach(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d end = eye.add(player.getLook(1.0F).scale(reach));
        net.minecraft.util.math.RayTraceResult hit =
                player.world.rayTraceBlocks(eye, end, false, true, false);
        if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            return Math.min(reach, eye.distanceTo(hit.hitVec));
        }
        return reach;
    }

    private static EntityLivingBase raycastLiving(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        AxisAlignedBB box = player.getEntityBoundingBox().grow(reach);
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(player, box);
        EntityLivingBase best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase) || !entity.canBeCollidedWith()) {
                continue;
            }
            AxisAlignedBB target = entity.getEntityBoundingBox();
            Vec3d center = target.getCenter();
            Vec3d toTarget = center.subtract(eye);
            double along = toTarget.dotProduct(look);
            if (along < 0.0D || along > reach) {
                continue;
            }
            Vec3d closest = eye.add(look.scale(along));
            double lateral = closest.distanceTo(center);
            double allowed = Math.max(target.getAverageEdgeLength() * 0.5D + 0.6D, 1.0D);
            if (lateral <= allowed && along < bestAlong) {
                bestAlong = along;
                best = (EntityLivingBase) entity;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()
                || right.getItem() != Items.ENCHANTED_BOOK) {
            return;
        }
        Map<Enchantment, Integer> book = EnchantmentHelper.getEnchantments(right);
        if (book.isEmpty()) {
            return;
        }
        boolean spear = left.getItem() instanceof ItemDiamondSpear;
        boolean mace = left.getItem() instanceof ItemMace;
        if (!spear && !mace) {
            return;
        }
        ItemStack out = left.copy();
        Map<Enchantment, Integer> merged = EnchantmentHelper.getEnchantments(left);
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : book.entrySet()) {
            Enchantment ench = entry.getKey();
            if (ench == null) {
                continue;
            }
            boolean allowed = ench.canApply(left)
                    || (spear && isSpearAnvilEnchant(ench))
                    || (mace && isMaceAnvilEnchant(ench));
            if (allowed) {
                int current = merged.containsKey(ench) ? merged.get(ench) : 0;
                merged.put(ench, Math.min(Math.max(current, entry.getValue()), ench.getMaxLevel()));
                changed = true;
            }
        }
        if (changed) {
            EnchantmentHelper.setEnchantments(merged, out);
            event.setOutput(out);
            event.setCost(5);
            event.setMaterialCost(1);
        }
    }

    private static boolean isSpearAnvilEnchant(Enchantment ench) {
        return ench == ModEnchants.LUNGE
                || (ench.getRegistryName() != null
                && (ench.getRegistryName().toString().startsWith("minecraft:sharpness")
                || ench.getRegistryName().toString().startsWith("minecraft:smite")
                || ench.getRegistryName().toString().startsWith("minecraft:bane_of_arthropods")
                || ench.getRegistryName().toString().startsWith("minecraft:fire_aspect")
                || ench.getRegistryName().toString().startsWith("minecraft:looting")
                || ench.getRegistryName().toString().startsWith("minecraft:knockback")
                || ench.getRegistryName().toString().startsWith("minecraft:unbreaking")
                || ench.getRegistryName().toString().startsWith("minecraft:mending")));
    }

    private static boolean isMaceAnvilEnchant(Enchantment ench) {
        return ench == ModEnchants.DENSITY || ench == ModEnchants.BREACH
                || ench == ModEnchants.WIND_BURST
                || (ench.getRegistryName() != null
                && (ench.getRegistryName().toString().startsWith("minecraft:smite")
                || ench.getRegistryName().toString().startsWith("minecraft:bane_of_arthropods")
                || ench.getRegistryName().toString().startsWith("minecraft:fire_aspect")
                || ench.getRegistryName().toString().startsWith("minecraft:unbreaking")
                || ench.getRegistryName().toString().startsWith("minecraft:mending")));
    }

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        String name = event.getName().toString();
        if (!"minecraft:chests/simple_dungeon".equals(name)
                && !"minecraft:chests/end_city_treasure".equals(name)) {
            return;
        }
        LootPool pool = new LootPool(
                new LootEntryItem[]{
                        new LootEntryItem(ModItems.DIAMOND_SPEAR, 5, 0,
                                new LootFunction[0], new LootCondition[0],
                                SpearMaceBackportMod.MOD_ID + ":diamond_spear"),
                        new LootEntryItem(Items.ENCHANTED_BOOK, 3, 0,
                                new LootFunction[]{new WindBurstBookFunction()},
                                new LootCondition[0],
                                SpearMaceBackportMod.MOD_ID + ":wind_burst_book")
                },
                new LootCondition[0], new RandomValueRange(0, 1), new RandomValueRange(0),
                SpearMaceBackportMod.MOD_ID + "_loot");
        event.getTable().addPool(pool);
    }

    public static class WindBurstBookFunction extends LootFunction {
        WindBurstBookFunction() {
            super(new LootCondition[0]);
        }

        @Override
        public ItemStack apply(ItemStack stack, Random rand, net.minecraft.world.storage.loot.LootContext context) {
            NBTTagList stored = new NBTTagList();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(ModEnchants.WIND_BURST));
            tag.setShort("lvl", (short) 1);
            stored.appendTag(tag);
            NBTTagCompound root = stack.getTagCompound();
            if (root == null) {
                root = new NBTTagCompound();
                stack.setTagCompound(root);
            }
            root.setTag("StoredEnchantments", stored);
            return stack;
        }
    }
}
