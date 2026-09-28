package dev.skylasliver.creeperphantom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skylasliver.creeperphantom.CreeperPhantomMod;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at=@At("HEAD"),cancellable=true)
    private void thunder$hideCape(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
            float swing,float amount,float partialTick,float age,float yaw,float pitch,CallbackInfo ci) {
        if(player.getItemBySlot(EquipmentSlot.CHEST).is(CreeperPhantomMod.THUNDER_ELYTRA.get()))ci.cancel();
    }
}
