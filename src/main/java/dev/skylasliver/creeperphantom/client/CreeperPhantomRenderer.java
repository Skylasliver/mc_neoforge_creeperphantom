package dev.skylasliver.creeperphantom.client;

import dev.skylasliver.creeperphantom.CreeperPhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class CreeperPhantomRenderer extends MobRenderer<CreeperPhantomEntity, PhantomModel<CreeperPhantomEntity>> {
    private static final ResourceLocation NORMAL = ResourceLocation.fromNamespaceAndPath("skylasliver_creeperphantom", "textures/entity/creeper_phantom.png");
    private static final ResourceLocation CHARGED = ResourceLocation.fromNamespaceAndPath("skylasliver_creeperphantom", "textures/entity/creeper_phantom_charged.png");
    public CreeperPhantomRenderer(EntityRendererProvider.Context context) {
        super(context, new PhantomModel<>(context.bakeLayer(ModelLayers.PHANTOM)), 0.8F);
        // The custom atlas is used only for the body, head and tail.
        var body = model.root().getChild("body");
        body.getChild("left_wing_base").visible = false;
        body.getChild("right_wing_base").visible = false;
        addLayer(new VanillaWingsLayer(this, context));
        addLayer(new PhantomHarnessLayer(this));
    }
    /** Uses the same transforms as the rendered belly band, including size and flight pitch. */
    public net.minecraft.world.phys.Vec3 harnessAnchor(CreeperPhantomEntity entity, float partialTick, boolean skeleton, int end) {
        PoseStack pose = new PoseStack();
        float scale = entity.getScale();
        pose.scale(scale, scale, scale);
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        setupRotations(entity, pose, getBob(entity, partialTick), yaw, partialTick, scale);
        pose.scale(-1, -1, 1);
        scale(entity, pose, partialTick);
        pose.translate(0, -1.501F, 0);
        model.root().translateAndRotate(pose);
        model.root().getChild("body").translateAndRotate(pose);
        var point = pose.last().pose().transformPosition(new org.joml.Vector3f(
            (skeleton ? -3.35F : 2.35F)/16, 2.1F/16, (end == 0 ? -4F : -2F)/16));
        return entity.getPosition(partialTick).add(point.x, point.y, point.z);
    }

    private static final class VanillaWingsLayer extends RenderLayer<CreeperPhantomEntity, PhantomModel<CreeperPhantomEntity>> {
        private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/phantom.png");
        private final PhantomModel<CreeperPhantomEntity> wings;

        private VanillaWingsLayer(CreeperPhantomRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer);
            wings = new PhantomModel<>(context.bakeLayer(ModelLayers.PHANTOM));
            var body = wings.root().getChild("body");
            body.skipDraw = true;
            body.getChild("head").visible = false;
            body.getChild("tail_base").visible = false;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CreeperPhantomEntity entity,
                           float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
            coloredCutoutModelCopyLayerRender(getParentModel(), wings, TEXTURE, pose, buffers, light, entity,
                limbSwing, limbAmount, age, yaw, pitch, partialTick, -1);
        }
    }
    @Override
    public ResourceLocation getTextureLocation(CreeperPhantomEntity entity) { return entity.isCharged() ? CHARGED : NORMAL; }
    @Override
    protected void scale(CreeperPhantomEntity entity, PoseStack pose, float partialTick) {
        float size=1.5F*(1.0F+0.15F*entity.getPhantomSize());
        pose.scale(size,size,size);
        pose.translate(0.0F,1.3125F,0.1875F);
    }
    @Override
    protected void setupRotations(CreeperPhantomEntity entity, PoseStack pose, float age, float yaw, float partialTick, float scale) {
        super.setupRotations(entity,pose,age,yaw,partialTick,scale);
        pose.mulPose(Axis.XP.rotationDegrees(net.minecraft.util.Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
    }
}
