package org.xiaojian999.superpowers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.xiaojian999.superpowers.math.GlungFastMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nature powers: the Flower Trail toggle, the spinning Vine Ring, and the
 * Earthquake of Lucifer ultimate.
 */
final class NaturePowerHandler {
    // ----- Flower trail -----
    // One flower every 0.25 seconds (5 ticks), and never on a block that already
    // holds a flower (the trail never stacks).
    private static final int FLOWER_PLACEMENT_INTERVAL = 5;
    private static final int FLOWER_LIFETIME_TICKS = 200; // 10 seconds
    private static final double FLOWER_DAMAGE_HORIZONTAL = 0.9D;
    private static final double FLOWER_DAMAGE_VERTICAL = 1.4D;
    private static final float FLOWER_TRAIL_DAMAGE = 4.0F; // 2 hearts
    private static final int FLOWER_TRAIL_DAMAGE_INTERVAL = 20; // once per second
    private static final Block[] TRAIL_FLOWERS = {
            Blocks.POPPY,
            Blocks.DANDELION,
            Blocks.ALLIUM,
            Blocks.AZURE_BLUET,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER,
            Blocks.BLUE_ORCHID,
            Blocks.LILY_OF_THE_VALLEY
    };

    // ----- Vine ring (second power) -----
    private static final int VINE_RING_DURATION = 600; // 30 seconds
    private static final int VINE_RING_COOLDOWN = 600; // 30 seconds after it ends
    private static final double VINE_RING_RADIUS = 1.7D;
    private static final double VINE_RING_THICKNESS = 0.9D;
    private static final double VINE_RING_VERTICAL_HALF = 0.95D;
    private static final float VINE_RING_DAMAGE = 5.0F; // 2.5 hearts per hit
    private static final int VINE_RING_HIT_COOLDOWN = 12;
    private static final double VINE_RING_PUSH = 1.6D;

    // ----- Earthquake of Lucifer (ultimate) -----
    private static final int EARTHQUAKE_DURATION = 1200; // 60 seconds
    private static final int EARTHQUAKE_COOLDOWN = 3000; // 150 seconds — the longest ultimate cooldown
    private static final double EARTHQUAKE_RADIUS = 24.0D;
    private static final int EARTHQUAKE_DAMAGE_INTERVAL = 25;
    private static final int EARTHQUAKE_JOLT_INTERVAL = 8;
    private static final int EARTHQUAKE_TOSS_INTERVAL = 32;
    private static final int EARTHQUAKE_IGNITION_COUNT = 90;

    private static final Set<UUID> FLOWER_TRAIL_PLAYERS = new HashSet<>();
    private static final Map<ServerWorld, List<ActiveFlower>> ACTIVE_FLOWERS = new HashMap<>();
    private static final Map<ServerWorld, Set<BlockPos>> FLOWER_POSITIONS = new HashMap<>();
    private static final Map<UUID, Integer> TRAIL_DAMAGE_COOLDOWNS = new HashMap<>();

    private static final Map<SlotKey, Integer> VINE_RING_TICKS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> VINE_RING_HIT_COOLDOWNS = new HashMap<>();

    private static final Map<UUID, ActiveEarthquake> ACTIVE_EARTHQUAKES = new HashMap<>();

    private NaturePowerHandler() {
    }

    // ----- Flower trail -----

