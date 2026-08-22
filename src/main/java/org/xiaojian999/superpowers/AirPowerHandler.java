package org.xiaojian999.superpowers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.xiaojian999.superpowers.math.GlungFastMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Air powers: flight, the Wind Burst push, and the Tempest Tornado ultimate. */
final class AirPowerHandler {
    private static final int AIR_PUSH_COOLDOWN = 60;
    private static final double AIR_PUSH_RANGE = 20.0D;
    private static final double AIR_PUSH_CONE_DOT = 0.5D;
    private static final int AIR_TORNADO_DURATION = 80;
    private static final double AIR_TORNADO_RADIUS = 8.0D;
    private static final float AIR_TORNADO_DAMAGE = 3.5F;

    private static final Set<UUID> AIR_FLIGHT_PLAYERS = new HashSet<>();
    private static final Map<ServerWorld, List<ActiveTornado>> ACTIVE_TORNADOES = new HashMap<>();

    private AirPowerHandler() {
    }

    static int toggleFlight(ServerPlayerEntity player) {
        if (AIR_FLIGHT_PLAYERS.contains(player.getUuid())) {
            disableFlight(player);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Air flight disabled."), true);
            return 1;
        }

        AIR_FLIGHT_PLAYERS.add(player.getUuid());
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Air flight enabled. Gravity reduced by 15%."), true);
        return 1;
    }

    static void disableFlight(ServerPlayerEntity player) {
        if (!AIR_FLIGHT_PLAYERS.remove(player.getUuid())) {
            return;
        }

        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
    }

    static boolean isFlightActive(UUID playerUuid) {
        return AIR_FLIGHT_PLAYERS.contains(playerUuid);
    }

    public static boolean isAirFlightActive(Entity entity) {
        return entity instanceof ServerPlayerEntity player && AIR_FLIGHT_PLAYERS.contains(player.getUuid());
    }

    static int pushAirCone(ServerPlayerEntity player, SlotKey slotKey) {
        int remainingTicks = PowerCooldowns.secondPowerRemaining(slotKey);
        if (remainingTicks > 0) {
            PowerManager.sendPowerStatus(player);
            PowerManager.sendCooldownMessage(player, "Wind Burst", remainingTicks);
            return 0;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Box searchBox = player.getBoundingBox()
                .stretch(direction.multiply(AIR_PUSH_RANGE))
                .expand(4.0D);
        List<Entity> targets = world.getOtherEntities(
                player,
                searchBox,
                entity -> !entity.isSpectator() && entity.canHit() && entity.isAlive()
        );
        int pushedEntities = 0;
        for (Entity target : targets) {
            Vec3d toTarget = target.getEntityPos().subtract(start);
            double distance = toTarget.length();
            if (distance < 0.01D || distance > AIR_PUSH_RANGE) {
                continue;
            }
            if (direction.dotProduct(toTarget.normalize()) < AIR_PUSH_CONE_DOT) {
                continue;
            }

            double force = 2.0D + (AIR_PUSH_RANGE - distance) / AIR_PUSH_RANGE;
            Vec3d push = direction.multiply(force).add(0.0D, 0.35D, 0.0D);
            target.addVelocity(push);
            target.velocityDirty = true;
            pushedEntities++;
        }

        PowerCooldowns.setSecondPower(slotKey, AIR_PUSH_COOLDOWN);
        world.spawnParticles(ParticleTypes.CLOUD, start.x, start.y, start.z, 90, 3.0D, 2.0D, 3.0D, 0.12D);
        world.spawnParticles(ParticleTypes.GUST, start.x + direction.x * 3.0D, start.y + direction.y * 3.0D, start.z + direction.z * 3.0D, 18, 1.5D, 1.5D, 1.5D, 0.08D);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_WHIRL, SoundCategory.PLAYERS, 1.4F, 1.0F);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Wind Burst launched — " + pushedEntities + " entities pushed."), true);
        return 1;
    }

    static void startTornado(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        ACTIVE_TORNADOES.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new ActiveTornado(player.getUuid(), AIR_TORNADO_DURATION));

