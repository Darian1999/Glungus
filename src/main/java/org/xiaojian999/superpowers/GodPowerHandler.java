package org.xiaojian999.superpowers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
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
    private static final double LASER_KILL_RADIUS = 1.0D;
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

    // ----- KP2 GIANT: 3x size toggle -----
    private static final float GIANT_SCALE = 3.0F;
    private static final float NORMAL_SCALE = 1.0F;
    private static final Set<UUID> GIANT_PLAYERS = new HashSet<>();

    // ----- KP3 TELEKINESIS: grab blocks/mobs and throw -----
    private static final double TK_RANGE = 32.0D;
    private static final double TK_HOLD_DISTANCE = 4.0D;
    private static final double TK_GIANT_HOLD_DISTANCE = 6.5D;
    private static final double TK_THROW_POWER = 2.8D;
    private static final Map<UUID, Integer> TK_HELD_ENTITY = new HashMap<>();

    // Thrown blocks that deal hardness-based damage on landing
    private static final Map<Integer, ThrownBlockInfo> THROWN_BLOCKS = new HashMap<>();
    private static class ThrownBlockInfo {
        ServerWorld world;
        BlockState state;
        float damage;
        Vec3d lastPos;
        UUID owner;
        ThrownBlockInfo(ServerWorld world, BlockState state, float damage, Vec3d lastPos, UUID owner) {
            this.world = world; this.state = state; this.damage = damage; this.lastPos = lastPos; this.owner = owner;
        }
    }

    private static final Set<UUID> GOD_MODE_PLAYERS = new HashSet<>();
    private static final Set<UUID> GOD_NOCLIP_PLAYERS = new HashSet<>();
    private static final Set<UUID> CLIENT_GOD_NOCLIP_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT_GOD_MODE_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ACTIVE_LASERS = new HashSet<>();
    private static final Map<UUID, GameMode> PREVIOUS_GAME_MODES = new HashMap<>();
    // Players who were granted temporary OP by God Mode (to revoke on disable)
    private static final Set<UUID> GOD_TEMP_OP = new HashSet<>();
    // Pending ascension: player must jump and reach apex before God Mode fully activates
    private static final Map<UUID, PendingAscension> PENDING_ASCENSION = new HashMap<>();

    private static class PendingAscension {
        final GameMode previousGameMode;
        int ticks;
        boolean hasRisen;
        double lastVy;
        double startY;
        double prevY;
        double maxY;
        PendingAscension(GameMode previousGameMode) {
            this.previousGameMode = previousGameMode;
            this.ticks = 0;
            this.hasRisen = false;
            this.lastVy = 0.0D;
            this.startY = 0.0D;
            this.prevY = 0.0D;
            this.maxY = 0.0D;
        }
    }
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

    private static void grantOpIfNeeded(ServerPlayerEntity player) {
        try {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) return;
            PlayerManager playerManager = server.getPlayerManager();
            PlayerConfigEntry entry = player.getPlayerConfigEntry();
            if (playerManager.isOperator(entry)) {
                return;
            }
            playerManager.addToOperators(
                    entry,
                    java.util.Optional.of(net.minecraft.command.permission.LeveledPermissionPredicate.OWNERS),
                    java.util.Optional.empty()
            );
            GOD_TEMP_OP.add(player.getUuid());
        } catch (Exception ignored) {}
    }

    private static void revokeTempOpIfGranted(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!GOD_TEMP_OP.contains(uuid)) {
            return;
        }
        try {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) {
                GOD_TEMP_OP.remove(uuid);
                return;
            }
            PlayerManager playerManager = server.getPlayerManager();
            PlayerConfigEntry entry = player.getPlayerConfigEntry();
            if (playerManager.isOperator(entry)) {
                playerManager.removeFromOperators(entry);
            }
        } catch (Exception ignored) {
        } finally {
            GOD_TEMP_OP.remove(uuid);
        }
    }

    private static void revokeTempOpIfGranted(UUID uuid, MinecraftServer server) {
        if (!GOD_TEMP_OP.contains(uuid)) {
            return;
        }
        try {
            if (server != null) {
                PlayerManager playerManager = server.getPlayerManager();
                // Need to find GameProfile – try to get player online first, else construct entry from UUID
                net.minecraft.server.network.ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
                if (online != null) {
                    PlayerConfigEntry entry = online.getPlayerConfigEntry();
                    if (playerManager.isOperator(entry)) {
                        playerManager.removeFromOperators(entry);
                    }
                } else {
                    // Offline: create entry from UUID (name unknown) and try to remove – OperatorList removal
                    // uses UUID comparison, so name is irrelevant
                    PlayerConfigEntry entry = new PlayerConfigEntry(uuid, "");
                    if (playerManager.isOperator(entry)) {
                        // isOperator for offline with empty name will still check ops list via UUID
                        // Use direct removal from op list to be safe even if isOperator returns false due to name check
                        playerManager.removeFromOperators(entry);
                    } else {
                        // Force removal attempt anyway (op list contains by UUID)
                        try {
                            playerManager.getOpList().remove(entry);
                        } catch (Exception ignored2) {}
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            GOD_TEMP_OP.remove(uuid);
        }
    }

    static int toggleGodMode(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (GOD_MODE_PLAYERS.contains(playerUuid)) {
            disableGodMode(player);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("God Mode disabled."), true);
            return 1;
        }
        if (PENDING_ASCENSION.containsKey(playerUuid)) {
            // Cancel pending ascension
            PENDING_ASCENSION.remove(playerUuid);
            PREVIOUS_GAME_MODES.remove(playerUuid);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("God Mode ascension cancelled."), true);
            return 1;
        }

        // Begin ascension: store previous game mode and force a jump; God Mode activates at apex
        GameMode previous = player.interactionManager.getGameMode();
        PREVIOUS_GAME_MODES.put(playerUuid, previous);
        PENDING_ASCENSION.put(playerUuid, new PendingAscension(previous));

        // Force automatic jump – set velocity directly and sync to client via EntityVelocityUpdateS2CPacket
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d pos = player.getEntityPos();
        Vec3d currentVel = player.getVelocity();
        // Consistent strong jump regardless of ground state; ensures visible apex even for creative/flying players
        double jumpVelocity = 0.92D;
        // Include jump boost if active
        var jumpEffect = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jumpEffect != null) {
            jumpVelocity += (jumpEffect.getAmplifier() + 1) * 0.1D;
        }
        // Preserve horizontal motion but damp slightly so jump is mostly vertical
        player.setVelocity(currentVel.x * 0.35D, jumpVelocity, currentVel.z * 0.35D);
        player.velocityDirty = true;
        // Ensure gravity applies – temporarily disable creative flight during ascension
        boolean wasFlying = player.getAbilities().flying;
        player.getAbilities().flying = false;
        // Keep allowFlying as previous permits, but not actively flying
        player.sendAbilitiesUpdate();
        // Force sync to client immediately (velocityDirty alone is not enough for ServerPlayerEntity)
        try {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        } catch (Exception ignored) {}
        // Also force client-side jump animation/status
        player.setOnGround(false);
        if (wasFlying) {
            // Impulse was larger, ensure client also sees vertical motion even if it was flying
            try {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
            } catch (Exception ignored2) {}
        }
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y + 0.1D, pos.z, 18, 0.3D, 0.2D, 0.3D, 0.05D);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.8F, 1.4F);
        PowerManager.sendPowerStatus(player);
        player.sendMessage(Text.literal("Ascending to godhood — reach the peak to ascend!"), true);
        return 1;
    }

    private static void activateGodModeAtApex(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (GOD_MODE_PLAYERS.contains(playerUuid)) {
            return;
        }
        GOD_MODE_PLAYERS.add(playerUuid);
        grantOpIfNeeded(player);
        // PREVIOUS_GAME_MODES already stored at jump initiation
        player.changeGameMode(GameMode.CREATIVE);
        player.noClip = false;
        player.fallDistance = 0.0F;
        // Stop vertical fall at apex for a clean hover and automatically start flying
        player.setVelocity(0.0D, 0.05D, 0.0D);
        player.velocityDirty = true;
        try {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        } catch (Exception ignored) {}
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        PowerManager.sendPowerStatus(player);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z, 42, 0.6D, 1.0D, 0.6D, 0.2D);
        world.spawnParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 90, 1.0D, 1.2D, 1.0D, 0.06D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.4F, 1.15F);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0F, 1.6F);
        player.sendMessage(Text.literal(
                "God Mode enabled — KP2 giant, KP3 telekinesis, KP7 bless, KP8 levitate, KP9 laser, KP0 smite, KP. blast, KPENTER nova, KP* omnipotence, KP/ banish, \\ noclip."
        ), true);
    }

    private static void tickPendingAscension(MinecraftServer server) {
        if (PENDING_ASCENSION.isEmpty()) {
            return;
        }
        for (UUID uuid : Set.copyOf(PENDING_ASCENSION.keySet())) {
            PendingAscension pending = PENDING_ASCENSION.get(uuid);
            if (pending == null) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                PENDING_ASCENSION.remove(uuid);
                if (player == null) {
                    PREVIOUS_GAME_MODES.remove(uuid);
                }
                continue;
            }
            pending.ticks++;
            double vy = player.getVelocity().y;
            double y = player.getY();
            if (pending.ticks == 1) {
                pending.startY = y;
                pending.prevY = y;
                pending.maxY = y;
            }
            if (y > pending.maxY) {
                pending.maxY = y;
            }
            if (vy > 0.05D || y > pending.startY + 0.15D) {
                pending.hasRisen = true;
            }
            boolean atApex = false;
            // Apex when upward motion stops: velocity near zero or Y starts decreasing after having risen
            if (pending.hasRisen && pending.ticks > 3 && (vy <= 0.03D || y <= pending.prevY - 0.01D)) {
                atApex = true;
            }
            // Fallback: if max height reached and now descending (Y at least 0.2 above start but decreasing)
            if (pending.hasRisen && pending.ticks > 6 && y < pending.maxY - 0.02D && pending.maxY > pending.startY + 0.4D) {
                atApex = true;
            }
            // If player landed after rising, also consider apex reached (e.g., short jump)
            if (pending.hasRisen && player.isOnGround() && pending.ticks > 8) {
                atApex = true;
            }
            if (atApex) {
                PENDING_ASCENSION.remove(uuid);
                activateGodModeAtApex(player);
                continue;
            }
            if (pending.ticks > 60) {
                // Timeout fallback – activate anyway to avoid soft-lock
                PENDING_ASCENSION.remove(uuid);
                activateGodModeAtApex(player);
                continue;
            }
            // Visual trail during ascent
            if (pending.ticks % 2 == 0 && player.getEntityWorld() instanceof ServerWorld sw) {
                Vec3d center = player.getEntityPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
                sw.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 1, 0.18D, 0.18D, 0.18D, 0.01D);
            }
            pending.lastVy = vy;
            pending.prevY = y;
        }
    }

    // ----- KP2: Giant toggle -----
    static int toggleGiant(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!isActive(uuid)) {
            return 0;
        }
        boolean becomingGiant = !GIANT_PLAYERS.contains(uuid);
        if (becomingGiant) {
            GIANT_PLAYERS.add(uuid);
            var attr = player.getAttributeInstance(EntityAttributes.SCALE);
            if (attr != null) {
                attr.setBaseValue(GIANT_SCALE);
            }
            try {
                player.calculateDimensions();
            } catch (Exception ignored) {}
            player.sendMessage(Text.literal("GOD GIANT — you are now 3x size! (KP2 to revert)"), true);
        } else {
            GIANT_PLAYERS.remove(uuid);
            var attr = player.getAttributeInstance(EntityAttributes.SCALE);
            if (attr != null) {
                attr.setBaseValue(NORMAL_SCALE);
            }
            try {
                player.calculateDimensions();
            } catch (Exception ignored) {}
            player.sendMessage(Text.literal("GOD GIANT — returned to normal size."), true);
        }
        PowerManager.sendPowerStatus(player);
        return 1;
    }

    static boolean isGiant(UUID uuid) {
        return GIANT_PLAYERS.contains(uuid);
    }

    // ----- KP3: Telekinesis -----
    static int toggleTelekinesis(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!isActive(uuid)) {
            return 0;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        // If already holding something, throw it
        if (TK_HELD_ENTITY.containsKey(uuid)) {
            int entityId = TK_HELD_ENTITY.get(uuid);
            Entity held = world.getEntityById(entityId);
            if (held != null) {
                Vec3d dir = player.getRotationVec(1.0F).normalize();
                held.setNoGravity(false);
                held.setVelocity(dir.x * TK_THROW_POWER, dir.y * TK_THROW_POWER + 0.25D, dir.z * TK_THROW_POWER);
                held.velocityDirty = true;
                // For living mobs, ensure they take fall/throw damage physics
                if (held instanceof LivingEntity living) {
                    living.setVelocity(dir.x * TK_THROW_POWER, dir.y * TK_THROW_POWER + 0.25D, dir.z * TK_THROW_POWER);
                    living.velocityDirty = true;
                }
                // If it's a falling block, register hardness-based impact damage
                // Scaled to 3 hearts (6 dmg) minimum, 25 hearts (50 dmg) maximum
                if (held instanceof FallingBlockEntity fbe) {
                    BlockState fState = fbe.getBlockState();
                    float hardness = getBlockHardness(fState, world, BlockPos.ofFloored(fbe.getEntityPos()));
                    if (hardness < 0) hardness = 5.0F;
                    float damage = hardness * 2.0F + 4.0F;
                    if (damage < 6.0F) damage = 6.0F; // 3 hearts minimum
                    if (damage > 50.0F) damage = 50.0F; // 25 hearts maximum (50 health)
                    THROWN_BLOCKS.put(fbe.getId(), new ThrownBlockInfo(world, fState, damage, fbe.getEntityPos(), uuid));
                }
                world.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.2F, 0.8F);
                world.spawnParticles(ParticleTypes.PORTAL, held.getX(), held.getY() + held.getHeight() * 0.5D, held.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.12D);
            }
            TK_HELD_ENTITY.remove(uuid);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Telekinesis — thrown!"), true);
            return 1;
        }
        // Otherwise try to grab
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d maxEnd = start.add(direction.multiply(TK_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d blockEnd = blockHit.getType() == HitResult.Type.MISS ? maxEnd : blockHit.getPos();
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, maxEnd, player.getBoundingBox().stretch(direction.multiply(TK_RANGE)).expand(1.0D), e -> (e instanceof MobEntity mob && mob.isAlive() && !mob.isSpectator()) || (e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && !(e instanceof ServerPlayerEntity)), TK_RANGE * TK_RANGE);
        boolean entityCloser = entityHit != null && (blockHit.getType() == HitResult.Type.MISS || entityHit.getPos().squaredDistanceTo(start) <= blockEnd.squaredDistanceTo(start));
        if (entityCloser) {
            Entity target = entityHit.getEntity();
            if (!(target instanceof MobEntity) && !(target instanceof LivingEntity)) {
                player.sendMessage(Text.literal("Telekinesis — can only grab mobs and blocks."), true);
                return 0;
            }
            target.setNoGravity(true);
            target.setVelocity(Vec3d.ZERO);
            target.velocityDirty = true;
            TK_HELD_ENTITY.put(uuid, target.getId());
            Vec3d pos = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 30, 0.5D, 0.6D, 0.5D, 0.05D);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.4F);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Telekinesis — grabbed " + target.getDisplayName().getString() + ". Press KP3 again to throw."), true);
            return 1;
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = world.getBlockState(hitPos);
            if (!isDestructible(state) || state.isAir()) {
                player.sendMessage(Text.literal("Telekinesis — cannot grab that block."), true);
                return 0;
            }
            // Check chunk loaded
            if (!world.getChunkManager().isChunkLoaded(hitPos.getX() >> 4, hitPos.getZ() >> 4)) {
                player.sendMessage(Text.literal("Telekinesis — chunk not loaded."), true);
                return 0;
            }
            // Remove block and spawn falling block entity to hold
            world.setBlockState(hitPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, hitPos, state);
            if (falling == null) {
                // Fallback: restore block if spawning failed
                world.setBlockState(hitPos, state, Block.NOTIFY_ALL);
                player.sendMessage(Text.literal("Telekinesis — failed to grab block."), true);
                return 0;
            }
            falling.setNoGravity(true);
            falling.setVelocity(Vec3d.ZERO);
            // Prevent instant drop/conversion
            falling.timeFalling = 1;
            TK_HELD_ENTITY.put(uuid, falling.getId());
            double fx = hitPos.getX() + 0.5D;
            double fy = hitPos.getY() + 0.5D;
            double fz = hitPos.getZ() + 0.5D;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), fx, fy, fz, 30, 0.4D, 0.4D, 0.4D, 0.08D);
            world.playSound(null, fx, fy, fz, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
            PowerManager.sendPowerStatus(player);
            player.sendMessage(Text.literal("Telekinesis — grabbed block. Press KP3 again to throw."), true);
            return 1;
        }
        player.sendMessage(Text.literal("Telekinesis failed — aim at a mob or block within " + (int) TK_RANGE + " blocks."), true);
        return 0;
    }

    static boolean isTelekinesisHolding(UUID uuid) {
        return TK_HELD_ENTITY.containsKey(uuid);
    }

    private static void tickTelekinesis(MinecraftServer server) {
        for (UUID uuid : Set.copyOf(TK_HELD_ENTITY.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            Integer entityId = TK_HELD_ENTITY.get(uuid);
            ServerWorld world = null;
            if (player != null && player.getEntityWorld() instanceof ServerWorld sw) {
                world = sw;
            }
            if (player == null || !isActive(uuid) || entityId == null || world == null) {
                // Drop without throw
                if (entityId != null && world != null) {
                    Entity e = world.getEntityById(entityId);
                    if (e != null) e.setNoGravity(false);
                } else if (entityId != null && player != null && player.getEntityWorld() instanceof ServerWorld sw2) {
                    Entity e = sw2.getEntityById(entityId);
                    if (e != null) e.setNoGravity(false);
                }
                TK_HELD_ENTITY.remove(uuid);
                if (player != null) PowerManager.sendPowerStatus(player);
                continue;
            }
            Entity held = world.getEntityById(entityId);
            if (held == null || !held.isAlive() || held.isRemoved()) {
                TK_HELD_ENTITY.remove(uuid);
                PowerManager.sendPowerStatus(player);
                continue;
            }
            // Update hold position in front of player
            Vec3d eye = player.getCameraPosVec(1.0F);
            Vec3d dir = player.getRotationVec(1.0F).normalize();
            double dist = GIANT_PLAYERS.contains(uuid) ? TK_GIANT_HOLD_DISTANCE : TK_HOLD_DISTANCE;
            Vec3d target = eye.add(dir.multiply(dist));
            // Offset so entity center is at target; for blocks/mobs the pos is feet
            double yOffset = held.getHeight() * 0.5D;
            // Keep entity at target height centered
            double tx = target.x;
            double ty = target.y - yOffset;
            double tz = target.z;
            // Teleport / set position
            held.setPosition(tx, ty, tz);
            held.setVelocity(Vec3d.ZERO);
            held.velocityDirty = true;
            held.setNoGravity(true);
            // Visual particles
            if (world.getTime() % 4 == 0) {
                world.spawnParticles(ParticleTypes.WAX_ON, held.getX(), held.getY() + held.getHeight() * 0.5D, held.getZ(), 1, 0.15D, 0.15D, 0.15D, 0.01D);
            }
            // Prevent falling block from landing while held
            if (held instanceof FallingBlockEntity fbe) {
                fbe.timeFalling = 1;
            }
        }
    }

    private static float getBlockHardness(BlockState state, ServerWorld world, BlockPos pos) {
        try {
            return state.getHardness(world, pos);
        } catch (Throwable t) {
            return 1.5F;
        }
    }

    private static void tickThrownBlocks(MinecraftServer server) {
        if (THROWN_BLOCKS.isEmpty()) return;
        for (Integer id : Set.copyOf(THROWN_BLOCKS.keySet())) {
            ThrownBlockInfo info = THROWN_BLOCKS.get(id);
            if (info == null) continue;
            ServerWorld world = info.world;
            Entity entity = world.getEntityById(id);
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                // Consider landed at last known pos
                Vec3d impact = info.lastPos != null ? info.lastPos : new Vec3d(0, 64, 0);
                // Try to get actual block pos from lastPos
                applyBlockImpact(world, info.state, info.damage, impact, info.owner);
                THROWN_BLOCKS.remove(id);
                continue;
            }
            // Update last known pos
            info.lastPos = entity.getEntityPos();
            // Detect landing: falling block on ground or velocity near zero after falling
            boolean onGround = entity.isOnGround();
            boolean velNearZero = entity.getVelocity().lengthSquared() < 0.02D;
            // For FallingBlockEntity, timeFalling > 5 and onGround indicates impact
            if (entity instanceof FallingBlockEntity fbe) {
                if (fbe.isOnGround() || (onGround && fbe.timeFalling > 3)) {
                    Vec3d impact = entity.getEntityPos();
                    applyBlockImpact(world, info.state, info.damage, impact, info.owner);
                    // Let vanilla place the block; we just dealt damage
                    THROWN_BLOCKS.remove(id);
                    // Give it a little bounce prevention: ensure it lands
                    continue;
                }
                // Also if fallen and now has very low velocity for a few ticks
                if (velNearZero && fbe.timeFalling > 20) {
                    Vec3d impact = entity.getEntityPos();
                    applyBlockImpact(world, info.state, info.damage, impact, info.owner);
                    THROWN_BLOCKS.remove(id);
                }
            } else if (onGround && velNearZero) {
                Vec3d impact = entity.getEntityPos();
                applyBlockImpact(world, info.state, info.damage, impact, info.owner);
                THROWN_BLOCKS.remove(id);
            }
            // Timeout: if falling for > 10 seconds without landing, clean up
            if (entity.age > 200) {
                THROWN_BLOCKS.remove(id);
            }
        }
    }

    private static void applyBlockImpact(ServerWorld world, BlockState state, float damage, Vec3d impactPos, UUID ownerUuid) {
        // Radius scales slightly with hardness/damage: base 2.5 + damage*0.05
        double radius = 2.5D + (damage * 0.04D);
        if (radius > 5.0D) radius = 5.0D;
        Box box = new Box(impactPos.x - radius, impactPos.y - radius, impactPos.z - radius,
                impactPos.x + radius, impactPos.y + radius, impactPos.z + radius);
        ServerPlayerEntity owner = ownerUuid != null ? world.getServer().getPlayerManager().getPlayer(ownerUuid) : null;
        List<Entity> targets = world.getOtherEntities(null, box, e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && (owner == null || !e.getUuid().equals(ownerUuid)));
        int hit = 0;
        for (Entity e : targets) {
            if (e.squaredDistanceTo(impactPos) > radius * radius) continue;
            LivingEntity le = (LivingEntity) e;
            // Use magic damage for telekinesis impacts so God Mode's instant-kill melee handler
            // (LivingEntityMixin: playerAttack with God attacker => setHealth(0)) does not
            // one-shot wardens with soft blocks like grass. Damage is already hardness-scaled
            // (6 = 3 hearts min, 50 = 25 hearts max).
            le.damage(world, world.getDamageSources().magic(), damage);
            // Knockback away from impact
            Vec3d away = e.getEntityPos().subtract(impactPos).normalize();
            if (away.lengthSquared() > 0.001D) {
                double kb = 0.6D + damage * 0.02D;
                e.addVelocity(away.x * kb, 0.35D + damage * 0.01D, away.z * kb);
                e.velocityDirty = true;
            }
            hit++;
        }
        if (hit > 0 || true) {
            // Impact effects even without hit
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), impactPos.x, impactPos.y + 0.5D, impactPos.z, 40, radius * 0.5D, 0.6D, radius * 0.5D, 0.12D);
            world.spawnParticles(ParticleTypes.POOF, impactPos.x, impactPos.y + 0.2D, impactPos.z, 12, 0.5D, 0.3D, 0.5D, 0.08D);
            world.playSound(null, impactPos.x, impactPos.y, impactPos.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.2F, 0.7F);
            world.playSound(null, impactPos.x, impactPos.y, impactPos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 0.9F);
        }
    }

    static boolean isActive(UUID playerUuid) {
        return GOD_MODE_PLAYERS.contains(playerUuid);
    }

    static boolean isPending(UUID playerUuid) {
        return PENDING_ASCENSION.containsKey(playerUuid);
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

    static boolean isClientGodModeActive(UUID playerUuid) {
        return CLIENT_GOD_MODE_PLAYERS.contains(playerUuid);
    }

    static void setClientGodModeActive(UUID playerUuid, boolean active) {
        if (active) {
            CLIENT_GOD_MODE_PLAYERS.add(playerUuid);
        } else {
            CLIENT_GOD_MODE_PLAYERS.remove(playerUuid);
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
        tickPendingAscension(server);
        tickCooldowns();
        tickTelekinesis(server);
        tickThrownBlocks(server);
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
        CLIENT_GOD_MODE_PLAYERS.remove(playerUuid);
        PENDING_ASCENSION.remove(playerUuid);
        OMNIPOTENCE_TICKS.remove(playerUuid);
        revokeTempOpIfGranted(player);
        // Restore normal size if giant
        if (GIANT_PLAYERS.remove(playerUuid)) {
            var attr = player.getAttributeInstance(EntityAttributes.SCALE);
            if (attr != null) {
                attr.setBaseValue(NORMAL_SCALE);
            }
            try { player.calculateDimensions(); } catch (Exception ignored) {}
        }
        // Release telekinesis hold without throw
        Integer heldId = TK_HELD_ENTITY.remove(playerUuid);
        if (heldId != null && player.getEntityWorld() instanceof ServerWorld world) {
            Entity held = world.getEntityById(heldId);
            if (held != null) {
                held.setNoGravity(false);
                held.velocityDirty = true;
                if (held instanceof FallingBlockEntity fbe) {
                    fbe.timeFalling = 1;
                }
            }
        }
        // HACKY LIGHT cleanup: remove the fake Night Vision we applied; no block to remove.
        player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        GameMode previousGameMode = PREVIOUS_GAME_MODES.remove(playerUuid);
        if (previousGameMode != null && player.interactionManager.getGameMode() == GameMode.CREATIVE) {
            player.changeGameMode(previousGameMode);
        }
        player.noClip = false;
        PowerManager.sendPowerStatus(player);
    }

    static void removePlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        disableGodMode(player);
        // Ensure temp OP is revoked even if disableGodMode failed to (e.g., server null)
        if (GOD_TEMP_OP.contains(playerUuid)) {
            revokeTempOpIfGranted(player);
        }
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
        CLIENT_GOD_MODE_PLAYERS.remove(playerUuid);
        PENDING_ASCENSION.remove(playerUuid);
        PREVIOUS_GAME_MODES.remove(playerUuid);
        GIANT_PLAYERS.remove(playerUuid);
        TK_HELD_ENTITY.remove(playerUuid);
        // Clean up any thrown blocks owned by this player (they will still land and deal damage, but we keep tracking for damage)
        // We keep them in THROWN_BLOCKS until they land, so do not remove here; just allow them to still impact.
        // If we want to cancel, uncomment: THROWN_BLOCKS.entrySet().removeIf(e -> e.getValue().owner.equals(playerUuid));
        var attr = player.getAttributeInstance(EntityAttributes.SCALE);
        if (attr != null && attr.getBaseValue() != NORMAL_SCALE) {
            attr.setBaseValue(NORMAL_SCALE);
            try { player.calculateDimensions(); } catch (Exception ignored) {}
        }
    }

    static void clearAll() {
        GOD_MODE_PLAYERS.clear();
        GOD_NOCLIP_PLAYERS.clear();
        CLIENT_GOD_NOCLIP_PLAYERS.clear();
        CLIENT_GOD_MODE_PLAYERS.clear();
        PENDING_ASCENSION.clear();
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
        GIANT_PLAYERS.clear();
        TK_HELD_ENTITY.clear();
        THROWN_BLOCKS.clear();
        GOD_TEMP_OP.clear();
    }

    static void clearAll(MinecraftServer server) {
        // Revoke any temporary OPs still held (e.g., server stopped while God Mode active)
        for (UUID uuid : Set.copyOf(GOD_TEMP_OP)) {
            revokeTempOpIfGranted(uuid, server);
        }
        clearAll();
    }
}
