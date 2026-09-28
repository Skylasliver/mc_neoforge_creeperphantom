package dev.skylasliver.creeperphantom.mixin;

import dev.skylasliver.creeperphantom.ThunderSmithingRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
    @Shadow private RecipeHolder<SmithingRecipe> selectedRecipe;

    @Inject(method="onTake",at=@At("HEAD"))
    private void thunder$consumeSecondMembrane(Player player,ItemStack result,CallbackInfo ci) {
        if(selectedRecipe!=null && selectedRecipe.value() instanceof ThunderSmithingRecipe) {
            // Do not recalculate the result mid-transaction. Vanilla consumes the remaining one
            // and notifies the container below, for both normal pickup and quick-move.
            ((SmithingMenu)(Object)this).getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().shrink(ThunderSmithingRecipe.MEMBRANE_COST - 1);
        }
    }
}
