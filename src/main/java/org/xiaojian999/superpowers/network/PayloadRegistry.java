package org.xiaojian999.superpowers.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiaojian999.superpowers.Glungus;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Central registry that auto-generates and auto-registers {@link CustomPayload}
 * codecs for client-to-server (C2S) and server-to-client (S2C) traffic.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li><b>Annotation-driven auto-registration:</b> any payload annotated with
 *       {@link AutoPayload} is registered in the correct direction without manual
 *       {@code PayloadTypeRegistry} calls in {@code Glungus}.</li>
 *   <li><b>Reflection-based convention:</b> even without the annotation, any
 *       {@code CustomPayload} with public static fields {@code ID} and {@code CODEC}
 *       can be auto-registered via {@link #autoRegister(Class)} or
 *       {@link #ensureRegistered(Class, PayloadDirection)} (idempotent).</li>
 *   <li><b>Runtime auto-generation:</b> {@link #registerC2S}, {@link #registerS2C},
 *       {@link #registerBoth} and {@link #createAndRegisterC2S} allow creating
 *       payload types on-the-fly when a new feature "needs" network traffic but no
 *       class has been written yet.</li>
 *   <li><b>Generic fallback:</b> {@link GenericPayload} is registered bidirectionally
 *       at startup so any future feature can send {@code channel + bytes} without
 *       defining a new record at all.</li>
 *   <li><b>Handler helpers:</b> registers Fabric global receivers alongside codecs
 *       when desired, avoiding split boilerplate between {@code Glungus} and
 *       {@code PowerManager} / {@code GlungusClient}.</li>
 * </ul>
 */
public final class PayloadRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(Glungus.MOD_ID + "/network");

    private static final Set<Identifier> REGISTERED_C2S = new HashSet<>();
    private static final Set<Identifier> REGISTERED_S2C = new HashSet<>();

    /** Handlers for GenericPayload multiplexed by channel (C2S side). */
    private static final Map<Identifier, ServerPlayNetworking.PlayPayloadHandler<GenericPayload>> GENERIC_C2S_HANDLERS = new HashMap<>();

    private PayloadRegistry() {}

    // ---------------------------------------------------------------
    //  Low-level register (idempotent)
    // ---------------------------------------------------------------

    /**
     * Registers a codec for client -> server if not already registered.
     * Idempotent – second calls are ignored with a debug log.
     */
    public static <T extends CustomPayload> void registerC2S(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        Identifier identifier = id.id();
        if (!REGISTERED_C2S.add(identifier)) {
            LOGGER.debug("C2S payload {} already registered, skipping", identifier);
            return;
        }
        PayloadTypeRegistry.playC2S().register(id, codec);
        LOGGER.info("Auto-registered C2S payload {}", identifier);
    }

    /**
     * Registers a codec for server -> client if not already registered.
     */
    public static <T extends CustomPayload> void registerS2C(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        Identifier identifier = id.id();
        if (!REGISTERED_S2C.add(identifier)) {
            LOGGER.debug("S2C payload {} already registered, skipping", identifier);
            return;
        }
        PayloadTypeRegistry.playS2C().register(id, codec);
        LOGGER.info("Auto-registered S2C payload {}", identifier);
    }

    /**
     * Registers a codec for both directions (bidirectional).
     */
    public static <T extends CustomPayload> void registerBoth(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        registerC2S(id, codec);
        registerS2C(id, codec);
    }

    /** Whether the given identifier is already registered for C2S. */
    public static boolean isRegisteredC2S(Identifier id) {
        return REGISTERED_C2S.contains(id);
    }

    /** Whether the given identifier is already registered for S2C. */
    public static boolean isRegisteredS2C(Identifier id) {
        return REGISTERED_S2C.contains(id);
    }

    // ---------------------------------------------------------------
    //  Auto-register via reflection / annotation
    // ---------------------------------------------------------------

    /**
     * Auto-registers a payload class that follows the conventional shape:
     * <pre>
     * public static final CustomPayload.Id&lt;MyPayload&gt; ID = ...
     * public static final PacketCodec&lt;RegistryByteBuf, MyPayload&gt; CODEC = ...
     * </pre>
     * Direction is inferred from {@link AutoPayload} if present, otherwise
     * {@code defaultDirection} is used.
     *
     * @return true if registration happened, false if already registered
     */
    @SuppressWarnings("unchecked")
    public static boolean autoRegister(Class<? extends CustomPayload> clazz, PayloadDirection defaultDirection) {
        try {
            // --- Resolve ID (auto-generate from @AutoPayload if missing) ---
            CustomPayload.Id<?> id;
            try {
                Field idField = clazz.getField("ID");
                id = (CustomPayload.Id<?>) idField.get(null);
            } catch (NoSuchFieldException e) {
                AutoPayload ann = clazz.getAnnotation(AutoPayload.class);
                if (ann != null && !ann.value().isEmpty()) {
                    id = new CustomPayload.Id<>(Identifier.of(Glungus.MOD_ID, ann.value()));
                    LOGGER.info("Auto-generated ID for {} -> {}", clazz.getSimpleName(), id.id());
                } else {
                    LOGGER.warn("Cannot auto-register {}: missing public static ID field and no @AutoPayload(value) to generate from", clazz.getName());
                    return false;
                }
            }

            // --- Resolve CODEC (auto-generate if missing) ---
            PacketCodec<? super RegistryByteBuf, ?> codec;
            try {
                Field codecField = clazz.getField("CODEC");
                codec = (PacketCodec<? super RegistryByteBuf, ?>) codecField.get(null);
            } catch (NoSuchFieldException e) {
                codec = tryAutoGenerateCodec(clazz, id);
                if (codec == null) {
                    LOGGER.warn("Cannot auto-register {}: missing CODEC and auto-generation failed. Add a public static CODEC field or use a supported record shape (0 or 1 field with int/boolean/float/String/Identifier/UUID/byte[]).", clazz.getName());
                    return false;
                }
                LOGGER.info("Auto-generated CODEC for {}", clazz.getSimpleName());
            }

            AutoPayload ann = clazz.getAnnotation(AutoPayload.class);
            PayloadDirection direction = ann != null ? ann.direction() : defaultDirection;

            // Raw cast required because generic type is erased via reflection; safe as codec matches id's payload type.
            CustomPayload.Id rawId = id;
            PacketCodec rawCodec = codec;

            int beforeC2S = REGISTERED_C2S.size();
            int beforeS2C = REGISTERED_S2C.size();

            switch (direction) {
                case C2S -> registerC2S(rawId, rawCodec);
                case S2C -> registerS2C(rawId, rawCodec);
                case BOTH -> registerBoth(rawId, rawCodec);
            }

            boolean changed = REGISTERED_C2S.size() != beforeC2S || REGISTERED_S2C.size() != beforeS2C;
            if (!changed) {
                LOGGER.debug("Payload {} already registered, skipping autoRegister", clazz.getSimpleName());
            }
            return changed;
        } catch (IllegalAccessException e) {
            LOGGER.warn("Cannot auto-register {}: cannot access ID/CODEC", clazz.getName(), e);
            return false;
        }
    }

    /**
     * Tries to auto-generate a {@link PacketCodec} for simple payload shapes when
     * no {@code CODEC} field exists. Supports:
     * <ul>
     *   <li>Records with 0 components (unit payloads) – uses {@link PacketCodec#unit}</li>
     *   <li>Records with 1 component of type int, boolean, float, double, String, Identifier, UUID, byte[]</li>
     * </ul>
     * More complex payloads should define their own CODEC (tuple, etc.).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PacketCodec<? super RegistryByteBuf, ?> tryAutoGenerateCodec(Class<? extends CustomPayload> clazz, CustomPayload.Id<?> id) {
        try {
            if (!clazz.isRecord()) {
                return null;
            }
            var components = clazz.getRecordComponents();
            if (components.length == 0) {
                // Unit payload: construct via no-arg canonical constructor
                var ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object instance = ctor.newInstance();
                return (PacketCodec) PacketCodec.unit((CustomPayload) instance);
            }
            if (components.length == 1) {
                var comp = components[0];
                Class<?> type = comp.getType();
                PacketCodec codecForType = codecForType(type);
                if (codecForType == null) return null;
                var accessor = comp.getAccessor();
                var ctor = clazz.getDeclaredConstructor(type);
                ctor.setAccessible(true);
                accessor.setAccessible(true);
                // Build tuple codec: PacketCodec.tuple(codecForType, getter, constructor)
                // Use raw PacketCodec to avoid generic capture issues
                return (PacketCodec) PacketCodec.tuple(
                        (PacketCodec) codecForType,
                        payload -> {
                            try {
                                return accessor.invoke(payload);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        value -> {
                            try {
                                return (CustomPayload) ctor.newInstance(value);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
            }
            // For >1 fields we could chain tuples, but keep warning for now unless needed
            // Attempt to support 2-field common case (UUID + boolean) like LightningFormStatePayload
            if (components.length == 2) {
                return tryGenerateTwoFieldCodec(clazz, components);
            }
            return null;
        } catch (Exception e) {
            LOGGER.debug("Codec auto-generation failed for {}: {}", clazz.getSimpleName(), e.toString());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PacketCodec<? super RegistryByteBuf, ?> tryGenerateTwoFieldCodec(Class<?> clazz, java.lang.reflect.RecordComponent[] components) {
        try {
            Class<?> t1 = components[0].getType();
            Class<?> t2 = components[1].getType();
            PacketCodec c1 = codecForType(t1);
            PacketCodec c2 = codecForType(t2);
            if (c1 == null || c2 == null) return null;
            var a1 = components[0].getAccessor();
            var a2 = components[1].getAccessor();
            a1.setAccessible(true);
            a2.setAccessible(true);
            Class<?>[] ctorTypes = {t1, t2};
            var ctor = clazz.getDeclaredConstructor(ctorTypes);
            ctor.setAccessible(true);
            // Use nested tuple? PacketCodec.tuple has overloads for BiFunction
            // For 2 fields we can use PacketCodec.tuple(c1, getter1, c2, getter2, constructor)
            // Fabric's PacketCodec.tuple supports multiple arities. Pick generic approach via manual codec.
            return new PacketCodec<RegistryByteBuf, Object>() {
                @Override
                public Object decode(RegistryByteBuf buf) {
                    try {
                        Object v1 = c1.decode(buf);
                        Object v2 = c2.decode(buf);
                        return ctor.newInstance(v1, v2);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void encode(RegistryByteBuf buf, Object value) {
                    try {
                        Object v1 = a1.invoke(value);
                        Object v2 = a2.invoke(value);
                        ((PacketCodec) c1).encode(buf, v1);
                        ((PacketCodec) c2).encode(buf, v2);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (Exception e) {
            LOGGER.debug("Two-field codec generation failed for {}: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    private static PacketCodec<?, ?> codecForType(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return net.minecraft.network.codec.PacketCodecs.VAR_INT;
        }
        if (type == boolean.class || type == Boolean.class) {
            return net.minecraft.network.codec.PacketCodecs.BOOLEAN;
        }
        if (type == float.class || type == Float.class) {
            return net.minecraft.network.codec.PacketCodecs.FLOAT;
        }
        if (type == double.class || type == Double.class) {
            return net.minecraft.network.codec.PacketCodecs.DOUBLE;
        }
        if (type == String.class) {
            return net.minecraft.network.codec.PacketCodecs.STRING;
        }
        if (type == Identifier.class) {
            return Identifier.PACKET_CODEC;
        }
        if (type == java.util.UUID.class) {
            return net.minecraft.util.Uuids.PACKET_CODEC;
        }
        if (type == byte[].class) {
            return net.minecraft.network.codec.PacketCodecs.BYTE_ARRAY;
        }
        return null;
    }

    /**
     * Overload that defaults to {@link PayloadDirection#C2S} when no annotation is present.
     */
    public static boolean autoRegister(Class<? extends CustomPayload> clazz) {
        return autoRegister(clazz, PayloadDirection.C2S);
    }

    /**
     * If the payload class is not yet registered, registers it now in the given direction.
     * This is the "if necessary" hook – call before sending or before handling to guarantee
     * the codec exists even if {@code Glungus} forgot to register it.
     */
    public static void ensureRegistered(Class<? extends CustomPayload> clazz, PayloadDirection direction) {
        try {
            Identifier identifier = resolveIdentifier(clazz);
            if (identifier == null) {
                LOGGER.warn("ensureRegistered failed for {}: cannot resolve Identifier (no ID field and no @AutoPayload value)", clazz.getName());
                return;
            }
            boolean alreadyDone = switch (direction) {
                case C2S -> REGISTERED_C2S.contains(identifier);
                case S2C -> REGISTERED_S2C.contains(identifier);
                case BOTH -> REGISTERED_C2S.contains(identifier) && REGISTERED_S2C.contains(identifier);
            };
            if (!alreadyDone) {
                LOGGER.info("Payload {} not yet registered for {}, auto-generating now (ensureRegistered)", clazz.getSimpleName(), direction);
                autoRegister(clazz, direction);
            }
        } catch (Exception e) {
            LOGGER.warn("ensureRegistered failed for {}", clazz.getName(), e);
        }
    }

    private static Identifier resolveIdentifier(Class<? extends CustomPayload> clazz) {
        try {
            Field idField = clazz.getField("ID");
            CustomPayload.Id<?> id = (CustomPayload.Id<?>) idField.get(null);
            return id.id();
        } catch (NoSuchFieldException e) {
            AutoPayload ann = clazz.getAnnotation(AutoPayload.class);
            if (ann != null && !ann.value().isEmpty()) {
                return Identifier.of(Glungus.MOD_ID, ann.value());
            }
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Bulk helper: auto-registers every given payload class, each in its
     * annotated direction (or C2S/S2C fallback for mixed lists).
     */
    @SafeVarargs
    public static void autoRegisterAll(Class<? extends CustomPayload>... payloads) {
        for (Class<? extends CustomPayload> c : payloads) {
            autoRegister(c);
        }
    }

    /**
     * Bulk helper for a collection.
     */
    public static void autoRegisterAll(Collection<Class<? extends CustomPayload>> payloads) {
        for (Class<? extends CustomPayload> c : payloads) {
            autoRegister(c);
        }
    }

    // ---------------------------------------------------------------
    //  Runtime generation helpers – create and register without a file
    // ---------------------------------------------------------------

    /**
     * One-shot helper to define and register a C2S payload at runtime.
     * This is useful when a new power needs networking "if necessary" but
     * you don't want to create a new {@code *Payload.java} file up front.
     *
     * @param id codec identifier
     * @param codec packet codec
     * @param handler server handler
     */
    public static <T extends CustomPayload> void createAndRegisterC2S(
            CustomPayload.Id<T> id,
            PacketCodec<? super RegistryByteBuf, T> codec,
            ServerPlayNetworking.PlayPayloadHandler<T> handler
    ) {
        registerC2S(id, codec);
        ServerPlayNetworking.registerGlobalReceiver(id, handler);
        LOGGER.info("Created and registered C2S payload {} with handler", id.id());
    }

    /**
     * Creates minimal codecs for payloads that carry no data (unit / empty records).
     * Shorthand for {@code PacketCodec.unit(new MyPayload())}.
     */
    public static <T extends CustomPayload> PacketCodec<RegistryByteBuf, T> unitCodec(T instance) {
        return PacketCodec.unit(instance);
    }

    /**
     * Helper to build a bidirectional identifier under the mod namespace.
     */
    public static CustomPayload.Id<GenericPayload> genericId() {
        return GenericPayload.ID;
    }

    // ---------------------------------------------------------------
    //  GenericPayload dispatch (server side)
    // ---------------------------------------------------------------

    /**
     * Registers the {@link GenericPayload} bidirectionally (codec) and wires its
     * server-side multiplexed channel dispatch. Called once during mod init; safe to call twice.
     * <p>
     * Client-side dispatch is registered separately via
     * {@link org.xiaojian999.superpowers.network.client.PayloadRegistryClient#registerGenericClient()}.
     */
    public static void registerGenericPayload() {
        if (!isRegisteredC2S(GenericPayload.ID.id()) || !isRegisteredS2C(GenericPayload.ID.id())) {
            registerBoth(GenericPayload.ID, GenericPayload.CODEC);
        }
        // Ensure server dispatcher is registered exactly once (when C2S just became registered we add handler,
        // otherwise if already registered we check if handler already present via flag).
        // We use a set check: if GENERIC_C2S_HANDLERS is specially flagged? Simpler: always try to register,
        // but ServerPlayNetworking will throw if duplicate ID handler. So guard with a static boolean.
        if (!genericServerDispatcherRegistered) {
            ServerPlayNetworking.registerGlobalReceiver(GenericPayload.ID, (payload, context) -> {
                var handler = GENERIC_C2S_HANDLERS.get(payload.channel());
                if (handler != null) {
                    handler.receive(payload, context);
                } else {
                    LOGGER.warn("No C2S handler for generic channel {}", payload.channel());
                }
            });
            genericServerDispatcherRegistered = true;
            LOGGER.info("Registered generic payload dispatcher (C2S)");
        }
    }

    private static boolean genericServerDispatcherRegistered = false;

    /**
     * Registers a handler for a specific generic channel (C2S).
     * The channel is auto-created if necessary – no prior GenericPayload setup needed.
     */
    public static void registerGenericC2SHandler(Identifier channel, ServerPlayNetworking.PlayPayloadHandler<GenericPayload> handler) {
        ensureGenericRegistered();
        GENERIC_C2S_HANDLERS.put(channel, handler);
        LOGGER.info("Registered generic C2S handler for channel {}", channel);
    }

    /**
     * Overload taking a path under {@code glungus} namespace.
     */
    public static void registerGenericC2SHandler(String path, ServerPlayNetworking.PlayPayloadHandler<GenericPayload> handler) {
        registerGenericC2SHandler(Identifier.of(Glungus.MOD_ID, path), handler);
    }

    public static void ensureGenericRegistered() {
        if (!isRegisteredC2S(GenericPayload.ID.id()) || !isRegisteredS2C(GenericPayload.ID.id())) {
            registerGenericPayload();
        }
    }

    /**
     * Sends a generic S2C payload from server to a specific player.
     */
    public static void sendGenericS2C(ServerPlayerEntity player, Identifier channel, byte[] data) {
        ensureGenericRegistered();
        ServerPlayNetworking.send(player, new GenericPayload(channel, data));
    }

    /**
     * Overload for glungus-namespace channel.
     */
    public static void sendGenericS2C(ServerPlayerEntity player, String path, byte[] data) {
        sendGenericS2C(player, Identifier.of(Glungus.MOD_ID, path), data);
    }

    // ---------------------------------------------------------------
    //  Bulk init helpers
    // ---------------------------------------------------------------

    /**
     * Attempts to discover all {@link CustomPayload} classes under the given base
     * package that carry {@link AutoPayload} and auto-registers them. This is the
     * "if necessary" scanning path – new payload records only need the annotation
     * and will be picked up without editing {@code registerBuiltins()}.
     * <p>
     * Best-effort: failures are logged but do not abort mod init. In remapped
     * production jars the scan walks the mod jar; in the dev environment it walks
     * the filesystem. If scanning fails, the explicit list below ensures built-ins
     * still register.
     */
    public static void discoverAndRegister(String basePackage) {
        String packagePath = basePackage.replace('.', '/');
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = PayloadRegistry.class.getClassLoader();
            Enumeration<URL> resources = cl.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    Path root = Path.of(url.toURI());
                    Files.walkFileTree(root, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.toString().endsWith(".class")) {
                                String rel = root.relativize(file).toString()
                                        .replace('\\', '/')
                                        .replace('/', '.')
                                        .replace(".class", "");
                                String className = basePackage + "." + rel;
                                tryScanClass(className);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else if ("jar".equals(protocol)) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = conn.getJarFile()) {
                        var entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(packagePath) && name.endsWith(".class") && !entry.isDirectory()) {
                                String className = name.replace('/', '.').replace(".class", "");
                                // Skip inner classes and the registry itself to avoid recursion
                                if (className.contains("$") || className.endsWith(".PayloadRegistry") || className.endsWith(".GenericPayload")) {
                                    continue;
                                }
                                tryScanClass(className);
                            }
                        }
                    }
                }
            }
        } catch (IOException | java.net.URISyntaxException e) {
            LOGGER.debug("Payload auto-discover scan failed for {}: {}", basePackage, e.toString());
        }
    }

    private static void tryScanClass(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, PayloadRegistry.class.getClassLoader());
            if (!CustomPayload.class.isAssignableFrom(clazz)) return;
            AutoPayload ann = clazz.getAnnotation(AutoPayload.class);
            if (ann == null) return;
            @SuppressWarnings("unchecked")
            Class<? extends CustomPayload> payloadClass = (Class<? extends CustomPayload>) clazz;
            PayloadDirection dir = ann.direction();
            // Avoid double-registering the generic payload here (it has @AutoPayload? no)
            if (clazz == GenericPayload.class) return;
            LOGGER.debug("Discovered @AutoPayload {} -> {}", className, dir);
            autoRegister(payloadClass, dir);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOGGER.trace("Skipping class {} during payload scan: {}", className, e.toString());
        } catch (Throwable t) {
            LOGGER.debug("Failed to auto-register discovered payload {}: {}", className, t.toString());
        }
    }

    /**
     * Registers all built-in Glungus payloads. This replaces the hand-written
     * sequence in {@link Glungus#onInitialize()} and is the point where
     * "auto-generate if necessary" is applied: any payload that was missed is
     * registered here automatically, and future payloads only need to be added
     * to the list (or discovered via annotation scan).
     */
    public static void registerBuiltins() {
        // Generic fallback first – always available for dynamic features.
        registerGenericPayload();

        // Try package-scan first: any new @AutoPayload record will be auto-picked.
        // This makes future payloads zero-boilerplate – just add a record file.
        discoverAndRegister("org.xiaojian999.superpowers");
        // Also scan network package for any custom auto payloads
        discoverAndRegister("org.xiaojian999.superpowers.network");

        // Explicit fallback list ensures built-ins work even if scanning is disabled
        // or misses classes due to mixin remapping / class-loader quirks.
        autoRegister(org.xiaojian999.superpowers.UsePowerPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GhostFlightSpeedPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodLaserPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodBlessPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodLevitatePayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodFlightSpeedPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodSmitePayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodAnnihilatePayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodNovaPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodOmnipotencePayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodBanishPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodNoClipPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodGiantPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.GodTelekinesisPayload.class, PayloadDirection.C2S);
        autoRegister(org.xiaojian999.superpowers.PowerStatusPayload.class, PayloadDirection.S2C);
        autoRegister(org.xiaojian999.superpowers.LightningFormStatePayload.class, PayloadDirection.S2C);
        autoRegister(org.xiaojian999.superpowers.NatureEarthquakePayload.class, PayloadDirection.S2C);
    }

    /**
     * Clears internal tracking sets – used only in tests.
     */
    static void clearForTests() {
        REGISTERED_C2S.clear();
        REGISTERED_S2C.clear();
        GENERIC_C2S_HANDLERS.clear();
        genericServerDispatcherRegistered = false;
    }
}
