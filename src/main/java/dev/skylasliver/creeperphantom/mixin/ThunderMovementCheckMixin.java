package dev.skylasliver.creeperphantom.mixin;

import dev.skylasliver.creeperphantom.ThunderElytraEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ThunderMovementCheckMixin {
    @Shadow public ServerPlayer player;

    // Vanilla compares squared displacement. Preserve its check and tolerance
    // for every other player, scaling only the equipped thunder elytra allowance.
    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 300.0F))
    private float thunder$movementAllowance(float vanilla) {
        double multiplier = ThunderElytraEvents.flightMultiplier(player);
        return (float)(vanilla * multiplier * multiplier);
    }
}
