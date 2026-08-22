package org.xiaojian999.superpowers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Extracted from PowerManager.sendPowerStatusForSlot — presentation logic isolated. */
final class PowerStatusSender {
    private PowerStatusSender(){}
    static void sendAll(ServerPlayerEntity player) { sendForSlot(player,0); sendForSlot(player,1); }
    static void sendForSlot(ServerPlayerEntity player, int slotIndex) {
        UUID uuid = player.getUuid();
        Power power = PowerManager.getEquippedPower(uuid, slotIndex);
        if (power == null) {
            ServerPlayNetworking.send(player, new PowerStatusPayload(0,0,0,0,-1,0.0F,slotIndex));
            return;
        }
        SlotKey key = new SlotKey(uuid, slotIndex);
        int flags = 0;
        if (power==Power.ICE) flags|=PowerStatusPayload.ICE_EQUIPPED;
        else if (power==Power.AIR){ flags|=PowerStatusPayload.AIR_EQUIPPED; if(AirPowerHandler.isFlightActive(uuid)) flags|=PowerStatusPayload.AIR_FLIGHT_ACTIVE; }
        else if (power==Power.FIRE){ flags|=PowerStatusPayload.FIRE_EQUIPPED; if(FirePowerHandler.isImmune(uuid)) flags|=PowerStatusPayload.FIRE_IMMUNE_ACTIVE; if(FirePowerHandler.isBeamActive(key)) flags|=PowerStatusPayload.FIRE_BEAM_ACTIVE; }
        else if (power==Power.WATER) flags|=PowerStatusPayload.WATER_EQUIPPED;
        else if (power==Power.GHOST){ flags|=PowerStatusPayload.GHOST_EQUIPPED; if(GhostPowerHandler.isFormActive(uuid)) flags|=PowerStatusPayload.GHOST_FORM_ACTIVE; if(GhostPowerHandler.isPossessing(uuid)) flags|=PowerStatusPayload.GHOST_POSSESSING; }
        else if (power==Power.LIGHTNING){ flags|=PowerStatusPayload.LIGHTNING_EQUIPPED; if(LightningPowerHandler.isFormActive(key)) flags|=PowerStatusPayload.LIGHTNING_FORM_ACTIVE; }
        else if (power==Power.NATURE){ flags|=PowerStatusPayload.NATURE_EQUIPPED; if(NaturePowerHandler.isFlowerTrailActive(uuid)) flags|=PowerStatusPayload.NATURE_FLOWER_TRAIL_ACTIVE; if(NaturePowerHandler.isVineRingActive(key)) flags|=PowerStatusPayload.NATURE_VINE_RING_ACTIVE; if(NaturePowerHandler.isEarthquakeActive(uuid)) flags|=PowerStatusPayload.NATURE_EARTHQUAKE_ACTIVE; }
        else if (power==Power.GOD){ flags|=PowerStatusPayload.GOD_EQUIPPED; if(GodPowerHandler.isActive(uuid)) flags|=PowerStatusPayload.GOD_MODE_ACTIVE; if(GodPowerHandler.isNoClipActive(uuid)) flags|=PowerStatusPayload.GOD_NOCLIP_ACTIVE; if(GodPowerHandler.isGiant(uuid)) flags|=PowerStatusPayload.GOD_GIANT_ACTIVE; if(GodPowerHandler.isTelekinesisHolding(uuid)) flags|=PowerStatusPayload.GOD_TELEKINESIS_ACTIVE; }
        if (power==Power.ICE && IcePowerHandler.isSnowballPrimed(uuid)) flags|=PowerStatusPayload.SNOWBALL_PRIMED;
        Long last = PowerManager.getLastUltimatePress(key);
        if (last!=null){ long cur=player.getEntityWorld().getTime(); if(cur>=last && cur-last<=PowerManager.ULTIMATE_DOUBLE_TAP_WINDOW) flags|=PowerStatusPayload.ULTIMATE_PRIMED; else PowerManager.clearUltimatePress(key); }
        int beam=PowerCooldowns.beamRemaining(key);
        int snow=PowerCooldowns.secondPowerRemaining(key);
        if(power==Power.FIRE){ Integer t=FirePowerHandler.getActiveBeamTicks(key); if(t!=null&&t>0) beam=t; }
        if(power==Power.NATURE){ Integer t=NaturePowerHandler.getVineRingRemaining(key); if(t!=null) snow=t; }
        int ult=PowerCooldowns.ultimateRemaining(key);
        if(power==Power.LIGHTNING){ Integer t=LightningPowerHandler.getFormRemaining(key); if(t!=null) ult=t; }
        if(power==Power.NATURE){ Integer t=NaturePowerHandler.getEarthquakeRemaining(uuid); if(t!=null) ult=t; }
        int mobId=-1; float offY=0; MobEntity mob=GhostPowerHandler.getPossessedMob(player);
        if(mob!=null){ mobId=mob.getId(); offY=(float)((mob.getEyeY()-mob.getY())-(player.getEyeY()-player.getY())); }
        ServerPlayNetworking.send(player, new PowerStatusPayload(flags,beam,snow,ult,mobId,offY,slotIndex));
    }
}
