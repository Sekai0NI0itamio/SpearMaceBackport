package com.spearmacebackport.combat;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.enchant.ModEnchants;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ItemMace;
import com.spearmacebackport.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.SoundEvents;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.CombatRules;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Modern combat backport core. All numbers are the Java Edition engine values,
 * read from the official unobfuscated 26.3 sources: mace base 6, speed 0.6,
 * smash tiers +4/+2/+1 per fallen block (full fall distance, triggers at
 * >1.5, cooldown ignored per MC-270160), Density +0.5/block/level, Breach -15%
 * armor effectiveness/level, Wind Burst is a 3.5-radius explosion at the
 * attacker (multipliers 1.2/1.75/2.2, no fall protection); intentional
 * forgiveness deviations: landing within 1.0s of a 1.5+ fall still
 * smashes, and mob hitboxes count double while you hold a mace.
 * Smash knocks back within 3.5 blocks with the
 * official (3.5-dist) x 0.7 x high-fall-double formula and a fixed +0.7 lift,
 * smash resets vertical momentum to 0.01 and records the impact height so
 * landing only counts fall damage below it. Wind Burst is a 3.5-radius
 * explosion at the attacker (multipliers 1.2/1.75/2.2, no fall protection);
 * wind-charge bursts protect players below the burst height the same way.
 * Easy-swap: a sword hit followed by a mace smash
 * within 1.0s applies BOTH hits without vanilla attribute swapping.
 * Auto-swap: falling past the smash threshold with any non-mace weapon
 * fires the smash package of the first inventory mace (hotbar first)
 * on top of the held weapon's own hit, costing the mace 1 durability.
 * Backport limits (documented in README): no hold-to-charge
 * stages/dismount, single charge damage roll.
 */
@Mod.EventBusSubscriber(modid = SpearMaceBackportMod.MOD_ID)
public class CombatHandler {
    public static final long SWAP_WINDOW_MS = 1000L;
    private static final double DENSITY_PER_BLOCK_PER_LEVEL = 0.5D;
    private static final double BREACH_FRACTION_PER_LEVEL = 0.15D;
    private static final double[] WIND_BURST_MULTIPLIER = {0.0D, 1.2D, 1.75D, 2.2D};
    private static final double WIND_BURST_RADIUS = 3.5D;

    private static final Map<UUID, SwapMemory> LAST_SWORD_HIT = new HashMap<>();
    private static final Map<UUID, BurstRecord> WIND_BURSTS = new HashMap<>();
    private static final Map<UUID, SmashRecord> SMASH_IMPACTS = new HashMap<>();
    private static final Map<UUID, LandRecord> LAST_LAND = new HashMap<>();

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

    private static final class BurstRecord {
        final double y;
        final long time;

        BurstRecord(double y, long time) {
            this.y = y;
            this.time = time;
        }
    }

    private static final class SmashRecord {
        final double y;
        final boolean groundedVictim;
        final long time;

        SmashRecord(double y, boolean groundedVictim, long time) {
            this.y = y;
            this.groundedVictim = groundedVictim;
            this.time = time;
        }
    }

    private static final class LandRecord {
        final float distance;
        final long time;

        LandRecord(float distance, long time) {
            this.distance = distance;
            this.time = time;
        }
    }

    /**
     * Records a wind-burst launch so the next landing only accumulates fall
     * damage below the burst height, exactly like modern: the burst also
     * zeroes whatever fall distance the player had going in.
     */
    public static void recordWindBurst(EntityPlayer player, double burstY) {
        player.fallDistance = 0.0F;
        WIND_BURSTS.put(player.getUniqueID(),
                new BurstRecord(burstY, player.world.getTotalWorldTime()));
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

        SwingWatch watch = SWINGS.computeIfAbsent(attacker.getUniqueID(), k -> new SwingWatch());
        watch.lastHitTick = attacker.world.getTotalWorldTime();

        if (held.getItem() instanceof ItemMace) {
            handleMaceHit(event, attacker, held);
        } else if (isSmash(attacker)) {
            // Auto-swap: falling past the smash threshold with any non-mace
            // weapon in hand fires the smash package of the first mace found
            // in the inventory (hotbar first). The held weapon keeps its own
            // hit; the mace contributes its smash bonus, Breach shred, Wind
            // Burst, knockback, particles and fall protection, and takes 1
            // durability.
            ItemStack mace = findMace(attacker);
            if (!mace.isEmpty()) {
                applyMaceSmash(event, attacker, mace, smashFallen(attacker), true);
                return;
            }
            if (held.getItem() instanceof ItemSword) {
                LAST_SWORD_HIT.put(attacker.getUniqueID(),
                        new SwapMemory(System.currentTimeMillis(), event.getAmount(),
                                event.getEntityLiving().getEntityId()));
            }
        } else if (held.getItem() instanceof ItemSword) {
            LAST_SWORD_HIT.put(attacker.getUniqueID(),
                    new SwapMemory(System.currentTimeMillis(), event.getAmount(),
                            event.getEntityLiving().getEntityId()));
        }
    }

