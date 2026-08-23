package org.xiaojian999.superpowers.god;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.xiaojian999.superpowers.GodPowerHandler;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 30 god-only commands. Every command checks {@link GodPowerHandler#isActive(java.util.UUID)}
 * and rejects non-gods with a friendly error.
 * <p>
 * The showcase /gravity command multiplies {@link GodWorldState#getGravityMultiplier()} which is
 * applied in {@link org.xiaojian999.superpowers.mixin.EntityGravityMixin} to every entity's
 * final gravity (world-wide, not just the player).
 */
public final class GodCommands {
    private GodCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 1. /gravity <value 0.0..5.0> — world-wide gravity multiplier
        dispatcher.register(CommandManager.literal("gravity")
                .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0D, 5.0D))
                        .executes(GodCommands::executeGravity)));

        // 2. /gravityreset — reset gravity to normal
        dispatcher.register(CommandManager.literal("gravityreset")
                .executes(GodCommands::executeGravityReset));

        // 3. /godheal [target] — fully heal self or target
        dispatcher.register(CommandManager.literal("godheal")
                .executes(ctx -> executeGodHeal(ctx, null))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeGodHeal(ctx, EntityArgumentType.getPlayer(ctx, "target")))));

        // 4. /godfeed [target]
        dispatcher.register(CommandManager.literal("godfeed")
                .executes(ctx -> executeGodFeed(ctx, null))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeGodFeed(ctx, EntityArgumentType.getPlayer(ctx, "target")))));

        // 5. /godspeed <value 0.01..0.5>
        dispatcher.register(CommandManager.literal("godspeed")
                .then(CommandManager.argument("speed", FloatArgumentType.floatArg(0.01F, 0.5F))
                        .executes(GodCommands::executeGodSpeed)));

        // 6. /godtime <preset|ticks> — day/night/noon/midnight/sunrise/sunset or integer
        dispatcher.register(CommandManager.literal("godtime")
                .then(CommandManager.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("day", "noon", "sunset", "night", "midnight", "sunrise", "1000", "6000", "12000", "13000", "18000", "23000"), b))
                        .executes(GodCommands::executeGodTime)));

        // 7. /godweather <clear|rain|thunder>
        dispatcher.register(CommandManager.literal("godweather")
                .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("clear", "rain", "thunder"), b))
                        .executes(GodCommands::executeGodWeather)));

        // 8. /godsmite — delegated to GodPowerHandler.smiteTarget
        dispatcher.register(CommandManager.literal("godsmite")
                .executes(GodCommands::executeGodSmite));

        // 9. /godannihilate
        dispatcher.register(CommandManager.literal("godannihilate")
                .executes(GodCommands::executeGodAnnihilate));

        // 10. /godnova
        dispatcher.register(CommandManager.literal("godnova")
                .executes(GodCommands::executeGodNova));

        // 11. /godomnipotence
        dispatcher.register(CommandManager.literal("godomnipotence")
                .executes(GodCommands::executeGodOmnipotence));

        // 12. /godbanish
        dispatcher.register(CommandManager.literal("godbanish")
                .executes(GodCommands::executeGodBanish));

        // 13. /godbless
        dispatcher.register(CommandManager.literal("godbless")
                .executes(GodCommands::executeGodBless));

        // 14. /godlevitate
        dispatcher.register(CommandManager.literal("godlevitate")
                .executes(GodCommands::executeGodLevitate));

        // 15. /godgiant
        dispatcher.register(CommandManager.literal("godgiant")
                .executes(GodCommands::executeGodGiant));

        // 16. /godnoclip
        dispatcher.register(CommandManager.literal("godnoclip")
                .executes(GodCommands::executeGodNoClip));

        // 17. /explode <power 0.5..10.0> — explosion at crosshair
        dispatcher.register(CommandManager.literal("explode")
                .then(CommandManager.argument("power", FloatArgumentType.floatArg(0.5F, 10.0F))
                        .executes(GodCommands::executeExplode)));

        // 18. /killall [radius] [type] — kill mobs
        dispatcher.register(CommandManager.literal("killall")
                .executes(ctx -> executeKillAll(ctx, 0, "all"))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> executeKillAll(ctx, IntegerArgumentType.getInteger(ctx, "radius"), "all"))
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(List.of("all", "hostile", "passive"), b))
                                .executes(ctx -> executeKillAll(ctx, IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "type")))))
                .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("all", "hostile", "passive"), b))
                        .executes(ctx -> executeKillAll(ctx, 0, StringArgumentType.getString(ctx, "type")))));

        // 19. /butcher [radius] — kill hostile mobs around
        dispatcher.register(CommandManager.literal("butcher")
                .executes(ctx -> executeButcher(ctx, 64))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> executeButcher(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));

        // 20. /clearitems [radius] — delete item entities
        dispatcher.register(CommandManager.literal("clearitems")
                .executes(ctx -> executeClearItems(ctx, 64))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> executeClearItems(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));

        // 21. /summonhorde <entity> <count> [radius]
        dispatcher.register(CommandManager.literal("summonhorde")
                .then(CommandManager.argument("entity", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pig", "cow", "sheep", "warden", "blaze", "ghast"), b))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> executeSummonHorde(ctx, StringArgumentType.getString(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "count"), 8))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> executeSummonHorde(ctx, StringArgumentType.getString(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "radius")))))));

        // 22. /lightning [x y z] — if no coords, strike crosshair; else strike coords
        dispatcher.register(CommandManager.literal("lightning")
                .executes(GodCommands::executeLightningLook)
                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                        .executes(GodCommands::executeLightningCoords)))));

        // 23. /healall — heal every online player
        dispatcher.register(CommandManager.literal("healall")
                .executes(GodCommands::executeHealAll));

        // 24. /feedall — feed every online player
        dispatcher.register(CommandManager.literal("feedall")
                .executes(GodCommands::executeFeedAll));

        // 25. /repair — repair held item
        dispatcher.register(CommandManager.literal("repair")
                .executes(GodCommands::executeRepair));

        // 26. /godscale <value 0.5..5.0>
        dispatcher.register(CommandManager.literal("godscale")
                .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.2D, 5.0D))
                        .executes(GodCommands::executeGodScale)));

        // 27. /day — shortcut set day
        dispatcher.register(CommandManager.literal("day")
                .executes(ctx -> executeTimeShortcut(ctx, 1000L, "day")));

        // 28. /night — shortcut set night
        dispatcher.register(CommandManager.literal("night")
                .executes(ctx -> executeTimeShortcut(ctx, 13000L, "night")));

        // 29. /godtp <x y z> OR /godtp <player> — teleport
        dispatcher.register(CommandManager.literal("godtp")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(GodCommands::executeGodTpPlayer))
                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                        .executes(GodCommands::executeGodTpCoords)))));

        // 30. /godhelp — list all 30 god commands
        dispatcher.register(CommandManager.literal("godhelp")
                .executes(GodCommands::executeGodHelp));

        // Also add generic /gods alias for help? Not counting.
    }

    // ---- God check helper ----
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

    // ---- 1. gravity ----
    private static int executeGravity(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requireGod(ctx);
        if (player == null) return 0;
        double value = DoubleArgumentType.getDouble(ctx, "value");
        GodWorldState.setGravityMultiplier(value);
        double actual = GodWorldState.getGravityMultiplier();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        // Broadcast to all players in that world
        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal("§6[God] §eGravity set to §6" + String.format("%.3f", actual) + " §7(1.0 = normal, 0.0 = zero-G, 2.0 = heavy) §8by " + player.getDisplayName().getString()), false);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Gravity set to " + String.format("%.3f", actual) + " — affects EVERY entity in the world."), false);
        return 1;
    }

    private static int executeGravityReset(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requireGod(ctx);
        if (player == null) return 0;
        GodWorldState.reset();
        ctx.getSource().sendFeedback(() -> Text.literal("Gravity reset to 1.0 (normal)."), false);
        for (ServerPlayerEntity p : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal("§6[God] §eGravity reset to normal by " + player.getDisplayName().getString()), false);
        }
        return 1;
    }

    // ---- 3. godheal ----
    private static int executeGodHeal(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity targetOverride) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerPlayerEntity target = targetOverride != null ? targetOverride : god;
        // Full heal
        target.setHealth(target.getMaxHealth());
        target.getHungerManager().setFoodLevel(20);
        target.getHungerManager().setSaturationLevel(20.0F);
        target.setAir(target.getMaxAir());
        target.extinguish();
        target.clearStatusEffects();
        target.setAbsorptionAmount(10.0F);
        // Remove harmful but keep as clear already; we cleared all so re-add nothing.
        // Add a little divine protection
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1, false, true, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aHealed " + target.getDisplayName().getString()), false);
        return 1;
    }

    // ---- 4. godfeed ----
    private static int executeGodFeed(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity targetOverride) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerPlayerEntity target = targetOverride != null ? targetOverride : god;
        target.getHungerManager().setFoodLevel(20);
        target.getHungerManager().setSaturationLevel(20.0F);
        ctx.getSource().sendFeedback(() -> Text.literal("§aFed " + target.getDisplayName().getString()), false);
        return 1;
    }

    // ---- 5. godspeed ----
    private static int executeGodSpeed(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        float speed = FloatArgumentType.getFloat(ctx, "speed");
        god.getAbilities().setFlySpeed(speed);
        god.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("Flight speed set to %.3f", speed)), false);
        return 1;
    }

    // ---- 6. godtime ----
    private static int executeGodTime(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        String val = StringArgumentType.getString(ctx, "value");
        long ticks;
        String label;
        switch (val.toLowerCase()) {
            case "day" -> { ticks = 1000L; label = "day (1000)"; }
            case "noon" -> { ticks = 6000L; label = "noon (6000)"; }
            case "sunset" -> { ticks = 12000L; label = "sunset (12000)"; }
            case "night" -> { ticks = 13000L; label = "night (13000)"; }
            case "midnight" -> { ticks = 18000L; label = "midnight (18000)"; }
            case "sunrise" -> { ticks = 23000L; label = "sunrise (23000)"; }
            default -> {
                try {
                    ticks = Long.parseLong(val);
                    label = String.valueOf(ticks);
                } catch (NumberFormatException e) {
                    ctx.getSource().sendError(Text.literal("Unknown time '" + val + "'. Use day/noon/sunset/night/midnight/sunrise or ticks 0-24000."));
                    return 0;
                }
            }
        }
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        world.setTimeOfDay(ticks % 24000L);
        ctx.getSource().sendFeedback(() -> Text.literal("§eTime set to " + label), false);
        return 1;
    }

    // ---- 7. godweather ----
    private static int executeGodWeather(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        switch (type) {
            case "clear" -> {
                world.setWeather(6000, 0, false, false);
                ctx.getSource().sendFeedback(() -> Text.literal("§eWeather → clear"), false);
            }
            case "rain" -> {
                world.setWeather(0, 6000, true, false);
                ctx.getSource().sendFeedback(() -> Text.literal("§eWeather → rain"), false);
            }
            case "thunder", "storm" -> {
                world.setWeather(0, 6000, true, true);
                ctx.getSource().sendFeedback(() -> Text.literal("§eWeather → thunder"), false);
            }
            default -> {
                ctx.getSource().sendError(Text.literal("Use clear, rain, or thunder"));
                return 0;
            }
        }
        return 1;
    }

    // ---- 8. godsmite ----
    private static int executeGodSmite(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.smiteTarget(god);
    }

    // ---- 9. godannihilate ----
    private static int executeGodAnnihilate(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.annihilateArea(god);
    }

    // ---- 10. godnova ----
    private static int executeGodNova(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.holyNova(god);
    }

    // ---- 11. godomnipotence ----
    private static int executeGodOmnipotence(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.activateOmnipotence(god);
    }

    // ---- 12. godbanish ----
    private static int executeGodBanish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.banishTarget(god);
    }

    // ---- 13. godbless ----
    private static int executeGodBless(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.blessTarget(god);
    }

    // ---- 14. godlevitate ----
    private static int executeGodLevitate(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.levitateMobs(god);
    }

    // ---- 15. godgiant ----
    private static int executeGodGiant(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.toggleGiant(god);
    }

    // ---- 16. godnoclip ----
    private static int executeGodNoClip(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.toggleNoClip(god);
    }

    // ---- 17. explode ----
    private static int executeExplode(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        float power = FloatArgumentType.getFloat(ctx, "power");
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        Vec3d start = god.getCameraPosVec(1.0F);
        Vec3d dir = god.getRotationVec(1.0F).normalize();
        Vec3d end = start.add(dir.multiply(40.0D));
        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, god));
        Vec3d pos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        world.createExplosion(god, pos.x, pos.y, pos.z, power, false, World.ExplosionSourceType.MOB);
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§cBOOM at %.1f %.1f %.1f (power %.1f)", pos.x, pos.y, pos.z, power)), false);
        return 1;
    }

    // ---- 18. killall ----
    private static int executeKillAll(CommandContext<ServerCommandSource> ctx, int radius, String type) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        double r = radius <= 0 ? Double.MAX_VALUE : radius;
        double rSq = r * r;
        int killed = 0;
        for (Entity e : world.iterateEntities()) {
            if (!(e instanceof MobEntity mob) || !mob.isAlive()) continue;
            if (radius > 0 && mob.squaredDistanceTo(god) > rSq) continue;
            if ("hostile".equalsIgnoreCase(type) && !(mob instanceof Monster)) continue;
            if ("passive".equalsIgnoreCase(type) && (mob instanceof Monster)) continue;
            mob.setHealth(0.0F);
            mob.kill(world);
            killed++;
        }
        int finalKilled = killed;
        ctx.getSource().sendFeedback(() -> Text.literal("§cKilled " + finalKilled + " mobs (" + type + ", radius " + (radius <= 0 ? "∞" : radius) + ")"), false);
        return 1;
    }

    // ---- 19. butcher ----
    private static int executeButcher(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        double rSq = (double) radius * radius;
        int killed = 0;
        for (Entity e : world.iterateEntities()) {
            if (!(e instanceof MobEntity mob) || !mob.isAlive()) continue;
            if (!(mob instanceof Monster)) continue;
            if (mob.squaredDistanceTo(god) > rSq) continue;
            mob.setHealth(0.0F);
            mob.kill(world);
            killed++;
        }
        int finalKilled = killed;
        ctx.getSource().sendFeedback(() -> Text.literal("§cButchered " + finalKilled + " hostile mobs within " + radius), false);
        return 1;
    }

    // ---- 20. clearitems ----
    private static int executeClearItems(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        double rSq = (double) radius * radius;
        int cleared = 0;
        for (Entity e : world.iterateEntities()) {
            if (!(e instanceof ItemEntity item)) continue;
            if (radius > 0 && item.squaredDistanceTo(god) > rSq) {
                // if radius==0 we clear all globally, but default here 64 so always check
                continue;
            }
            // radius==0 means global? But our default is 64, so this path is radius-limited.
            // For true global we would need to not check. We treat passed radius as limit.
            // To allow global clear via butcher pattern, we handle radius check only if >0.
            item.discard();
            cleared++;
        }
        // Correction: if caller wanted global clear (radius 0) we already filtered; reuse logic for global.
        // To handle, if radius was 64 default we did radius-limited. That's fine.
        // If we really want global, caller can use killall or we provide separate branch.
        int finalCleared = cleared;
        ctx.getSource().sendFeedback(() -> Text.literal("§eCleared " + finalCleared + " item entities"), false);
        return 1;
    }

    // ---- 21. summonhorde ----
    private static int executeSummonHorde(CommandContext<ServerCommandSource> ctx, String entityId, int count, int radius) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        Identifier id;
        if (entityId.contains(":")) {
            id = Identifier.of(entityId);
        } else {
            id = Identifier.of("minecraft", entityId.toLowerCase());
        }
        if (!Registries.ENTITY_TYPE.containsId(id)) {
            ctx.getSource().sendError(Text.literal("Unknown entity: " + entityId));
            return 0;
        }
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        if (type == EntityType.AREA_EFFECT_CLOUD || type == EntityType.MARKER) {
            ctx.getSource().sendError(Text.literal("Cannot summon entity: " + entityId));
            return 0;
        }
        // Verify it's a living type we can spawn
        int spawned = 0;
        Vec3d center = god.getEntityPos();
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) count) * Math.PI * 2.0D;
            double r = 2.0D + world.getRandom().nextDouble() * Math.max(1, radius - 2);
            double x = center.x + Math.cos(angle) * r + (world.getRandom().nextDouble() - 0.5) * 1.5;
            double z = center.z + Math.sin(angle) * r + (world.getRandom().nextDouble() - 0.5) * 1.5;
            double y = center.y;
            // Find ground
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            // Try to find safe Y within 8 blocks up/down
            if (world.getBlockState(pos).isAir()) {
                // keep y
            } else {
                // search up
                for (int dy = 0; dy < 8; dy++) {
                    BlockPos up = pos.up(dy);
                    if (world.getBlockState(up).isAir() && world.getBlockState(up.up()).isAir()) {
                        pos = up;
                        break;
                    }
                }
            }
            Entity entity = type.create(world, net.minecraft.entity.SpawnReason.COMMAND);
            if (entity == null) continue;
            entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0F, 0.0F);
            if (entity instanceof MobEntity mob) {
                mob.setPersistent();
            }
            if (world.spawnEntity(entity)) spawned++;
        }
        int finalSpawned = spawned;
        ctx.getSource().sendFeedback(() -> Text.literal("§aSummoned " + finalSpawned + " × " + id), false);
        return 1;
    }

    // ---- 22. lightning ----
    private static int executeLightningLook(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        Vec3d start = god.getCameraPosVec(1.0F);
        Vec3d dir = god.getRotationVec(1.0F).normalize();
        Vec3d end = start.add(dir.multiply(100.0D));
        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, god));
        Vec3d pos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        var bolt = new net.minecraft.entity.LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, world);
        bolt.setPosition(pos.x, pos.y, pos.z);
        world.spawnEntity(bolt);
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§eLightning at %.1f %.1f %.1f", pos.x, pos.y, pos.z)), false);
        return 1;
    }

    private static int executeLightningCoords(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        var bolt = new net.minecraft.entity.LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, world);
        bolt.setPosition(x, y, z);
        world.spawnEntity(bolt);
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§eLightning at %.1f %.1f %.1f", x, y, z)), false);
        return 1;
    }

    // ---- 23. healall ----
    private static int executeHealAll(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        int healed = 0;
        for (ServerPlayerEntity p : god.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            p.setHealth(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20.0F);
            p.extinguish();
            p.clearStatusEffects();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
            healed++;
        }
        int finalHealed = healed;
        ctx.getSource().sendFeedback(() -> Text.literal("§aHealed all " + finalHealed + " players"), false);
        return 1;
    }

    // ---- 24. feedall ----
    private static int executeFeedAll(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        int fed = 0;
        for (ServerPlayerEntity p : god.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20.0F);
            fed++;
        }
        int finalFed = fed;
        ctx.getSource().sendFeedback(() -> Text.literal("§aFed all " + finalFed + " players"), false);
        return 1;
    }

    // ---- 25. repair ----
    private static int executeRepair(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        var stack = god.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable()) {
            ctx.getSource().sendError(Text.literal("Hold a damageable item to repair."));
            return 0;
        }
        stack.setDamage(0);
        // Also try offhand if mainhand not repairable? We already checked.
        ctx.getSource().sendFeedback(() -> Text.literal("§aRepaired " + stack.getName().getString()), false);
        return 1;
    }

    // ---- 26. godscale ----
    private static int executeGodScale(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        double scale = DoubleArgumentType.getDouble(ctx, "scale");
        var attr = god.getAttributeInstance(EntityAttributes.SCALE);
        if (attr != null) {
            attr.setBaseValue(scale);
            try { god.calculateDimensions(); } catch (Exception ignored) {}
            ctx.getSource().sendFeedback(() -> Text.literal(String.format("§eScale set to %.2f", scale)), false);
            return 1;
        } else {
            ctx.getSource().sendError(Text.literal("Scale attribute not found."));
            return 0;
        }
    }

    // ---- 27/28. day/night shortcuts ----
    private static int executeTimeShortcut(CommandContext<ServerCommandSource> ctx, long ticks, String label) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        world.setTimeOfDay(ticks);
        ctx.getSource().sendFeedback(() -> Text.literal("§eTime set to " + label + " (" + ticks + ")"), false);
        return 1;
    }

    // ---- 29. godtp ----
    private static int executeGodTpPlayer(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(ctx, "target");
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Target not found."));
            return 0;
        }
        ServerWorld world = (ServerWorld) target.getEntityWorld();
        // Teleport god to target, handling cross-dimension via teleport method
        boolean ok = god.teleport(world, target.getX(), target.getY(), target.getZ(), Set.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z), god.getYaw(), god.getPitch(), false);
        // Fallback to requestTeleport if above fails due to signature differences
        if (!ok) {
            god.requestTeleport(target.getX(), target.getY(), target.getZ());
        }
        ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to " + target.getDisplayName().getString()), false);
        return 1;
    }

    private static int executeGodTpCoords(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        ServerWorld world = (ServerWorld) god.getEntityWorld();
        boolean ok = god.teleport(world, x, y, z, Collections.emptySet(), god.getYaw(), god.getPitch(), true);
        if (!ok) {
            god.requestTeleport(x, y, z);
        }
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§aTeleported to %.1f %.1f %.1f", x, y, z)), false);
        return 1;
    }

    // ---- 30. godhelp ---- now lists all 68 (30+38)
    private static int executeGodHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§6§l=== GOD COMMANDS (68) — PAGE 1/2 (30 core) ===\n" +
                "§e1 §7/gravity <0.0-5.0> §8— world gravity (0=zero-G,1=normal)\n" +
                "§e2 §7/gravityreset §8— reset gravity to 1.0\n" +
                "§e3 §7/godheal [player] §8— full heal\n" +
                "§e4 §7/godfeed [player] §8— restore hunger\n" +
                "§e5 §7/godspeed <0.01-0.5>\n" +
                "§e6 §7/godtime <day|noon|sunset|night|midnight|sunrise|ticks>\n" +
                "§e7 §7/godweather <clear|rain|thunder>\n" +
                "§e8 §7/godsmite — lightning + AoE at crosshair\n" +
                "§e9 §7/godannihilate — destroy sphere\n" +
                "§e10 §7/godnova — holy nova\n" +
                "§e11 §7/godomnipotence — 15s invulnerable\n" +
                "§e12 §7/godbanish — void-banish looked mob\n" +
                "§e13 §7/godbless — bless looked mob\n" +
                "§e14 §7/godlevitate — levitate mobs 30 blocks\n" +
                "§e15 §7/godgiant — toggle 3× scale\n" +
                "§e16 §7/godnoclip — toggle phase\n" +
                "§e17 §7/explode <0.5-10> — explosion at crosshair\n" +
                "§e18 §7/killall [radius] [all|hostile|passive]\n" +
                "§e19 §7/butcher [radius]\n" +
                "§e20 §7/clearitems [radius]\n" +
                "§e21 §7/summonhorde <entity> <count> [radius]\n" +
                "§e22 §7/lightning [x y z]\n" +
                "§e23 §7/healall\n" +
                "§e24 §7/feedall\n" +
                "§e25 §7/repair\n" +
                "§e26 §7/godscale <0.2-5.0>\n" +
                "§e27 §7/day §8— 1000\n" +
                "§e28 §7/night §8— 13000\n" +
                "§e29 §7/godtp <player|x y z>\n" +
                "§e30 §7/godhelp §8— this list (68 total)\n" +
                "§6--- 38 EXTRA (Singleplayer) — tab for more ---\n" +
                "§e31 §7/godfly §8— toggle flight\n" +
                "§e32 §7/godinvisible §8— toggle invis\n" +
                "§e33 §7/godinvulnerable\n" +
                "§e34 §7/godfireproof\n" +
                "§e35 §7/godwaterbreathing\n" +
                "§e36 §7/godjump [0-10]\n" +
                "§e37 §7/godhaste [0-10]\n" +
                "§e38 §7/godstrength [0-10]\n" +
                "§e39 §7/godnightvision\n" +
                "§e40 §7/godcure §8— clear negatives\n" +
                "§e41 §7/godextinguish §8— extinguish radius\n" +
                "§e42 §7/godclearinv\n" +
                "§e43 §7/godenchant §8— glint+enchantable\n" +
                "§e44 §7/godgive <item> [count]\n" +
                "§e45 §7/godgamemode <survival|creative|adventure|spectator>\n" +
                "§e46 §7/godlocate [query]\n" +
                "§e47 §7/godsay <message>\n" +
                "§e48 §7/godtimeadd <ticks>\n" +
                "§e49 §7/godsun §8— clear+day\n" +
                "§e50 §7/godstorm §8— thunder\n" +
                "§e51 §7/godhealradius [radius]\n" +
                "§e52 §7/godkillaura [radius]\n" +
                "§e53 §7/godlight §8— NV+glowing\n" +
                "§e54 §7/godsummon <entity> [count]\n" +
                "§e55 §7/godtphere <player>\n" +
                "§e56 §7/godvanish §8— toggle vanish\n" +
                "§e57 §7/godrename <name> §8— & = §\n" +
                "§e58 §7/godlore <text> §8— | = new line\n" +
                "§e59 §7/godxp <amount>\n" +
                "§e60 §7/godlevel <levels>\n" +
                "§e61 §7/godworldborder <size>\n" +
                "§e62 §7/godspawnpoint\n" +
                "§e63 §7/godbiome\n" +
                "§e64 §7/godspeedreset\n" +
                "§e65 §7/godresistance [amp]\n" +
                "§e66 §7/godregen [amp]\n" +
                "§e67 §7/godlaunch [0.5-5.0]\n" +
                "§e68 §7/godfreeze [radius] §8— ice+lava+slo\n" +
                "§7All require §6God Mode §7(/superpowers god → ascend).\n" +
                "§7Current gravity: §e" + String.format("%.3f", GodWorldState.getGravityMultiplier())
        ), false);
        return 1;
    }
}
