package org.xiaojian999.superpowers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fire powers: the flamethrower beam, fire immunity, and the Ring of Fire ultimate. */
final class FirePowerHandler {
    private static final int FIRE_BEAM_DURATION = 100;
    private static final double FIRE_BEAM_RANGE = 10.0D;
    private static final double FIRE_BEAM_WIDTH = 1.2D;
    private static final int FIRE_BEAM_IGNITE_SECONDS = 5;
    private static final int FIRE_BEAM_MAX_IGNITIONS = 12;
    private static final int FIRE_BEAM_COOLDOWN = 100;
    private static final int FIRE_RESISTANCE_REFRESH_TICKS = 100;
    private static final int RING_OF_FIRE_DURATION = 100;
    private static final double RING_OF_FIRE_RADIUS = 9.0D;
    private static final double RING_OF_FIRE_SPEED = 0.3D;
    private static final double RING_OF_FIRE_THICKNESS = 1.5D;
    private static final double RING_OF_FIRE_HEIGHT = 2.5D;
    private static final float RING_OF_FIRE_DAMAGE = 24.0F;
    private static final int RING_OF_FIRE_IGNITE_SECONDS = 6;

    private static final Set<UUID> FIRE_IMMUNE_PLAYERS = new HashSet<>();
    private static final Map<SlotKey, Integer> FIRE_BEAM_TICKS = new HashMap<>();
    private static final Map<ServerWorld, List<ActiveFireRing>> ACTIVE_FIRE_RINGS = new HashMap<>();

    private FirePowerHandler() {
    }

    static int fireFlamethrower(ServerPlayerEntity player, SlotKey slotKey) {
        if (FIRE_BEAM_TICKS.containsKey(slotKey)) {
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Your flamethrower is already burning."), true);
            return 0;
        }

        int remainingTicks = PowerCooldowns.beamRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Flamethrower", remainingTicks);
            return 0;
        }

