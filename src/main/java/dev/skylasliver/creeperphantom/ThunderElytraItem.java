package dev.skylasliver.creeperphantom;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class ThunderElytraItem extends ElytraItem {
    public ThunderElytraItem(Properties properties) { super(properties); }

    @Override public boolean elytraFlightTick(ItemStack stack,LivingEntity entity,int flightTicks) {
        // Vanilla wear occurs first. Recharge repairs exactly one point per 20 active flight ticks.
        boolean flying=super.elytraFlightTick(stack,entity,flightTicks);
        if(!entity.level().isClientSide && (flightTicks+1)%20==0 && stack.isDamaged()) {
            var enchantment=entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ThunderSmithingRecipe.INDUCTIVE_RECHARGE);
            if(EnchantmentHelper.getItemEnchantmentLevel(enchantment,stack)>0)
                stack.setDamageValue(Math.max(0,stack.getDamageValue()-1));
        }
        return flying;
    }
}
