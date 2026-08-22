package org.xiaojian999.superpowers.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.xiaojian999.superpowers.PowerStatusPayload;
import org.xiaojian999.superpowers.Glungus;

import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawAbilityCard;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawDecorations;
import static org.xiaojian999.superpowers.client.AbilityHudSupport.drawPanel;

public final class LightningHud {
    private static final Identifier HUD_ID = Identifier.of(Glungus.MOD_ID, "lightning_hud");

    private LightningHud() {
    }

    public static void initialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HUD_ID, LightningHud::render);
    }

    private static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (HudState.isDual() || client.player == null || client.options.hudHidden) {
            return;
        }
        HudState.Slot slot = HudState.slot(0);
        if (!"lightning".equals(slot.power)) {
            return;
        }

        HudData.Layout layout = HudData.layout();
        HudData.PowerConfig config = HudData.power("lightning");
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

        drawPanel(drawContext, panelX, panelY, panelWidth, layout.panel.height, HudData.color(config.accent), layout, config);

        int textMaxWidth = panelWidth - header.paddingX * 2;
        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config.title,
                panelX + header.paddingX,
                panelY + header.titleY,
                textMaxWidth,
                HudData.color(palette.primaryText)
        );
        AbilityHudSupport.drawTextClipped(
                drawContext,
                client.textRenderer,
                config.subtitle,
                panelX + header.paddingX,
                panelY + header.subtitleY,
                textMaxWidth,
                HudData.color(palette.subtitleText)
        );

        drawDecorations(drawContext, panelX, panelY, panelWidth, config);

        int cardGap = layout.card.gap;
        int cardWidth = (panelWidth - header.paddingX * 2 - cardGap * 2) / 3;
        int cardHeight = layout.card.height;
        int firstCardX = panelX + header.paddingX;

        drawAbilityCard(
                drawContext,
                client.textRenderer,
                firstCardX,
                cardY,
                cardWidth,
                cardHeight,
                layout,
                config.cards.get(0),
                slot.beamCooldown,
                false,
                config
        );
        drawAbilityCard(
                drawContext,
                client.textRenderer,
                firstCardX + cardWidth + cardGap,
                cardY,
                cardWidth,
                cardHeight,
                layout,
                config.cards.get(1),
                slot.snowballCooldown,
                false,
                config
        );
        drawAbilityCard(
                drawContext,
                client.textRenderer,
                firstCardX + (cardWidth + cardGap) * 2,
                cardY,
                cardWidth,
                cardHeight,
                layout,
                config.cards.get(2),
                slot.ultimateCooldown,
                slot.ultimatePromptTicks > 0,
                config
        );

        int footerY = panelY + layout.footer.y;
        AbilityHudSupport.drawCenteredTextClipped(
                drawContext,
                client.textRenderer,
                slot.ultimatePromptTicks > 0 ? config.footer.prompt : config.footer.hint,
                panelX + panelWidth / 2,
                footerY,
                panelWidth - header.paddingX * 2,
                HudData.color(slot.ultimatePromptTicks > 0 ? palette.primaryText : palette.dimText)
        );

        drawContext.getMatrices().popMatrix();
    }
}
