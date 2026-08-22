package org.xiaojian999.superpowers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Water powers: the water cannon, the Tidal Wave, and the Water Meteor ultimate. */
final class WaterPowerHandler {
    private static final int WATER_CANNON_COOLDOWN = 300;
    private static final double WATER_CANNON_SPEED = 1.25D;
    private static final int WATER_CANNON_LIFETIME = 80;
    private static final double WATER_CANNON_PROJECTILE_SIZE = 1.4D;
    private static final double WATER_CANNON_BLAST_RADIUS = 5.0D;
    private static final float WATER_CANNON_DAMAGE = 18.0F;
    private static final int WATER_CANNON_CRATER_RADIUS = 4;
    private static final int WATER_CANNON_CRATER_DEPTH = 3;
    private static final int TIDAL_WAVE_COOLDOWN = 400;
    private static final double TIDAL_WAVE_SPEED = 0.8D;
    private static final double TIDAL_WAVE_RANGE = 24.0D;
    private static final int TIDAL_WAVE_DURATION = 30;
    private static final double TIDAL_WAVE_HALF_WIDTH = 4.5D;
    private static final double TIDAL_WAVE_HEIGHT = 5.0D;
    private static final float TIDAL_WAVE_DAMAGE = 18.0F;
    private static final double TIDAL_WAVE_KNOCKBACK = 3.0D;
    private static final int WATER_METEOR_COOLDOWN = 1200;
    private static final double WATER_METEOR_SPEED = 0.42D;
    private static final double WATER_METEOR_GRAVITY = 0.012D;
    private static final double WATER_METEOR_SEARCH_RADIUS = 12.0D;
    private static final double WATER_METEOR_SPAWN_HEIGHT = 24.0D;
    private static final int WATER_METEOR_LIFETIME = 180;
    private static final double WATER_METEOR_PROJECTILE_SIZE = 2.8D;
    private static final double WATER_METEOR_BLAST_RADIUS = 8.0D;
    private static final float WATER_METEOR_DAMAGE = 30.0F;
    private static final int WATER_METEOR_CRATER_RADIUS = 6;
    private static final int WATER_METEOR_CRATER_DEPTH = 4;

    private static final Map<ServerWorld, List<ActiveWaterProjectile>> ACTIVE_WATER_PROJECTILES = new HashMap<>();
    private static final Map<ServerWorld, List<ActiveTidalWave>> ACTIVE_TIDAL_WAVES = new HashMap<>();

    private WaterPowerHandler() {
    }

