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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.enchantment.EnchantmentProtection;
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
 * Direct hits deal 1 damage; any impact sets off a 2.4-block wind burst that
 * knocks entities back (+22% over a matching raw explosion impulse, so aiming
 * at your own feet launches you ~6 blocks), zeroes the launched players' fall
 * distance and lets them land damage-free down to the burst height. Block
 * impacts also toggle nearby wooden doors/trapdoors, fence gates, levers and
 * buttons, exactly like the modern projectile interacting with redstone parts.
 */
public class EntityWindCharge extends EntityThrowable {
    private static final double BURST_RADIUS = 2.4D;
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
        if (this.world.isRemote) {
            if (!this.inGround) {
                this.world.spawnParticle(EnumParticleTypes.CLOUD,
                        this.posX, this.posY, this.posZ, 0.0D, 0.0D, 0.0D);
            }
        } else if (this.posY > (double) (this.world.getHeight() + 30)) {
            burst(this.posX, this.posY, this.posZ);
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
        } else if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
            toggleBlock(result.getBlockPos());
        }
        burst(result.hitVec.x, result.hitVec.y, result.hitVec.z);
        this.setDead();
    }

    private void burst(double x, double y, double z) {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) this.world;
        ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, x, y, z,
                2, 0.25D, 0.25D, 0.25D, 0.0D);
        ws.spawnParticle(EnumParticleTypes.CLOUD, x, y, z,
                24, 0.9D, 0.9D, 0.9D, 0.15D);
        ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z,
                10, 0.6D, 0.6D, 0.6D, 0.05D);
        ws.playSound(null, new BlockPos(x, y, z), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 0.6F, 1.4F);

        // Same impulse structure as a vanilla explosion — directional impulse
        // with (1 - distance/radius) falloff, block-density exposure and Blast
        // Protection reduction — over the modern 2.4-block sphere with the
        // modern 1.22x knockback multiplier. Shooting your own feet leaves a
        // near-maximum impulse pointing almost straight up: ~6-block launch.
        Vec3d center = new Vec3d(x, y, z);
        AxisAlignedBB box = new AxisAlignedBB(
                x - BURST_RADIUS, y - BURST_RADIUS, z - BURST_RADIUS,
                x + BURST_RADIUS, y + BURST_RADIUS, z + BURST_RADIUS);
        List<Entity> caught = this.world.getEntitiesWithinAABB(Entity.class, box);
        for (Entity target : caught) {
            if (target == this) {
                continue;
            }
            double dist = target.getDistance(x, y, z);
            if (dist > BURST_RADIUS) {
                continue;
            }
            double density = this.world.getBlockDensity(center, target.getEntityBoundingBox());
            double impact = (1.0D - dist / BURST_RADIUS) * density * KNOCKBACK_MULTIPLIER;
            if (target instanceof EntityLivingBase) {
                impact = EnchantmentProtection.getBlastDamageReduction(
                        (EntityLivingBase) target, impact);
            }
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
            if (target instanceof EntityPlayer) {
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
