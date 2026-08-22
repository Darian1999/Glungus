package org.xiaojian999.superpowers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ghost powers: spectral form, the Wail of the Damned, Soul Nova, and mob possession. */
final class GhostPowerHandler {
    private static final int GHOST_WAIL_COOLDOWN = 200;
    private static final double GHOST_WAIL_RADIUS = 9.0D;
    private static final float GHOST_WAIL_DAMAGE = 14.0F;
    private static final int GHOST_WAIL_SLOWNESS_DURATION = 120;
    private static final int GHOST_WAIL_SLOWNESS_AMPLIFIER = 1;
    private static final double GHOST_WAIL_KNOCKBACK = 2.2D;
    private static final int GHOST_SOUL_MARK_DURATION = 200;
    private static final int GHOST_SOUL_MARK_TICK_INTERVAL = 8;
    private static final double GHOST_SOUL_NOVA_RADIUS = 14.0D;
    private static final float GHOST_SOUL_NOVA_DAMAGE = 12.0F;
    private static final float GHOST_SOUL_NOVA_MARK_DAMAGE = 20.0F;
    private static final float GHOST_SOUL_NOVA_CHAIN_DAMAGE = 10.0F;
    private static final double GHOST_SOUL_NOVA_CHAIN_RADIUS = 3.5D;
    private static final float GHOST_SOUL_NOVA_HEAL = 4.0F;
    private static final float GHOST_SOUL_NOVA_HEAL_PER_SOUL = 1.0F;
    private static final double POSSESSION_GRAVITY = 0.08D;
    private static final double POSSESSION_TERMINAL_FALL_SPEED = 2.0D;
    private static final float GHOST_FLIGHT_SPEED_STEP = 0.01F;
    private static final float GHOST_FLIGHT_SPEED_MIN = 0.01F;
    private static final float GHOST_FLIGHT_SPEED_MAX = 0.50F;

    private static final Set<UUID> GHOST_FORM_PLAYERS = new HashSet<>();
    private static final Set<UUID> CLIENT_GHOST_FORM_PLAYERS = new HashSet<>();
    private static final Map<UUID, GameMode> GHOST_PREVIOUS_GAMEMODES = new HashMap<>();
    private static final Map<UUID, Float> GHOST_PREVIOUS_FLY_SPEEDS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> SOUL_MARKED_ENTITIES = new HashMap<>();
    private static final Map<UUID, UUID> POSSESSED_MOBS = new HashMap<>();

    private GhostPowerHandler() {
    }

    static int toggleForm(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (GHOST_FORM_PLAYERS.contains(playerUuid)) {
            disableForm(player);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Ghost form disabled."), true);
            return 1;
        }

        GHOST_PREVIOUS_GAMEMODES.put(playerUuid, player.interactionManager.getGameMode());
        GHOST_PREVIOUS_FLY_SPEEDS.put(playerUuid, player.getAbilities().getFlySpeed());
        GHOST_FORM_PLAYERS.add(playerUuid);
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Ghost form enabled — spectral body, creative flight, and wall phasing. Keypad +/- changes flight speed."), true);
        return 1;
    }

    static void disableForm(ServerPlayerEntity player) {
        endPossession(player);
        UUID playerUuid = player.getUuid();
        if (!GHOST_FORM_PLAYERS.remove(playerUuid)) {
            return;
        }

        GameMode previousMode = GHOST_PREVIOUS_GAMEMODES.remove(playerUuid);
        if (previousMode != null) {
            player.changeGameMode(previousMode);
        }
        Float previousFlySpeed = GHOST_PREVIOUS_FLY_SPEEDS.remove(playerUuid);
        if (previousFlySpeed != null) {
            player.getAbilities().setFlySpeed(previousFlySpeed);
            player.sendAbilitiesUpdate();
        }
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
    }

    static boolean isFormActive(UUID playerUuid) {
        return GHOST_FORM_PLAYERS.contains(playerUuid);
    }

