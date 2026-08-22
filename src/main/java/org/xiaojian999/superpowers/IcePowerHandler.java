package org.xiaojian999.superpowers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ice powers: ice beam, empowered snowballs that freeze terrain, and the Glacial Cataclysm ultimate. */
final class IcePowerHandler {
    private static final double ICE_BEAM_RANGE = 20.0D;
    private static final float ICE_BEAM_DAMAGE = 0.5F;
    private static final int ICE_SLOWNESS_DURATION = 100;
    private static final int ICE_SLOWNESS_AMPLIFIER = 3;
    private static final int ICE_BEAM_COOLDOWN = 40;
    private static final int ICE_SNOWBALL_COOLDOWN = 100;
    private static final int TEMPORARY_ICE_DURATION = 200;
    private static final double ULTIMATE_RADIUS = 12.0D;
    private static final float ULTIMATE_DAMAGE = 6.0F;
    private static final int ULTIMATE_SLOWNESS_DURATION = 160;
    private static final int ULTIMATE_SLOWNESS_AMPLIFIER = 4;
    private static final int ULTIMATE_FROZEN_TICKS = 160;

    private static final Set<UUID> ARMED_SNOWBALL_PLAYERS = new HashSet<>();
    private static final Set<UUID> EMPOWERED_SNOWBALLS = new HashSet<>();
    private static final Map<ServerWorld, List<TemporaryIceCube>> TEMPORARY_ICE_CUBES = new HashMap<>();
    private static final Map<ServerWorld, Map<BlockPos, BlockState>> ORIGINAL_ICE_STATES = new HashMap<>();
    private static final Map<ServerWorld, Map<BlockPos, Integer>> ICE_BLOCK_COUNTS = new HashMap<>();

    private IcePowerHandler() {
    }

    static int fireIceBeam(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.beamRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Ice beam", remainingTicks);
            return 0;
        }

        fireIceBeam(player);
        PowerCooldowns.setBeam(slotKey, ICE_BEAM_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        return 1;
    }

    static int primeIceSnowball(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Ice snowball", remainingTicks);
            return 0;
        }
        if (ARMED_SNOWBALL_PLAYERS.contains(player.getUuid())) {
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Your Ice snowball power is already primed."), true);
            return 0;
        }

