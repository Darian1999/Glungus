package org.xiaojian999.superpowers.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.Glungus;
import org.xiaojian999.superpowers.PowerStatusPayload;

import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawAbilityCard;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawDecorations;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawPanel;

public final class GodHud {
    private static final Identifier HUD_ID = Identifier.of(Glungus.MOD_ID, "god_hud");

    private GodHud() {
    }

    public static void initialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HUD_ID, GodHud::render);
    }

    public static boolean isGodModeActive(){
        for (int index = 0; index < HudState.SLOT_COUNT; index++){
            if ((HudState.slot(index).flags & PowerStatusPayload.GOD_MODE_ACTIVE) != 0){
                return true;
            }
        }
        return false;
    }

    public static boolean isGodNoClipActive(){
        return HudState.isGodNoClipActive();
    }

    private static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (HudState.isDual() || client.player == null || client.options.hudHidden) {
            return;
        }
        HudState.Slot slot = HudState.slot(0);
        if (!"god".equals(slot.power)) {
            return;
        }

        HudData.Layout layout = HudData.layout();
        HudData.PowerConfig config = HudData.power("god");
        if (layout == null || config == null) {
            return;
        }
        HudData.PowerConfig.Palette palette = config.palette;
        HudData.Layout.Header header = layout.header;
        float inverseGuiScale = 1.0F / client.getWindow().getScaleFactor();
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().scale(inverseGuiScale);

        int panelWidth = AbilityHudSupport.panelWidth(client, layout);
        int panelX = layout.panel.x;
        int panelY = layout.panel.y;
        int cardY = panelY + layout.card.top;
        drawPanel(drawContext, panelX, panelY, panelWidth, layout.panel.height,
                HudData.color(config.accent), layout, config);

        int textMaxWidth = panelWidth - header.paddingX * 2;
        AbilityHudSupport.drawTextClipped(drawContext, client.textRenderer, config.title,
                panelX + header.paddingX, panelY + header.titleY, textMaxWidth,
                HudData.color(palette.primaryText));
        AbilityHudSupport.drawTextClipped(drawContext, client.textRenderer, config.subtitle,
                panelX + header.paddingX, panelY + header.subtitleY, textMaxWidth,
                HudData.color(palette.subtitleText));
        drawDecorations(drawContext, panelX, panelY, panelWidth, config);

        int cardGap = layout.card.gap;
        int cardWidth = (panelWidth - header.paddingX * 2 - cardGap * 2) / 3;
        int cardHeight = layout.card.height;
        int firstCardX = panelX + header.paddingX;
        boolean active = (slot.flags & PowerStatusPayload.GOD_MODE_ACTIVE) != 0;
        drawAbilityCard(drawContext, client.textRenderer, firstCardX, cardY, cardWidth, cardHeight,
                layout, config.cards.get(0), 0, active, config);
        drawAbilityCard(drawContext, client.textRenderer, firstCardX + cardWidth + cardGap, cardY,
                cardWidth, cardHeight, layout, config.cards.get(1), 0, false, config);
        drawAbilityCard(drawContext, client.textRenderer, firstCardX + (cardWidth + cardGap) * 2, cardY,
                cardWidth, cardHeight, layout, config.cards.get(2), 0, false, config);

        int footerY = panelY + layout.footer.y;
        AbilityHudSupport.drawCenteredTextClipped(drawContext, client.textRenderer,
                config.footer.hint, panelX + panelWidth / 2, footerY,
                panelWidth - header.paddingX * 2,
                HudData.color(active ? palette.primaryText : palette.dimText));
        if (active && config.footer.prompt != null && !config.footer.prompt.isEmpty()) {
            // While God Mode is live, a second line lists the divine arsenal keys.
            AbilityHudSupport.drawCenteredTextClipped(drawContext, client.textRenderer,
                    config.footer.prompt, panelX + panelWidth / 2, footerY + 9,
                    panelWidth - header.paddingX * 2,
                    HudData.color(palette.readyText));
        }
        drawContext.getMatrices().popMatrix();
    }
}