    static void adjustFlightSpeed(ServerPlayerEntity player, int direction) {
        if (!isFormActive(player.getUuid()) || direction == 0) {
            return;
        }

        float currentSpeed = player.getAbilities().getFlySpeed();
        float adjustedSpeed = currentSpeed + (direction > 0 ? GHOST_FLIGHT_SPEED_STEP : -GHOST_FLIGHT_SPEED_STEP);
        adjustedSpeed = Math.max(GHOST_FLIGHT_SPEED_MIN, Math.min(GHOST_FLIGHT_SPEED_MAX, adjustedSpeed));
        adjustedSpeed = Math.round(adjustedSpeed * 1000.0F) / 1000.0F;
        player.getAbilities().setFlySpeed(adjustedSpeed);
        player.sendAbilitiesUpdate();
        player.sendMessage(Text.literal(String.format("Ghost flight speed: %.2f", adjustedSpeed)), true);
    }

    static boolean isClientFormActive(UUID playerUuid) {
        return CLIENT_GHOST_FORM_PLAYERS.contains(playerUuid);
    }

    static void setClientFormActive(UUID playerUuid, boolean active) {
        if (active) {
            CLIENT_GHOST_FORM_PLAYERS.add(playerUuid);
        } else {
            CLIENT_GHOST_FORM_PLAYERS.remove(playerUuid);
        }
    }

    static boolean isPossessing(UUID playerUuid) {
        return POSSESSED_MOBS.containsKey(playerUuid);
    }

    static boolean isPossessed(UUID mobUuid) {
        return POSSESSED_MOBS.containsValue(mobUuid);
    }

    static MobEntity getPossessedMob(ServerPlayerEntity player) {
        UUID mobUuid = POSSESSED_MOBS.get(player.getUuid());
        if (mobUuid == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        Entity entity = world.getEntity(mobUuid);
        return entity instanceof MobEntity mob && mob.isAlive() ? mob : null;
    }

    static void possess(ServerPlayerEntity player, MobEntity mob) {
        endPossession(player);
        startPossession(player, mob);
    }

    static int wailOfTheDamned(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Wail of the Damned", remainingTicks);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d center = player.getEntityPos();
        Box area = player.getBoundingBox().expand(GHOST_WAIL_RADIUS);
        List<Entity> targets = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );
        for (Entity entity : targets) {
            if (player.squaredDistanceTo(entity) > GHOST_WAIL_RADIUS * GHOST_WAIL_RADIUS) {
                continue;
            }
            LivingEntity target = (LivingEntity) entity;
            target.damage(world, world.getDamageSources().playerAttack(player), GHOST_WAIL_DAMAGE);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    GHOST_WAIL_SLOWNESS_DURATION,
                    GHOST_WAIL_SLOWNESS_AMPLIFIER,
                    false,
                    true,
                    true
            ), player);
            Vec3d away = target.getEntityPos().subtract(center);
            double distance = away.length();
            if (distance > 0.001D) {
                Vec3d knockback = away.multiply(GHOST_WAIL_KNOCKBACK / distance);
                target.addVelocity(knockback.x, 0.5D, knockback.z);
                target.velocityDirty = true;
            }

