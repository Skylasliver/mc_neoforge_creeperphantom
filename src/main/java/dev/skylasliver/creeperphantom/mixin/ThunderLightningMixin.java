package dev.skylasliver.creeperphantom.mixin;

import dev.skylasliver.creeperphantom.ThunderElytraEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ThunderLightningMixin {
    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
    private void thunder$absorbLightning(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity living && ThunderElytraEvents.absorbLightning(living)) {
            ci.cancel();
        }
    }
}
