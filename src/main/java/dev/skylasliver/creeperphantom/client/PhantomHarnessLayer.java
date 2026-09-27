package dev.skylasliver.creeperphantom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skylasliver.creeperphantom.CreeperPhantomEntity;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** A narrow belly band attached to the body, never to the flapping wings. */
final class PhantomHarnessLayer extends RenderLayer<CreeperPhantomEntity, PhantomModel<CreeperPhantomEntity>> {
    private final ModelPart band;
    private final ModelPart rings;

    PhantomHarnessLayer(CreeperPhantomRenderer renderer) {
        super(renderer);
        MeshDefinition leather = new MeshDefinition();
        MeshDefinition brass = new MeshDefinition();
        AirliftHarnessModel.box(leather, "back_band", -3.25F, -2.3F, -4.5F, 5.5F, .3F, 3F);
        AirliftHarnessModel.box(leather, "belly_band", -3.25F, 1F, -4.5F, 5.5F, .3F, 3F);
        AirliftHarnessModel.box(leather, "left_band", -3.3F, -2F, -4.5F, .3F, 3F, 3F);
        AirliftHarnessModel.box(leather, "right_band", 2F, -2F, -4.5F, .3F, 3F, 3F);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? -3.7F : 2F;
            for (int end = 0; end < 2; end++) {
                float z = end == 0 ? -4F : -2F;
                AirliftHarnessModel.box(brass, "lug_"+side+"_"+end, x, .9F, z-.3F, .7F, 1.4F, .6F);
            }
        }
        band = LayerDefinition.create(leather, 64, 64).bakeRoot();
        rings = LayerDefinition.create(brass, 64, 64).bakeRoot();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, CreeperPhantomEntity entity,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (entity.isInvisible() || !entity.isAlive() || entity.getPassengers().isEmpty()) return;
        pose.pushPose();
        getParentModel().root().translateAndRotate(pose);
        getParentModel().root().getChild("body").translateAndRotate(pose);
        band.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(AirliftHarnessModel.LEATHER)), light, OverlayTexture.NO_OVERLAY);
        rings.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(AirliftHarnessModel.BRASS)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
