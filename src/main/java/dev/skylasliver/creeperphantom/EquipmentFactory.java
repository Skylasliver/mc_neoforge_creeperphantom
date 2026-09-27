package dev.skylasliver.creeperphantom;

import java.util.*;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.core.component.DataComponents;

public final class EquipmentFactory {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EquipmentFactory() {}

    public static void equip(Mob mob, PhantomConfig.Equipment c, RandomSource random) {
        for(EquipmentSlot slot:EquipmentSlot.values()) {
            mob.setItemSlot(slot,ItemStack.EMPTY);
            mob.setDropChance(slot,0);
        }
        if(random.nextDouble()*100<c.weaponChance) {
            var weapon=pick(c.weapons,o->o.chance,random);
            if(weapon!=null) {
                ItemStack stack=stack(weapon.item);
                apply(mob,stack,weapon.enchantments,random);
                if(stack.is(Items.BOW)) {
                    var power=pick(c.powerLevels,PhantomConfig.Power::chance,random);
                    if(power!=null) applyOne(mob,stack,new PhantomConfig.Enchant("minecraft:power",power.level(),100));
                }
                apply(mob,stack,c.weaponEnchantments,random);
                mob.setItemSlot(EquipmentSlot.MAINHAND,stack);
            }
        }
        var armor=pick(c.armorSets,a->a.chance,random);
        if(armor!=null) {
            String[] ids={armor.head,armor.chest,armor.legs,armor.feet};
            for(int i=0;i<ARMOR_SLOTS.length;i++){
                ItemStack stack=stack(ids[i]);apply(mob,stack,armor.enchantments,random);mob.setItemSlot(ARMOR_SLOTS[i],stack);
            }
        }
        mob.setCanPickUpLoot(false);
    }
    static <T> T pick(List<T> options,ToDoubleFunction<T> chance,RandomSource random) {
        double roll=random.nextDouble()*100;
        for(T option:options) {roll-=chance.applyAsDouble(option);if(roll<0)return option;}
        return null;
    }
    private static ItemStack stack(String id) {return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));}
    private static void apply(Mob mob,ItemStack stack,List<PhantomConfig.Enchant> rules,RandomSource random) {
        for(var e:rules)if(random.nextDouble()*100<e.chance())applyOne(mob,stack,e);
    }
    static void applyOne(Mob mob,ItemStack stack,PhantomConfig.Enchant rule) {
        if(stack.isEmpty())return;
        var registry=mob.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var holder=registry.getHolder(ResourceLocation.parse(rule.id()));
        if(holder.isEmpty() || !holder.get().value().canEnchant(stack))return;
        var current=stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(Holder<Enchantment> old:current.keySet())
            if(!old.equals(holder.get()) && !Enchantment.areCompatible(old,holder.get()))return;
        stack.enchant(holder.get(),rule.level());
    }
}
