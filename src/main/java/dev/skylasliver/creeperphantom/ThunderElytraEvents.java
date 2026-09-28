package dev.skylasliver.creeperphantom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ThunderElytraEvents {
    public static final String ATTRACTED_BOLT = CreeperPhantomMod.MOD_ID + ":elytra_lightning";
    private static final String FIRE_GUARD = CreeperPhantomMod.MOD_ID + ":lightning_fire_guard";

    public static boolean wearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(CreeperPhantomMod.THUNDER_ELYTRA.get());
    }

    public static boolean hasThunderContinuance(LivingEntity entity) {
        if (!ThunderConfig.value(ThunderConfig.CONTINUANCE_ENABLED) || !wearing(entity)) return false;
        var enchantment = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ThunderSmithingRecipe.THUNDER_CONTINUANCE);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment,
            entity.getItemBySlot(EquipmentSlot.CHEST)) > 0;
    }

    /** Scale the displacement once, retaining vanilla velocity integration and collision handling. */
    public static Vec3 flightMovement(LivingEntity entity, Vec3 vanillaMovement) {
        return vanillaMovement.scale(flightMultiplier(entity));
    }

    public static double flightMultiplier(LivingEntity entity) {
        if (!entity.isFallFlying() || !wearing(entity)) return 1.0;
        double multiplier = ThunderConfig.value(ThunderConfig.FLIGHT_SPEED);
        if (hasThunderContinuance(entity) && entity.hasEffect(CreeperPhantomMod.THUNDER_CHARGE)) {
            multiplier *= 1.0 + ThunderConfig.value(ThunderConfig.LIGHTNING_BOOST) / 100.0;
        }
        return multiplier;
    }

    /** Called before vanilla thunderHit can ignite or damage a wearer. */
    public static boolean absorbLightning(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !wearing(entity)) return false;
        entity.clearFire();
        entity.getPersistentData().putLong(FIRE_GUARD, level.getGameTime() + 40);
        if (hasThunderContinuance(entity)) {
            // Reset the duration and level even if a stronger effect was applied externally.
            entity.removeEffect(CreeperPhantomMod.THUNDER_CHARGE);
            entity.addEffect(new MobEffectInstance(CreeperPhantomMod.THUNDER_CHARGE,
                ThunderConfig.value(ThunderConfig.BOOST_SECONDS) * 20, 0, false, true, true));
        }
        var chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (ThunderConfig.value(ThunderConfig.LIGHTNING_REPAIR) && chest.isDamaged()) {
            chest.setDamageValue(0);
        }
        return true;
    }

    @SubscribeEvent
    public void damage(LivingIncomingDamageEvent event) {
        var entity = event.getEntity();
        if (!wearing(entity)) return;
        if (event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
            absorbLightning(entity);
            event.setCanceled(true);
        } else if ((event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE))
                && entity.getPersistentData().contains(FIRE_GUARD)
                && entity.level().getGameTime() < entity.getPersistentData().getLong(FIRE_GUARD)) {
            entity.clearFire();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || !(entity.level() instanceof ServerLevel level)) return;
        if (entity.getPersistentData().contains(FIRE_GUARD)
                && level.getGameTime() >= entity.getPersistentData().getLong(FIRE_GUARD)) {
            entity.getPersistentData().remove(FIRE_GUARD);
        }
        if (!hasThunderContinuance(entity)) {
            entity.removeEffect(CreeperPhantomMod.THUNDER_CHARGE);
            return;
        }
        if (!(entity instanceof Player player) || !player.isAlive() || player.isSpectator()) return;
        int interval = ThunderConfig.value(ThunderConfig.ATTRACTION_SECONDS) * 20;
        if (Math.floorMod(player.tickCount + player.getId(), interval) != 0
                || !level.isThundering() || !level.isRainingAt(player.blockPosition())) return;
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.getPersistentData().putBoolean(ATTRACTED_BOLT, true);
        bolt.moveTo(player.position());
        if (level.addFreshEntity(bolt)) {
            // Fast gliders may leave the bolt's hit box before its next tick.
            // Resolve their strike now while respecting other mods' cancellation.
            if (!EventHooks.onEntityStruckByLightning(player, bolt)) player.thunderHit(level, bolt);
        }
    }
}