        Vec3d center = player.getEntityPos().add(0.0D, 1.0D, 0.0D);
        world.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 120, 4.0D, 3.0D, 4.0D, 0.15D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BREEZE_WHIRL, SoundCategory.PLAYERS, 2.2F, 0.65F);
        player.sendMessage(Text.literal("Tempest Tornado unleashed!"), true);
    }

    static void tickPlayer(ServerPlayerEntity player) {
        if (AIR_FLIGHT_PLAYERS.contains(player.getUuid()) && !player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
    }

    static void tick(ServerWorld world) {
        List<ActiveTornado> tornadoes = ACTIVE_TORNADOES.get(world);
        if (tornadoes == null) {
            return;
        }

        for (int index = tornadoes.size() - 1; index >= 0; index--) {
            ActiveTornado tornado = tornadoes.get(index);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(tornado.ownerUuid);
            if (owner == null || owner.getEntityWorld() != world) {
                tornadoes.remove(index);
                continue;
            }

            tornado.remainingTicks--;
            tornado.elapsedTicks++;
            Vec3d center = owner.getEntityPos().add(0.0D, 1.0D, 0.0D);
            Box area = owner.getBoundingBox().expand(AIR_TORNADO_RADIUS, 5.0D, AIR_TORNADO_RADIUS);
            List<Entity> targets = world.getOtherEntities(
                    owner,
                    area,
                    entity -> !entity.isSpectator() && entity.canHit() && entity.isAlive()
            );

            for (Entity target : targets) {
                Vec3d offset = target.getEntityPos().subtract(center);
                // GlungFastMath: fast hypot + fast invSqrt for horizontal distance
                double horizontalDistance = GlungFastMath.hypotFast(offset.x, offset.z);
                if (horizontalDistance > AIR_TORNADO_RADIUS || Math.abs(offset.y) > 5.0D) {
                    continue;
                }

                // Use GlungFastMath tangent/pull helpers (table trig, fast normalize)
                Vec3d tangent = GlungFastMath.tornadoTangent(center, target.getEntityPos(), 0.36D);
                Vec3d pull = horizontalDistance < 0.01D ? Vec3d.ZERO
                        : GlungFastMath.tornadoPull(center, target.getEntityPos(), 0.09D);
                double lift = 0.42D + Math.max(0.0D, AIR_TORNADO_RADIUS - horizontalDistance) * 0.035D;
                target.addVelocity(tangent.x + pull.x, lift, tangent.z + pull.z);
                target.velocityDirty = true;

                if (target instanceof LivingEntity livingTarget && tornado.elapsedTicks % 10 == 0) {
                    livingTarget.damage(world, world.getDamageSources().playerAttack(owner), AIR_TORNADO_DAMAGE);
                }
            }

            if (tornado.elapsedTicks % 2 == 0) {
                for (int angle = 0; angle < 360; angle += 30) {
                    // GlungFastMath: table-based sin/cos, no toRadians allocation
                    double radians = angle * GlungFastMath.DEG_TO_RAD + tornado.elapsedTicks * 0.18D;
                    double radius = 2.0D + ((angle / 30 + tornado.elapsedTicks / 4) % 5) * 1.25D;
                    double x = center.x + GlungFastMath.fastCos(radians) * radius;
                    double z = center.z + GlungFastMath.fastSin(radians) * radius;
                    double y = center.y - 2.0D + ((angle / 30 + tornado.elapsedTicks) % 12) * 0.65D;
                    world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.25D, 0.3D, 0.25D, 0.02D);
                    world.spawnParticles(ParticleTypes.GUST, x, y, z, 1, 0.1D, 0.1D, 0.1D, 0.03D);
                }
            }

            if (tornado.remainingTicks <= 0) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 1.5F, 0.8F);
                tornadoes.remove(index);
            }
        }

        if (tornadoes.isEmpty()) {
            ACTIVE_TORNADOES.remove(world);
        }
    }

    static void removePlayer(UUID playerUuid) {
        AIR_FLIGHT_PLAYERS.remove(playerUuid);
    }

    static void clearAll() {
        AIR_FLIGHT_PLAYERS.clear();
        ACTIVE_TORNADOES.clear();
    }

    private static final class ActiveTornado {
        private final UUID ownerUuid;
        private final int durationTicks;
        private int remainingTicks;
        private int elapsedTicks;

        private ActiveTornado(UUID ownerUuid, int durationTicks) {
            this.ownerUuid = ownerUuid;
            this.durationTicks = durationTicks;
            this.remainingTicks = durationTicks;
        }
    }
}