            markSoul(player.getUuid(), target);
            Vec3d targetCenter = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            world.spawnParticles(
                    ParticleTypes.SCULK_SOUL,
                    targetCenter.x,
                    targetCenter.y,
                    targetCenter.z,
                    10,
                    0.3D,
                    0.3D,
                    0.3D,
                    0.05D
            );
        }

        world.spawnParticles(
                ParticleTypes.SCULK_SOUL,
                center.x,
                center.y + 1.0D,
                center.z,
                120,
                GHOST_WAIL_RADIUS,
                1.5D,
                GHOST_WAIL_RADIUS,
                0.08D
        );
        world.spawnParticles(
                ParticleTypes.SOUL,
                center.x,
                center.y + 1.0D,
                center.z,
                60,
                GHOST_WAIL_RADIUS * 0.6D,
                1.0D,
                GHOST_WAIL_RADIUS * 0.6D,
                0.04D
        );
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.PLAYERS,
                1.6F,
                0.5F
        );
        PowerCooldowns.setSecondPower(slotKey, GHOST_WAIL_COOLDOWN);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Wail of the Damned — souls marked! Soul Nova will detonate them."), true);
        return 1;
    }

    private static void markSoul(UUID ghostUuid, LivingEntity target) {
        SOUL_MARKED_ENTITIES
                .computeIfAbsent(ghostUuid, ignored -> new HashMap<>())
                .put(target.getUuid(), GHOST_SOUL_MARK_DURATION);
    }

    static void unleashSoulNova(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        UUID playerUuid = player.getUuid();
        Vec3d center = player.getEntityPos().add(0.0D, 1.0D, 0.0D);
        Box area = player.getBoundingBox().expand(GHOST_SOUL_NOVA_RADIUS);
        List<Entity> targets = world.getOtherEntities(
                player,
                area,
                entity -> entity instanceof LivingEntity livingEntity
                        && livingEntity.isAlive()
                        && !entity.isSpectator()
        );

        Map<UUID, Integer> marks = SOUL_MARKED_ENTITIES.get(playerUuid);
        Set<UUID> detonatedSouls = new HashSet<>();
        for (Entity entity : targets) {
            if (player.squaredDistanceTo(entity) > GHOST_SOUL_NOVA_RADIUS * GHOST_SOUL_NOVA_RADIUS) {
                continue;
            }
            LivingEntity target = (LivingEntity) entity;
            target.damage(world, world.getDamageSources().playerAttack(player), GHOST_SOUL_NOVA_DAMAGE);
            if (marks != null && marks.containsKey(target.getUuid())) {
                detonatedSouls.add(target.getUuid());
            }
            Vec3d away = target.getEntityPos().subtract(center);
            double distance = away.length();
            if (distance > 0.001D) {
                Vec3d knockback = away.multiply(2.6D / distance);
                target.addVelocity(knockback.x, 0.9D, knockback.z);
                target.velocityDirty = true;
            }
        }

        int detonations = 0;
        for (UUID soulUuid : detonatedSouls) {
            Entity soulEntity = world.getEntity(soulUuid);
            if (!(soulEntity instanceof LivingEntity markedTarget) || !markedTarget.isAlive()) {
                continue;
            }
            detonations++;
            markedTarget.damage(world, world.getDamageSources().playerAttack(player), GHOST_SOUL_NOVA_MARK_DAMAGE);

            Vec3d soulPos = markedTarget.getEntityPos().add(0.0D, markedTarget.getHeight() * 0.5D, 0.0D);
            world.spawnParticles(
                    ParticleTypes.SCULK_SOUL,
                    soulPos.x,
                    soulPos.y,
                    soulPos.z,
                    30,
                    1.2D,
                    1.2D,
                    1.2D,
                    0.12D
            );
            world.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    soulPos.x,
                    soulPos.y,
                    soulPos.z,
                    12,
                    0.6D,
                    0.6D,
                    0.6D,
                    0.06D
            );
            world.playSound(
                    null,
                    soulPos.x,
                    soulPos.y,
                    soulPos.z,
                    SoundEvents.ENTITY_GHAST_SHOOT,
                    SoundCategory.PLAYERS,
                    1.4F,
                    0.4F
            );

            Box chainArea = markedTarget.getBoundingBox().expand(GHOST_SOUL_NOVA_CHAIN_RADIUS);
            List<Entity> chainTargets = world.getOtherEntities(
                    player,
                    chainArea,
                    entity -> entity instanceof LivingEntity livingEntity
                            && livingEntity.isAlive()
                            && !entity.isSpectator()
                            && entity != markedTarget
            );
            for (Entity chainEntity : chainTargets) {
                if (chainEntity.squaredDistanceTo(markedTarget)
                        > GHOST_SOUL_NOVA_CHAIN_RADIUS * GHOST_SOUL_NOVA_CHAIN_RADIUS) {
                    continue;
                }
                LivingEntity chainTarget = (LivingEntity) chainEntity;
                chainTarget.damage(world, world.getDamageSources().playerAttack(player), GHOST_SOUL_NOVA_CHAIN_DAMAGE);
                Vec3d away = chainTarget.getEntityPos().subtract(markedTarget.getEntityPos());
                double distance = away.length();
                if (distance > 0.001D) {
                    Vec3d knockback = away.multiply(1.8D / distance);
                    chainTarget.addVelocity(knockback.x, 0.5D, knockback.z);
                    chainTarget.velocityDirty = true;
                }
            }
        }

        player.heal(GHOST_SOUL_NOVA_HEAL + detonations * GHOST_SOUL_NOVA_HEAL_PER_SOUL);
        if (marks != null) {
            marks.keySet().removeAll(detonatedSouls);
        }

        world.spawnParticles(
                ParticleTypes.SCULK_SOUL,
                center.x,
                center.y,
                center.z,
                200,
                GHOST_SOUL_NOVA_RADIUS,
                2.0D,
                GHOST_SOUL_NOVA_RADIUS,
                0.1D
        );
        world.spawnParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                center.x,
                center.y,
                center.z,
                120,
                GHOST_SOUL_NOVA_RADIUS * 0.5D,
                1.5D,
                GHOST_SOUL_NOVA_RADIUS * 0.5D,
                0.05D
        );
        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            double x = player.getX() + Math.cos(radians) * GHOST_SOUL_NOVA_RADIUS;
            double z = player.getZ() + Math.sin(radians) * GHOST_SOUL_NOVA_RADIUS;
            world.spawnParticles(ParticleTypes.SCULK_SOUL, x, player.getY() + 0.3D, z, 4, 0.15D, 0.5D, 0.15D, 0.02D);
        }
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.BLOCK_SCULK_CATALYST_BLOOM,
                SoundCategory.PLAYERS,
                2.0F,
                0.6F
        );
        if (detonations > 0) {
            player.sendMessage(Text.literal(
                    "Soul Nova — " + detonations + " marked " + (detonations == 1 ? "soul" : "souls") + " detonated!"
            ), true);
        } else {
            player.sendMessage(Text.literal("Soul Nova — the dead cry out, but no marked souls are claimed."), true);
        }
    }

    static void tickPlayer(ServerPlayerEntity player) {
        if (GHOST_FORM_PLAYERS.contains(player.getUuid()) && !player.getAbilities().flying) {
            // Ghost Form phases through walls only while flying; keep the
            // server-side state airborne so the form can't be dropped.
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }
        tickPossession(player);
    }

    static void tick(ServerWorld world) {
        if (SOUL_MARKED_ENTITIES.isEmpty()) {
            return;
        }

        SOUL_MARKED_ENTITIES.entrySet().removeIf(ownerEntry -> {
            ownerEntry.getValue().entrySet().removeIf(markEntry -> {
                int remainingTicks = markEntry.getValue() - 1;
                if (remainingTicks <= 0) {
                    return true;
                }
                markEntry.setValue(remainingTicks);
                Entity target = world.getEntity(markEntry.getKey());
                if (target != null && target.isAlive() && remainingTicks % GHOST_SOUL_MARK_TICK_INTERVAL == 0) {
                    Vec3d position = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
                    world.spawnParticles(
                            ParticleTypes.SCULK_SOUL,
                            position.x,
                            position.y,
                            position.z,
                            2,
                            0.25D,
                            0.25D,
                            0.25D,
                            0.02D
                    );
                }
                return false;
            });
            return ownerEntry.getValue().isEmpty();
        });
    }

    private static void startPossession(ServerPlayerEntity player, MobEntity mob) {
        UUID playerUuid = player.getUuid();
        POSSESSED_MOBS.put(playerUuid, mob.getUuid());
        player.setInvisible(true);
        mob.setAiDisabled(true);
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setPersistent();
        snapPlayerToMob(player, mob);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal(
                "You possess the " + mob.getDisplayName().getString() + "! Sneak to return to your spectral body."
        ), true);
    }

    private static void tickPossession(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        UUID mobUuid = POSSESSED_MOBS.get(playerUuid);
        if (mobUuid == null) {
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world)
                || !(world.getEntity(mobUuid) instanceof MobEntity mob)
                || !mob.isAlive()) {
            endPossession(player);
            return;
        }

        // Keep the possessed body docile and protected from fire.
        mob.setAiDisabled(true);
        mob.setTarget(null);
        mob.getNavigation().stop();
        if (!mob.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            mob.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE,
                    40,
                    0,
                    false,
                    false,
                    false
            ));
        }

        // Face the body wherever the player looks so input maps to first-person control.
        float yaw = player.getYaw();
        mob.setYaw(yaw);
        mob.setHeadYaw(yaw);
        mob.setBodyYaw(yaw);

        steerPossessedBody(mob, player.getPlayerInput(), yaw);

        // Lock the player to the body: player position = mob position, so moving the
        // mob moves the player and interactions happen at the body's position.
        snapPlayerToMob(player, mob);

        if (player.isSneaking()) {
            endPossession(player);
        }
    }

    private static void steerPossessedBody(MobEntity mob, PlayerInput input, float yaw) {
        double forward = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        // Vanilla's strafe convention: positive = left (A), negative = right (D).
        double strafe = (input.left() ? 1.0D : 0.0D) - (input.right() ? 1.0D : 0.0D);

        double yawRadians = Math.toRadians(yaw);
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);
        double dx = -sin * forward + cos * strafe;
        double dz = cos * forward + sin * strafe;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0.001D) {
            dx /= length;
            dz /= length;
        }
        // The movementSpeed field is only kept fresh by the mob's AI (MoveControl), which is
        // disabled while possessed, so read the underlying attribute instead.
        double speed = mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);

        double vy = mob.getVelocity().y;
        if (mob.isOnGround()) {
            vy = 0.0D;
        } else {
            vy = Math.max(vy - POSSESSION_GRAVITY, -POSSESSION_TERMINAL_FALL_SPEED);
        }
        if (input.jump() && mob.isOnGround()) {
            mob.jump();
            vy = mob.getVelocity().y;
        }

        mob.setVelocity(dx * speed, vy, dz * speed);
        mob.move(MovementType.SELF, new Vec3d(dx * speed, vy, dz * speed));
        mob.velocityDirty = true;
    }

    private static void snapPlayerToMob(ServerPlayerEntity player, MobEntity mob) {
        player.setPosition(mob.getX(), mob.getY(), mob.getZ());
        player.setVelocity(mob.getVelocity());
        player.velocityDirty = true;
    }

    private static void endPossession(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        UUID mobUuid = POSSESSED_MOBS.remove(playerUuid);
        if (mobUuid == null) {
            return;
        }

        player.setInvisible(false);
        if (player.getEntityWorld() instanceof ServerWorld world
                && world.getEntity(mobUuid) instanceof MobEntity mob) {
            mob.setAiDisabled(false);
            mob.getNavigation().stop();
            mob.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
        }
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("You release the body and return to your spectral form."), true);
    }

    static void removePlayer(ServerPlayerEntity player) {
        disableForm(player);
        UUID playerUuid = player.getUuid();
        SOUL_MARKED_ENTITIES.remove(playerUuid);
        CLIENT_GHOST_FORM_PLAYERS.remove(playerUuid);
    }

    static void clearAll() {
        GHOST_FORM_PLAYERS.clear();
        GHOST_PREVIOUS_GAMEMODES.clear();
        GHOST_PREVIOUS_FLY_SPEEDS.clear();
        CLIENT_GHOST_FORM_PLAYERS.clear();
        SOUL_MARKED_ENTITIES.clear();
        POSSESSED_MOBS.clear();
    }
}
