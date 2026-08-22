package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xiaojian999.superpowers.client.GhostHud;

import java.util.function.Predicate;

/**
 * The local player's position is normally driven by client-side movement
 * prediction, so the server's possession snap never reaches the camera. This
 * snaps the client player onto the possessed body at the end of every tick,
 * which makes the camera track the body smoothly instead of drifting off.
 *
 * <p>The same mixin keeps the possessed body out of the crosshair target: while
 * possessing, the player's eye is inside the body's bounding box, so the entity
 * raycast would always hit the body itself and mining or attacking would target
 * it instead of the block or enemy behind it.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void superpowers$possessedBodySnap(CallbackInfo ci) {
        int mobId = GhostHud.getPossessedMobId();
        if (mobId < 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        Entity mob = client.world.getEntityById(mobId);
        if (mob == null || !mob.isAlive()) {
            return;
        }
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        player.setPosition(mob.getX(), mob.getY(), mob.getZ());
        player.setVelocity(mob.getVelocity());
    }

    @Redirect(
            method = "getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;")
    )
    private static EntityHitResult superpowers$possessedRaycast(
            Entity entity,
            Vec3d min,
            Vec3d max,
            Box box,
            Predicate<Entity> predicate,
            double maxDistance
    ) {
        return ProjectileUtil.raycast(entity, min, max, box, superpowers$excludePossessedBody(predicate), maxDistance);
    }

    @Redirect(
            method = "getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/component/type/AttackRangeComponent;getHitResult(Lnet/minecraft/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/util/hit/HitResult;")
    )
    private HitResult superpowers$possessedAttackRange(
            AttackRangeComponent component,
            Entity entity,
            float tickDelta,
            Predicate<Entity> predicate
    ) {
        return component.getHitResult(entity, tickDelta, superpowers$excludePossessedBody(predicate));
    }

    /**
     * Excludes the possessed body from entity targeting, mirroring how the
     * raycast never hits the player themselves.
     */
    private static Predicate<Entity> superpowers$excludePossessedBody(Predicate<Entity> canHit) {
        int possessedMobId = GhostHud.getPossessedMobId();
        if (possessedMobId < 0) {
            return canHit;
        }
        return canHit.and(entity -> entity == null || entity.getId() != possessedMobId);
    }
}
