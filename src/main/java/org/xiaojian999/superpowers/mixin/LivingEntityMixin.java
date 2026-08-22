package org.xiaojian999.superpowers.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.PowerManager;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** Walking on the water's surface moves 45% faster than normal ground speed. */
    private static final float WATER_WALK_SPEED_MULTIPLIER = 1.45F;

    /**
     * While walking on the water's surface, borrow the ice block's slipperiness
     * so movement glides like ice: low friction keeps momentum going, making the
     * water feel slippery underfoot.
     */
    @Redirect(
            method = "travelMidAir",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F")
    )
    private float superpowers$waterWalkSlipperiness(Block block) {
        if (block == Blocks.WATER && superpowers$isWalkingOnWaterSurface()) {
            return Blocks.ICE.getSlipperiness();
        }
        return block.getSlipperiness();
    }

    /**
     * Ground movement speed is resolved through {@code getMovementSpeed(float)}
     * inside {@code travelMidAir}; boost it by 45% while walking on water.
     */
    @Redirect(
            method = "getMovementSpeed(F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getMovementSpeed()F")
    )
    private float superpowers$waterWalkSpeed(LivingEntity entity) {
        float speed = entity.getMovementSpeed();
        if (superpowers$isWalkingOnWaterSurface()) {
            speed *= WATER_WALK_SPEED_MULTIPLIER;
        }
        return speed;
    }

    /**
     * True only while the player is standing on the water's surface with the
     * Water power active — the same state the surface snap in {@code travel}
     * handles, so land movement is never affected.
     */
    private boolean superpowers$isWalkingOnWaterSurface() {
        if (!((Object) this instanceof PlayerEntity player)
                || !PowerManager.isWaterWalkingActive(player)
                || player.isSpectator()) {
            return false;
        }
        return player.getEntityWorld()
                .getFluidState(player.getVelocityAffectingPos())
                .isIn(FluidTags.WATER);
    }

    /** God Mode kills mobs immediately when the player hits them. */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void superpowers$godModeInstantKill(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if ((Object) this instanceof MobEntity
                && source.getAttacker() instanceof PlayerEntity attacker
                && PowerManager.isGodModeActive(attacker)) {
            LivingEntity target = (LivingEntity) (Object) this;
            target.setHealth(0.0F);
            target.kill(world);
            callbackInfo.setReturnValue(true);
        }
    }

    /**
     * Storm Form players are living lightning: no damage source can hurt them
     * while the form is active.
     */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void superpowers$stormFormInvincible(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (PowerManager.isLightningFormActive((LivingEntity) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * Omnipotence is a simulation of absolute power: while it lasts, no damage
     * source — not even the void — can touch the god.
     */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void superpowers$omnipotenceInvincible(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (PowerManager.isOmnipotenceActive((LivingEntity) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void superpowers$reduceAirGravity(CallbackInfoReturnable<Double> callbackInfo) {
        if ((Object) this instanceof PlayerEntity player
                && player.getAbilities().allowFlying
                && !player.isCreative()
                && !player.isSpectator()) {
            callbackInfo.setReturnValue(callbackInfo.getReturnValueD() * 0.85D);
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void superpowers$walkOnWater(Vec3d movementInput, CallbackInfo callbackInfo) {
        if (!((Object) this instanceof PlayerEntity player)
                || !PowerManager.isWaterWalkingActive(player)
                || player.isSpectator()) {
            return;
        }

        boolean swimming = player.isSneaking()
                || player.isSwimming()
                || Math.abs(movementInput.y) > 1.0E-4D;
        if (swimming) {
            // The previous surface snap marks the player as grounded. Clear that stale state
            // before vanilla water movement handles vertical input (jump/sneak) again.
            if (player.isTouchingWater()) {
                player.setOnGround(false);
            }
            return;
        }

        BlockPos waterPosition = BlockPos.ofFloored(player.getX(), player.getY() - 0.1D, player.getZ());
        if (!player.getEntityWorld().getFluidState(waterPosition).isIn(FluidTags.WATER)) {
            return;
        }

        double waterSurface = waterPosition.getY() + 1.0D;
        if (player.getY() < waterSurface + 0.2D && player.getY() > waterSurface - 1.5D) {
            player.setPosition(player.getX(), waterSurface, player.getZ());
            player.setVelocity(player.getVelocity().x, 0.0D, player.getVelocity().z);
            player.setOnGround(true);
        }
    }
}
