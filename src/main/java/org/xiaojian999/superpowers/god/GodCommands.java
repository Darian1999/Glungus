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
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.xiaojian999.superpowers.GodPowerHandler;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Curated god-only commands — only useful, distinctive God powers remain.
 * Useless duplicates / singleplayer-irrelevant / not-godly commands have been removed.
 * World-wide gravity remains the flagship feature.
 */
public final class GodCommands {
    private GodCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 1. /gravity <0.0-5.0> — world-wide gravity multiplier (flagship)
        dispatcher.register(CommandManager.literal("gravity")
                .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0D, 5.0D))
                        .executes(GodCommands::executeGravity)));

        // 2. /gravityreset
        dispatcher.register(CommandManager.literal("gravityreset")
                .executes(GodCommands::executeGravityReset));

        // 3. /godheal [target]
        dispatcher.register(CommandManager.literal("godheal")
                .executes(ctx -> executeGodHeal(ctx, null))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeGodHeal(ctx, EntityArgumentType.getPlayer(ctx, "target")))));

        // 4. /godfeed [target]
        dispatcher.register(CommandManager.literal("godfeed")
                .executes(ctx -> executeGodFeed(ctx, null))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeGodFeed(ctx, EntityArgumentType.getPlayer(ctx, "target")))));

        // 5. /godspeed <0.01-0.5>
        dispatcher.register(CommandManager.literal("godspeed")
                .then(CommandManager.argument("speed", FloatArgumentType.floatArg(0.01F, 0.5F))
                        .executes(GodCommands::executeGodSpeed)));

        // 6. /godtime <preset|ticks>
        dispatcher.register(CommandManager.literal("godtime")
                .then(CommandManager.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("day", "noon", "sunset", "night", "midnight", "sunrise", "1000", "6000", "12000", "13000", "18000", "23000"), b))
                        .executes(GodCommands::executeGodTime)));

        // 7. /godweather <clear|rain|thunder>
        dispatcher.register(CommandManager.literal("godweather")
                .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("clear", "rain", "thunder"), b))
                        .executes(GodCommands::executeGodWeather)));

        // 8. /godsmite
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

        // 17. /killall [radius] [all|hostile|passive] — kept (godly purge), butcher removed as duplicate
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

        // 18. /summonhorde <entity> <count> [radius] — kept, godsummon removed as duplicate
        dispatcher.register(CommandManager.literal("summonhorde")
                .then(CommandManager.argument("entity", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pig", "cow", "sheep", "warden", "blaze", "ghast"), b))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> executeSummonHorde(ctx, StringArgumentType.getString(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "count"), 8))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> executeSummonHorde(ctx, StringArgumentType.getString(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "radius")))))));

        // 19. /lightning [x y z] — kept as aimed variant distinct from godsmite
        dispatcher.register(CommandManager.literal("lightning")
                .executes(GodCommands::executeLightningLook)
                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                        .executes(GodCommands::executeLightningCoords)))));

        // 20. /repair — kept (godly maintenance)
        dispatcher.register(CommandManager.literal("repair")
                .executes(GodCommands::executeRepair));

        // 21. /godscale <0.2-5.0>
        dispatcher.register(CommandManager.literal("godscale")
                .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.2D, 5.0D))
                        .executes(GodCommands::executeGodScale)));

        // 22. /godtp <x y z> OR <player>
        dispatcher.register(CommandManager.literal("godtp")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(GodCommands::executeGodTpPlayer))
                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                        .executes(GodCommands::executeGodTpCoords)))));

        // 23. /godhelp — curated list
        dispatcher.register(CommandManager.literal("godhelp")
                .executes(GodCommands::executeGodHelp));
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

    private static int executeGravity(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requireGod(ctx);
        if (player == null) return 0;
        double value = DoubleArgumentType.getDouble(ctx, "value");
        GodWorldState.setGravityMultiplier(value);
        double actual = GodWorldState.getGravityMultiplier();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
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

    private static int executeGodHeal(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity targetOverride) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerPlayerEntity target = targetOverride != null ? targetOverride : god;
        target.setHealth(target.getMaxHealth());
        target.getHungerManager().setFoodLevel(20);
        target.getHungerManager().setSaturationLevel(20.0F);
        target.setAir(target.getMaxAir());
        target.extinguish();
        target.clearStatusEffects();
        target.setAbsorptionAmount(10.0F);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1, false, true, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aHealed " + target.getDisplayName().getString()), false);
        return 1;
    }

    private static int executeGodFeed(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity targetOverride) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ServerPlayerEntity target = targetOverride != null ? targetOverride : god;
        target.getHungerManager().setFoodLevel(20);
        target.getHungerManager().setSaturationLevel(20.0F);
        ctx.getSource().sendFeedback(() -> Text.literal("§aFed " + target.getDisplayName().getString()), false);
        return 1;
    }

    private static int executeGodSpeed(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        float speed = FloatArgumentType.getFloat(ctx, "speed");
        god.getAbilities().setFlySpeed(speed);
        god.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("Flight speed set to %.3f", speed)), false);
        return 1;
    }

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

    private static int executeGodSmite(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.smiteTarget(god);
    }

    private static int executeGodAnnihilate(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.annihilateArea(god);
    }

    private static int executeGodNova(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.holyNova(god);
    }

    private static int executeGodOmnipotence(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.activateOmnipotence(god);
    }

    private static int executeGodBanish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.banishTarget(god);
    }

    private static int executeGodBless(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.blessTarget(god);
    }

    private static int executeGodLevitate(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.levitateMobs(god);
    }

    private static int executeGodGiant(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.toggleGiant(god);
    }

    private static int executeGodNoClip(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        return GodPowerHandler.toggleNoClip(god);
    }

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
        int spawned = 0;
        Vec3d center = god.getEntityPos();
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) count) * Math.PI * 2.0D;
            double r = 2.0D + world.getRandom().nextDouble() * Math.max(1, radius - 2);
            double x = center.x + Math.cos(angle) * r + (world.getRandom().nextDouble() - 0.5) * 1.5;
            double z = center.z + Math.sin(angle) * r + (world.getRandom().nextDouble() - 0.5) * 1.5;
            double y = center.y;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            if (world.getBlockState(pos).isAir()) {
            } else {
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

    private static int executeRepair(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        var stack = god.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable()) {
            ctx.getSource().sendError(Text.literal("Hold a damageable item to repair."));
            return 0;
        }
        stack.setDamage(0);
        ctx.getSource().sendFeedback(() -> Text.literal("§aRepaired " + stack.getName().getString()), false);
        return 1;
    }

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
        boolean ok = god.teleport(world, target.getX(), target.getY(), target.getZ(), Set.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z), god.getYaw(), god.getPitch(), false);
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

    private static int executeGodHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity god = requireGod(ctx);
        if (god == null) return 0;
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§6§l=== CURATED GOD COMMANDS (23+9) ===\n" +
                "§eCore (23):\n" +
                " §7/gravity <0-5> §8world-wide §7/gravityreset §7/godheal §7/godfeed §7/godspeed §7/godtime §7/godweather\n" +
                " §7/godsmite §7/godannihilate §7/godnova §7/godomnipotence §7/godbanish §7/godbless §7/godlevitate\n" +
                " §7/godgiant §7/godnoclip §7/killall [r] [type] §7/summonhorde §7/lightning [x y z] §7/repair §7/godscale §7/godtp\n" +
                "§eExtra curated (9): §7/godcure §7/godextinguish §7/godlight §7/godvanish §7/godworldborder §7/godspawnpoint\n" +
                " §7/godspeedreset §7/godlaunch §7/godfreeze\n" +
                "§cRemoved as useless: explode, butcher, clearitems, healall/feedall, day/night (→godtime), godfly/invisible/invulnerable/fireproof/waterbreathing/jump/haste/strength/nightvision/clearinv/enchant/give/gamemode/locate/say/timeadd/sun/storm/healradius/killaura/summon/tphere/rename/lore/xp/level/biome/resistance/regen\n" +
                "§7All require §6God Mode §7(/superpowers god)\n" +
                "§7Gravity: §e" + String.format("%.3f", GodWorldState.getGravityMultiplier())
        ), false);
        return 1;
    }
}
