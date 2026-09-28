package dev.skylasliver.creeperphantom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skylasliver.creeperphantom.CreeperPhantomMod;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
    @Shadow @Final private ElytraModel<T> elytraModel;
    @Unique private static final ResourceLocation THUNDER_TEXTURE=ResourceLocation.fromNamespaceAndPath(
        CreeperPhantomMod.MOD_ID,"textures/entity/thunder_elytra.png");

    protected ElytraLayerMixin(RenderLayerParent<T,M> parent){super(parent);}

    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at=@At("HEAD"),cancellable=true)
    private void thunder$render(PoseStack pose,MultiBufferSource buffers,int light,T entity,
            float swing,float amount,float partialTick,float age,float yaw,float pitch,CallbackInfo ci) {
        var stack=entity.getItemBySlot(EquipmentSlot.CHEST);
        if(!stack.is(CreeperPhantomMod.THUNDER_ELYTRA.get()))return;
        pose.pushPose();
        pose.translate(0,0,.125F);
        getParentModel().copyPropertiesTo(elytraModel);
        elytraModel.setupAnim(entity,swing,amount,age,yaw,pitch);
        var vertices=ItemRenderer.getArmorFoilBuffer(buffers,RenderType.armorCutoutNoCull(THUNDER_TEXTURE),stack.hasFoil());
        elytraModel.renderToBuffer(pose,vertices,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        ci.cancel();
    }
}
