package org.xiaojian999.superpowers.god;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
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
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.xiaojian999.superpowers.GodPowerHandler;

import java.util.List;

/**
 * 38 additional god-only Singleplayer commands.
 * Every command requires God Mode (checked via GodPowerHandler.isActive).
 * All work on integrated server (singleplayer) – no dedicated server assumption.
 */
public final class GodExtraCommands {
    private GodExtraCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 31 godfly - toggle flight
        dispatcher.register(CommandManager.literal("godfly")
                .executes(GodExtraCommands::executeGodFly));

        // 32 godinvisible - toggle invisibility
        dispatcher.register(CommandManager.literal("godinvisible")
                .executes(GodExtraCommands::executeGodInvisible));

        // 33 godinvulnerable - toggle invulnerable flag
        dispatcher.register(CommandManager.literal("godinvulnerable")
                .executes(GodExtraCommands::executeGodInvulnerable));

        // 34 godfireproof - toggle fire resistance
        dispatcher.register(CommandManager.literal("godfireproof")
                .executes(GodExtraCommands::executeGodFireproof));

        // 35 godwaterbreathing - toggle water breathing
        dispatcher.register(CommandManager.literal("godwaterbreathing")
                .executes(GodExtraCommands::executeGodWaterBreathing));

        // 36 godjump <amp 0-10> - jump boost
        dispatcher.register(CommandManager.literal("godjump")
                .executes(ctx -> executeGodJump(ctx, 2))
                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeGodJump(ctx, IntegerArgumentType.getInteger(ctx, "amplifier")))));

        // 37 godhaste <0-10>
        dispatcher.register(CommandManager.literal("godhaste")
                .executes(ctx -> executeGodHaste(ctx, 2))
                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeGodHaste(ctx, IntegerArgumentType.getInteger(ctx, "amplifier")))));

        // 38 godstrength <0-10>
        dispatcher.register(CommandManager.literal("godstrength")
                .executes(ctx -> executeGodStrength(ctx, 2))
                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeGodStrength(ctx, IntegerArgumentType.getInteger(ctx, "amplifier")))));

        // 39 godnightvision
        dispatcher.register(CommandManager.literal("godnightvision")
                .executes(GodExtraCommands::executeGodNightVision));

        // 40 godcure
        dispatcher.register(CommandManager.literal("godcure")
                .executes(GodExtraCommands::executeGodCure));

        // 41 godextinguish
        dispatcher.register(CommandManager.literal("godextinguish")
                .executes(GodExtraCommands::executeGodExtinguish));

        // 42 godclearinv
        dispatcher.register(CommandManager.literal("godclearinv")
                .executes(GodExtraCommands::executeGodClearInv));

        // 43 godenchant
        dispatcher.register(CommandManager.literal("godenchant")
                .executes(GodExtraCommands::executeGodEnchant));

        // 44 godgive <item> [count]
        dispatcher.register(CommandManager.literal("godgive")
                .then(CommandManager.argument("item", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(Registries.ITEM.getIds().stream().map(Identifier::toString), b))
                        .executes(ctx -> executeGodGive(ctx, StringArgumentType.getString(ctx, "item"), 1))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 640))
                                .executes(ctx -> executeGodGive(ctx, StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))));

        // 45 godgamemode <mode>
        dispatcher.register(CommandManager.literal("godgamemode")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("survival", "creative", "adventure", "spectator"), b))
                        .executes(GodExtraCommands::executeGodGamemode)));

        // 46 godlocate - show biome/spawn/pos (no arg) or try structure name
        dispatcher.register(CommandManager.literal("godlocate")
                .executes(GodExtraCommands::executeGodLocateSimple)
                .then(CommandManager.argument("query", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("spawn", "biome", "village", "stronghold", "fortress"), b))
                        .executes(GodExtraCommands::executeGodLocate)));

        // 47 godsay <message...>
        dispatcher.register(CommandManager.literal("godsay")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(GodExtraCommands::executeGodSay)));

        // 48 godtimeadd <ticks>
        dispatcher.register(CommandManager.literal("godtimeadd")
                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(-24000, 24000))
                        .executes(GodExtraCommands::executeGodTimeAdd)));

        // 49 godsun - clear weather + day
        dispatcher.register(CommandManager.literal("godsun")
                .executes(GodExtraCommands::executeGodSun));

        // 50 godstorm - thunder
        dispatcher.register(CommandManager.literal("godstorm")
                .executes(GodExtraCommands::executeGodStorm));

        // 51 godhealradius <radius>
        dispatcher.register(CommandManager.literal("godhealradius")
                .executes(ctx -> executeGodHealRadius(ctx, 30))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(5, 200))
                        .executes(ctx -> executeGodHealRadius(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));

        // 52 godkillaura <radius>
        dispatcher.register(CommandManager.literal("godkillaura")
                .executes(ctx -> executeGodKillAura(ctx, 20))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(5, 100))
                        .executes(ctx -> executeGodKillAura(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));

        // 53 godlight - divine light buff
        dispatcher.register(CommandManager.literal("godlight")
                .executes(GodExtraCommands::executeGodLight));

        // 54 godsummon <entity> [count]
        dispatcher.register(CommandManager.literal("godsummon")
                .then(CommandManager.argument("entity", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("zombie", "skeleton", "creeper", "pig", "cow", "enderman", "warden", "blaze", "ghast", "villager"), b))
                        .executes(ctx -> executeGodSummon(ctx, StringArgumentType.getString(ctx, "entity"), 1))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> executeGodSummon(ctx, StringArgumentType.getString(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "count"))))));

        // 55 godtphere <player>
        dispatcher.register(CommandManager.literal("godtphere")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(GodExtraCommands::executeGodTpHere)));

        // 56 godvanish
        dispatcher.register(CommandManager.literal("godvanish")
                .executes(GodExtraCommands::executeGodVanish));

        // 57 godrename <name...>
        dispatcher.register(CommandManager.literal("godrename")
                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(GodExtraCommands::executeGodRename)));

        // 58 godlore <text...>
        dispatcher.register(CommandManager.literal("godlore")
                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(GodExtraCommands::executeGodLore)));

        // 59 godxp <amount>
        dispatcher.register(CommandManager.literal("godxp")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 100000))
                        .executes(GodExtraCommands::executeGodXp)));

        // 60 godlevel <levels>
        dispatcher.register(CommandManager.literal("godlevel")
                .then(CommandManager.argument("levels", IntegerArgumentType.integer(1, 1000))
                        .executes(GodExtraCommands::executeGodLevel)));

        // 61 godworldborder <size>
        dispatcher.register(CommandManager.literal("godworldborder")
                .then(CommandManager.argument("size", DoubleArgumentType.doubleArg(10, 59999968))
                        .executes(GodExtraCommands::executeGodWorldBorder)));

        // 62 godspawnpoint
        dispatcher.register(CommandManager.literal("godspawnpoint")
                .executes(GodExtraCommands::executeGodSpawnPoint));

        // 63 godbiome
        dispatcher.register(CommandManager.literal("godbiome")
                .executes(GodExtraCommands::executeGodBiome));

        // 64 godspeedreset
        dispatcher.register(CommandManager.literal("godspeedreset")
                .executes(GodExtraCommands::executeGodSpeedReset));

        // 65 godresistance <amp>
        dispatcher.register(CommandManager.literal("godresistance")
                .executes(ctx -> executeGodResistance(ctx, 4))
                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeGodResistance(ctx, IntegerArgumentType.getInteger(ctx, "amplifier")))));

        // 66 godregen <amp>
        dispatcher.register(CommandManager.literal("godregen")
                .executes(ctx -> executeGodRegen(ctx, 2))
                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeGodRegen(ctx, IntegerArgumentType.getInteger(ctx, "amplifier")))));

        // 67 godlaunch [power]
        dispatcher.register(CommandManager.literal("godlaunch")
                .executes(ctx -> executeGodLaunch(ctx, 2.0))
                .then(CommandManager.argument("power", DoubleArgumentType.doubleArg(0.5, 5.0))
                        .executes(ctx -> executeGodLaunch(ctx, DoubleArgumentType.getDouble(ctx, "power")))));

        // 68 godfreeze [radius]
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

    // 31
    private static int executeGodFly(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        boolean now = !p.getAbilities().allowFlying;
        p.getAbilities().allowFlying = now;
        if (now) p.getAbilities().flying = true;
        else p.getAbilities().flying = false;
        p.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal(now ? "§aFlight enabled." : "§cFlight disabled."), false);
        return 1;
    }

    // 32
    private static int executeGodInvisible(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            ctx.getSource().sendFeedback(() -> Text.literal("§cInvisibility off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aInvisibility on (infinite)."), false);
        }
        return 1;
    }

    // 33
    private static int executeGodInvulnerable(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        boolean now = !p.getAbilities().invulnerable;
        p.getAbilities().invulnerable = now;
        p.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal(now ? "§aInvulnerable on." : "§cInvulnerable off."), false);
        return 1;
    }

    // 34
    private static int executeGodFireproof(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            p.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
            ctx.getSource().sendFeedback(() -> Text.literal("§cFire Resistance off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aFire Resistance on (infinite)."), false);
        }
        return 1;
    }

    // 35
    private static int executeGodWaterBreathing(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.WATER_BREATHING)) {
            p.removeStatusEffect(StatusEffects.WATER_BREATHING);
            ctx.getSource().sendFeedback(() -> Text.literal("§cWater Breathing off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aWater Breathing on (infinite)."), false);
        }
        return 1;
    }

    // 36
    private static int executeGodJump(CommandContext<ServerCommandSource> ctx, int amp) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (amp < 0) {
            if (p.hasStatusEffect(StatusEffects.JUMP_BOOST)) p.removeStatusEffect(StatusEffects.JUMP_BOOST);
            ctx.getSource().sendFeedback(() -> Text.literal("§cJump Boost cleared."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, -1, amp, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aJump Boost " + amp + " on (infinite)."), false);
        }
        return 1;
    }

    // 37
    private static int executeGodHaste(CommandContext<ServerCommandSource> ctx, int amp) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, -1, Math.max(0, amp), false, false, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aHaste " + amp + " on."), false);
        return 1;
    }

    // 38
    private static int executeGodStrength(CommandContext<ServerCommandSource> ctx, int amp) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, -1, Math.max(0, amp), false, false, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aStrength " + amp + " on."), false);
        return 1;
    }

    // 39
    private static int executeGodNightVision(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            ctx.getSource().sendFeedback(() -> Text.literal("§cNight Vision off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aNight Vision on (infinite)."), false);
        }
        return 1;
    }

    // 40
    private static int executeGodCure(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        // remove harmful
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
        // Use effectively final variable in lambda
        int finalRemoved = removed;
        ctx.getSource().sendFeedback(() -> Text.literal("§aCured " + finalRemoved + " negative effects, extinguished."), false);
        return 1;
    }

    // 41
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

    // 42
    private static int executeGodClearInv(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.getInventory().clear();
        ctx.getSource().sendFeedback(() -> Text.literal("§aInventory cleared."), false);
        return 1;
    }

    // 43
    private static int executeGodEnchant(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ItemStack stack = p.getMainHandStack();
        if (stack.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Hold an item to enchant."));
            return 0;
        }
        // Simplified god enchant: give glint + repair. Real registry enchants are handled via vanilla /enchant
        // To keep Singleplayer-compatible without registry lookups, we just add glint override.
        try {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            stack.set(DataComponentTypes.ENCHANTABLE, new net.minecraft.component.type.EnchantableComponent(30));
        } catch (Exception ignored) {}
        // Also try to add a couple of safe enchants via direct ItemStack API using string lookup fallback
        try {
            var world = (ServerWorld) p.getEntityWorld();
            var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            // try mending via identifier lookup (containsId path)
            var mendingId = Identifier.of("minecraft", "mending");
            var mendingEntry = registry.getEntry(Identifier.of("minecraft", "mending"));
            // The above may fail in this MC version, so we rely on glint only.
            // Keep glint feedback
        } catch (Exception ignored2) {}
        ctx.getSource().sendFeedback(() -> Text.literal("§aEnchanted (glint + enchantable 30). Use anvil for specifics."), false);
        return 1;
    }

    // 44
    private static int executeGodGive(CommandContext<ServerCommandSource> ctx, String itemId, int count) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        Identifier id;
        if (itemId.contains(":")) id = Identifier.of(itemId);
        else id = Identifier.of("minecraft", itemId.toLowerCase());
        if (!Registries.ITEM.containsId(id)) {
            ctx.getSource().sendError(Text.literal("Unknown item: " + itemId));
            return 0;
        }
        var item = Registries.ITEM.get(id);
        ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxCount() * 4));
        // if count > maxStack, give multiple
        int remaining = count;
        int given = 0;
        while (remaining > 0) {
            int toGive = Math.min(remaining, item.getMaxCount());
            ItemStack s = new ItemStack(item, toGive);
            p.giveItemStack(s);
            remaining -= toGive;
            given += toGive;
        }
        // initial stack variable not needed
        int finalGiven = given;
        ctx.getSource().sendFeedback(() -> Text.literal("§aGave " + finalGiven + " × " + id), false);
        return 1;
    }

    // 45
    private static int executeGodGamemode(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        String modeStr = StringArgumentType.getString(ctx, "mode").toLowerCase();
        GameMode target;
        switch (modeStr) {
            case "survival", "0", "s" -> target = GameMode.SURVIVAL;
            case "creative", "1", "c" -> target = GameMode.CREATIVE;
            case "adventure", "2", "a" -> target = GameMode.ADVENTURE;
            case "spectator", "3", "sp" -> target = GameMode.SPECTATOR;
            default -> {
                ctx.getSource().sendError(Text.literal("Unknown gamemode: " + modeStr));
                return 0;
            }
        }
        p.changeGameMode(target);
        ctx.getSource().sendFeedback(() -> Text.literal("§aGamemode → " + target.asString()), false);
        return 1;
    }

    // 46 simple + with query
    private static int executeGodLocateSimple(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        return sendLocateInfo(ctx, p, "spawn");
    }
    private static int executeGodLocate(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        String q = StringArgumentType.getString(ctx, "query");
        return sendLocateInfo(ctx, p, q);
    }
    private static int sendLocateInfo(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity p, String query) {
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        BlockPos pos = p.getBlockPos();
        // biome
        String biomeId = "unknown";
        try {
            var biomeEntry = world.getBiome(pos);
            biomeId = biomeEntry.getKey().map(k -> k.getValue().toString()).orElseGet(() -> {
                var reg = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                var id = reg.getId(biomeEntry.value());
                return id != null ? id.toString() : biomeEntry.toString();
            });
        } catch (Exception ignored) {}
        BlockPos spawn = world.getSpawnPoint().getPos();
        String msg = "§6[Locate] §ePos: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " §7| Biome: " + biomeId
                + " §7| Spawn: " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ()
                + " §7| Dim: " + world.getRegistryKey().getValue();
        if (query != null) {
            msg += " §7| Query: " + query;
            // attempt structure locate for known structures via simple message; real locate would need async search
            if (query.equalsIgnoreCase("village") || query.equalsIgnoreCase("stronghold") || query.equalsIgnoreCase("fortress")) {
                msg += " §8(nearby structure search requires /locate in vanilla – godlocate shows biome/pos only. Use /godtp to travel.)";
            }
        }
        String finalMsg = msg;
        ctx.getSource().sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    // 47
    private static int executeGodSay(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        String msg = StringArgumentType.getString(ctx, "message");
        var server = p.getEntityWorld().getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(Text.literal("§6[God §e" + p.getDisplayName().getString() + "§6] §f" + msg), false);
        }
        return 1;
    }

    // 48
    private static int executeGodTimeAdd(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        int add = IntegerArgumentType.getInteger(ctx, "ticks");
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        long cur = world.getTimeOfDay();
        world.setTimeOfDay(cur + add);
        ctx.getSource().sendFeedback(() -> Text.literal("§eTime +" + add + " → " + world.getTimeOfDay()), false);
        return 1;
    }

    // 49
    private static int executeGodSun(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        world.setWeather(6000, 0, false, false);
        world.setTimeOfDay(1000);
        ctx.getSource().sendFeedback(() -> Text.literal("§eSet sun: clear + day (1000)"), false);
        return 1;
    }

    // 50
    private static int executeGodStorm(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        world.setWeather(0, 6000, true, true);
        ctx.getSource().sendFeedback(() -> Text.literal("§eStorm summoned (rain+thunder)."), false);
        return 1;
    }

    // 51
    private static int executeGodHealRadius(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        double rSq = (double) radius * radius;
        int healed = 0;
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity le && le.isAlive() && le.squaredDistanceTo(p) <= rSq) {
                le.setHealth(le.getMaxHealth());
                if (le instanceof ServerPlayerEntity sp) {
                    sp.getHungerManager().setFoodLevel(20);
                    sp.getHungerManager().setSaturationLevel(20);
                    sp.extinguish();
                } else {
                    le.clearStatusEffects();
                }
                healed++;
            }
        }
        int finalHealed = healed;
        ctx.getSource().sendFeedback(() -> Text.literal("§aHealed " + finalHealed + " entities within " + radius), false);
        return 1;
    }

    // 52
    private static int executeGodKillAura(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        double rSq = (double) radius * radius;
        int killed = 0;
        for (Entity e : world.iterateEntities()) {
            if (!(e instanceof MobEntity mob) || !mob.isAlive()) continue;
            if (mob.squaredDistanceTo(p) > rSq) continue;
            mob.setHealth(0);
            mob.kill(world);
            killed++;
        }
        int finalKilled = killed;
        ctx.getSource().sendFeedback(() -> Text.literal("§cKillAura killed " + finalKilled + " mobs within " + radius), false);
        return 1;
    }

    // 53
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

    // 54
    private static int executeGodSummon(CommandContext<ServerCommandSource> ctx, String entityId, int count) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        Identifier id = entityId.contains(":") ? Identifier.of(entityId) : Identifier.of("minecraft", entityId.toLowerCase());
        if (!Registries.ENTITY_TYPE.containsId(id)) {
            ctx.getSource().sendError(Text.literal("Unknown entity: " + entityId));
            return 0;
        }
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        // raycast to look pos for summon
        Vec3d start = p.getCameraPosVec(1.0F);
        Vec3d dir = p.getRotationVec(1.0F).normalize();
        Vec3d end = start.add(dir.multiply(20));
        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d pos = hit.getType() == HitResult.Type.MISS ? p.getEntityPos().add(dir.multiply(3)) : hit.getPos();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity e = type.create(world, net.minecraft.entity.SpawnReason.COMMAND);
            if (e == null) continue;
            double offX = (world.getRandom().nextDouble() - 0.5) * 2;
            double offZ = (world.getRandom().nextDouble() - 0.5) * 2;
            e.refreshPositionAndAngles(pos.x + offX, pos.y + 0.5, pos.z + offZ, world.getRandom().nextFloat() * 360, 0);
            if (e instanceof MobEntity mob) mob.setPersistent();
            if (world.spawnEntity(e)) spawned++;
        }
        int finalSpawned = spawned;
        ctx.getSource().sendFeedback(() -> Text.literal("§aSummoned " + finalSpawned + " × " + id), false);
        return 1;
    }

    // 55
    private static int executeGodTpHere(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(ctx, "target");
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Target not found."));
            return 0;
        }
        if (target.getUuid().equals(p.getUuid())) {
            ctx.getSource().sendError(Text.literal("Cannot teleport yourself to yourself."));
            return 0;
        }
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        // teleport target to god
        boolean ok = target.teleport(world, p.getX(), p.getY(), p.getZ(), java.util.Set.of(net.minecraft.network.packet.s2c.play.PositionFlag.X, net.minecraft.network.packet.s2c.play.PositionFlag.Y, net.minecraft.network.packet.s2c.play.PositionFlag.Z), target.getYaw(), target.getPitch(), false);
        if (!ok) target.requestTeleport(p.getX(), p.getY(), p.getZ());
        ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported " + target.getDisplayName().getString() + " to you."), false);
        return 1;
    }

    // 56
    private static int executeGodVanish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        if (p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            p.getAbilities().allowFlying = true; // keep flight
            ctx.getSource().sendFeedback(() -> Text.literal("§cVanish off."), false);
        } else {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1, 0, false, false, true));
            ctx.getSource().sendFeedback(() -> Text.literal("§aVanished (invisible)."), false);
        }
        return 1;
    }

    // 57
    private static int executeGodRename(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ItemStack stack = p.getMainHandStack();
        if (stack.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Hold an item to rename."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        // support color codes with & -> §
        String parsed = name.replace('&', '§');
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(parsed));
        ctx.getSource().sendFeedback(() -> Text.literal("§aRenamed to: " + parsed), false);
        return 1;
    }

    // 58
    private static int executeGodLore(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ItemStack stack = p.getMainHandStack();
        if (stack.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Hold an item to set lore."));
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        String parsed = text.replace('&', '§');
        // split by | or \n literal?
        String[] lines = parsed.split("\\|");
        java.util.List<Text> loreLines = new java.util.ArrayList<>();
        for (String line : lines) loreLines.add(Text.literal(line));
        stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        ctx.getSource().sendFeedback(() -> Text.literal("§aLore set (" + loreLines.size() + " line/s)."), false);
        return 1;
    }

    // 59
    private static int executeGodXp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        p.addExperience(amount);
        ctx.getSource().sendFeedback(() -> Text.literal("§aGave " + amount + " XP."), false);
        return 1;
    }

    // 60
    private static int executeGodLevel(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        int levels = IntegerArgumentType.getInteger(ctx, "levels");
        p.addExperienceLevels(levels);
        ctx.getSource().sendFeedback(() -> Text.literal("§aGave " + levels + " levels (now " + p.experienceLevel + ")."), false);
        return 1;
    }

    // 61
    private static int executeGodWorldBorder(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        double size = DoubleArgumentType.getDouble(ctx, "size");
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        world.getWorldBorder().setSize(size);
        ctx.getSource().sendFeedback(() -> Text.literal("§aWorldBorder size → " + (int) size), false);
        return 1;
    }

    // 62
    private static int executeGodSpawnPoint(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        BlockPos pos = p.getBlockPos();
        try {
            world.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()));
        } catch (Exception e) {
            // fallback via properties
            try {
                world.getServer().getOverworld().setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()));
            } catch (Exception ignored) {}
        }
        // also set player spawn - 1.21.11 uses Respawn record
        try {
            p.setSpawnPoint(new ServerPlayerEntity.Respawn(
                    WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, p.getYaw(), p.getPitch()), true), true);
        } catch (Exception ignored) {
            // older fallback - try global pos method if above fails (should not)
        }
        ctx.getSource().sendFeedback(() -> Text.literal("§aSpawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        return 1;
    }

    // 63
    private static int executeGodBiome(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        BlockPos pos = p.getBlockPos();
        String biomeId = "unknown";
        try {
            var entry = world.getBiome(pos);
            biomeId = entry.getKey().map(k -> k.getValue().toString()).orElseGet(() -> {
                try {
                    var reg = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                    Identifier id = reg.getId(entry.value());
                    return id != null ? id.toString() : entry.toString();
                } catch (Exception ex) { return entry.toString(); }
            });
        } catch (Exception ignored) {}
        String finalBiome = biomeId;
        ctx.getSource().sendFeedback(() -> Text.literal("§eBiome: " + finalBiome + " §7at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        return 1;
    }

    // 64
    private static int executeGodSpeedReset(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.getAbilities().setFlySpeed(0.05F);
        p.sendAbilitiesUpdate();
        ctx.getSource().sendFeedback(() -> Text.literal("§aFly speed reset to 0.05"), false);
        return 1;
    }

    // 65
    private static int executeGodResistance(CommandContext<ServerCommandSource> ctx, int amp) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, -1, amp, false, false, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aResistance " + amp + " on."), false);
        return 1;
    }

    // 66
    private static int executeGodRegen(CommandContext<ServerCommandSource> ctx, int amp) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, -1, amp, false, false, true));
        ctx.getSource().sendFeedback(() -> Text.literal("§aRegeneration " + amp + " on."), false);
        return 1;
    }

    // 67
    private static int executeGodLaunch(CommandContext<ServerCommandSource> ctx, double power) {
        ServerPlayerEntity p = requireGod(ctx);
        if (p == null) return 0;
        Vec3d look = p.getRotationVec(1.0F).normalize();
        // launch forward + up
        double px = look.x * power;
        double py = 0.8 + power * 0.6;
        double pz = look.z * power;
        p.setVelocity(px, py, pz);
        p.velocityDirty = true;
        // also send packet
        try { p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p)); } catch (Exception ignored) {}
        ctx.getSource().sendFeedback(() -> Text.literal(String.format("§aLaunched (%.2f)", power)), false);
        return 1;
    }

    // 68
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
        // slowness to nearby mobs
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
