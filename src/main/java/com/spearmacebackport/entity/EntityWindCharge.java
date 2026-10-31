package com.spearmacebackport.entity;

import com.spearmacebackport.combat.CombatHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * Modern Wind Charge backport: a straight-flying (zero gravity) throwable.
 * Direct hits deal 1 damage; any impact sets off the modern 1.2-radius wind
 * burst (2.4-block knockback cutoff) that knocks entities back, so aiming
 * at your own feet launches you ~6 blocks, zeroes the launched players' fall
 * distance and lets them land damage-free down to the burst height. Block
 * impacts also toggle nearby wooden doors/trapdoors, fence gates, levers and
 * buttons, exactly like the modern projectile interacting with redstone parts.
 */
public class EntityWindCharge extends EntityThrowable {
    private static final double BURST_RADIUS = 1.2D;
    private static final float DIRECT_DAMAGE = 1.0F;
    /** Modern wind burst deals 22% more knockback than a matching raw explosion. */
    private static final double KNOCKBACK_MULTIPLIER = 1.22D;

    public EntityWindCharge(World world) {
        super(world);
        setSize(0.3125F, 0.3125F);
    }

    public EntityWindCharge(World world, EntityLivingBase thrower) {
        super(world, thrower);
        setSize(0.3125F, 0.3125F);
    }

    public EntityWindCharge(World world, double x, double y, double z) {
        super(world, x, y, z);
        setSize(0.3125F, 0.3125F);
    }

    @Override
    protected float getGravityVelocity() {
        // Modern charges fly in a perfectly straight line.
        return 0.0F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!this.world.isRemote && this.posY > (double) (this.world.getHeight() + 30)) {
            burst(this.world, this.posX, this.posY, this.posZ,
                    BURST_RADIUS, KNOCKBACK_MULTIPLIER, true, this);
            this.setDead();
        }
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (this.world.isRemote) {
            return;
        }
        if (result.typeOfHit == RayTraceResult.Type.ENTITY && result.entityHit != null) {
            result.entityHit.attackEntityFrom(
                    DamageSource.causeThrownDamage(this, this.getThrower()), DIRECT_DAMAGE);
            burst(this.world, result.hitVec.x, result.hitVec.y, result.hitVec.z,
                    BURST_RADIUS, KNOCKBACK_MULTIPLIER, true, this);
        } else if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
            toggleBlock(result.getBlockPos());
            double x = result.hitVec.x;
            double y = result.hitVec.y;
            double z = result.hitVec.z;
            if (result.sideHit != null) {
                x += result.sideHit.getFrontOffsetX() * 0.25D;
                y += result.sideHit.getFrontOffsetY() * 0.25D;
                z += result.sideHit.getFrontOffsetZ() * 0.25D;
            }
            burst(this.world, x, y, z, BURST_RADIUS, KNOCKBACK_MULTIPLIER, true, this);
        } else {
            return;
        }
        this.setDead();
    }

    /**
     * Shared wind-burst explosion, matching the modern ServerExplosion shape:
     * knockback cutoff at 2x radius, feet-based distance, eye-based
     * direction, (1 - dist/diameter) falloff, block-density exposure, the
     * caller-supplied knockback multiplier, and knockback-resistance
     * reduction. The wind-burst enchant reuses this with its own radius and
     * per-level multiplier. fallRecord mirrors the modern ServerPlayer
     * onExplosionHit: only wind-charge bursts from the item protect players
     * below the burst height.
     */
    public static void burst(World world, double x, double y, double z,
            double radius, double multiplier, boolean recordFall, Entity exclude) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        ws.spawnParticle(EnumParticleTypes.CLOUD, x, y, z,
                30, 1.0D, 1.0D, 1.0D, 0.2D);
        ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z,
                12, 0.7D, 0.7D, 0.7D, 0.05D);
        ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z,
                6, 0.8D, 0.8D, 0.8D, 0.1D);
        ws.playSound(null, new BlockPos(x, y, z), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 0.6F, 1.4F);

        double diameter = radius * 2.0D;
        Vec3d center = new Vec3d(x, y, z);
        AxisAlignedBB box = new AxisAlignedBB(
                x - diameter, y - diameter, z - diameter,
                x + diameter, y + diameter, z + diameter);
        List<Entity> caught = world.getEntitiesWithinAABB(Entity.class, box);
        for (Entity target : caught) {
            if (target == exclude) {
                continue;
            }
            double dist = target.getDistance(x, y, z);
            if (dist > diameter) {
                continue;
            }
            double density = world.getBlockDensity(center, target.getEntityBoundingBox());
            double resist = 0.0D;
            if (target instanceof EntityLivingBase) {
                resist = ((EntityLivingBase) target).getEntityAttribute(
                        SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
            }
            double impact = (1.0D - dist / diameter) * density * multiplier
                    * Math.max(0.0D, 1.0D - resist);
            double dx = target.posX - x;
            double dy = target.posY + target.getEyeHeight() - y;
            double dz = target.posZ - z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.001D) {
                dx = 0.0D;
                dy = 1.0D;
                dz = 0.0D;
                len = 1.0D;
            }
            target.motionX += dx / len * impact;
            target.motionY += dy / len * impact;
            target.motionZ += dz / len * impact;
            target.velocityChanged = true;
            if (recordFall && target instanceof EntityPlayer) {
                CombatHandler.recordWindBurst((EntityPlayer) target, y);
            }
        }
    }

    private void toggleBlock(BlockPos pos) {
        IBlockState state = this.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BlockDoor) {
            // Modern bursts only move wooden doors; iron stays shut.
            if (block == Blocks.IRON_DOOR) {
                return;
            }
            boolean open = state.getValue(BlockDoor.OPEN);
            boolean lower = state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
            BlockPos other = lower ? pos.up() : pos.down();
            IBlockState otherState = this.world.getBlockState(other);
            this.world.setBlockState(pos, state.withProperty(BlockDoor.OPEN, !open), 3);
            if (otherState.getBlock() instanceof BlockDoor) {
                this.world.setBlockState(other,
                        otherState.withProperty(BlockDoor.OPEN, !open), 3);
            }
            this.world.playSound(null, pos,
                    !open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN
                            : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else if (block instanceof BlockTrapDoor) {
            // Modern bursts only move wooden trapdoors; iron stays shut.
            if (block == Blocks.IRON_TRAPDOOR) {
                return;
            }
            boolean open = state.getValue(BlockTrapDoor.OPEN);
            this.world.setBlockState(pos, state.withProperty(BlockTrapDoor.OPEN, !open), 3);
            this.world.playSound(null, pos,
                    !open ? SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN
                            : SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else if (block instanceof BlockFenceGate) {
            boolean open = state.getValue(BlockFenceGate.OPEN);
            this.world.setBlockState(pos, state.withProperty(BlockFenceGate.OPEN, !open), 3);
            this.world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK,
                    SoundCategory.BLOCKS, 0.6F, 1.2F);
        } else if (block instanceof BlockLever) {
            this.world.setBlockState(pos, state.cycleProperty(BlockLever.POWERED), 3);
            this.world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK,
                    SoundCategory.BLOCKS, 0.6F, 1.0F);
        } else if (block instanceof BlockButton) {
            if (!state.getValue(BlockButton.POWERED)) {
                this.world.setBlockState(pos, state.withProperty(BlockButton.POWERED, true), 3);
                this.world.scheduleUpdate(pos, block, block.tickRate(this.world));
                this.world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK,
                        SoundCategory.BLOCKS, 0.6F, 1.0F);
            }
        }
    }
}
