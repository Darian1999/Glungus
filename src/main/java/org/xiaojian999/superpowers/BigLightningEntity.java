package org.xiaojian999.superpowers;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.world.World;

/**
 * A purely visual lightning bolt for the Lightning ultimate. It is cosmetic
 * (never sets fire, powers rods, or strikes entities on its own), so the mod
 * applies the "big lightning" damage itself. The 75% larger appearance is
 * handled client-side by {@code BigLightningEntityRenderer}.
 */
public class BigLightningEntity extends LightningEntity {
    public BigLightningEntity(EntityType<? extends LightningEntity> entityType, World world) {
        super(entityType, world);
        this.setCosmetic(true);
    }
}
