package dev.skylasliver.creeperphantom;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreeperPhantomMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThunderEquipmentTests {
    private static FakePlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"thunder-test"));
    }
    private static SmithingMenu menu(GameTestHelper h,FakePlayer player,int count) {
        var menu=new SmithingMenu(1,player.getInventory());
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        var base=new ItemStack(Items.ELYTRA);
        base.setDamageValue(127);
        base.set(DataComponents.CUSTOM_NAME,Component.literal("Storm keepsake"));
        base.set(DataComponents.REPAIR_COST,3);
        base.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);
        menu.getSlot(1).set(base);
        menu.getSlot(2).set(new ItemStack(CreeperPhantomMod.THUNDER_MEMBRANE.get(),count));
        return menu;
    }
    private static int recharge(ItemStack stack,GameTestHelper h) {
        return EnchantmentHelper.getItemEnchantmentLevel(h.getLevel().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ThunderSmithingRecipe.INDUCTIVE_RECHARGE),stack);
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void smithingNeedsTwoAndPreservesComponents(GameTestHelper h) {
        var p=player(h);var menu=menu(h,p,1);
        h.assertTrue(menu.getSlot(3).getItem().isEmpty() && !menu.getSlot(3).mayPickup(p),"One membrane must not allow crafting");
        menu.getSlot(2).set(new ItemStack(CreeperPhantomMod.THUNDER_MEMBRANE.get(),3));
        var output=menu.getSlot(3).getItem();
        h.assertTrue(output.is(CreeperPhantomMod.THUNDER_ELYTRA.get()) && menu.getSlot(3).mayPickup(p),"Two or more membranes must allow crafting");
        h.assertTrue(output.getDamageValue()==127 && output.getHoverName().getString().equals("Storm keepsake")
            && output.getOrDefault(DataComponents.REPAIR_COST,0)==3,"Smithing must preserve input components");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(h.getLevel().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ThunderSmithingRecipe.THUNDER_CONTINUANCE),output)==1,
            "Smithing must add Thunder Continuance I");
        h.assertTrue(recharge(output,h)==1 && EnchantmentHelper.getItemEnchantmentLevel(
            h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),output)==3,
            "Upgrade must merge exclusive and existing enchantments");
        menu.clicked(3,0,ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().is(CreeperPhantomMod.THUNDER_ELYTRA.get()),"Normal click must take output");
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
            && menu.getSlot(2).getItem().getCount()==1,"Normal pickup must consume 1 template, 1 elytra and exactly 2 membranes");
        h.assertTrue(menu.getSlot(3).getItem().isEmpty(),"No stale duplicate output");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void shiftClickAndFullInventoryAreAtomic(GameTestHelper h) {
        var p=player(h);var menu=menu(h,p,2);
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        menu.clicked(3,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(menu.getSlot(2).getItem().getCount()==2 && menu.getSlot(0).hasItem()
            && menu.getSlot(3).hasItem(),"Failed shift-click must consume nothing");
        p.getInventory().setItem(8,ItemStack.EMPTY);
        menu.clicked(3,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(p.getInventory().getItem(8).is(CreeperPhantomMod.THUNDER_ELYTRA.get()),"Shift-click delivers exactly one elytra");
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
            && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),"Shift-click consumes exactly two membranes");
        menu.clicked(3,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(p.getInventory().getItem(8).getCount()==1,"Repeated quick-move must not duplicate");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void vanillaSmithingStillConsumesOne(GameTestHelper h) {
        var p=player(h);var menu=new SmithingMenu(1,p.getInventory());
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT,3));
        menu.clicked(3,0,ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().is(Items.NETHERITE_SWORD) && menu.getSlot(2).getItem().getCount()==2,
            "Vanilla smithing must remain unchanged");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void rechargeOnlyDuringFlightAtOnePerSecond(GameTestHelper h) {
        var p=player(h);
        var enchanted=ThunderSmithingRecipe.enchantResult(new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()),h.getLevel().registryAccess());
        enchanted.setDamageValue(100);
        // Remove vanilla wear in this fixture to measure the repair itself independently.
        p.getAbilities().instabuild=true;
        p.setItemSlot(EquipmentSlot.CHEST,enchanted);
        var item=CreeperPhantomMod.THUNDER_ELYTRA.get();
        for(int tick=0;tick<19;tick++)item.elytraFlightTick(enchanted,p,tick);
        h.assertTrue(enchanted.getDamageValue()==100,"No repair before 20 active flight ticks");
        item.elytraFlightTick(enchanted,p,19);
        h.assertTrue(enchanted.getDamageValue()==99,"Exactly one repair at the 20th tick");
        for(int tick=20;tick<40;tick++)item.elytraFlightTick(enchanted,p,tick);
        h.assertTrue(enchanted.getDamageValue()==98,"Exactly two repairs after 40 ticks");
        for(int tick=0;tick<40;tick++)item.inventoryTick(enchanted,h.getLevel(),p,0,false);
        h.assertTrue(enchanted.getDamageValue()==98,"Standing or carrying must not repair");
        p.getAbilities().instabuild=false;
        item.elytraFlightTick(enchanted,p,59);
        h.assertTrue(enchanted.getDamageValue()==98,"One-point repair offsets normal one-point flight wear");
        var bare=new ItemStack(item);bare.setDamageValue(100);
        item.elytraFlightTick(bare,p,19);
        h.assertTrue(bare.getDamageValue()==101,"Removing the enchantment must remove recharge, but retain normal wear");
        var full=ThunderSmithingRecipe.enchantResult(new ItemStack(item),h.getLevel().registryAccess());
        full.set(DataComponents.UNBREAKABLE,new Unbreakable(false));
        item.elytraFlightTick(full,p,19);
        h.assertTrue(full.getDamageValue()==0,"Repair cannot exceed maximum durability");
        bare.setDamageValue(bare.getMaxDamage()-1);
        h.assertTrue(!item.canElytraFly(bare,p),"Broken elytra cannot start flight");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void exclusiveEnchantmentIsNotRandomlyObtainable(GameTestHelper h) {
        for (var key : java.util.List.of(ThunderSmithingRecipe.INDUCTIVE_RECHARGE, ThunderSmithingRecipe.THUNDER_CONTINUANCE)) {
        var enchantment=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        for(var tag:java.util.List.of(EnchantmentTags.IN_ENCHANTING_TABLE,EnchantmentTags.ON_RANDOM_LOOT,
                EnchantmentTags.TRADEABLE,EnchantmentTags.ON_TRADED_EQUIPMENT,EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT))
            h.assertTrue(!enchantment.is(tag),"Exclusive enchantment must be excluded from acquisition tag "+tag.location());
        h.assertTrue(enchantment.value().getMaxLevel()==1 && enchantment.value().isSupportedItem(new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get())),
            "Exclusive level one enchantment must support thunder elytra");
        }
        var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreeperPhantomMod.MOD_ID,"thunder_elytra"));
        h.assertTrue(recipe.isPresent() && recipe.get().value() instanceof ThunderSmithingRecipe,"Datapack recipe must load with custom serializer");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void grindstoneRemovesBothExclusiveEnchantments(GameTestHelper h) {
        var p=player(h);
        var menu=new GrindstoneMenu(2,p.getInventory());
        menu.getSlot(0).set(ThunderSmithingRecipe.enchantResult(
            new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()),h.getLevel().registryAccess()));
        var result=menu.getSlot(2).getItem();
        h.assertTrue(result.is(CreeperPhantomMod.THUNDER_ELYTRA.get())
            && result.getEnchantments().isEmpty(), "Grindstone removes both exclusive enchantments");
        p.setItemSlot(EquipmentSlot.CHEST,result);
        p.thunderHit(h.getLevel(),EntityType.LIGHTNING_BOLT.create(h.getLevel()));
        h.assertTrue(!p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Disenchanted result cannot charge");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void onlyChargedPhantomsDropMembrane(GameTestHelper h) {
        var level=h.getLevel();
        for(boolean charged:new boolean[]{false,true}){
            var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(level);
            phantom.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2)));
            phantom.setNoAi(true);
            level.addFreshEntity(phantom);
            if(charged)phantom.thunderHit(level,EntityType.LIGHTNING_BOLT.create(level));
            phantom.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
            var drops=level.getEntitiesOfClass(ItemEntity.class,phantom.getBoundingBox().inflate(2),
                i->i.getItem().is(CreeperPhantomMod.THUNDER_MEMBRANE.get()));
            int total=drops.stream().mapToInt(i->i.getItem().getCount()).sum();
            h.assertTrue(total==(charged?1:0),"Only charged phantom death drops exactly one membrane; charged="+charged);
            drops.forEach(ItemEntity::discard);phantom.discard();
        }
        h.succeed();
    }
}