        FIRE_BEAM_TICKS.put(slotKey, FIRE_BEAM_DURATION);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        world.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                1.2F,
                0.8F
        );
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Flamethrower ignited — 5 seconds of fire!"), true);
        return 1;
    }

    static int toggleFireImmunity(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (FIRE_IMMUNE_PLAYERS.contains(playerUuid)) {
            FIRE_IMMUNE_PLAYERS.remove(playerUuid);
            player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Fire immunity disabled."), true);
            return 1;
        }

        FIRE_IMMUNE_PLAYERS.add(playerUuid);
        player.setFireTicks(0);
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE,
                FIRE_RESISTANCE_REFRESH_TICKS,
                0,
                false,
                false,
                false
        ));
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Fire immunity enabled — you are immune to fire and lava."), true);
        return 1;
    }

    static void clearFireState(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        FIRE_IMMUNE_PLAYERS.remove(playerUuid);
        removeAll(FIRE_BEAM_TICKS, playerUuid);
        player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    static boolean isImmune(UUID playerUuid) {
        return FIRE_IMMUNE_PLAYERS.contains(playerUuid);
    }

    static boolean isBeamActive(SlotKey slotKey) {
        return FIRE_BEAM_TICKS.containsKey(slotKey);
    }

    static Integer getActiveBeamTicks(SlotKey slotKey) {
        return FIRE_BEAM_TICKS.get(slotKey);
    }

    static void startRingOfFire(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d direction = new Vec3d(look.x, 0.0D, look.z);
        if (direction.lengthSquared() < 0.0001D) {
            Direction facing = player.getHorizontalFacing();
            direction = new Vec3d(facing.getOffsetX(), 0.0D, facing.getOffsetZ());
        }
        direction = direction.normalize();
        Vec3d center = player.getEyePos().add(direction.multiply(2.5D));

        ACTIVE_FIRE_RINGS.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveFireRing(player.getUuid(), direction, center, RING_OF_FIRE_RADIUS, RING_OF_FIRE_DURATION));

        world.spawnParticles(
                ParticleTypes.LAVA,
                center.x,
                center.y,
                center.z,
                80,
                RING_OF_FIRE_RADIUS,
                1.5D,
                RING_OF_FIRE_RADIUS,
                0.05D
        );
        world.spawnParticles(
                ParticleTypes.FLAME,
                center.x,
                center.y,
                center.z,
                120,
                RING_OF_FIRE_RADIUS,
                2.0D,
                RING_OF_FIRE_RADIUS,
                0.1D
        );
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                2.0F,
                0.6F
        );
        player.sendMessage(Text.literal("The Ring of Fire has been unleashed!"), true);
    }

    static void tickPlayer(ServerPlayerEntity player) {
        if (FIRE_IMMUNE_PLAYERS.contains(player.getUuid())
                && !player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE,
                    FIRE_RESISTANCE_REFRESH_TICKS,
                    0,
                    false,
                    false,
                    false
            ));
        }
    }

    static void tickServer(MinecraftServer server) {
        FIRE_BEAM_TICKS.entrySet().removeIf(entry -> {
            SlotKey slotKey = entry.getKey();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(slotKey.playerUuid());
            if (player == null) {
                return true;
            }

            tickFireBeam(player);
            int next = entry.getValue() - 1;
            if (next <= 0) {
                PowerCooldowns.setBeam(slotKey, FIRE_BEAM_COOLDOWN);
                PowerManager.sendPowerStatus(player);
                return true;
            }
            entry.setValue(next);
            return false;
        });
    }

    private static void tickFireBeam(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(FIRE_BEAM_RANGE));

        spawnFireBeamParticles(world, start, maximumEnd);
        igniteBlocksAlongBeam(world, start, maximumEnd, player);

        Box searchBox = player.getBoundingBox()
                .stretch(direction.multiply(FIRE_BEAM_RANGE))
                .expand(FIRE_BEAM_WIDTH);
        List<Entity> targets = world.getOtherEntities(
                player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        for (Entity target : targets) {
            Vec3d toTarget = target.getEntityPos().subtract(start);
            double along = toTarget.dotProduct(direction);
            if (along < 0.0D || along > FIRE_BEAM_RANGE) {
                continue;
            }
            Vec3d closest = start.add(direction.multiply(along));
            if (target.getEntityPos().squaredDistanceTo(closest) <= FIRE_BEAM_WIDTH * FIRE_BEAM_WIDTH) {
                ((LivingEntity) target).setOnFireFor(FIRE_BEAM_IGNITE_SECONDS);
            }
        }
    }

    private static void spawnFireBeamParticles(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d beam = end.subtract(start);
        double length = beam.length();
        if (length == 0.0D) {
            return;
        }

        Vec3d direction = beam.normalize();
        for (double distance = 0.0D; distance <= length; distance += 0.4D) {
            Vec3d position = start.add(direction.multiply(distance));
            world.spawnParticles(
                    ParticleTypes.FLAME,
                    position.x,
                    position.y,
                    position.z,
                    2,
                    0.06D,
                    0.06D,
                    0.06D,
                    0.015D
            );
            if ((int) (distance * 5.0D) % 10 == 0) {
                world.spawnParticles(
                        ParticleTypes.LAVA,
                        position.x,
                        position.y,
                        position.z,
                        1,
                        0.05D,
                        0.05D,
                        0.05D,
                        0.0D
                );
            }
        }
        world.spawnParticles(
                ParticleTypes.FLAME,
                end.x,
                end.y,
                end.z,
                10,
                0.25D,
                0.25D,
                0.25D,
                0.05D
        );
    }

    private static void igniteBlocksAlongBeam(
            ServerWorld world,
            Vec3d start,
            Vec3d end,
            ServerPlayerEntity player
    ) {
        Vec3d beam = end.subtract(start);
        double length = beam.length();
        if (length == 0.0D) {
            return;
        }

        Vec3d direction = beam.normalize();
        BlockPos playerPos = player.getBlockPos();
        int ignited = 0;
        for (double distance = 0.0D; distance <= length && ignited < FIRE_BEAM_MAX_IGNITIONS; distance += 0.5D) {
            BlockPos center = BlockPos.ofFloored(start.add(direction.multiply(distance)));
            for (int dx = -1; dx <= 1 && ignited < FIRE_BEAM_MAX_IGNITIONS; dx++) {
                for (int dy = -1; dy <= 1 && ignited < FIRE_BEAM_MAX_IGNITIONS; dy++) {
                    for (int dz = -1; dz <= 1 && ignited < FIRE_BEAM_MAX_IGNITIONS; dz++) {
                        BlockPos position = center.add(dx, dy, dz);
                        if (isNearPlayer(position, playerPos)) {
                            continue;
                        }
                        if (tryIgniteBlock(world, position)) {
                            ignited++;
                        }
                    }
                }
            }
        }
    }

    private static boolean isNearPlayer(BlockPos position, BlockPos playerPos) {
        return Math.abs(position.getX() - playerPos.getX()) <= 1
                && Math.abs(position.getY() - playerPos.getY()) <= 1
                && Math.abs(position.getZ() - playerPos.getZ()) <= 1;
    }

    private static boolean tryIgniteBlock(ServerWorld world, BlockPos position) {
        BlockState state = world.getBlockState(position);
        if (!state.isAir() || !Blocks.FIRE.getDefaultState().canPlaceAt(world, position)) {
            return false;
        }
        world.setBlockState(position, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
        return true;
    }

    static void tick(ServerWorld world) {
        List<ActiveFireRing> rings = ACTIVE_FIRE_RINGS.get(world);
        if (rings == null) {
            return;
        }

        for (int index = rings.size() - 1; index >= 0; index--) {
            ActiveFireRing ring = rings.get(index);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ring.ownerUuid);
            if (owner == null || owner.getEntityWorld() != world) {
                rings.remove(index);
                continue;
            }

            ring.remainingTicks--;
            ring.elapsedTicks++;
            ring.position = ring.position.add(ring.direction.multiply(RING_OF_FIRE_SPEED));

            Box area = new Box(
                    ring.position.x - ring.radius - 2.0D,
                    ring.position.y - RING_OF_FIRE_HEIGHT,
                    ring.position.z - ring.radius - 2.0D,
                    ring.position.x + ring.radius + 2.0D,
                    ring.position.y + RING_OF_FIRE_HEIGHT,
                    ring.position.z + ring.radius + 2.0D
            );
            List<Entity> targets = world.getOtherEntities(
                    owner,
                    area,
                    entity -> entity instanceof LivingEntity livingEntity
                            && livingEntity.isAlive()
                            && !entity.isSpectator()
            );
            for (Entity entity : targets) {
                Vec3d entityPos = entity.getEntityPos();
                double horizontalDistance = Math.sqrt(
                        (entityPos.x - ring.position.x) * (entityPos.x - ring.position.x)
                                + (entityPos.z - ring.position.z) * (entityPos.z - ring.position.z)
                );
                if (Math.abs(horizontalDistance - ring.radius) <= RING_OF_FIRE_THICKNESS
                        && Math.abs(entityPos.y - ring.position.y) <= RING_OF_FIRE_HEIGHT) {
                    LivingEntity livingTarget = (LivingEntity) entity;
                    livingTarget.setOnFireFor(RING_OF_FIRE_IGNITE_SECONDS);
                    livingTarget.damage(world, world.getDamageSources().playerAttack(owner), RING_OF_FIRE_DAMAGE);
                }
            }

            Set<BlockPos> positionsToIgnite = new HashSet<>();
            int samples = 36;
            for (int sample = 0; sample < samples; sample++) {
                double angle = Math.toRadians(sample * 10.0D) + ring.elapsedTicks * 0.18D;
                double x = ring.position.x + Math.cos(angle) * ring.radius;
                double z = ring.position.z + Math.sin(angle) * ring.radius;
                double y = ring.position.y;
                world.spawnParticles(ParticleTypes.FLAME, x, y, z, 2, 0.18D, 0.12D, 0.18D, 0.02D);
                if (sample % 4 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, x, y + 0.3D, z, 1, 0.06D, 0.06D, 0.06D, 0.01D);
                }
                if (sample % 3 == 0) {
                    world.spawnParticles(ParticleTypes.FLAME, x, y + 1.2D, z, 1, 0.1D, 0.35D, 0.1D, 0.03D);
                }

                BlockPos center = BlockPos.ofFloored(x, y, z);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            positionsToIgnite.add(center.add(dx, dy, dz));
                        }
                    }
                }
            }
            BlockPos ownerPos = owner.getBlockPos();
            for (BlockPos position : positionsToIgnite) {
                if (isNearPlayer(position, ownerPos)) {
                    continue;
                }
                tryIgniteBlock(world, position);
            }

            if (ring.remainingTicks <= 0) {
                rings.remove(index);
            }
        }

        if (rings.isEmpty()) {
            ACTIVE_FIRE_RINGS.remove(world);
        }
    }

    static void removePlayer(UUID playerUuid) {
        FIRE_IMMUNE_PLAYERS.remove(playerUuid);
        removeAll(FIRE_BEAM_TICKS, playerUuid);
    }

    static void clearAll() {
        FIRE_IMMUNE_PLAYERS.clear();
        FIRE_BEAM_TICKS.clear();
        ACTIVE_FIRE_RINGS.clear();
    }

    private static <V> void removeAll(Map<SlotKey, V> map, UUID playerUuid) {
        map.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
    }

    private static final class ActiveFireRing {
        private final UUID ownerUuid;
        private final Vec3d direction;
        private final double radius;
        private Vec3d position;
        private int remainingTicks;
        private int elapsedTicks;

        private ActiveFireRing(UUID ownerUuid, Vec3d direction, Vec3d position, double radius, int durationTicks) {
            this.ownerUuid = ownerUuid;
            this.direction = direction;
            this.position = position;
            this.radius = radius;
            this.remainingTicks = durationTicks;
        }
    }
}
