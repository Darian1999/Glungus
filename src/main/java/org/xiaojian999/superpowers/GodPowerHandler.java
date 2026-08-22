package org.xiaojian999.superpowers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.entity.projectile.ProjectileUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The God powerset: creative godhood, instant kills, a held laser, and blessings. */
final class GodPowerHandler {
    private static final int LASER_RANGE = 100;
    private static final double LASER_KILL_RADIUS = 20.0D;
    private static final double LASER_BLOCK_RADIUS = 1.0D;
    private static final double BLESS_RANGE = 32.0D;
    private static final int BLESS_DURATION = 1200;
    private static final int AURA_PARTICLE_INTERVAL = 2;
    private static final float GOD_FLIGHT_SPEED_MIN = 0.01f;
    private static final float GOD_FLIGHT_SPEED_MAX = 0.20f;
    private static final float SPEED_STEP = 0.01f;
    private static final double LEVITATE_RADIUS = 30.0D;
    private static final int LEVITATE_DURATION = 600;
    private static final int LEVITATE_AMPLIFIER = 1;

    // ----- Divine arsenal: smite, annihilate, holy nova, omnipotence, banish -----
    private static final double SMITE_RANGE = 40.0D;
    private static final float SMITE_AOE_DAMAGE = 12.0F;
    private static final double SMITE_AOE_RADIUS = 4.0D;
    private static final int SMITE_COOLDOWN = 30;

    private static final double ANNIHILATE_RANGE = 40.0D;
    private static final double ANNIHILATE_RADIUS = 6.0D;
    private static final float ANNIHILATE_DAMAGE = 20.0F;
    private static final int ANNIHILATE_COOLDOWN = 600;

    private static final double NOVA_RADIUS = 24.0D;
    private static final float NOVA_DAMAGE = 10.0F;
    private static final double NOVA_KNOCKBACK = 2.4D;
    private static final int NOVA_COOLDOWN = 400;

    private static final int OMNIPOTENCE_DURATION = 300;
    private static final int OMNIPOTENCE_COOLDOWN = 600;
    private static final int OMNIPOTENCE_REFRESH_INTERVAL = 20;

    private static final double BANISH_RANGE = 32.0D;
    private static final float BANISH_DAMAGE = 25.0F;
    private static final int BANISH_COOLDOWN = 200;

    private static final Set<UUID> GOD_MODE_PLAYERS = new HashSet<>();
    private static final Set<UUID> GOD_NOCLIP_PLAYERS = new HashSet<>();
    private static final Set<UUID> CLIENT_GOD_NOCLIP_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ACTIVE_LASERS = new HashSet<>();
    private static final Map<UUID, GameMode> PREVIOUS_GAME_MODES = new HashMap<>();
    private static final Map<UUID, Integer> SMITE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> ANNIHILATE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> NOVA_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> OMNIPOTENCE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> BANISH_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> BLESS_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> LEVITATE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> OMNIPOTENCE_TICKS = new HashMap<>();
    private static final int BLESS_COOLDOWN = 60;
    private static final int LEVITATE_COOLDOWN = 200;

    private GodPowerHandler() {
    }

    static int toggleGodMode(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (GOD_MODE_PLAYERS.contains(playerUuid)) {
            disableGodMode(player);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("God Mode disabled."), true);
            return 1;
        }

