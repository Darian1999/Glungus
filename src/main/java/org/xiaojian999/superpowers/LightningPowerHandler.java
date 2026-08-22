package org.xiaojian999.superpowers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lightning powers: chain lightning, targeted strikes, and the Storm Form ultimate. */
final class LightningPowerHandler {
    private static final double LIGHTNING_BEAM_RANGE = 20.0D;
    private static final float LIGHTNING_BEAM_DAMAGE = 6.0F;
    private static final double LIGHTNING_BEAM_CHAIN_RADIUS = 6.0D;
    private static final int LIGHTNING_BEAM_MAX_TARGETS = 5;
    private static final double LIGHTNING_BEAM_CHAIN_DAMAGE_LOSS = 0.2D;
    private static final int LIGHTNING_BEAM_COOLDOWN = 60;
    private static final double LIGHTNING_STRIKE_RANGE = 40.0D;
    private static final float LIGHTNING_STRIKE_AOE_DAMAGE = 4.0F;
    private static final double LIGHTNING_STRIKE_AOE_RADIUS = 3.5D;
    private static final int LIGHTNING_STRIKE_COOLDOWN = 120;
    private static final int LIGHTNING_FORM_DURATION = 600;
    private static final int LIGHTNING_FORM_COOLDOWN = 600;
    private static final double LIGHTNING_FORM_PARTICLE_RADIUS = 1.5D;
    private static final double LIGHTNING_FORM_TOUCH_RADIUS = 2.0D;
    private static final float BIG_LIGHTNING_DAMAGE = 10.0F;
    private static final int LIGHTNING_FORM_STRIKE_COOLDOWN = 20;

    private static final Map<SlotKey, Integer> LIGHTNING_FORM_TICKS = new HashMap<>();
    private static final Set<UUID> CLIENT_LIGHTNING_FORM_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> STORM_STRIKE_COOLDOWNS = new HashMap<>();

    private LightningPowerHandler() {
    }

    static int fireChainLightning(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.beamRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Chain Lightning", remainingTicks);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(LIGHTNING_BEAM_RANGE));

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Box searchBox = player.getBoundingBox().stretch(direction.multiply(LIGHTNING_BEAM_RANGE)).expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                maximumEnd,
                searchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                LIGHTNING_BEAM_RANGE * LIGHTNING_BEAM_RANGE
        );

        Vec3d beamEnd = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getPos();
        LivingEntity primaryTarget = null;
        if (entityHit != null
                && (blockHit.getType() == HitResult.Type.MISS
                || entityHit.getPos().squaredDistanceTo(start) <= beamEnd.squaredDistanceTo(start))) {
            beamEnd = entityHit.getPos();
            if (entityHit.getEntity() instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
                primaryTarget = livingTarget;
            }
        }

        spawnLightningBeamParticles(world, start, beamEnd);
        world.playSound(
                null,
                beamEnd.x,
                beamEnd.y,
                beamEnd.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                SoundCategory.PLAYERS,
                1.0F,
                1.3F
        );

        int struck = 0;
        if (primaryTarget != null) {
            Set<UUID> hit = new HashSet<>();
            LivingEntity current = primaryTarget;
            double damage = LIGHTNING_BEAM_DAMAGE;
            while (current != null && struck < LIGHTNING_BEAM_MAX_TARGETS) {
                hit.add(current.getUuid());
                struck++;
                current.damage(world, world.getDamageSources().playerAttack(player), (float) damage);

                LivingEntity next = findChainTarget(world, player, current, hit);
                if (next != null) {
                    Vec3d from = current.getEntityPos().add(0.0D, current.getHeight() * 0.5D, 0.0D);
                    Vec3d to = next.getEntityPos().add(0.0D, next.getHeight() * 0.5D, 0.0D);
                    spawnLightningArcParticles(world, from, to);
                    world.playSound(
                            null,
                            to.x,
                            to.y,
                            to.z,
                            SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                            SoundCategory.PLAYERS,
                            1.0F - struck * 0.12F,
                            1.4F
                    );
                }
                current = next;
                damage *= 1.0D - LIGHTNING_BEAM_CHAIN_DAMAGE_LOSS;
            }
        }

