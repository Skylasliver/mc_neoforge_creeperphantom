package dev.skylasliver.creeperphantom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skylasliver.creeperphantom.CreeperPhantomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Uses the real animated torso and render-entry matrix, including baby and riding transforms. */
public final class AirliftHarnessLayer extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {
    private static final ResourceLocation ROPE = ResourceLocation.withDefaultNamespace("textures/block/brown_wool.png");
    private static final int SEGMENTS = 20;
    private final AirliftHarnessModel harness = new AirliftHarnessModel();

    private AirliftHarnessLayer(LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> renderer) {
        super(renderer);
    }

    public static void install(EntityRenderersEvent.AddLayers event) {
        attach(event, EntityType.ZOMBIE);
        attach(event, EntityType.SKELETON);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attach(EntityRenderersEvent.AddLayers event, EntityType<? extends LivingEntity> type) {
        var renderer = event.getRenderer(type);
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel) {
            living.addLayer(new AirliftHarnessLayer(living));
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity rider,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        Matrix4f root = AirliftRenderEvents.takeRoot(rider);
        if (!(rider.getVehicle() instanceof CreeperPhantomEntity phantom)
            || rider.isInvisible() || !rider.isAlive() || phantom.isInvisible() || !phantom.isAlive()) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(phantom);
        if (!(renderer instanceof CreeperPhantomRenderer carrier)) return;

        pose.pushPose();
        // Match AgeableListModel's body branch, not the separately enlarged baby head.
        if (getParentModel().young) {
            pose.scale(.5F, .5F, .5F);
            pose.translate(0, 1.5F, 0);
        }
        getParentModel().body.translateAndRotate(pose);
        // Keep the straps outside worn chest armour while retaining the normal torso height.
        if (!rider.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) pose.scale(1.18F, 1.02F, 1.2F);
        harness.render(pose, buffers, light);
        if (root != null) {
            Matrix4f chestToEntity = new Matrix4f(root).invert().mul(pose.last().pose());
            PoseStack ropePose = new PoseStack();
            ropePose.mulPose(root);
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE));
            for (int end = 0; end < 2; end++) {
                float sign = end == 0 ? -1 : 1;
                Vec3 start = transform(chestToEntity, sign*2.7F/16, 1F/16, 4.1F/16);
                // Lead away from the upper back before rising, keeping the head and hands clear.
                float rearClearance = getParentModel().young ? .95F : .7F;
                Vec3 controlA = transform(chestToEntity, sign*.24F, .08F, rearClearance);
                Vec3 controlB = transform(chestToEntity, sign*.35F, -.85F, rearClearance);
                Vec3 target = carrier.harnessAnchor(phantom, partialTick, rider instanceof AbstractSkeleton, end)
                    .subtract(rider.getPosition(partialTick));
                drawRope(ropePose, vertices, start, controlA, controlB, target, light, age+end*9);
            }
        }
        pose.popPose();
    }

    private static Vec3 transform(Matrix4f matrix, float x, float y, float z) {
        Vector3f v = matrix.transformPosition(new Vector3f(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    private static void drawRope(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 a, Vec3 b,
                                 Vec3 end, int light, float age) {
        double distanceSquared = start.distanceToSqr(end);
        if (distanceSquared > 64 || distanceSquared < 1.0E-6) return;
        Vec3 previous = start;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = (double)i/SEGMENTS, u = 1-t;
            Vec3 next = start.scale(u*u*u).add(a.scale(3*u*u*t)).add(b.scale(3*u*t*t)).add(end.scale(t*t*t));
            // Restrained wind bow; both attachment endpoints remain exact.
            next = next.add(Math.sin(age*.08+t*3)*Math.sin(Math.PI*t)*.012, 0, 0);
            segment(pose, out, previous, next, light, i);
            previous = next;
        }
    }

    private static void segment(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, int light, int index) {
        Vec3 axis = b.subtract(a).normalize();
        if (axis.lengthSqr() < .5) return;
        Vec3 reference = Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(reference).normalize().scale(.012);
        Vec3 v = axis.cross(u).normalize().scale(.012);
        Vec3[] corners = {u.add(v), u.subtract(v), u.add(v).scale(-1), v.subtract(u)};
        int shade = index%2 == 0 ? 190 : 172;
        for (int side = 0; side < 4; side++) {
            Vec3 c = corners[side], d = corners[(side+1)%4];
            Vec3 normal = c.add(d).normalize();
            vertex(pose, out, a.add(c), 0, 0, normal, light, shade);
            vertex(pose, out, a.add(d), 1, 0, normal, light, shade);
            vertex(pose, out, b.add(d), 1, 1, normal, light, shade);
            vertex(pose, out, b.add(c), 0, 1, normal, light, shade);
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer out, Vec3 p, float u, float v, Vec3 n, int light, int shade) {
        out.addVertex(pose.last(), (float)p.x, (float)p.y, (float)p.z)
            .setColor(shade, shade, shade, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose.last(), (float)n.x, (float)n.y, (float)n.z);
    }
}