    private static ItemStack findMace(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemMace) {
                return stack;
            }
        }
        ItemStack offhand = player.getHeldItemOffhand();
        if (!offhand.isEmpty() && offhand.getItem() instanceof ItemMace) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }

    private static void handleMaceHit(LivingHurtEvent event, EntityPlayer attacker, ItemStack held) {
        int breach = EnchantmentHelper.getEnchantmentLevel(ModEnchants.BREACH, held);
        if (isSmash(attacker)) {
            applyMaceSmash(event, attacker, held, smashFallen(attacker), false);

            SwapMemory memory = LAST_SWORD_HIT.remove(attacker.getUniqueID());
            if (memory != null
                    && System.currentTimeMillis() - memory.timeMs <= SWAP_WINDOW_MS
                    && memory.victimId == event.getEntityLiving().getEntityId()) {
                event.setAmount(event.getAmount() + memory.damage);
            }
        } else {
            if (breach > 0) {
                applyBreach(event, breach);
            }
        }
    }

    private static void applyMaceSmash(LivingHurtEvent event, EntityPlayer attacker,
            ItemStack maceStack, float fallen, boolean damageMace) {
        int density = EnchantmentHelper.getEnchantmentLevel(ModEnchants.DENSITY, maceStack);
        int windBurst = EnchantmentHelper.getEnchantmentLevel(ModEnchants.WIND_BURST, maceStack);
        int breach = EnchantmentHelper.getEnchantmentLevel(ModEnchants.BREACH, maceStack);

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
        attacker.motionY = 0.01D;
        attacker.velocityChanged = true;
        if (breach > 0) {
            applyBreach(event, breach);
        }

        if (windBurst > 0) {
            EntityWindCharge.burst(attacker.world, attacker.posX, attacker.posY, attacker.posZ,
                    WIND_BURST_RADIUS, WIND_BURST_MULTIPLIER[Math.min(windBurst, 3)], false, null);
        } else {
            SMASH_IMPACTS.put(attacker.getUniqueID(), new SmashRecord(attacker.posY,
                    event.getEntityLiving().onGround, attacker.world.getTotalWorldTime()));
        }
        smashKnockback(attacker, event.getEntityLiving(), fallen);
        spawnSmashParticles(attacker, event.getEntityLiving(), fallen);
        attacker.fallDistance = 0.0F;
        if (damageMace) {
            maceStack.damageItem(1, attacker);
        }
    }

    /**
     * Mace hit-assist (deliberate deviation, no vanilla equivalent): mob
     * hitboxes count triple while you hold a mace, so clean visual misses
     * still land. Missed swings never reach the server, but the swing
     * animation does, so every click (single or spam) with no landed hit
     * within 3 ticks runs a generous raycast on the click-time aim
     * (3x boxes, wall-checked) and lands the mace hit directly.
     */
    private static final class SwingWatch {
        boolean wasSwinging;
        int prevProgress;
        long swingStartTick;
        long assistTick = -1L;
        long hitTickAtClick = -1L;
        long lastHitTick = -1L;
        net.minecraft.util.math.Vec3d swingEye;
        net.minecraft.util.math.Vec3d swingLook;
    }

    private static final Map<UUID, SwingWatch> SWINGS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.player;
        SwingWatch watch = SWINGS.computeIfAbsent(player.getUniqueID(), k -> new SwingWatch());
        boolean swinging = player.isSwingInProgress;
        int progress = player.swingProgressInt;
        if (swinging && (!watch.wasSwinging || progress < watch.prevProgress)) {
            // Fresh click: rising edge for single swings, progress reset for
            // spam clicks (the flag never drops between rapid swings).
            // Snapshot the aim now so falling hits don't drift.
            watch.swingStartTick = player.world.getTotalWorldTime();
            watch.assistTick = watch.swingStartTick + 3L;
            watch.hitTickAtClick = watch.lastHitTick;
            watch.swingEye = player.getPositionEyes(1.0F);
            watch.swingLook = player.getLook(1.0F);
        }
        watch.wasSwinging = swinging;
        watch.prevProgress = progress;
        if (watch.assistTick >= 0L
                && player.world.getTotalWorldTime() >= watch.assistTick) {
            watch.assistTick = -1L;
            if (watch.lastHitTick == watch.hitTickAtClick
                    && watch.swingEye != null && watch.swingLook != null) {
                ItemStack held = player.getHeldItemMainhand();
                if (held.isEmpty() || !(held.getItem() instanceof ItemMace)) {
                    held = player.getHeldItemOffhand();
                }
                if (!held.isEmpty() && held.getItem() instanceof ItemMace) {
                    assistMaceHit(player, held, watch.swingEye, watch.swingLook);
                }
            }
        }
    }

    private static void assistMaceHit(EntityPlayer attacker, ItemStack maceStack,
            Vec3d eye, Vec3d look) {
        // Tripled mob boxes: a zombie reads ~1.8 blocks wide, so clean
        // visual misses still connect. Wall-checked like vanilla.
        net.minecraft.util.math.RayTraceResult wall = attacker.world.rayTraceBlocks(eye,
                eye.add(look.scale(3.5D)), false, true, false);
        double reach = 3.5D;
        if (wall != null
                && wall.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            reach = Math.min(reach, eye.distanceTo(wall.hitVec));
        }
        Vec3d end = eye.add(look.scale(reach));
        EntityLivingBase best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityLivingBase target : attacker.world.getEntitiesWithinAABB(EntityLivingBase.class,
                attacker.getEntityBoundingBox().grow(reach + 2.0D))) {
            if (target == attacker || !target.canBeCollidedWith()
                    || target instanceof EntityPlayer
                            && ((EntityPlayer) target).isSpectator()) {
                continue;
            }
            if (target instanceof EntityTameable) {
                EntityTameable tame = (EntityTameable) target;
                if (tame.isTamed() && tame.isOwner(attacker)) {
                    continue;
                }
            }
            AxisAlignedBB box = target.getEntityBoundingBox();
            double w = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);
            double h = box.maxY - box.minY;
            AxisAlignedBB tripled = box.grow(w, h, w);
            net.minecraft.util.math.RayTraceResult hit =
                    tripled.calculateIntercept(eye, end);
            if (hit == null) {
                continue;
            }
            double dist = eye.distanceTo(hit.hitVec);
            if (dist < bestDist) {
                bestDist = dist;
                best = target;
            }
        }
        if (best == null) {
            return;
        }
        float strength = attacker.getCooledAttackStrength(0.5F);
        float damage = (float) attacker.getEntityAttribute(
                SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()
                + EnchantmentHelper.getModifierForCreature(maceStack,
                        best.getCreatureAttribute());
        damage *= 0.2F + strength * strength * 0.8F;
        if (isVanillaCrit(attacker, best)) {
            damage *= 1.5F;
        }
        if (best.attackEntityFrom(DamageSource.causePlayerDamage(attacker), damage)) {
            maceStack.damageItem(1, attacker);
            if (isVanillaCrit(attacker, best)) {
                attacker.world.playSound(null, attacker.posX, attacker.posY, attacker.posZ,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                if (attacker.world instanceof WorldServer) {
                    ((WorldServer) attacker.world).spawnParticle(EnumParticleTypes.CRIT,
                            best.posX, best.posY + best.height * 0.5D, best.posZ,
                            8, 0.3D, 0.3D, 0.3D, 0.2D);
                }
            }
        }
    }

    private static void smashKnockback(EntityPlayer attacker, EntityLivingBase victim, float fallen) {
        double radius = 3.5D;
        for (EntityLivingBase nearby : attacker.world.getEntitiesWithinAABB(EntityLivingBase.class,
                attacker.getEntityBoundingBox().grow(radius))) {
            if (isSmashImmune(attacker, victim, nearby)) {
                continue;
            }
            double dx = nearby.posX - victim.posX;
            double dy = nearby.posY - victim.posY;
            double dz = nearby.posZ - victim.posZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.001D || len > radius) {
                continue;
            }
            double resist = nearby.getEntityAttribute(
                    SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
            double power = (radius - len) * 0.7D * (fallen > 5.0F ? 2.0D : 1.0D)
                    * Math.max(0.0D, 1.0D - resist);
            if (power <= 0.0D) {
                continue;
            }
            nearby.motionX += dx / len * power;
            nearby.motionY += 0.7D;
            nearby.motionZ += dz / len * power;
            nearby.velocityChanged = true;
        }
    }

    private static boolean isSmashImmune(EntityPlayer attacker, EntityLivingBase victim,
            EntityLivingBase nearby) {
        if (nearby == attacker || nearby == victim
                || nearby instanceof EntityPlayer && ((EntityPlayer) nearby).isSpectator()
                || attacker.isOnSameTeam(nearby)) {
            return true;
        }
        if (nearby instanceof EntityArmorStand && ((EntityArmorStand) nearby).hasMarker()) {
            return true;
        }
        if (nearby instanceof EntityPlayer) {
            EntityPlayer other = (EntityPlayer) nearby;
            if (other.capabilities.isCreativeMode && other.capabilities.isFlying) {
                return true;
            }
        }
        if (nearby instanceof EntityTameable) {
            EntityTameable tame = (EntityTameable) nearby;
            if (tame.isTamed() && tame.isOwner(victim)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Modern smash visuals: an explosive burst at the victim plus block-dust
     * of the block the victim stands on — skipped when the victim's center is
     * in the air, exactly like the modern dust_pillar behavior.
     */
    private static void spawnSmashParticles(EntityPlayer attacker, EntityLivingBase victim,
            float fallen) {
        if (!(attacker.world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) attacker.world;
        double x = victim.posX;
        double y = victim.posY + victim.height * 0.5D;
        double z = victim.posZ;
        boolean heavy = fallen > 5.0F;
        boolean grounded = victim.onGround;
        ws.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, grounded ? (heavy ? 1.0F : 0.7F) : 0.5F,
                grounded ? (heavy ? 0.9F : 1.2F) : 1.6F);
        ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, x, y, z,
                2, 0.3D, 0.3D, 0.3D, 0.0D);
        ws.spawnParticle(EnumParticleTypes.CLOUD, x, y, z,
                12, 0.6D, 0.6D, 0.6D, 0.05D);
        BlockPos below = new BlockPos(x, victim.getEntityBoundingBox().minY - 0.2D, z);
        IBlockState ground = attacker.world.getBlockState(below);
        if (ground.getBlock() != Blocks.AIR) {
            ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x,
                    victim.getEntityBoundingBox().minY + 0.1D, z,
                    24, 0.5D, 0.1D, 0.5D, 0.0D, Block.getStateId(ground));
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
        if (!attacker.onGround && attacker.fallDistance > 1.5F) {
            return true;
        }
        // Intentional forgiveness deviation from vanilla: landing within the
        // last 1.0s after a 1.5+ block fall still smashes, so clicks that
        // land late are not lost.
        LandRecord land = LAST_LAND.get(attacker.getUniqueID());
        return attacker.onGround && land != null
                && attacker.world.getTotalWorldTime() - land.time < 20L
                && land.distance > 1.5F;
    }

    private static float smashFallen(EntityPlayer attacker) {
        if (!attacker.onGround) {
            return attacker.fallDistance;
        }
        LandRecord land = LAST_LAND.get(attacker.getUniqueID());
        return land != null ? land.distance : 0.0F;
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
        if (event.getDistance() > 0.0F) {
            LAST_LAND.put(player.getUniqueID(),
                    new LandRecord(event.getDistance(), player.world.getTotalWorldTime()));
        }
        BurstRecord burst = WIND_BURSTS.remove(player.getUniqueID());
        if (burst != null && player.world.getTotalWorldTime() - burst.time < 40L) {
            float adjusted = (float) Math.max(0.0D, burst.y - player.posY);
            event.setDistance(Math.min(event.getDistance(), adjusted));
        }
        SmashRecord smash = SMASH_IMPACTS.remove(player.getUniqueID());
        if (smash != null && player.world.getTotalWorldTime() - smash.time < 40L) {
            float fallen = event.getDistance();
            float adjusted = (float) Math.max(0.0D, smash.y - player.posY);
            event.setDistance(Math.min(fallen, adjusted));
            if (smash.groundedVictim) {
                spawnLandingDust(player, fallen);
            }
        }
    }

    private static void spawnLandingDust(EntityPlayer player, float fallen) {
        if (!(player.world instanceof WorldServer) || fallen <= 0.0F) {
            return;
        }
        WorldServer ws = (WorldServer) player.world;
        BlockPos below = new BlockPos(player.posX,
                player.getEntityBoundingBox().minY - 0.2D, player.posZ);
        IBlockState ground = player.world.getBlockState(below);
        if (ground.getBlock() == Blocks.AIR) {
            return;
        }
        int count = Math.min((int) (50.0F * fallen), 200);
        ws.spawnParticle(EnumParticleTypes.BLOCK_CRACK, player.posX,
                player.getEntityBoundingBox().minY + 0.1D, player.posZ,
                count, 0.3D, 0.1D, 0.3D, 0.15D, Block.getStateId(ground));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            WIND_BURSTS.remove(event.player.getUniqueID());
            SMASH_IMPACTS.remove(event.player.getUniqueID());
            LAST_LAND.remove(event.player.getUniqueID());
            SWINGS.remove(event.player.getUniqueID());
        }
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
        boolean mace = left.getItem() instanceof ItemMace;
        if (!mace) {
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

    private static boolean isMaceAnvilEnchant(Enchantment ench) {
        return ench == ModEnchants.DENSITY || ench == ModEnchants.BREACH
                || ench == ModEnchants.WIND_BURST
                || (ench.getRegistryName() != null
                && (ench.getRegistryName().toString().startsWith("minecraft:smite")
                || ench.getRegistryName().toString().startsWith("minecraft:bane_of_arthropods")
                || ench.getRegistryName().toString().startsWith("minecraft:fire_aspect")
                || ench.getRegistryName().toString().startsWith("minecraft:vanishing")
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