    static int fireWaterCannon(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.beamRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Water Cannon", remainingTicks);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d position = player.getCameraPosVec(1.0F).add(direction.multiply(1.2D));
        ACTIVE_WATER_PROJECTILES.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveWaterProjectile(
                        player.getUuid(),
                        position,
                        direction.multiply(WATER_CANNON_SPEED),
                        0.0D,
                        WATER_CANNON_LIFETIME,
                        WATER_CANNON_PROJECTILE_SIZE,
                        false
                ));
        PowerCooldowns.setBeam(slotKey, WATER_CANNON_COOLDOWN);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 1.4F, 0.7F);
        player.sendMessage(Text.literal("Water Cannon fired!"), true);
        PowerManager.sendPowerStatus(player);
        return 1;
    }

    static int startTidalWave(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Tidal Wave", remainingTicks);
            return 0;
        }
        if (!isNearOcean(player)) {
            player.sendMessage(Text.literal("Tidal Wave requires an ocean within 8 blocks."), true);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d direction = new Vec3d(look.x, 0.0D, look.z);
        if (direction.lengthSquared() < 0.0001D) {
            Direction facing = player.getHorizontalFacing();
            direction = new Vec3d(facing.getOffsetX(), 0.0D, facing.getOffsetZ());
        }
        direction = direction.normalize();
        Vec3d position = player.getEntityPos().add(direction.multiply(2.0D));
        ACTIVE_TIDAL_WAVES.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveTidalWave(player.getUuid(), position, direction, TIDAL_WAVE_DURATION));
        PowerCooldowns.setSecondPower(slotKey, TIDAL_WAVE_COOLDOWN);
        world.playSound(null, position.x, position.y, position.z, SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 2.0F, 0.55F);
        player.sendMessage(Text.literal("Tidal Wave unleashed!"), true);
        PowerManager.sendPowerStatus(player);
        return 1;
    }

    static void startWaterMeteor(ServerPlayerEntity player, SlotKey slotKey) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d target = findNearestEnemyTarget(player, world);
        if (target == null) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(player.getRandom().nextDouble()) * WATER_METEOR_SEARCH_RADIUS;
            target = new Vec3d(
                    player.getX() + Math.cos(angle) * distance,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * distance
            );
        }
        double spawnY = target.y + WATER_METEOR_SPAWN_HEIGHT;
        Vec3d position = new Vec3d(target.x, spawnY, target.z);
        ACTIVE_WATER_PROJECTILES.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveWaterProjectile(
                        player.getUuid(),
                        position,
                        new Vec3d(0.0D, -WATER_METEOR_SPEED, 0.0D),
                        WATER_METEOR_GRAVITY,
                        WATER_METEOR_LIFETIME,
                        WATER_METEOR_PROJECTILE_SIZE,
                        true
                ));
        PowerCooldowns.setUltimate(slotKey, WATER_METEOR_COOLDOWN);
        world.playSound(null, target.x, spawnY, target.z, SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 2.0F, 0.45F);
        player.sendMessage(Text.literal("Water Meteor summoned — brace for impact!"), true);
    }

    private static boolean isNearOcean(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos origin = player.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) {
                    continue;
                }
                if (world.getBiome(origin.add(dx, 0, dz)).isIn(BiomeTags.IS_OCEAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Vec3d findNearestEnemyTarget(ServerPlayerEntity player, ServerWorld world) {
        Box searchBox = player.getBoundingBox().expand(WATER_METEOR_SEARCH_RADIUS);
        List<Entity> candidates = world.getOtherEntities(
                player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        Entity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double distanceSquared = player.squaredDistanceTo(candidate);
            if (distanceSquared > WATER_METEOR_SEARCH_RADIUS * WATER_METEOR_SEARCH_RADIUS) {
                continue;
            }
            if (distanceSquared < nearestDistanceSquared) {
                nearest = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest == null ? null : nearest.getEntityPos();
    }

    static void tick(ServerWorld world) {
        tickWaterProjectiles(world);
        tickTidalWaves(world);
    }

    private static void tickWaterProjectiles(ServerWorld world) {
        List<ActiveWaterProjectile> projectiles = ACTIVE_WATER_PROJECTILES.get(world);
        if (projectiles == null) {
            return;
        }

        for (int index = projectiles.size() - 1; index >= 0; index--) {
            ActiveWaterProjectile projectile = projectiles.get(index);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(projectile.ownerUuid);
            if (owner == null || owner.getEntityWorld() != world) {
                projectiles.remove(index);
                continue;
            }

            projectile.remainingTicks--;
            Vec3d nextPosition = projectile.position.add(projectile.velocity);
            BlockHitResult blockHit = world.raycast(new RaycastContext(
                    projectile.position,
                    nextPosition,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    owner
            ));
            spawnWaterProjectileParticles(world, projectile);

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                impactWaterProjectile(world, projectile, blockHit.getPos(), owner);
                projectiles.remove(index);
                continue;
            }

            projectile.position = nextPosition;
            projectile.velocity = projectile.velocity.add(0.0D, -projectile.gravity, 0.0D);
            if (projectile.remainingTicks <= 0) {
                projectiles.remove(index);
            }
        }

        if (projectiles.isEmpty()) {
            ACTIVE_WATER_PROJECTILES.remove(world);
        }
    }

    private static void spawnWaterProjectileParticles(ServerWorld world, ActiveWaterProjectile projectile) {
        Vec3d position = projectile.position;
        int count = projectile.meteor ? 18 : 8;
        world.spawnParticles(ParticleTypes.SPLASH, position.x, position.y, position.z, count,
                projectile.size, projectile.size, projectile.size, 0.08D);
        world.spawnParticles(ParticleTypes.BUBBLE, position.x, position.y, position.z, projectile.meteor ? 8 : 3,
                projectile.size * 0.7D, projectile.size * 0.7D, projectile.size * 0.7D, 0.04D);
    }

    private static void impactWaterProjectile(
            ServerWorld world,
            ActiveWaterProjectile projectile,
            Vec3d impactPosition,
            ServerPlayerEntity owner
    ) {
        double blastRadius = projectile.meteor ? WATER_METEOR_BLAST_RADIUS : WATER_CANNON_BLAST_RADIUS;
        float damage = projectile.meteor ? WATER_METEOR_DAMAGE : WATER_CANNON_DAMAGE;
        Box area = new Box(
                impactPosition.x - blastRadius,
                impactPosition.y - blastRadius,
                impactPosition.z - blastRadius,
                impactPosition.x + blastRadius,
                impactPosition.y + blastRadius,
                impactPosition.z + blastRadius
        );
        List<Entity> targets = world.getOtherEntities(owner, area, entity -> entity instanceof LivingEntity livingEntity
                && livingEntity.isAlive() && !entity.isSpectator());
        for (Entity entity : targets) {
            if (entity.squaredDistanceTo(impactPosition) > blastRadius * blastRadius) {
                continue;
            }
            LivingEntity target = (LivingEntity) entity;
            target.damage(world, world.getDamageSources().playerAttack(owner), damage);
            Vec3d away = target.getEntityPos().subtract(impactPosition);
            double distance = away.length();
            if (distance > 0.001D) {
                double force = projectile.meteor ? 2.4D : 1.2D;
                Vec3d knockback = away.multiply(force / distance);
                target.addVelocity(knockback.x, projectile.meteor ? 1.15D : 0.65D, knockback.z);
                target.velocityDirty = true;
            }
        }

        BlockPos center = BlockPos.ofFloored(impactPosition);
        if (projectile.meteor) {
            carveExplosionCrater(world, center, WATER_METEOR_CRATER_RADIUS, WATER_METEOR_CRATER_DEPTH, owner);
        } else {
            createWaterCrater(world, center, WATER_CANNON_CRATER_RADIUS, WATER_CANNON_CRATER_DEPTH, owner);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impactPosition.x, impactPosition.y, impactPosition.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.SPLASH, impactPosition.x, impactPosition.y, impactPosition.z, projectile.meteor ? 180 : 100,
                blastRadius * 0.6D, blastRadius * 0.35D, blastRadius * 0.6D, 0.15D);
        world.playSound(null, impactPosition.x, impactPosition.y, impactPosition.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.2F, projectile.meteor ? 0.45F : 0.7F);
    }

    private static void carveExplosionCrater(
            ServerWorld world,
            BlockPos center,
            int radius,
            int depth,
            ServerPlayerEntity owner
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double horizontalDistance = Math.sqrt(x * x + z * z);
                if (horizontalDistance > radius) {
                    continue;
                }
                int columnDepth = Math.max(1, (int) Math.ceil((1.0D - horizontalDistance / radius) * depth));
                for (int y = -columnDepth; y <= 2; y++) {
                    BlockPos position = center.add(x, y, z);
                    if (isBreakableCraterBlock(world.getBlockState(position))) {
                        world.breakBlock(position, false, owner);
                    }
                }
            }
        }
    }

    private static void createWaterCrater(
            ServerWorld world,
            BlockPos center,
            int radius,
            int depth,
            ServerPlayerEntity owner
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double horizontalDistance = Math.sqrt(x * x + z * z);
                if (horizontalDistance > radius) {
                    continue;
                }
                int columnDepth = Math.max(1, (int) Math.ceil((1.0D - horizontalDistance / radius) * depth));
                for (int y = -columnDepth; y <= 1; y++) {
                    BlockPos position = center.add(x, y, z);
                    if (isBreakableCraterBlock(world.getBlockState(position))) {
                        world.breakBlock(position, false, owner);
                    }
                }
                for (int y = -columnDepth; y <= 1; y++) {
                    BlockPos position = center.add(x, y, z);
                    if (world.getBlockState(position).isAir()) {
                        world.setBlockState(position, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    private static boolean isBreakableCraterBlock(BlockState state) {
        return !state.isAir()
                && !state.isOf(Blocks.BEDROCK)
                && !state.isOf(Blocks.BARRIER)
                && !state.isOf(Blocks.END_PORTAL)
                && !state.isOf(Blocks.END_GATEWAY);
    }

    private static void tickTidalWaves(ServerWorld world) {
        List<ActiveTidalWave> waves = ACTIVE_TIDAL_WAVES.get(world);
        if (waves == null) {
            return;
        }

        for (int index = waves.size() - 1; index >= 0; index--) {
            ActiveTidalWave wave = waves.get(index);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(wave.ownerUuid);
            if (owner == null || owner.getEntityWorld() != world) {
                waves.remove(index);
                continue;
            }

            wave.remainingTicks--;
            wave.position = wave.position.add(wave.direction.multiply(TIDAL_WAVE_SPEED));
            Vec3d side = new Vec3d(-wave.direction.z, 0.0D, wave.direction.x);
            Box area = new Box(
                    wave.position.x - TIDAL_WAVE_HALF_WIDTH,
                    wave.position.y - 2.0D,
                    wave.position.z - TIDAL_WAVE_HALF_WIDTH,
                    wave.position.x + TIDAL_WAVE_HALF_WIDTH,
                    wave.position.y + TIDAL_WAVE_HEIGHT,
                    wave.position.z + TIDAL_WAVE_HALF_WIDTH
            );
            List<Entity> targets = world.getOtherEntities(owner, area, entity -> entity instanceof LivingEntity livingEntity
                    && livingEntity.isAlive() && !entity.isSpectator());
            for (Entity entity : targets) {
                if (!wave.hitEntities.add(entity.getUuid())) {
                    continue;
                }
                LivingEntity target = (LivingEntity) entity;
                target.damage(world, world.getDamageSources().playerAttack(owner), TIDAL_WAVE_DAMAGE);
                target.addVelocity(
                        wave.direction.x * TIDAL_WAVE_KNOCKBACK,
                        0.9D,
                        wave.direction.z * TIDAL_WAVE_KNOCKBACK
                );
                target.velocityDirty = true;
            }

            for (double lateral = -TIDAL_WAVE_HALF_WIDTH; lateral <= TIDAL_WAVE_HALF_WIDTH; lateral += 1.0D) {
                for (double vertical = 0.0D; vertical <= TIDAL_WAVE_HEIGHT; vertical += 1.0D) {
                    Vec3d particlePosition = wave.position.add(side.multiply(lateral)).add(0.0D, vertical, 0.0D);
                    world.spawnParticles(ParticleTypes.SPLASH, particlePosition.x, particlePosition.y, particlePosition.z,
                            4, 0.35D, 0.35D, 0.35D, 0.08D);
                    if ((int) vertical % 2 == 0) {
                        world.spawnParticles(ParticleTypes.BUBBLE, particlePosition.x, particlePosition.y, particlePosition.z,
                                1, 0.2D, 0.2D, 0.2D, 0.02D);
                    }
                }
            }

            if (wave.remainingTicks <= 0 || wave.position.distanceTo(owner.getEntityPos()) > TIDAL_WAVE_RANGE + 3.0D) {
                waves.remove(index);
            }
        }

        if (waves.isEmpty()) {
            ACTIVE_TIDAL_WAVES.remove(world);
        }
    }

    static void clearAll() {
        ACTIVE_WATER_PROJECTILES.clear();
        ACTIVE_TIDAL_WAVES.clear();
    }

    private static final class ActiveWaterProjectile {
        private final UUID ownerUuid;
        private final double gravity;
        private final double size;
        private final boolean meteor;
        private Vec3d position;
        private Vec3d velocity;
        private int remainingTicks;

        private ActiveWaterProjectile(
                UUID ownerUuid,
                Vec3d position,
                Vec3d velocity,
                double gravity,
                int remainingTicks,
                double size,
                boolean meteor
        ) {
            this.ownerUuid = ownerUuid;
            this.position = position;
            this.velocity = velocity;
            this.gravity = gravity;
            this.remainingTicks = remainingTicks;
            this.size = size;
            this.meteor = meteor;
        }
    }

    private static final class ActiveTidalWave {
        private final UUID ownerUuid;
        private final Vec3d direction;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vec3d position;
        private int remainingTicks;

        private ActiveTidalWave(UUID ownerUuid, Vec3d position, Vec3d direction, int durationTicks) {
            this.ownerUuid = ownerUuid;
            this.position = position;
            this.direction = direction;
            this.remainingTicks = durationTicks;
        }
    }
}