        ARMED_SNOWBALL_PLAYERS.add(player.getUuid());
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Your next thrown snowball will create temporary ice."), true);
        return 1;
    }

    static void onSnowballLoaded(SnowballEntity snowball, ServerPlayerEntity owner) {
        UUID ownerUuid = owner.getUuid();
        Power primary = PowerManager.getEquippedPower(ownerUuid, 0);
        Power secondary = PowerManager.getEquippedPower(ownerUuid, 1);
        if ((primary != Power.ICE && secondary != Power.ICE) || !ARMED_SNOWBALL_PLAYERS.remove(ownerUuid)) {
            return;
        }

        EMPOWERED_SNOWBALLS.add(snowball.getUuid());
        int iceSlot = primary == Power.ICE ? 0 : 1;
        PowerCooldowns.setSecondPower(new SlotKey(ownerUuid, iceSlot), ICE_SNOWBALL_COOLDOWN);
        PowerManager.sendPowerStatus(owner);
    }

    static void handleSnowballCollision(SnowballEntity snowball, HitResult hitResult) {
        boolean empowered = EMPOWERED_SNOWBALLS.remove(snowball.getUuid());
        if (!empowered || !(snowball.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        spawnTemporaryIceCube(world, hitResult);
    }

    static void clearPrimedSnowball(UUID playerUuid) {
        ARMED_SNOWBALL_PLAYERS.remove(playerUuid);
    }

    static boolean isSnowballPrimed(UUID playerUuid) {
        return ARMED_SNOWBALL_PLAYERS.contains(playerUuid);
    }

    static void unleashGlacialCataclysm(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Box area = player.getBoundingBox().expand(ULTIMATE_RADIUS);
        List<Entity> targets = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !livingEntity.isSpectator()
        );

        for (Entity entity : targets) {
            if (player.squaredDistanceTo(entity) > ULTIMATE_RADIUS * ULTIMATE_RADIUS) {
                continue;
            }

            LivingEntity target = (LivingEntity) entity;
            target.damage(world, world.getDamageSources().playerAttack(player), ULTIMATE_DAMAGE);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    ULTIMATE_SLOWNESS_DURATION,
                    ULTIMATE_SLOWNESS_AMPLIFIER,
                    false,
                    true,
                    true
            ), player);
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), ULTIMATE_FROZEN_TICKS));

            Vec3d away = target.getEntityPos().subtract(player.getEntityPos());
            double distance = away.length();
            if (distance > 0.001D) {
                Vec3d knockback = away.multiply(1.4D / distance);
                target.addVelocity(knockback.x, 0.7D, knockback.z);
                target.velocityDirty = true;
            }
        }

        Vec3d center = player.getEntityPos().add(0.0D, 1.0D, 0.0D);
        world.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                center.x,
                center.y,
                center.z,
                250,
                ULTIMATE_RADIUS,
                3.0D,
                ULTIMATE_RADIUS,
                0.08D
        );
        world.spawnParticles(
                ParticleTypes.CLOUD,
                center.x,
                center.y,
                center.z,
                80,
                ULTIMATE_RADIUS * 0.7D,
                1.0D,
                ULTIMATE_RADIUS * 0.7D,
                0.12D
        );
        for (int angle = 0; angle < 360; angle += 12) {
            double radians = Math.toRadians(angle);
            double x = player.getX() + Math.cos(radians) * ULTIMATE_RADIUS;
            double z = player.getZ() + Math.sin(radians) * ULTIMATE_RADIUS;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, player.getY() + 0.2D, z, 5, 0.15D, 0.8D, 0.15D, 0.03D);
        }
        world.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS,
                2.0F,
                0.5F
        );
        player.sendMessage(Text.literal("Glacial Cataclysm unleashed!"), true);
    }

    private static void fireIceBeam(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(ICE_BEAM_RANGE));

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d beamEnd = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();

        Box searchBox = player.getBoundingBox().stretch(direction.multiply(ICE_BEAM_RANGE)).expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                maximumEnd,
                searchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                ICE_BEAM_RANGE * ICE_BEAM_RANGE
        );

        if (entityHit != null
                && (blockHit.getType() == HitResult.Type.MISS
                || entityHit.getPos().squaredDistanceTo(start) <= beamEnd.squaredDistanceTo(start))) {
            beamEnd = entityHit.getPos();
            Entity target = entityHit.getEntity();
            target.damage(world, world.getDamageSources().playerAttack(player), ICE_BEAM_DAMAGE);
            if (target instanceof LivingEntity livingTarget) {
                livingTarget.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS,
                        ICE_SLOWNESS_DURATION,
                        ICE_SLOWNESS_AMPLIFIER,
                        false,
                        true,
                        true
                ), player);
            }
        }

        spawnBeamParticles(world, start, beamEnd);
        world.playSound(
                null,
                beamEnd.x,
                beamEnd.y,
                beamEnd.z,
                SoundEvents.BLOCK_GLASS_HIT,
                SoundCategory.PLAYERS,
                0.7F,
                1.5F
        );
    }

    private static void spawnBeamParticles(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d beam = end.subtract(start);
        double length = beam.length();
        if (length == 0.0D) {
            return;
        }

        Vec3d direction = beam.normalize();
        for (double distance = 0.0D; distance <= length; distance += 0.35D) {
            Vec3d position = start.add(direction.multiply(distance));
            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    position.x,
                    position.y,
                    position.z,
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D
            );
            if ((int) (distance * 10.0D) % 10 == 0) {
                world.spawnParticles(
                        ParticleTypes.SNOWFLAKE,
                        position.x,
                        position.y,
                        position.z,
                        2,
                        0.04D,
                        0.04D,
                        0.04D,
                        0.0D
                );
            }
        }
        world.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                end.x,
                end.y,
                end.z,
                18,
                0.25D,
                0.25D,
                0.25D,
                0.04D
        );
    }

    private static void spawnTemporaryIceCube(ServerWorld world, HitResult hitResult) {
        BlockPos center;
        if (hitResult instanceof BlockHitResult blockHit) {
            center = blockHit.getBlockPos().offset(blockHit.getSide());
        } else {
            center = BlockPos.ofFloored(hitResult.getPos());
        }

        Set<BlockPos> positions = new HashSet<>();
        Map<BlockPos, BlockState> originalStates = ORIGINAL_ICE_STATES.computeIfAbsent(world, ignored -> new HashMap<>());
        Map<BlockPos, Integer> blockCounts = ICE_BLOCK_COUNTS.computeIfAbsent(world, ignored -> new HashMap<>());

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos position = center.add(x, y, z).toImmutable();
                    positions.add(position);
                    originalStates.putIfAbsent(position, world.getBlockState(position));
                    blockCounts.merge(position, 1, Integer::sum);
                    world.setBlockState(position, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        TEMPORARY_ICE_CUBES.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new TemporaryIceCube(positions, TEMPORARY_ICE_DURATION));
        world.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                center.getX() + 0.5D,
                center.getY() + 0.5D,
                center.getZ() + 0.5D,
                100,
                2.5D,
                2.5D,
                2.5D,
                0.08D
        );
        world.playSound(
                null,
                center,
                SoundEvents.BLOCK_GLASS_PLACE,
                SoundCategory.PLAYERS,
                1.2F,
                1.2F
        );
    }

    static void tick(ServerWorld world) {
        List<TemporaryIceCube> cubes = TEMPORARY_ICE_CUBES.get(world);
        if (cubes == null) {
            return;
        }

        for (int index = cubes.size() - 1; index >= 0; index--) {
            TemporaryIceCube cube = cubes.get(index);
            cube.remainingTicks--;
            if (cube.remainingTicks <= 0) {
                restoreIceCube(world, cube.positions);
                cubes.remove(index);
            }
        }

        if (cubes.isEmpty()) {
            TEMPORARY_ICE_CUBES.remove(world);
        }
    }

    private static void restoreIceCube(ServerWorld world, Set<BlockPos> positions) {
        Map<BlockPos, BlockState> originalStates = ORIGINAL_ICE_STATES.get(world);
        Map<BlockPos, Integer> blockCounts = ICE_BLOCK_COUNTS.get(world);
        if (originalStates == null || blockCounts == null) {
            return;
        }

        for (BlockPos position : positions) {
            int remainingCubes = blockCounts.getOrDefault(position, 0) - 1;
            if (remainingCubes > 0) {
                blockCounts.put(position, remainingCubes);
                continue;
            }

            BlockState originalState = originalStates.remove(position);
            blockCounts.remove(position);
            if (originalState != null && world.getBlockState(position).isOf(Blocks.ICE)) {
                world.setBlockState(position, originalState, Block.NOTIFY_ALL);
            }
        }

        if (originalStates.isEmpty()) {
            ORIGINAL_ICE_STATES.remove(world);
        }
        if (blockCounts.isEmpty()) {
            ICE_BLOCK_COUNTS.remove(world);
        }
    }

    static void removePlayer(UUID playerUuid) {
        ARMED_SNOWBALL_PLAYERS.remove(playerUuid);
    }

    static void clearAll() {
        ARMED_SNOWBALL_PLAYERS.clear();
        EMPOWERED_SNOWBALLS.clear();
        TEMPORARY_ICE_CUBES.clear();
        ORIGINAL_ICE_STATES.clear();
        ICE_BLOCK_COUNTS.clear();
    }

    private static final class TemporaryIceCube {
        private final Set<BlockPos> positions;
        private int remainingTicks;

        private TemporaryIceCube(Set<BlockPos> positions, int remainingTicks) {
            this.positions = positions;
            this.remainingTicks = remainingTicks;
        }
    }
}
