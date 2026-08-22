package org.xiaojian999.superpowers;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Entity types added by the mod. Mirrors the vanilla lightning bolt setup
 * (zero-sized, wide tracking range, no position updates), but the type is
 * dedicated to cosmetic "big" bolts so the client can render them scaled up.
 */
public final class ModEntities {
    public static final EntityType<BigLightningEntity> BIG_LIGHTNING = Registry.register(
            Registries.ENTITY_TYPE,
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Glungus.MOD_ID, "big_lightning")),
            EntityType.Builder.create(BigLightningEntity::new, SpawnGroup.MISC)
                    .dimensions(0.0F, 0.0F)
                    .maxTrackingRange(16)
                    .trackingTickInterval(Integer.MAX_VALUE)
                    .disableSaving()
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Glungus.MOD_ID, "big_lightning")))
    );

    private ModEntities() {
    }

    /** Forces the entity types to be registered. Called from the mod initializer. */
    public static void register() {
        // The static field above registers the types; touching the class is enough.
    }
}