        PREVIOUS_GAME_MODES.put(playerUuid, player.interactionManager.getGameMode());
        GOD_MODE_PLAYERS.add(playerUuid);
        player.changeGameMode(GameMode.CREATIVE);
        // God Mode starts without noclip; press backslash (\\) to toggle phasing through walls.
        player.noClip = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal(
                "God Mode enabled — KP7 bless, KP8 levitate, KP9 laser, KP0 smite, KP. blast, KPENTER nova, KP* omnipotence, KP/ banish, \\ noclip."
        ), true);
        return 1;
    }

    static boolean isActive(UUID playerUuid) {
        return GOD_MODE_PLAYERS.contains(playerUuid);
    }

    static boolean isNoClipActive(UUID playerUuid) {
        return GOD_NOCLIP_PLAYERS.contains(playerUuid);
    }

    static boolean isClientNoClipActive(UUID playerUuid) {
        return CLIENT_GOD_NOCLIP_PLAYERS.contains(playerUuid);
    }

    static void setClientNoClipActive(UUID playerUuid, boolean active) {
        if (active) {
            CLIENT_GOD_NOCLIP_PLAYERS.add(playerUuid);
        } else {
            CLIENT_GOD_NOCLIP_PLAYERS.remove(playerUuid);
        }
    }

    static int toggleNoClip(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (!isActive(playerUuid)) {
            return 0;
        }
        boolean enabled;
        if (GOD_NOCLIP_PLAYERS.contains(playerUuid)) {
            GOD_NOCLIP_PLAYERS.remove(playerUuid);
            enabled = false;
        } else {
            GOD_NOCLIP_PLAYERS.add(playerUuid);
            enabled = true;
        }
        // Keep server-side noClip in sync immediately; the END_SERVER_TICK loop
        // also reapplies isNoClipActive every tick.
        player.noClip = enabled;
        if (enabled) {
            // Lock flying on — noclip without flight lets you fall through the floor.
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal(
                enabled ? "God Mode no-clip enabled — phase through walls (flight locked)." : "God Mode no-clip disabled."
        ), true);
        return 1;
    }

    static void setLaserActive(ServerPlayerEntity player, boolean active) {
        if (active && isActive(player.getUuid())) {
            if (ACTIVE_LASERS.add(player.getUuid())) {
                player.getEntityWorld().playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.ENTITY_EVOKER_CAST_SPELL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.8F
                );
            }
        } else {
            ACTIVE_LASERS.remove(player.getUuid());
        }
    }

    static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player.getUuid())) {
            return;
        }

        if (player.interactionManager.getGameMode() != GameMode.CREATIVE) {
            player.changeGameMode(GameMode.CREATIVE);
        }
        // God Mode noclip is opt-in via backslash; otherwise keep spectator-style noclip disabled.
        // The END_SERVER_TICK loop already sets player.noClip via PowerManager.isNoClipActive,
        // so we don't force it off here when toggled on.
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        tickOmnipotence(player, world);
        if (world.getTime() % AURA_PARTICLE_INTERVAL == 0) {
            world.spawnParticles(
                    ParticleTypes.WAX_ON,
                    player.getX(),
                    player.getY() + player.getHeight() * 0.55D,
                    player.getZ(),
                    10, // decreased from 18
                    0.7D,
                    0.9D,
                    0.7D,
                    0.03D
            );
        }
        // HACKY LIGHT (no block placement): give the god permanent Night Vision
        // plus a client-side fullbright mixin, so the player *appears* to be a
        // light source without ever calling world.setBlockState(Blocks.LIGHT).
        // This avoids overwriting/breaking the block at the player's feet and
        // leaves no holes when God Mode ends. A soft halo of END_ROD particles
        // sells the visual illusion.
        var nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 200) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
        if (world.getTime() % 3 == 0) {
            Vec3d halo = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
            world.spawnParticles(ParticleTypes.END_ROD, halo.x, halo.y, halo.z, 2, 0.25D, 0.35D, 0.25D, 0.01D);
        }
        // No-clip flight lock: while noclip is on, the god must stay airborne.
        // Re-assert flying/allowFlying every tick so double-tapping space cannot drop them.
        if (GOD_NOCLIP_PLAYERS.contains(player.getUuid())) {
            boolean updated = false;
            if (!player.getAbilities().allowFlying) {
                player.getAbilities().allowFlying = true;
                updated = true;
            }
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                updated = true;
            }
            if (updated) {
                player.sendAbilitiesUpdate();
            }
        }
    }

    static void tickServer(MinecraftServer server) {
        tickCooldowns();
        for (UUID playerUuid : Set.copyOf(ACTIVE_LASERS)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player == null || !isActive(playerUuid) || !(player.getEntityWorld() instanceof ServerWorld world)) {
                ACTIVE_LASERS.remove(playerUuid);
                continue;
            }
            fireLaser(world, player);
        }
    }

    private static void fireLaser(ServerWorld world, ServerPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maxEnd = start.add(direction.multiply(LASER_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d end = blockHit.getType() == HitResult.Type.MISS ? maxEnd : blockHit.getPos();
        double effectiveRange = start.distanceTo(end);

        // Tunnel: only to hit point, chunk-loaded guard, skip player feet
        for (double distance = 2.0D; distance <= effectiveRange; distance += 0.5D) {
            Vec3d position = start.add(direction.multiply(distance));
            BlockPos center = BlockPos.ofFloored(position);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos blockPos = center.add(x, y, z);
                        if (blockPos.getSquaredDistance(player.getBlockPos()) < 4.0D) {
                            continue;
                        }
                        if (!world.getChunkManager().isChunkLoaded(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                            continue;
                        }
                        BlockState state = world.getBlockState(blockPos);
                        if (isDestructible(state)) {
                            world.breakBlock(blockPos, false, player);
                        }
                    }
                }
            }
        }

        Box searchBox = new Box(
                Math.min(start.x, end.x) - LASER_KILL_RADIUS,
                Math.min(start.y, end.y) - LASER_KILL_RADIUS,
                Math.min(start.z, end.z) - LASER_KILL_RADIUS,
                Math.max(start.x, end.x) + LASER_KILL_RADIUS,
                Math.max(start.y, end.y) + LASER_KILL_RADIUS,
                Math.max(start.z, end.z) + LASER_KILL_RADIUS
        );
        List<Entity> targets = world.getOtherEntities(
                player,
                searchBox,
                entity -> entity instanceof MobEntity mob && mob.isAlive() && !mob.isSpectator()
        );
        for (Entity target : targets) {
            if (distanceSquaredToSegment(target.getEntityPos(), start, end) <= LASER_KILL_RADIUS * LASER_KILL_RADIUS) {
                MobEntity mob = (MobEntity) target;
                mob.setHealth(0.0F);
                mob.kill(world);
            }
        }

        for (double distance = 0.0D; distance <= effectiveRange; distance += 1.0D) {
            Vec3d position = start.add(direction.multiply(distance));
            world.spawnParticles(ParticleTypes.WAX_ON, position.x, position.y, position.z, 2, 0.12D, 0.12D, 0.12D, 0.02D);
        }
    }

    static int blessTarget(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(BLESS_COOLDOWNS, player, "Bless")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(BLESS_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                maximumEnd,
                player.getBoundingBox().stretch(direction.multiply(BLESS_RANGE)).expand(1.0D),
                entity -> entity instanceof MobEntity mob && mob.isAlive() && !mob.isSpectator(),
                BLESS_RANGE * BLESS_RANGE
        );
        if (entityHit == null
                || (blockHit.getType() != HitResult.Type.MISS
                && entityHit.getPos().squaredDistanceTo(start) > blockEnd.squaredDistanceTo(start))) {
            player.sendMessage(Text.literal("God Mode blessing failed — look directly at a mob."), true);
            return 0;
        }

        MobEntity target = (MobEntity) entityHit.getEntity();
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, BLESS_DURATION, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, BLESS_DURATION, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, BLESS_DURATION, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, BLESS_DURATION, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, BLESS_DURATION, 0, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, BLESS_DURATION, 0, false, true, true));

        Vec3d position = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(ParticleTypes.WAX_ON, position.x, position.y, position.z, 80, 0.8D, 1.0D, 0.8D, 0.08D);
        world.playSound(null, position.x, position.y, position.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 1.3F);
        BLESS_COOLDOWNS.put(player.getUuid(), BLESS_COOLDOWN);
        player.sendMessage(Text.literal("The " + target.getDisplayName().getString() + " has been blessed."), true);
        return 1;
    }

    /**
     * Levitates every mob within 30 blocks of the player up into the sky
     * (the player themselves and other players are never affected).
     */
    static int levitateMobs(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(LEVITATE_COOLDOWNS, player, "Levitate")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Box searchBox = new Box(
                player.getX() - LEVITATE_RADIUS,
                player.getY() - LEVITATE_RADIUS,
                player.getZ() - LEVITATE_RADIUS,
                player.getX() + LEVITATE_RADIUS,
                player.getY() + LEVITATE_RADIUS,
                player.getZ() + LEVITATE_RADIUS
        );
        List<Entity> mobs = world.getOtherEntities(
                player,
                searchBox,
                entity -> entity instanceof MobEntity mob && mob.isAlive() && !mob.isSpectator()
        );
        int count = 0;
        for (Entity entity : mobs) {
            if (entity.squaredDistanceTo(player) > LEVITATE_RADIUS * LEVITATE_RADIUS) {
                continue;
            }
            LivingEntity mob = (LivingEntity) entity;
            mob.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.LEVITATION,
                    LEVITATE_DURATION,
                    LEVITATE_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            count++;
        }

        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 120, 0.9D, 1.0D, 0.9D, 0.08D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0F, 0.7F);
        LEVITATE_COOLDOWNS.put(player.getUuid(), LEVITATE_COOLDOWN);
        player.sendMessage(Text.literal(count + " mob(s) are ascending to the sky."), true);
        return 1;
    }

    static void adjustFlightSpeed(ServerPlayerEntity player, int direction){
        if (!isActive(player.getUuid()) || direction == 0) {
            return;
        }

        float currentSpeed = player.getAbilities().getFlySpeed();
        float adjustedSpeed = currentSpeed + (direction > 0 ? SPEED_STEP : -SPEED_STEP);
        adjustedSpeed = Math.clamp(adjustedSpeed, GOD_FLIGHT_SPEED_MIN, GOD_FLIGHT_SPEED_MAX);
        adjustedSpeed = Math.round(adjustedSpeed * 1000.0F) / 1000.0F;
        player.getAbilities().setFlySpeed(adjustedSpeed);
        player.sendAbilitiesUpdate();
        player.sendMessage(Text.literal(String.format("God Mode flight speed: %.2f", adjustedSpeed)), true);
    }

    /** Calls down a lightning bolt on whatever the god is looking at. */
    static int smiteTarget(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(SMITE_COOLDOWNS, player, "Smite")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(SMITE_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                maximumEnd,
                player.getBoundingBox().stretch(direction.multiply(SMITE_RANGE)).expand(1.0D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator(),
                SMITE_RANGE * SMITE_RANGE
        );

        Vec3d strikePos;
        if (entityHit != null
                && (blockHit.getType() == HitResult.Type.MISS
                || entityHit.getPos().squaredDistanceTo(start) <= blockEnd.squaredDistanceTo(start))) {
            strikePos = entityHit.getPos();
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            strikePos = blockHit.getPos();
        } else {
            player.sendMessage(Text.literal("Smite failed — aim at a mob or the ground."), true);
            return 0;
        }

        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        bolt.setPosition(strikePos.x, strikePos.y, strikePos.z);
        world.spawnEntity(bolt);

        Box area = new Box(
                strikePos.x - SMITE_AOE_RADIUS,
                strikePos.y - SMITE_AOE_RADIUS,
                strikePos.z - SMITE_AOE_RADIUS,
                strikePos.x + SMITE_AOE_RADIUS,
                strikePos.y + SMITE_AOE_RADIUS,
                strikePos.z + SMITE_AOE_RADIUS
        );
        int struck = 0;
        for (Entity entity : world.getOtherEntities(
                player,
                area,
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator()
        )) {
            if (entity.squaredDistanceTo(strikePos) > SMITE_AOE_RADIUS * SMITE_AOE_RADIUS) {
                continue;
            }
            entity.damage(world, world.getDamageSources().playerAttack(player), SMITE_AOE_DAMAGE);
            struck++;
        }
        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                strikePos.x,
                strikePos.y + 1.0D,
                strikePos.z,
                40,
                2.0D,
                2.0D,
                2.0D,
                0.1D
        );
        world.playSound(
                null,
                strikePos.x,
                strikePos.y,
                strikePos.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS,
                2.0F,
                1.0F
        );
        SMITE_COOLDOWNS.put(player.getUuid(), SMITE_COOLDOWN);
        player.sendMessage(Text.literal("SMITE — the heavens strike down! " + struck + " hit."), true);
        return 1;
    }

    /** Obliterates blocks and enemies in a sphere around the aimed-at point. */
    static int annihilateArea(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(ANNIHILATE_COOLDOWNS, player, "Annihilate")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(ANNIHILATE_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Annihilate — aim at the ground or a structure."), true);
            return 0;
        }

        Vec3d center = blockHit.getPos();
        BlockPos centerPos = BlockPos.ofFloored(center);
        int radius = (int) Math.ceil(ANNIHILATE_RADIUS);
        int destroyed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos position = centerPos.add(dx, dy, dz);
                    if (position.getSquaredDistance(centerPos) > ANNIHILATE_RADIUS * ANNIHILATE_RADIUS) {
                        continue;
                    }
                    if (isDestructible(world.getBlockState(position))) {
                        world.breakBlock(position, false, player);
                        destroyed++;
                    }
                }
            }
        }

        Box area = new Box(
                center.x - ANNIHILATE_RADIUS,
                center.y - ANNIHILATE_RADIUS,
                center.z - ANNIHILATE_RADIUS,
                center.x + ANNIHILATE_RADIUS,
                center.y + ANNIHILATE_RADIUS,
                center.z + ANNIHILATE_RADIUS
        );
        int blasted = 0;
        for (Entity entity : world.getOtherEntities(
                player,
                area,
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator()
        )) {
            if (entity.squaredDistanceTo(center) > ANNIHILATE_RADIUS * ANNIHILATE_RADIUS) {
                continue;
            }
            entity.damage(world, world.getDamageSources().playerAttack(player), ANNIHILATE_DAMAGE);
            Vec3d away = entity.getEntityPos().subtract(center);
            double length = away.length();
            if (length > 0.01D) {
                away = away.multiply(1.8D / length);
                entity.addVelocity(away.x, 0.4D, away.z);
                entity.velocityDirty = true;
            }
            blasted++;
        }

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(
                ParticleTypes.LAVA,
                center.x,
                center.y,
                center.z,
                60,
                ANNIHILATE_RADIUS * 0.6D,
                ANNIHILATE_RADIUS * 0.6D,
                ANNIHILATE_RADIUS * 0.6D,
                0.1D
        );
        world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()),
                center.x,
                center.y,
                center.z,
                80,
                ANNIHILATE_RADIUS,
                ANNIHILATE_RADIUS,
                ANNIHILATE_RADIUS,
                0.3D
        );
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS,
                3.0F,
                0.5F
        );
        ANNIHILATE_COOLDOWNS.put(player.getUuid(), ANNIHILATE_COOLDOWN);
        player.sendMessage(Text.literal(
                "ANNIHILATE — " + destroyed + " blocks reduced to dust, " + blasted + " enemies blasted!"
        ), true);
        return 1;
    }

    /** Heals the god fully and smites every enemy around them with holy light. */
    static int holyNova(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(NOVA_COOLDOWNS, player, "Holy Nova")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);

        player.setHealth(player.getMaxHealth());
        player.extinguish();
        removeHarmfulEffects(player);

        Box area = new Box(
                center.x - NOVA_RADIUS,
                center.y - NOVA_RADIUS,
                center.z - NOVA_RADIUS,
                center.x + NOVA_RADIUS,
                center.y + NOVA_RADIUS,
                center.z + NOVA_RADIUS
        );
        int smitten = 0;
        for (Entity entity : world.getOtherEntities(
                player,
                area,
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator()
        )) {
            if (entity.squaredDistanceTo(center) > NOVA_RADIUS * NOVA_RADIUS) {
                continue;
            }
            entity.damage(world, world.getDamageSources().magic(), NOVA_DAMAGE);
            Vec3d away = entity.getEntityPos().subtract(center);
            double length = away.length();
            if (length > 0.01D) {
                away = away.multiply(NOVA_KNOCKBACK / length);
                entity.addVelocity(away.x, 0.45D, away.z);
                entity.velocityDirty = true;
            }
            smitten++;
        }
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 40, 0.6D, 1.0D, 0.6D, 0.2D);
        world.spawnParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 120, NOVA_RADIUS, NOVA_RADIUS, NOVA_RADIUS, 0.05D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.6F, 1.2F);
        NOVA_COOLDOWNS.put(player.getUuid(), NOVA_COOLDOWN);
        player.sendMessage(Text.literal(
                "HOLY NOVA — you are restored, and " + smitten + " enemies are smitten by your light!"
        ), true);
        return 1;
    }

    private static void removeHarmfulEffects(ServerPlayerEntity player) {
        List<RegistryEntry<StatusEffect>> harmfulEffects = List.of(
                StatusEffects.WITHER,
                StatusEffects.POISON,
                StatusEffects.HUNGER,
                StatusEffects.WEAKNESS,
                StatusEffects.SLOWNESS,
                StatusEffects.MINING_FATIGUE,
                StatusEffects.BLINDNESS,
                StatusEffects.NAUSEA,
                StatusEffects.DARKNESS,
                StatusEffects.INSTANT_DAMAGE,
                StatusEffects.LEVITATION,
                StatusEffects.UNLUCK
        );
        for (RegistryEntry<StatusEffect> effect : harmfulEffects) {
            player.removeStatusEffect(effect);
        }
    }

    /**
     * Omnipotence: for 15 seconds the god becomes a simulation of omnipotence —
     * no damage source can harm them and their body is flooded with divine power.
     */
    static int activateOmnipotence(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(OMNIPOTENCE_COOLDOWNS, player, "Omnipotence")) {
            return 0;
        }
        UUID playerUuid = player.getUuid();
        OMNIPOTENCE_TICKS.put(playerUuid, OMNIPOTENCE_DURATION);
        OMNIPOTENCE_COOLDOWNS.put(playerUuid, OMNIPOTENCE_COOLDOWN);
        applyOmnipotenceEffects(player);

        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 60, 0.5D, 1.0D, 0.5D, 0.2D);
        world.spawnParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 120, 3.0D, 2.0D, 3.0D, 0.05D);
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.PLAYERS,
                1.6F,
                1.1F
        );
        player.sendMessage(Text.literal(
                "OMNIPOTENCE — for 15 seconds nothing can harm you, and everything falls before you!"
        ), true);
        return 1;
    }

    static boolean isOmnipotenceActive(UUID playerUuid) {
        return OMNIPOTENCE_TICKS.getOrDefault(playerUuid, 0) > 0;
    }

    private static void applyOmnipotenceEffects(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, OMNIPOTENCE_DURATION, 9, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, OMNIPOTENCE_DURATION, 9, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, OMNIPOTENCE_DURATION, 4, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, OMNIPOTENCE_DURATION, 4, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, OMNIPOTENCE_DURATION, 2, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, OMNIPOTENCE_DURATION, 5, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, OMNIPOTENCE_DURATION, 2, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, OMNIPOTENCE_DURATION, 0, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, OMNIPOTENCE_DURATION, 0, false, true, true));
    }

    private static void tickOmnipotence(ServerPlayerEntity player, ServerWorld world) {
        UUID playerUuid = player.getUuid();
        int remaining = OMNIPOTENCE_TICKS.getOrDefault(playerUuid, 0);
        if (remaining <= 0) {
            return;
        }
        OMNIPOTENCE_TICKS.put(playerUuid, remaining - 1);
        // Re-top the divine statuses so the simulated omnipotence never flickers.
        if (remaining % OMNIPOTENCE_REFRESH_INTERVAL == 0) {
            applyOmnipotenceEffects(player);
        }
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 3, 1.2D, 1.5D, 1.2D, 0.04D);
    }

    /** Throws the looked-at mob into the void: heavy damage plus wither and darkness. */
    static int banishTarget(ServerPlayerEntity player) {
        if (!isActive(player.getUuid()) || onCooldown(BANISH_COOLDOWNS, player, "Banish")) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(BANISH_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                maximumEnd,
                player.getBoundingBox().stretch(direction.multiply(BANISH_RANGE)).expand(1.0D),
                e -> e instanceof MobEntity mob && mob.isAlive() && !e.isSpectator(),
                BANISH_RANGE * BANISH_RANGE
        );
        if (entityHit == null
                || (blockHit.getType() != HitResult.Type.MISS
                && entityHit.getPos().squaredDistanceTo(start) > blockEnd.squaredDistanceTo(start))) {
            player.sendMessage(Text.literal("Banishment failed — look directly at a mob."), true);
            return 0;
        }

        LivingEntity target = (LivingEntity) entityHit.getEntity();
        target.damage(world, world.getDamageSources().outOfWorld(), BANISH_DAMAGE);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0, false, true, true));
        Vec3d position = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, position.x, position.y, position.z, 60, 0.5D, 1.0D, 0.5D, 0.15D);
        world.spawnParticles(ParticleTypes.PORTAL, position.x, position.y, position.z, 40, 0.8D, 1.2D, 0.8D, 0.1D);
        world.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.4F,
                0.7F
        );
        BANISH_COOLDOWNS.put(player.getUuid(), BANISH_COOLDOWN);
        player.sendMessage(Text.literal("The " + target.getDisplayName().getString() + " has been banished to the void."), true);
        return 1;
    }

    private static boolean onCooldown(
            Map<UUID, Integer> cooldowns,
            ServerPlayerEntity player,
            String abilityName
    ) {
        int remainingTicks = cooldowns.getOrDefault(player.getUuid(), 0);
        if (remainingTicks > 0) {
            PowerManager.sendCooldownMessage(player, abilityName, remainingTicks);
            return true;
        }
        return false;
    }

    private static void tickCooldowns() {
        tickCooldownMap(SMITE_COOLDOWNS);
        tickCooldownMap(ANNIHILATE_COOLDOWNS);
        tickCooldownMap(NOVA_COOLDOWNS);
        tickCooldownMap(OMNIPOTENCE_COOLDOWNS);
        tickCooldownMap(BANISH_COOLDOWNS);
        tickCooldownMap(BLESS_COOLDOWNS);
        tickCooldownMap(LEVITATE_COOLDOWNS);
    }

    private static void tickCooldownMap(Map<UUID, Integer> cooldowns) {
        cooldowns.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            entry.setValue(remaining);
            return false;
        });
    }

    private static boolean isDestructible(BlockState state) {
        return !state.isAir()
                && !state.isOf(Blocks.BEDROCK)
                && !state.isOf(Blocks.BARRIER)
                && !state.isOf(Blocks.END_PORTAL)
                && !state.isOf(Blocks.END_GATEWAY);
    }

    private static double distanceSquaredToSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1.0E-6D) {
            return point.squaredDistanceTo(start);
        }
        double progress = point.subtract(start).dotProduct(segment) / lengthSquared;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return point.squaredDistanceTo(start.add(segment.multiply(progress)));
    }

    private static void disableGodMode(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ACTIVE_LASERS.remove(playerUuid);
        GOD_MODE_PLAYERS.remove(playerUuid);
        GOD_NOCLIP_PLAYERS.remove(playerUuid);
        CLIENT_GOD_NOCLIP_PLAYERS.remove(playerUuid);
        OMNIPOTENCE_TICKS.remove(playerUuid);
        // HACKY LIGHT cleanup: remove the fake Night Vision we applied; no block to remove.
        player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        GameMode previousGameMode = PREVIOUS_GAME_MODES.remove(playerUuid);
        if (previousGameMode != null && player.interactionManager.getGameMode() == GameMode.CREATIVE) {
            player.changeGameMode(previousGameMode);
        }
        player.noClip = false;
    }

    static void removePlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        disableGodMode(player);
        SMITE_COOLDOWNS.remove(playerUuid);
        ANNIHILATE_COOLDOWNS.remove(playerUuid);
        NOVA_COOLDOWNS.remove(playerUuid);
        OMNIPOTENCE_COOLDOWNS.remove(playerUuid);
        BANISH_COOLDOWNS.remove(playerUuid);
        BLESS_COOLDOWNS.remove(playerUuid);
        LEVITATE_COOLDOWNS.remove(playerUuid);
        OMNIPOTENCE_TICKS.remove(playerUuid);
        GOD_NOCLIP_PLAYERS.remove(playerUuid);
        CLIENT_GOD_NOCLIP_PLAYERS.remove(playerUuid);
    }

    static void clearAll() {
        GOD_MODE_PLAYERS.clear();
        GOD_NOCLIP_PLAYERS.clear();
        CLIENT_GOD_NOCLIP_PLAYERS.clear();
        ACTIVE_LASERS.clear();
        PREVIOUS_GAME_MODES.clear();
        SMITE_COOLDOWNS.clear();
        ANNIHILATE_COOLDOWNS.clear();
        NOVA_COOLDOWNS.clear();
        OMNIPOTENCE_COOLDOWNS.clear();
        BANISH_COOLDOWNS.clear();
        BLESS_COOLDOWNS.clear();
        LEVITATE_COOLDOWNS.clear();
        OMNIPOTENCE_TICKS.clear();
    }
}
