package org.xiaojian999.superpowers.client;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LightningEntityRenderer;
import net.minecraft.client.render.entity.state.LightningEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Renders {@link org.xiaojian999.superpowers.BigLightningEntity} bolts. The
 * whole bolt model is scaled by 1.75x around its origin, making it 75% bigger
 * than a normal lightning bolt.
 */
public class BigLightningEntityRenderer extends LightningEntityRenderer {
    private static final float SCALE = 1.75F;

    public BigLightningEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(
            LightningEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue renderCommandQueue,
            CameraRenderState cameraRenderState
    ) {
        matrices.push();
        matrices.scale(SCALE, SCALE, SCALE);
        super.render(state, matrices, renderCommandQueue, cameraRenderState);
        matrices.pop();
    }
}