    static int toggleFlowerTrail(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (FLOWER_TRAIL_PLAYERS.contains(playerUuid)) {
            FLOWER_TRAIL_PLAYERS.remove(playerUuid);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Flower trail disabled — your flowers will fade away."), true);
            return 1;
        }

        FLOWER_TRAIL_PLAYERS.add(playerUuid);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d center = player.getEntityPos();
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 1.0D, center.z, 24, 0.8D, 1.0D, 0.8D, 0.08D);
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ENTITY_BEE_POLLINATE,
                SoundCategory.PLAYERS,
                1.2F,
                1.0F
        );
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Flower trail enabled — walk and flowers bloom beneath you."), true);
        return 1;
    }

    static boolean isFlowerTrailActive(UUID playerUuid) {
        return FLOWER_TRAIL_PLAYERS.contains(playerUuid);
    }

    private static void placeTrailFlower(ServerWorld world, ServerPlayerEntity player) {
        if (!player.isOnGround()) {
            return;
        }
        // The flower goes into the air block the player's feet are standing in, just
        // above the ground block that supports it — never into the ground itself. The
        // small upward epsilon keeps float drift from flooring onto the block below.
        BlockPos position = BlockPos.ofFloored(player.getX(), player.getY() + 0.1D, player.getZ());
        Set<BlockPos> positions = FLOWER_POSITIONS.computeIfAbsent(world, ignored -> new HashSet<>());
        // Skip when this exact spot already has a trail flower (tracked here) or any
        // other block — a placed flower is never overwritten by a new one.
        if (positions.contains(position) || !world.getBlockState(position).isAir()) {
            return;
        }

        BlockState flower = TRAIL_FLOWERS[world.random.nextInt(TRAIL_FLOWERS.length)].getDefaultState();
        if (!flower.canPlaceAt(world, position)) {
            return;
        }

        world.setBlockState(position, flower, Block.NOTIFY_ALL);
        ACTIVE_FLOWERS.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveFlower(player.getUuid(), position, flower, FLOWER_LIFETIME_TICKS));
        positions.add(position);

        double x = position.getX() + 0.5D;
        double z = position.getZ() + 0.5D;
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, position.getY() + 1.0D, z, 3, 0.3D, 0.2D, 0.3D, 0.02D);
        world.spawnParticles(ParticleTypes.COMPOSTER, x, position.getY() + 0.9D, z, 2, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    private static void tickFlowers(ServerWorld world) {
        List<ActiveFlower> flowers = ACTIVE_FLOWERS.get(world);
        if (flowers == null || flowers.isEmpty()) {
            return;
        }

        for (int index = flowers.size() - 1; index >= 0; index--) {
            ActiveFlower flower = flowers.get(index);
            flower.remainingTicks--;
            if (flower.remainingTicks <= 0) {
                removeFlower(world, flower);
                flowers.remove(index);
            }
        }

        if (!flowers.isEmpty()) {
            damageMobsOnTrail(world, flowers);
        }
        if (flowers.isEmpty()) {
            ACTIVE_FLOWERS.remove(world);
        }
    }

    private static void removeFlower(ServerWorld world, ActiveFlower flower) {
        if (world.getBlockState(flower.position) == flower.placedState) {
            world.setBlockState(flower.position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            double x = flower.position.getX() + 0.5D;
            double z = flower.position.getZ() + 0.5D;
            world.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    x,
                    flower.position.getY() + 1.0D,
                    z,
                    4,
                    0.3D,
                    0.2D,
                    0.3D,
                    0.03D
            );
        }
        Set<BlockPos> positions = FLOWER_POSITIONS.get(world);
        if (positions != null) {
            positions.remove(flower.position);
        }
    }

    private static void damageMobsOnTrail(ServerWorld world, List<ActiveFlower> flowers) {
        for (ActiveFlower flower : flowers) {
            BlockPos position = flower.position;
            Box area = new Box(
                    position.getX() - FLOWER_DAMAGE_HORIZONTAL,
                    position.getY() - 0.2D,
                    position.getZ() - FLOWER_DAMAGE_HORIZONTAL,
                    position.getX() + 1.0D + FLOWER_DAMAGE_HORIZONTAL,
                    position.getY() + FLOWER_DAMAGE_VERTICAL,
                    position.getZ() + 1.0D + FLOWER_DAMAGE_HORIZONTAL
            );
            List<Entity> entities = world.getOtherEntities(
                    null,
                    area,
                    entity -> entity instanceof LivingEntity livingEntity
                            && livingEntity.isAlive()
                            && !entity.isSpectator()
            );
            for (Entity entity : entities) {
                UUID entityUuid = entity.getUuid();
                if (entityUuid.equals(flower.ownerUuid)
                        || TRAIL_DAMAGE_COOLDOWNS.getOrDefault(entityUuid, 0) > 0) {
                    continue;
                }
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(flower.ownerUuid);
                LivingEntity target = (LivingEntity) entity;
                target.damage(
                        world,
                        owner != null
                                ? world.getDamageSources().playerAttack(owner)
                                : world.getDamageSources().magic(),
                        FLOWER_TRAIL_DAMAGE
                );
                TRAIL_DAMAGE_COOLDOWNS.put(entityUuid, FLOWER_TRAIL_DAMAGE_INTERVAL);
                Vec3d targetPos = target.getEntityPos();
                world.spawnParticles(
                        ParticleTypes.COMPOSTER,
                        targetPos.x,
                        targetPos.y + target.getHeight() * 0.5D,
                        targetPos.z,
                        6,
                        0.4D,
                        0.4D,
                        0.4D,
                        0.04D
                );
            }
        }
    }

    // ----- Vine ring -----

    static int startVineRing(ServerPlayerEntity player, SlotKey slotKey) {
        if (VINE_RING_TICKS.containsKey(slotKey)) {
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Your vine ring is already spinning."), true);
            return 0;
        }

        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Vine Ring", remainingTicks);
            return 0;
        }

        VINE_RING_TICKS.put(slotKey, VINE_RING_DURATION);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.55D, 0.0D);
        for (int angle = 0; angle < 360; angle += 20) {
            double radians = angle * GlungFastMath.DEG_TO_RAD;
            double x = center.x + GlungFastMath.fastCos(radians) * VINE_RING_RADIUS;
            double z = center.z + GlungFastMath.fastSin(radians) * VINE_RING_RADIUS;
            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, center.y, z, 2, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ENTITY_BEE_LOOP,
                SoundCategory.PLAYERS,
                1.2F,
                1.5F
        );
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Vine Ring summoned — a thorned halo spins around you for 30s!"), true);
        return 1;
    }

    static boolean isVineRingActive(SlotKey slotKey) {
        return VINE_RING_TICKS.containsKey(slotKey);
    }

    static Integer getVineRingRemaining(SlotKey slotKey) {
        return VINE_RING_TICKS.get(slotKey);
    }

    private static void endVineRing(ServerPlayerEntity player, SlotKey slotKey) {
        VINE_RING_TICKS.remove(slotKey);
        PowerCooldowns.setSecondPower(slotKey, VINE_RING_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Your vine ring unravels. Cooldown: 30s."), true);
    }

    private static void tickVineRing(ServerWorld world, ServerPlayerEntity player, SlotKey slotKey) {
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.55D, 0.0D);
        double spin = world.getTime() * 0.4D;

        for (int sample = 0; sample < 14; sample++) {
            double angle = spin + sample * GlungFastMath.TAU / 14.0D;
            double x = center.x + GlungFastMath.fastCos(angle) * VINE_RING_RADIUS;
            double z = center.z + GlungFastMath.fastSin(angle) * VINE_RING_RADIUS;
            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, center.y, z, 1, 0.05D, 0.05D, 0.05D, 0.01D);
            if (sample % 3 == 0) {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, center.y, z, 1, 0.06D, 0.06D, 0.06D, 0.01D);
            }
            if (sample % 5 == 0) {
                world.spawnParticles(ParticleTypes.CHERRY_LEAVES, x, center.y + 0.3D, z, 1, 0.08D, 0.08D, 0.08D, 0.01D);
            }
        }

        Box area = new Box(
                center.x - VINE_RING_RADIUS - 1.0D,
                center.y - 1.3D,
                center.z - VINE_RING_RADIUS - 1.0D,
                center.x + VINE_RING_RADIUS + 1.0D,
                center.y + 1.3D,
                center.z + VINE_RING_RADIUS + 1.0D
        );
        List<Entity> targets = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        Map<UUID, Integer> hitCooldowns = VINE_RING_HIT_COOLDOWNS.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>());
        for (Entity entity : targets) {
            Vec3d entityPos = entity.getEntityPos();
            double horizontalDistance = Math.sqrt(
                    (entityPos.x - center.x) * (entityPos.x - center.x)
                            + (entityPos.z - center.z) * (entityPos.z - center.z)
            );
            double verticalCenter = entityPos.y + entity.getHeight() * 0.5D;
            if (Math.abs(horizontalDistance - VINE_RING_RADIUS) > VINE_RING_THICKNESS
                    || Math.abs(verticalCenter - center.y) > VINE_RING_VERTICAL_HALF) {
                continue;
            }
            if (hitCooldowns.getOrDefault(entity.getUuid(), 0) > 0) {
                continue;
            }

            Vec3d away = new Vec3d(entityPos.x - center.x, 0.0D, entityPos.z - center.z);
            double length = away.length();
            if (length < 0.01D) {
                away = new Vec3d(1.0D, 0.0D, 0.0D);
            } else {
                away = away.multiply(1.0D / length);
            }
            LivingEntity target = (LivingEntity) entity;
            target.addVelocity(away.x * VINE_RING_PUSH, 0.35D, away.z * VINE_RING_PUSH);
            target.velocityDirty = true;
            target.damage(world, world.getDamageSources().playerAttack(player), VINE_RING_DAMAGE);
            hitCooldowns.put(entity.getUuid(), VINE_RING_HIT_COOLDOWN);

            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, entityPos.x, entityPos.y + 1.0D, entityPos.z, 8, 0.3D, 0.4D, 0.3D, 0.05D);
            world.playSound(
                    null,
                    entityPos.x,
                    entityPos.y,
                    entityPos.z,
                    SoundEvents.ENTITY_BEE_STING,
                    SoundCategory.PLAYERS,
                    0.8F,
                    1.2F
            );
        }
    }

    private static void tickVineRings(MinecraftServer server) {
        for (SlotKey slotKey : List.copyOf(VINE_RING_TICKS.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(slotKey.playerUuid());
            if (player == null) {
                VINE_RING_TICKS.remove(slotKey);
                continue;
            }
            int remaining = VINE_RING_TICKS.getOrDefault(slotKey, 0) - 1;
            if (remaining <= 0) {
                endVineRing(player, slotKey);
                continue;
            }
            VINE_RING_TICKS.put(slotKey, remaining);
            if (player.getEntityWorld() instanceof ServerWorld world) {
                tickVineRing(world, player, slotKey);
            }
        }

        VINE_RING_HIT_COOLDOWNS.entrySet().removeIf(playerEntry -> {
            playerEntry.getValue().entrySet().removeIf(hitEntry -> {
                int remaining = hitEntry.getValue() - 1;
                if (remaining <= 0) {
                    return true;
                }
                hitEntry.setValue(remaining);
                return false;
            });
            return playerEntry.getValue().isEmpty();
        });
    }

    // ----- Earthquake of Lucifer -----

    static void startEarthquake(ServerPlayerEntity player, SlotKey slotKey) {
        UUID playerUuid = player.getUuid();
        ACTIVE_EARTHQUAKES.put(playerUuid, new ActiveEarthquake(EARTHQUAKE_DURATION));
        PowerCooldowns.setUltimate(slotKey, EARTHQUAKE_COOLDOWN);

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        broadcastEarthquake(world, playerUuid, true);
        Vec3d center = player.getEntityPos();
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS,
                3.0F,
                0.35F
        );
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("EARTHQUAKE OF LUCIFER — the ground itself rebels for 60 seconds!"), true);
    }

    static boolean isEarthquakeActive(UUID playerUuid) {
        return ACTIVE_EARTHQUAKES.containsKey(playerUuid);
    }

    static Integer getEarthquakeRemaining(UUID playerUuid) {
        ActiveEarthquake earthquake = ACTIVE_EARTHQUAKES.get(playerUuid);
        return earthquake == null ? null : earthquake.remainingTicks;
    }

    static void sendActiveEarthquakes(ServerPlayerEntity joiningPlayer, MinecraftServer server) {
        for (UUID uuid : ACTIVE_EARTHQUAKES.keySet()) {
            if (server.getPlayerManager().getPlayer(uuid) != null) {
                ServerPlayNetworking.send(joiningPlayer, new NatureEarthquakePayload(uuid, true));
            }
        }
    }

    private static void broadcastEarthquake(ServerWorld world, UUID playerUuid, boolean active) {
        NatureEarthquakePayload payload = new NatureEarthquakePayload(playerUuid, active);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void tickEarthquakes(MinecraftServer server) {
        for (UUID playerUuid : List.copyOf(ACTIVE_EARTHQUAKES.keySet())) {
            ActiveEarthquake earthquake = ACTIVE_EARTHQUAKES.get(playerUuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player == null) {
                ACTIVE_EARTHQUAKES.remove(playerUuid);
                continue;
            }

            earthquake.remainingTicks--;
            earthquake.elapsedTicks++;
            if (earthquake.remainingTicks <= 0) {
                endEarthquake(player);
                continue;
            }
            if (player.getEntityWorld() instanceof ServerWorld world) {
                tickEarthquake(world, player, earthquake);
            }
        }
    }

    private static void tickEarthquake(ServerWorld world, ServerPlayerEntity player, ActiveEarthquake earthquake) {
        Vec3d center = player.getEntityPos();

        if (earthquake.elapsedTicks % EARTHQUAKE_DAMAGE_INTERVAL == 0) {
            Box area = player.getBoundingBox().expand(EARTHQUAKE_RADIUS, 12.0D, EARTHQUAKE_RADIUS);
            List<Entity> targets = world.getOtherEntities(
                    player,
                    area,
                    entity -> entity instanceof LivingEntity livingEntity
                            && livingEntity.isAlive()
                            && !entity.isSpectator()
            );
            for (Entity entity : targets) {
                double distance = entity.squaredDistanceTo(center);
                if (distance > EARTHQUAKE_RADIUS * EARTHQUAKE_RADIUS) {
                    continue;
                }
                double closeness = 1.0D - Math.sqrt(distance) / EARTHQUAKE_RADIUS;
                float damage = (float) (5.0D + closeness * 6.0D);
                ((LivingEntity) entity).damage(world, world.getDamageSources().playerAttack(player), damage);
                Vec3d entityPos = entity.getEntityPos();
                world.spawnParticles(ParticleTypes.CRIT, entityPos.x, entityPos.y + 1.0D, entityPos.z, 8, 0.4D, 0.4D, 0.4D, 0.1D);
            }
        }

        if (earthquake.elapsedTicks % EARTHQUAKE_JOLT_INTERVAL == 0) {
            Box area = player.getBoundingBox().expand(EARTHQUAKE_RADIUS, 8.0D, EARTHQUAKE_RADIUS);
            List<Entity> targets = world.getOtherEntities(
                    player,
                    area,
                    entity -> entity instanceof LivingEntity livingEntity
                            && livingEntity.isAlive()
                            && !entity.isSpectator()
            );
            for (Entity entity : targets) {
                if (entity.squaredDistanceTo(center) > EARTHQUAKE_RADIUS * EARTHQUAKE_RADIUS) {
                    continue;
                }
                // GlungFastMath jitter via deterministic hash for stable shake without Random contention
            double joltX = (GlungFastMath.hash01(entity.getBlockPos().getX(), earthquake.elapsedTicks, 0) - 0.5) * 0.6D;
            double joltZ = (GlungFastMath.hash01(entity.getBlockPos().getZ(), earthquake.elapsedTicks, 1) - 0.5) * 0.6D;
                double lift = earthquake.elapsedTicks % EARTHQUAKE_TOSS_INTERVAL == 0
                        ? 0.5D + GlungFastMath.hash01(entity.getBlockPos().getY(), earthquake.elapsedTicks, 2) * 0.25D
                        : 0.05D;
                entity.addVelocity(joltX, lift, joltZ);
                entity.velocityDirty = true;
            }
        }

        spawnEarthquakeParticles(world, player, center, earthquake.elapsedTicks);
    }

    private static void spawnEarthquakeParticles(
            ServerWorld world,
            ServerPlayerEntity player,
            Vec3d center,
            int elapsedTicks
    ) {
        int samples = 6;
        for (int sample = 0; sample < samples; sample++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(world.random.nextDouble()) * EARTHQUAKE_RADIUS;
            int x = (int) Math.floor(center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.z + Math.sin(angle) * distance);
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            double y = surfaceY + 1.0D;

            world.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()),
                    x + 0.5D,
                    y,
                    z + 0.5D,
                    5,
                    0.6D,
                    0.15D,
                    0.6D,
                    0.3D
            );
            if (world.random.nextInt(3) == 0) {
                world.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                        x + 0.5D,
                        y + 0.6D,
                        z + 0.5D,
                        3,
                        0.4D,
                        0.5D,
                        0.4D,
                        0.35D
                );
            }
            if (world.random.nextInt(4) == 0) {
                world.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.STONE.getDefaultState()),
                        x + 0.5D,
                        y + 0.2D,
                        z + 0.5D,
                        2,
                        0.3D,
                        0.3D,
                        0.3D,
                        0.05D
                );
            }
            if (world.random.nextInt(5) == 0) {
                world.spawnParticles(ParticleTypes.LAVA, x + 0.5D, y - 0.2D, z + 0.5D, 2, 0.2D, 0.1D, 0.2D, 0.05D);
            }
            if (world.random.nextInt(4) == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x + 0.5D, y, z + 0.5D, 2, 0.3D, 0.3D, 0.3D, 0.02D);
            }
        }

        if (elapsedTicks % 30 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = 6.0D + world.random.nextDouble() * (EARTHQUAKE_RADIUS - 8.0D);
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, surfaceY + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()),
                    x,
                    surfaceY + 1.0D,
                    z,
                    25,
                    1.5D,
                    0.6D,
                    1.5D,
                    0.4D
            );
            world.playSound(
                    null,
                    x,
                    surfaceY + 1.0D,
                    z,
                    SoundEvents.BLOCK_STONE_BREAK,
                    SoundCategory.PLAYERS,
                    1.8F,
                    0.5F
            );
        }

        if (elapsedTicks % 10 == 0 && player.isAlive()) {
            double x = player.getX();
            double z = player.getZ();
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, player.getY() + 0.2D, z, 3, 0.5D, 0.3D, 0.5D, 0.02D);
        }
    }

    private static void endEarthquake(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ACTIVE_EARTHQUAKES.remove(playerUuid);
        if (player.getEntityWorld() instanceof ServerWorld world) {
            broadcastEarthquake(world, playerUuid, false);
            igniteScorchedBlocks(world, player);
            Vec3d center = player.getEntityPos();
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticles(
                    ParticleTypes.LAVA,
                    center.x,
                    center.y + 1.0D,
                    center.z,
                    60,
                    EARTHQUAKE_RADIUS * 0.35D,
                    2.0D,
                    EARTHQUAKE_RADIUS * 0.35D,
                    0.1D
            );
            world.playSound(
                    null,
                    center.x,
                    center.y,
                    center.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS,
                    2.6F,
                    0.3F
            );
        }
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("The Earthquake of Lucifer subsides — the land is set ablaze!"), true);
    }

    private static void igniteScorchedBlocks(ServerWorld world, ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int ignited = 0;
        int attempts = 0;
        while (ignited < EARTHQUAKE_IGNITION_COUNT && attempts < EARTHQUAKE_IGNITION_COUNT * 4) {
            attempts++;
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + Math.sqrt(world.random.nextDouble()) * (EARTHQUAKE_RADIUS - 3.0D);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            BlockPos position = new BlockPos(x, surfaceY, z);
            if (isNearPlayer(position, playerPos)) {
                continue;
            }
            for (int dy = 0; dy <= 2 && ignited < EARTHQUAKE_IGNITION_COUNT; dy++) {
                if (tryIgniteBlock(world, position.up(dy))) {
                    ignited++;
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

    // ----- Per-tick orchestration -----

    static void tick(ServerWorld world) {
        tickFlowers(world);
    }

    static void tickServer(MinecraftServer server) {
        TRAIL_DAMAGE_COOLDOWNS.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            entry.setValue(remaining);
            return false;
        });

        if (server.getTicks() % FLOWER_PLACEMENT_INTERVAL == 0) {
            for (UUID playerUuid : FLOWER_TRAIL_PLAYERS) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null && player.getEntityWorld() instanceof ServerWorld world) {
                    placeTrailFlower(world, player);
                }
            }
        }

        tickVineRings(server);
        tickEarthquakes(server);
    }

    /** Stops every Nature effect when the player switches to another power. */
    static void clearState(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        FLOWER_TRAIL_PLAYERS.remove(playerUuid);
        VINE_RING_HIT_COOLDOWNS.remove(playerUuid);
        for (SlotKey key : List.copyOf(VINE_RING_TICKS.keySet())) {
            if (key.playerUuid().equals(playerUuid)) {
                VINE_RING_TICKS.remove(key);
            }
        }
        ActiveEarthquake earthquake = ACTIVE_EARTHQUAKES.remove(playerUuid);
        if (earthquake != null && player.getEntityWorld() instanceof ServerWorld world) {
            broadcastEarthquake(world, playerUuid, false);
        }
    }

    static void removePlayer(ServerPlayerEntity player) {
        clearState(player);
    }

    static void clearAll() {
        FLOWER_TRAIL_PLAYERS.clear();
        ACTIVE_FLOWERS.clear();
        FLOWER_POSITIONS.clear();
        TRAIL_DAMAGE_COOLDOWNS.clear();
        VINE_RING_TICKS.clear();
        VINE_RING_HIT_COOLDOWNS.clear();
        ACTIVE_EARTHQUAKES.clear();
    }

    private static final class ActiveFlower {
        private final UUID ownerUuid;
        private final BlockPos position;
        private final BlockState placedState;
        private int remainingTicks;

        private ActiveFlower(UUID ownerUuid, BlockPos position, BlockState placedState, int lifetimeTicks) {
            this.ownerUuid = ownerUuid;
            this.position = position;
            this.placedState = placedState;
            this.remainingTicks = lifetimeTicks;
        }
    }

    private static final class ActiveEarthquake {
        private int remainingTicks;
        private int elapsedTicks;

        private ActiveEarthquake(int durationTicks) {
            this.remainingTicks = durationTicks;
        }
    }
}
