package org.xiaojian999.superpowers.god;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import org.xiaojian999.superpowers.GodPowerHandler;

import java.util.List;

/**
 * 9 curated extra god-only Singleplayer commands — only the useful, distinctive ones remain.
 * Removed 29 useless/duplicative/singleplayer-irrelevant/broken commands:
 * godfly, godinvisible, godinvulnerable, godfireproof, godwaterbreathing, godjump, godhaste,
 * godstrength, godnightvision, godclearinv, godenchant, godgive, godgamemode, godlocate, godsay,
 * godtimeadd, godsun, godstorm, godhealradius, godkillaura, godsummon, godtphere, godrename, godlore,
 * godxp, godlevel, godbiome, godresistance, godregen
 */
public final class GodExtraCommands {
    private GodExtraCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 1. godcure — purification distinct from heal (removes negatives, keeps buffs)
        dispatcher.register(CommandManager.literal("godcure")
                .executes(GodExtraCommands::executeGodCure));

        // 2. godextinguish — extinguish self + nearby burning entities
        dispatcher.register(CommandManager.literal("godextinguish")
                .executes(GodExtraCommands::executeGodExtinguish));

        // 3. godlight — divine light (NV + glowing + particles), distinct from nightvision toggle
        dispatcher.register(CommandManager.literal("godlight")
                .executes(GodExtraCommands::executeGodLight));

        // 4. godvanish — divine stealth (kept over godinvisible duplicate)
        dispatcher.register(CommandManager.literal("godvanish")
                .executes(GodExtraCommands::executeGodVanish));

        // 5. godworldborder <size>
        dispatcher.register(CommandManager.literal("godworldborder")
                .then(CommandManager.argument("size", DoubleArgumentType.doubleArg(10, 59999968))
                        .executes(GodExtraCommands::executeGodWorldBorder)));

        // 6. godspawnpoint
        dispatcher.register(CommandManager.literal("godspawnpoint")
                .executes(GodExtraCommands::executeGodSpawnPoint));

        // 7. godspeedreset
        dispatcher.register(CommandManager.literal("godspeedreset")
                .executes(GodExtraCommands::executeGodSpeedReset));

        // 8. godlaunch [power]
        dispatcher.register(CommandManager.literal("godlaunch")
                .executes(ctx -> executeGodLaunch(ctx, 2.0))
                .then(CommandManager.argument("power", DoubleArgumentType.doubleArg(0.5, 5.0))
                        .executes(ctx -> executeGodLaunch(ctx, DoubleArgumentType.getDouble(ctx, "power")))));

        // 9. godfreeze [radius] — godly ice/lava convert + slowness
        dispatcher.register(CommandManager.literal("godfreeze")
                .executes(ctx -> executeGodFreeze(ctx, 15))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(5, 50))
                        .executes(ctx -> executeGodFreeze(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static ServerPlayerEntity requireGod(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("§cOnly players can use god commands."));
            return null;
        }
        if (!GodPowerHandler.isActive(player.getUuid())) {
            source.sendError(Text.literal("§cYou must be in God Mode to use this command. Enable with /superpowers god"));
            return null;
        }
        return player;
    }

    private static int executeGodCure(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        List<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect>> harmful = List.of(
                StatusEffects.WITHER, StatusEffects.POISON, StatusEffects.WEAKNESS,
                StatusEffects.SLOWNESS, StatusEffects.MINING_FATIGUE, StatusEffects.BLINDNESS,
                StatusEffects.NAUSEA, StatusEffects.DARKNESS, StatusEffects.HUNGER, StatusEffects.UNLUCK
        );
        int removed = 0;
        for (var eff : harmful) {
            if (p.hasStatusEffect(eff)) {
                p.removeStatusEffect(eff);
                removed++;
            }
        }
        p.extinguish();
        p.setAir(p.getMaxAir());
        int finalRemoved = removed;
        ctx.getSource().sendFeedback(() -> Text.literal("§aCured " + finalRemoved + " negative effects, extinguished."), false);
        return 1;
    }

    private static int executeGodExtinguish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        p.extinguish();
        int count = 0;
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity le && le.isOnFire() && le.squaredDistanceTo(p) < 100) {
                le.extinguish();
                count++;
            }
        }
        int finalCount = count;
        ctx.getSource().sendFeedback(() -> Text.literal("§aExtinguished you + " + finalCount + " nearby burning entities."), false);
        return 1;
    }

    private static int executeGodLight(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, -1, 0, false, false, true));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false, true));
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        Vec3d center = p.getEntityPos().add(0, p.getHeight() * 0.5, 0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, center.x, center.y, center.z, 30, 1, 1, 1, 0.05);
        ctx.getSource().sendFeedback(() -> Text.literal("§aDivine light bestowed (NV+Glowing+particles)."), false);
        return 1;
    }

    private static int executeGodVanish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            ctx.getSource().sendFeedback(() -> Text.literal("§cVanish off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aVanished (invisible)."), false);
        }
        return 1;
    }

    private static int executeGodWorldBorder(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        double size = DoubleArgumentType.getDouble(ctx, "size");
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        world.getWorldBorder().setSize(size);
        ctx.getSource().sendFeedback(() -> Text.literal("§aWorldBorder size → " + (int) size), false);
        return 1;
    }

    private static int executeGodSpawnPoint(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        BlockPos pos = p.getBlockPos();
        try {
            world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()));
        } catch (Exception e) {
            try {
                world.getServer().getOverworld().setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()));
            } catch (Exception ignored) {}
        }
        try {
            p.setSpawnPoint(new ServerPlayerEntity.Respawn(
                    WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()), true), true);
        } catch (Exception ignored) {}
        ctx.getSource().sendFeedback(() -> Text.literal("§aSpawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        return 1;
    }

    private static int executeGodSpeedReset(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.getAbilities().setFlySpeed(0.05F);
        p.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal("§aFly speed reset to 0.05"), false);
        return 1;
    }

    private static int executeGodLaunch(CommandContext<ServerCommandSource> ctx, double power) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        Vec3d look = p.getRotationVec(1.0F).normalize();
        double px = look.x * power;
        double py = 0.8 + power * 0.6;
        double pz = look.z * power;
        p.setVelocity(px, py, pz);
        p.velocityDirty = true;
        try { p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p)); } catch (Exception ignored) {}
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§aLaunched (%.2f)", power)), false);
        return 1;
    }

    private static int executeGodFreeze(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        BlockPos center = p.getBlockPos();
        int frozen = 0;
        int slowed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx*dx + dz*dz > radius*radius) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    var state = world.getBlockState(pos);
                    if (state.isOf(Blocks.WATER) && world.getBlockState(pos.up()).isAir()) {
                        world.setBlockState(pos, Blocks.ICE.getDefaultState());
                        frozen++;
                    } else if (state.isOf(Blocks.LAVA)) {
                        world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
                        frozen++;
                    }
                }
            }
        }
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity le && le.isAlive() && le.squaredDistanceTo(p) <= radius*radius) {
                if (le != p) {
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2, false, true, true));
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 1, false, true, true));
                    slowed++;
                }
            }
        }
        int finalFrozen = frozen;
        int finalSlowed = slowed;
        ctx.getSource().sendFeedback(() -> Text.literal("§bFroze " + finalFrozen + " blocks, slowed " + finalSlowed + " entities (r=" + radius + ")."), false);
        return 1;
    }
}