        PowerCooldowns.setBeam(slotKey, LIGHTNING_BEAM_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        if (struck > 0) {
            player.sendMessage(Text.literal(
                    "Chain Lightning struck " + struck + " " + (struck == 1 ? "enemy" : "enemies") + "!"
            ), true);
        } else {
            player.sendMessage(Text.literal("Chain Lightning — no target in range."), true);
        }
        return 1;
    }

    private static LivingEntity findChainTarget(
            ServerWorld world,
            ServerPlayerEntity player,
            LivingEntity source,
            Set<UUID> hit
    ) {
        Box area = source.getBoundingBox().expand(LIGHTNING_BEAM_CHAIN_RADIUS);
        List<Entity> candidates = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
                        && !hit.contains(entity.getUuid())
        );
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double distance = candidate.squaredDistanceTo(source);
            if (distance > LIGHTNING_BEAM_CHAIN_RADIUS * LIGHTNING_BEAM_CHAIN_RADIUS) {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = (LivingEntity) candidate;
            }
        }
        return nearest;
    }

    private static void spawnLightningBeamParticles(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d beam = end.subtract(start);
        double length = beam.length();
        if (length == 0.0D) {
            return;
        }

        Vec3d direction = beam.normalize();
        for (double distance = 0.0D; distance <= length; distance += 0.4D) {
            Vec3d position = start.add(direction.multiply(distance));
            world.spawnParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    position.x,
                    position.y,
                    position.z,
                    2,
                    0.08D,
                    0.08D,
                    0.08D,
                    0.02D
            );
            if ((int) (distance * 10.0D) % 12 == 0) {
                world.spawnParticles(ParticleTypes.GLOW, position.x, position.y, position.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                end.x,
                end.y,
                end.z,
                14,
                0.4D,
                0.4D,
                0.4D,
                0.06D
        );
    }

    private static void spawnLightningArcParticles(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length == 0.0D) {
            return;
        }
        int segments = Math.max(4, (int) (length * 2.0D));
        Vec3d side = new Vec3d(-delta.z, 0.0D, delta.x);
        boolean canOffset = side.lengthSquared() > 1.0E-4D;
        if (canOffset) {
            side = side.normalize();
        }
        double arcHeight = Math.min(1.5D, length * 0.2D);
        for (int segment = 0; segment <= segments; segment++) {
            double t = (double) segment / segments;
            Vec3d position = from.lerp(to, t);
            double offset = canOffset ? Math.sin(segment * 1.7D) * 0.35D : 0.0D;
            double lift = Math.sin(t * Math.PI) * arcHeight;
            world.spawnParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    position.x + side.x * offset,
                    position.y + lift,
                    position.z + side.z * offset,
                    2,
                    0.07D,
                    0.07D,
                    0.07D,
                    0.02D
            );
        }
        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                to.x,
                to.y,
                to.z,
                12,
                0.35D,
                0.35D,
                0.35D,
                0.05D
        );
    }

    static int summonLightningStrike(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Lightning Strike", remainingTicks);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maximumEnd = start.add(direction.multiply(LIGHTNING_STRIKE_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                maximumEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Lightning Strike — aim at a block."), true);
            return 0;
        }

        Vec3d strikePos = blockHit.getPos();
        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        bolt.setPosition(strikePos.x, strikePos.y, strikePos.z);
        world.spawnEntity(bolt);

        // Nearby enemies (never the caster) take a small amount of extra damage.
        Box area = new Box(
                strikePos.x - LIGHTNING_STRIKE_AOE_RADIUS,
                strikePos.y - LIGHTNING_STRIKE_AOE_RADIUS,
                strikePos.z - LIGHTNING_STRIKE_AOE_RADIUS,
                strikePos.x + LIGHTNING_STRIKE_AOE_RADIUS,
                strikePos.y + LIGHTNING_STRIKE_AOE_RADIUS,
                strikePos.z + LIGHTNING_STRIKE_AOE_RADIUS
        );
        List<Entity> targets = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        int hit = 0;
        for (Entity entity : targets) {
            if (entity.squaredDistanceTo(strikePos) > LIGHTNING_STRIKE_AOE_RADIUS * LIGHTNING_STRIKE_AOE_RADIUS) {
                continue;
            }
            entity.damage(world, world.getDamageSources().playerAttack(player), LIGHTNING_STRIKE_AOE_DAMAGE);
            hit++;
        }

        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                strikePos.x,
                strikePos.y + 1.0D,
                strikePos.z,
                30,
                1.5D,
                1.5D,
                1.5D,
                0.08D
        );
        PowerCooldowns.setSecondPower(slotKey, LIGHTNING_STRIKE_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Lightning Strike — " + hit + " nearby enemies zapped!"), true);
        return 1;
    }

    static int startForm(ServerPlayerEntity player, SlotKey slotKey) {
        // Only one storm form per player; end a form started from the other slot first.
        List<SlotKey> otherForms = LIGHTNING_FORM_TICKS.keySet().stream()
                .filter(key -> key.playerUuid().equals(player.getUuid()) && !key.equals(slotKey))
                .toList();
        for (SlotKey other : otherForms) {
            endForm(player, other);
        }

        LIGHTNING_FORM_TICKS.put(slotKey, LIGHTNING_FORM_DURATION);
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        player.setInvisible(true);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        broadcastFormState(world, player.getUuid(), true);
        world.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS,
                1.6F,
                1.1F
        );
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal(
                "Storm Form active — you are living lightning for 30s! Touch enemies to fry them."
        ), true);
        return 1;
    }

    static void endForm(ServerPlayerEntity player, SlotKey slotKey) {
        if (LIGHTNING_FORM_TICKS.remove(slotKey) == null) {
            return;
        }

        if (player.getEntityWorld() instanceof ServerWorld endWorld) {
            broadcastFormState(endWorld, player.getUuid(), false);
        }
        boolean keepFlight = AirPowerHandler.isFlightActive(player.getUuid()) || GhostPowerHandler.isFormActive(player.getUuid());
        if (!keepFlight && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
        boolean keepInvisible = GhostPowerHandler.isFormActive(player.getUuid());
        if (!keepInvisible) {
            player.setInvisible(false);
        }
        PowerCooldowns.setUltimate(slotKey, LIGHTNING_FORM_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Storm Form ended. Cooldown: 30s."), true);
    }

    static void disableForm(ServerPlayerEntity player) {
        List<SlotKey> activeForms = LIGHTNING_FORM_TICKS.keySet().stream()
                .filter(key -> key.playerUuid().equals(player.getUuid()))
                .toList();
        for (SlotKey slotKey : activeForms) {
            endForm(player, slotKey);
        }
    }

    static boolean isFormActive(UUID playerUuid) {
        return LIGHTNING_FORM_TICKS.keySet().stream()
                .anyMatch(key -> key.playerUuid().equals(playerUuid));
    }

    static boolean isFormActive(SlotKey slotKey) {
        return LIGHTNING_FORM_TICKS.containsKey(slotKey);
    }

    static Integer getFormRemaining(SlotKey slotKey) {
        return LIGHTNING_FORM_TICKS.get(slotKey);
    }

    static boolean isClientFormActive(UUID playerUuid) {
        return CLIENT_LIGHTNING_FORM_PLAYERS.contains(playerUuid);
    }

    static void setClientFormActive(UUID playerUuid, boolean active) {
        if (active) {
            CLIENT_LIGHTNING_FORM_PLAYERS.add(playerUuid);
        } else {
            CLIENT_LIGHTNING_FORM_PLAYERS.remove(playerUuid);
        }
    }

    static void sendActiveFormStates(ServerPlayerEntity joiningPlayer, MinecraftServer server) {
        // A player joining mid-form still needs to hide the transformed player.
        for (SlotKey key : LIGHTNING_FORM_TICKS.keySet()) {
            if (server.getPlayerManager().getPlayer(key.playerUuid()) != null) {
                ServerPlayNetworking.send(joiningPlayer, new LightningFormStatePayload(key.playerUuid(), true));
            }
        }
    }

    private static void broadcastFormState(ServerWorld world, UUID playerUuid, boolean active) {
        LightningFormStatePayload payload = new LightningFormStatePayload(playerUuid, active);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    static void tickPlayer(ServerPlayerEntity player) {
        if (isFormActive(player.getUuid()) && !player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
    }

    static void tickServer(MinecraftServer server) {
        // Iterate a snapshot because endForm removes from the map.
        for (SlotKey slotKey : List.copyOf(LIGHTNING_FORM_TICKS.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(slotKey.playerUuid());
            if (player == null) {
                LIGHTNING_FORM_TICKS.remove(slotKey);
                continue;
            }
            int remaining = LIGHTNING_FORM_TICKS.getOrDefault(slotKey, 0) - 1;
            if (remaining <= 0) {
                endForm(player, slotKey);
                continue;
            }
            LIGHTNING_FORM_TICKS.put(slotKey, remaining);
            if (player.getEntityWorld() instanceof ServerWorld world) {
                tickStormForm(world, player, slotKey);
            }
        }

        STORM_STRIKE_COOLDOWNS.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            entry.setValue(remaining);
            return false;
        });
    }

    private static void tickStormForm(ServerWorld world, ServerPlayerEntity player, SlotKey slotKey) {
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                center.x,
                center.y,
                center.z,
                14,
                LIGHTNING_FORM_PARTICLE_RADIUS,
                LIGHTNING_FORM_PARTICLE_RADIUS,
                LIGHTNING_FORM_PARTICLE_RADIUS,
                0.02D
        );
        if (world.random.nextInt(3) == 0) {
            world.spawnParticles(
                    ParticleTypes.GLOW,
                    center.x,
                    center.y,
                    center.z,
                    4,
                    LIGHTNING_FORM_PARTICLE_RADIUS * 0.6D,
                    LIGHTNING_FORM_PARTICLE_RADIUS * 0.6D,
                    LIGHTNING_FORM_PARTICLE_RADIUS * 0.6D,
                    0.01D
            );
        }

        Box area = player.getBoundingBox().expand(LIGHTNING_FORM_TOUCH_RADIUS);
        List<Entity> touching = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        for (Entity entity : touching) {
            if (player.squaredDistanceTo(entity) > LIGHTNING_FORM_TOUCH_RADIUS * LIGHTNING_FORM_TOUCH_RADIUS) {
                continue;
            }
            if (STORM_STRIKE_COOLDOWNS.containsKey(entity.getUuid())) {
                continue;
            }
            strikeBigLightning(world, player, entity);
            STORM_STRIKE_COOLDOWNS.put(entity.getUuid(), LIGHTNING_FORM_STRIKE_COOLDOWN);
        }
    }

    private static void strikeBigLightning(ServerWorld world, ServerPlayerEntity owner, Entity target) {
        Vec3d position = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        BigLightningEntity bolt = new BigLightningEntity(ModEntities.BIG_LIGHTNING, world);
        bolt.setPosition(position.x, position.y, position.z);
        world.spawnEntity(bolt);

        double radius = 3.0D;
        Box area = new Box(
                position.x - radius,
                position.y - 3.0D,
                position.z - radius,
                position.x + radius,
                position.y + 3.0D,
                position.z + radius
        );
        List<Entity> struck = world.getOtherEntities(
                owner,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        for (Entity entity : struck) {
            if (entity.squaredDistanceTo(position) > radius * radius) {
                continue;
            }
            entity.damage(world, world.getDamageSources().lightningBolt(), BIG_LIGHTNING_DAMAGE);
        }

        world.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                SoundCategory.PLAYERS,
                2.0F,
                0.9F
        );
        world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                position.x,
                position.y,
                position.z,
                30,
                1.5D,
                1.5D,
                1.5D,
                0.1D
        );
    }

    static void removePlayer(ServerPlayerEntity player) {
        disableForm(player);
        CLIENT_LIGHTNING_FORM_PLAYERS.remove(player.getUuid());
    }

    static void clearAll() {
        LIGHTNING_FORM_TICKS.clear();
        CLIENT_LIGHTNING_FORM_PLAYERS.clear();
        STORM_STRIKE_COOLDOWNS.clear();
    }
}
