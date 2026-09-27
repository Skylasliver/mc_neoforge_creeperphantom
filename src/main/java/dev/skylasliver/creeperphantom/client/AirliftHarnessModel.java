package dev.skylasliver.creeperphantom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Cuboids authored in art/airlift_harness.bbmodel; body origin matches HumanoidModel.body. */
final class AirliftHarnessModel {
    static final ResourceLocation LEATHER = texture("airlift_leather");
    static final ResourceLocation BRASS = texture("airlift_brass");
    private final ModelPart leather;
    private final ModelPart brass;

    AirliftHarnessModel() {
        MeshDefinition leather = new MeshDefinition();
        MeshDefinition brass = new MeshDefinition();
        box(leather, "chest_belt_front_volume", -4.6F, 8F, -2.9F, 9.2F, 2F, 0.6F);
        box(leather, "chest_belt_back_volume", -4.6F, 8F, 2.3F, 9.2F, 2F, 0.6F);
        box(leather, "belt_left_volume", -4.6F, 8F, -2.3F, 0.6F, 2F, 4.6F);
        box(leather, "belt_right_volume", 4F, 8F, -2.3F, 0.6F, 2F, 4.6F);
        box(leather, "left_front_strap_volume", -3.325F, -0.5F, -2.9F, 1.25F, 8.5F, 0.6F);
        box(leather, "left_back_strap_volume", -3.325F, -0.5F, 2.3F, 1.25F, 8.5F, 0.6F);
        box(leather, "left_shoulder_volume", -3.325F, -0.5F, -2.9F, 1.25F, 0.6F, 5.8F);
        box(leather, "right_front_strap_volume", 2.075F, -0.5F, -2.9F, 1.25F, 8.5F, 0.6F);
        box(leather, "right_back_strap_volume", 2.075F, -0.5F, 2.3F, 1.25F, 8.5F, 0.6F);
        box(leather, "right_shoulder_volume", 2.075F, -0.5F, -2.9F, 1.25F, 0.6F, 5.8F);
        box(brass, "belt_buckle_volume", -1.4F, 7.6F, -3.3F, 2.8F, 2.8F, 0.5F);
        box(brass, "left_base_volume", -3.45F, 2.25F, 2.9F, 1.5F, 0.5F, 1.2F);
        box(brass, "left_loop_front_volume", -3.45F, 1.15F, 2.9F, 1.5F, 1.1F, 0.4F);
        box(brass, "left_loop_back_volume", -3.45F, 1.15F, 3.7F, 1.5F, 1.1F, 0.4F);
        box(brass, "left_loop_top_volume", -3.45F, 0.8F, 2.9F, 1.5F, 0.4F, 1.2F);
        box(brass, "right_base_volume", 1.95F, 2.25F, 2.9F, 1.5F, 0.5F, 1.2F);
        box(brass, "right_loop_front_volume", 1.95F, 1.15F, 2.9F, 1.5F, 1.1F, 0.4F);
        box(brass, "right_loop_back_volume", 1.95F, 1.15F, 3.7F, 1.5F, 1.1F, 0.4F);
        box(brass, "right_loop_top_volume", 1.95F, 0.8F, 2.9F, 1.5F, 0.4F, 1.2F);
        this.leather = LayerDefinition.create(leather, 64, 64).bakeRoot();
        this.brass = LayerDefinition.create(brass, 64, 64).bakeRoot();
    }

    void render(PoseStack pose, MultiBufferSource buffers, int light) {
        leather.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LEATHER)), light, OverlayTexture.NO_OVERLAY);
        brass.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BRASS)), light, OverlayTexture.NO_OVERLAY);
    }

    static void box(MeshDefinition mesh, String name, float x, float y, float z, float w, float h, float d) {
        mesh.getRoot().addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d), PartPose.ZERO);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("skylasliver_creeperphantom", "textures/entity/" + name + ".png");
    }
}
