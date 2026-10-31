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
import net.minecraft.init.MobEffects;
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
 * Modern combat backport core. All numbers are the Java Edition engine values:
 * mace base 6, speed 0.6, smash tiers +4/+2/+1 per fallen block (full fall
 * distance, triggers at >=1.5), Density +0.5/block/level, Breach -15% armor
 * effectiveness/level, Wind Burst 8 blocks of height/level, smash ignores
 * attack cooldown and is usually a (vanilla) crit. Diamond spear: jab 4 at
 * 1.05s meter, range 2-4.5, hard 2-block dead zone, never crits; charge is
 * floor(closingSpeed x 1.075) + 4 + Sharpness with 4.6/5.1 block/s gates;
 * Lunge propels 0.458 blocks/tick/level on jab for 1 durability + hunger.
 * Easy-swap: a sword hit followed by a mace smash within 1.0s applies BOTH
 * hits without vanilla attribute swapping. Backport limits (documented in
 * README): no hold-to-charge stages/dismount, single charge damage roll.
 */
@Mod.EventBusSubscriber(modid = SpearMaceBackportMod.MOD_ID)
public class CombatHandler {
    public static final long SWAP_WINDOW_MS = 1000L;
    private static final double DENSITY_PER_BLOCK_PER_LEVEL = 0.5D;
    private static final double BREACH_FRACTION_PER_LEVEL = 0.15D;
    private static final double[] WIND_BURST_VELOCITY = {0.0D, 1.289D, 1.873D, 2.349D};
    private static final double CHARGE_DAMAGE_GATE = 4.6D;
    private static final double CHARGE_KNOCKBACK_GATE = 5.1D;
    private static final double DIAMOND_CHARGE_MULT = 1.075D;
    private static final double LUNGE_BLOCKS_PER_TICK_PER_LEVEL = 0.458D;

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
        int breach = EnchantmentHelper.getEnchantmentLevel(ModEnchants.BREACH, held);
        if (isSmash(attacker)) {
            float fallen = attacker.fallDistance;
            int density = EnchantmentHelper.getEnchantmentLevel(ModEnchants.DENSITY, held);
            int windBurst = EnchantmentHelper.getEnchantmentLevel(ModEnchants.WIND_BURST, held);

            // Engine tiers: +4 for each of the first 3 blocks, +2 for the next
            // 5, +1 beyond; Density adds +0.5 per block per level. Smash
            // ignores attack cooldown (MC-270160, Works As Intended); the usual
            // falling crit is applied by vanilla itself (x1.5).
            double first = Math.min(fallen, 3.0D);
            double second = Math.min(Math.max(fallen - 3.0D, 0.0D), 5.0D);
            double rest = Math.max(fallen - 8.0D, 0.0D);
            double bonus = first * 4.0D + second * 2.0D + rest
                    + fallen * DENSITY_PER_BLOCK_PER_LEVEL * density;
            event.setAmount(event.getAmount() + (float) bonus);
            if (breach > 0) {
                applyBreach(event, breach);
            }

            if (windBurst > 0) {
                attacker.motionY = WIND_BURST_VELOCITY[Math.min(windBurst, 3)];
                attacker.velocityChanged = true;
            }
            smashKnockback(attacker, event.getEntityLiving(), fallen);
            attacker.fallDistance = 0.0F;
            LAST_SMASH_MS.put(attacker.getUniqueID(), System.currentTimeMillis());

            SwapMemory memory = LAST_SWORD_HIT.remove(attacker.getUniqueID());
            if (memory != null
                    && System.currentTimeMillis() - memory.timeMs <= SWAP_WINDOW_MS
                    && memory.victimId == event.getEntityLiving().getEntityId()) {
                event.setAmount(event.getAmount() + memory.damage);
            }
        } else if (breach > 0) {
            applyBreach(event, breach);
        }
    }

    private static void smashKnockback(EntityPlayer attacker, EntityLivingBase victim, float fallen) {
        double strength = fallen > 5.0F ? 1.4D : 0.8D;
        for (EntityLivingBase nearby : attacker.world.getEntitiesWithinAABB(EntityLivingBase.class,
                attacker.getEntityBoundingBox().grow(2.5D))) {
            if (nearby == attacker || nearby == victim
                    || attacker.equals(nearby.getRidingEntity())) {
                continue;
            }
            nearby.knockBack(attacker, (float) strength,
                    attacker.posX - nearby.posX, attacker.posZ - nearby.posZ);
        }
    }

    /**
     * True Breach semantics: shrink the victim's effective armor by 15% per
     * level, then top up the pre-armor amount so the post-armor result matches
     * a hit against that reduced armor (via vanilla CombatRules).
     */
    private static void applyBreach(LivingHurtEvent event, int breach) {
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
        // Hard 2-block dead zone: the spear cannot touch anything closer.
        if (attacker.getDistance(event.getEntityLiving()) < ItemDiamondSpear.MIN_REACH) {
            event.setAmount(0.0F);
            event.setCanceled(true);
            return;
        }
        // Spears never land critical hits (MC-302782, Works As Intended):
        // strip the x1.5 vanilla applies to a falling attack.
        if (isVanillaCrit(attacker, event.getEntityLiving())) {
            event.setAmount(event.getAmount() * (1.0F / 1.5F));
        }
    }

    private static boolean isVanillaCrit(EntityPlayer attacker, EntityLivingBase target) {
        return attacker.fallDistance > 0.0F && !attacker.onGround && !attacker.isOnLadder()
                && !attacker.isInWater() && !attacker.isPotionActive(MobEffects.BLINDNESS)
                && attacker.getRidingEntity() == null;
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
        // The jab demands a fully charged attack meter, exactly like modern.
        if (player.getCooledAttackStrength(0.5F) < 1.0F) {
            return;
        }
        double reach = blockLimitedReach(player, ItemDiamondSpear.MAX_REACH);
        List<EntityLivingBase> targets = raycastLiving(player, reach);
        if (targets.isEmpty()) {
            return;
        }
        double closing = closingSpeed(player, targets.get(0));
        boolean charge = closing >= CHARGE_DAMAGE_GATE;
        int sharp = EnchantmentHelper.getModifierForCreature(held,
                targets.get(0).getCreatureAttribute());
        boolean hurtAny = false;
        for (EntityLivingBase target : targets) {
            if (player.getDistance(target) < ItemDiamondSpear.MIN_REACH) {
                continue;
            }
            float damage;
            if (charge) {
                // Kinetic model: floor(closing speed x tier multiplier) + base + Sharpness.
                damage = (float) Math.floor(closing * DIAMOND_CHARGE_MULT)
                        + (float) ItemDiamondSpear.BASE_DAMAGE + sharp;
                if (closing >= CHARGE_KNOCKBACK_GATE) {
                    target.knockBack(player, 1.0F,
                            player.posX - target.posX, player.posZ - target.posZ);
                }
            } else {
                damage = (float) ItemDiamondSpear.BASE_DAMAGE + sharp;
            }
            if (target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage)) {
                hurtAny = true;
            }
        }
        if (hurtAny) {
            held.damageItem(1, player);
            if (!charge) {
                fireLunge(player, held);
            }
            // Spend the attack meter, mirroring a fully charged swing.
            player.resetCooldown();
            player.swingArm(event.getHand());
        }
        event.setCanceled(true);
    }

    private static double closingSpeed(EntityPlayer player, EntityLivingBase target) {
        Vec3d look = player.getLook(1.0F);
        double attackerForward = player.motionX * look.x + player.motionY * look.y
                + player.motionZ * look.z;
        if (attackerForward < 0.0D) {
            return 0.0D;
        }
        double relX = player.motionX - target.motionX;
        double relY = player.motionY - target.motionY;
        double relZ = player.motionZ - target.motionZ;
        return Math.max(0.0D, (relX * look.x + relY * look.y + relZ * look.z) * 20.0D);
    }

    private static void fireLunge(EntityPlayer player, ItemStack held) {
        int lunge = EnchantmentHelper.getEnchantmentLevel(ModEnchants.LUNGE, held);
        if (lunge <= 0 || player.getRidingEntity() != null || player.isElytraFlying()
                || player.isInWater() || player.getFoodStats().getFoodLevel() < 7) {
            return;
        }
        Vec3d look = player.getLook(1.0F);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontal < 1.0E-4D) {
            return;
        }
        double speed = LUNGE_BLOCKS_PER_TICK_PER_LEVEL * lunge * (player.onGround ? 1.0D : 1.2D);
        player.motionX = look.x / horizontal * speed;
        player.motionZ = look.z / horizontal * speed;
        player.velocityChanged = true;
        held.damageItem(1, player);
        player.addExhaustion(4.0F * lunge);
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

    private static List<EntityLivingBase> raycastLiving(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        AxisAlignedBB box = player.getEntityBoundingBox().grow(reach);
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(player, box);
        List<EntityLivingBase> hits = new java.util.ArrayList<>();
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
            double allowed = Math.max(target.getAverageEdgeLength() * 0.5D + 0.125D, 0.6D);
            if (lateral <= allowed) {
                hits.add((EntityLivingBase) entity);
            }
        }
        final Vec3d finalEye = eye;
        hits.sort((a, b) -> Double.compare(
                a.getEntityBoundingBox().getCenter().subtract(finalEye).lengthVector(),
                b.getEntityBoundingBox().getCenter().subtract(finalEye).lengthVector()));
        return hits;
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
                if (mace && isMaceExclusiveGroup(ench)) {
                    stripMaceExclusiveGroup(merged, ench);
                }
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

    private static boolean isMaceExclusiveGroup(Enchantment ench) {
        return ench == ModEnchants.DENSITY || ench == ModEnchants.BREACH
                || isVanilla(ench, "minecraft:smite")
                || isVanilla(ench, "minecraft:bane_of_arthropods");
    }

    private static void stripMaceExclusiveGroup(Map<Enchantment, Integer> merged, Enchantment keep) {
        for (Enchantment existing : new java.util.HashSet<>(merged.keySet())) {
            if (existing != keep && isMaceExclusiveGroup(existing)) {
                merged.remove(existing);
            }
        }
    }

    private static boolean isVanilla(Enchantment ench, String id) {
        return ench.getRegistryName() != null && id.equals(ench.getRegistryName().toString());
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
