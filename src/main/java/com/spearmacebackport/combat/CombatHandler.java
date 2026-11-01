package com.spearmacebackport.combat;

import com.spearmacebackport.SpearMaceBackportMod;
import com.spearmacebackport.enchant.ModEnchants;
import com.spearmacebackport.entity.EntityWindCharge;
import com.spearmacebackport.item.ItemDiamondSpear;
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
 * forgiveness deviation: landing within 0.5s of a 1.5+ fall still smashes.
 * Smash knocks back within 3.5 blocks with the
 * official (3.5-dist) x 0.7 x high-fall-double formula and a fixed +0.7 lift,
 * smash resets vertical momentum to 0.01 and records the impact height so
 * landing only counts fall damage below it. Wind Burst is a 3.5-radius
 * explosion at the attacker (multipliers 1.2/1.75/2.2, no fall protection);
 * wind-charge bursts protect players below the burst height the same way.
 * Diamond spear: jab 4 at 1.05s meter, range 2-4.5, hard 2-block dead zone,
 * never crits; charge is floor(closingSpeed x 1.075) + 4 + Sharpness with
 * 4.6/5.1 block/s gates; Lunge propels 0.458 blocks/tick/level on jab for
 * 1 durability + hunger. Easy-swap: a sword hit followed by a mace smash
 * within 1.0s applies BOTH hits without vanilla attribute swapping.
 * Auto-swap: falling past the smash threshold with any non-mace, non-spear
 * weapon fires the smash package of the first inventory mace (hotbar first)
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
    private static final double CHARGE_DAMAGE_GATE = 4.6D;
    private static final double CHARGE_KNOCKBACK_GATE = 5.1D;
    private static final double DIAMOND_CHARGE_MULT = 1.075D;
    private static final double LUNGE_BLOCKS_PER_TICK_PER_LEVEL = 0.458D;

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

        if (held.getItem() instanceof ItemMace) {
            handleMaceHit(event, attacker, held);
        } else if (held.getItem() instanceof ItemDiamondSpear) {
            handleSpearMelee(event, attacker);
        } else if (isSmash(attacker)) {
            // Auto-swap: falling past the smash threshold with any non-mace,
            // non-spear weapon in hand fires the smash package of the first
            // mace found in the inventory (hotbar first). The held weapon
            // keeps its own hit; the mace contributes its smash bonus,
            // Breach shred, Wind Burst, knockback, particles and fall
            // protection, and takes 1 durability.
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
        } else if (breach > 0) {
            applyBreach(event, breach);
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
        // last 0.5s after a 1.5+ block fall still smashes, so clicks that
        // land a tick late are not lost.
        LandRecord land = LAST_LAND.get(attacker.getUniqueID());
        return attacker.onGround && land != null
                && attacker.world.getTotalWorldTime() - land.time < 10L
                && land.distance > 1.5F;
    }

    private static float smashFallen(EntityPlayer attacker) {
        if (!attacker.onGround) {
            return attacker.fallDistance;
        }
        LandRecord land = LAST_LAND.get(attacker.getUniqueID());
        return land != null ? land.distance : 0.0F;
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
        }
    }

    /**
     * Spear jab, invoked from the item's own right-click dispatch (which is
     * proven to reach the server) rather than the interact event.
     */
    public static void performSpearJab(EntityPlayer player, ItemStack held, EnumHand hand) {
        if (held.isEmpty() || !(held.getItem() instanceof ItemDiamondSpear)) {
            return;
        }
        // The jab scales with the attack meter exactly like a vanilla swing
        // (0.2 + strength^2 x 0.8): spam-clicks land weak stabs, a full meter
        // lands the full 4. A fully charged swing is NOT required.
        float strength = player.getCooledAttackStrength(0.5F);
        float meterScale = 0.2F + strength * strength * 0.8F;
        double reach = blockLimitedReach(player, ItemDiamondSpear.MAX_REACH);
        List<EntityLivingBase> targets = raycastLiving(player, reach);
        if (targets.isEmpty()) {
            return;
        }
        double closing = closingSpeed(player, targets.get(0));
        boolean charge = closing >= CHARGE_DAMAGE_GATE;
        float sharp = EnchantmentHelper.getModifierForCreature(held,
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
                damage = ((float) ItemDiamondSpear.BASE_DAMAGE + sharp) * meterScale;
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
            player.swingArm(hand);
            EntityLivingBase first = targets.get(0);
            player.world.playSound(null, first.posX, first.posY, first.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SWEEP_ATTACK,
                        first.posX, first.posY + first.height * 0.5D, first.posZ,
                        2, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        } else {
            // Whiffed (dead zone, wall, or out of reach): swing + soft whoosh
            // so the jab always reads, exactly like the modern swing.
            player.swingArm(hand);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.4F, 1.2F);
        }
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
                || player.isInWater()
                || player.getFoodStats().getFoodLevel() < 7 && !player.capabilities.isCreativeMode) {
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
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 0.7F);
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
                a.getEntityBoundingBox().getCenter().distanceTo(finalEye),
                b.getEntityBoundingBox().getCenter().distanceTo(finalEye)));
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
