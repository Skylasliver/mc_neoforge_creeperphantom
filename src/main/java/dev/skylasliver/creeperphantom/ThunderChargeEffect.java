package dev.skylasliver.creeperphantom;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Duration and visibility are synchronized by vanilla's mob-effect packets. */
public final class ThunderChargeEffect extends MobEffect {
    public ThunderChargeEffect() { super(MobEffectCategory.BENEFICIAL, 0x55CCFF); }
}
