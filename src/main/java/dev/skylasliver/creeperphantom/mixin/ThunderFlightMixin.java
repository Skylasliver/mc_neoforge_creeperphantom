package dev.skylasliver.creeperphantom.mixin;

import dev.skylasliver.creeperphantom.ThunderElytraEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class ThunderFlightMixin {
    // travel's third move() call is the elytra branch (after water and lava).
    // Changing displacement, rather than multiplying stored velocity every tick,
    // keeps the configured ratio stable instead of producing exponential acceleration.
    @ModifyArg(method = "travel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        ordinal = 2), index = 1)
    private Vec3 thunder$flightMovement(Vec3 movement) {
        return ThunderElytraEvents.flightMovement((LivingEntity)(Object)this, movement);
    }
}
