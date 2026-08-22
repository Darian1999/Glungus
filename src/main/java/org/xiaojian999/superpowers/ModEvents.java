package org.xiaojian999.superpowers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.UUID;

final class ModEvents {
    private ModEvents(){}
    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(UsePowerPayload.ID, (p,c)->c.server().execute(()->PowerManager.usePower(c.player(),p.slot())));
        ServerPlayNetworking.registerGlobalReceiver(GhostFlightSpeedPayload.ID,(p,c)->c.server().execute(()->GhostPowerHandler.adjustFlightSpeed(c.player(),p.direction())));
        ServerPlayNetworking.registerGlobalReceiver(GodLaserPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.setLaserActive(c.player(),p.active())));
        ServerPlayNetworking.registerGlobalReceiver(GodBlessPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.blessTarget(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodLevitatePayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.levitateMobs(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodFlightSpeedPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.adjustFlightSpeed(c.player(),p.direction())));
        ServerPlayNetworking.registerGlobalReceiver(GodSmitePayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.smiteTarget(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodAnnihilatePayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.annihilateArea(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodNovaPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.holyNova(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodOmnipotencePayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.activateOmnipotence(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodBanishPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.banishTarget(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodNoClipPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.toggleNoClip(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodGiantPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.toggleGiant(c.player())));
        ServerPlayNetworking.registerGlobalReceiver(GodTelekinesisPayload.ID,(p,c)->c.server().execute(()->GodPowerHandler.toggleTelekinesis(c.player())));
        UseEntityCallback.EVENT.register((player,world,hand,entity,hitResult)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if(!(entity instanceof MobEntity mob)||!GhostPowerHandler.isFormActive(sp.getUuid())||GhostPowerHandler.isPossessed(mob.getUuid())) return ActionResult.PASS;
            GhostPowerHandler.possess(sp,mob); return ActionResult.FAIL;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{ if(entity instanceof SnowballEntity s && s.getOwner() instanceof ServerPlayerEntity o) IcePowerHandler.onSnowballLoaded(s,o); });
        ServerTickEvents.END_WORLD_TICK.register(world->{ WaterPowerHandler.tick(world); AirPowerHandler.tick(world); FirePowerHandler.tick(world); IcePowerHandler.tick(world); NaturePowerHandler.tick(world); });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            PowerCooldowns.tickAll(); GhostPowerHandler.tickServer(server); FirePowerHandler.tickServer(server); LightningPowerHandler.tickServer(server); NaturePowerHandler.tickServer(server); GodPowerHandler.tickServer(server);
            for(ServerPlayerEntity p: server.getPlayerManager().getPlayerList()){ p.noClip=PowerManager.isNoClipActive(p); AirPowerHandler.tickPlayer(p); GhostPowerHandler.tickPlayer(p); LightningPowerHandler.tickPlayer(p); FirePowerHandler.tickPlayer(p); GodPowerHandler.tickPlayer(p); }
        });
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->{ PowerManager.sendPowerStatus(h.player); LightningPowerHandler.sendActiveFormStates(h.player,server); NaturePowerHandler.sendActiveEarthquakes(h.player,server); });
        ServerPlayConnectionEvents.DISCONNECT.register((h,server)->{
            UUID id=h.player.getUuid(); PowerManager.onDisconnect(id,h.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server-> PowerManager.onServerStopped(server));
    }
}
