package dev.skylasliver.creeperphantom;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreeperPhantomMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThunderFlightTests {
    private static FakePlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "storm-test"));
    }

    private static ItemStack enchantedWings(GameTestHelper h) {
        return ThunderSmithingRecipe.enchantResult(new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()), h.getLevel().registryAccess());
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void craftingToggleBlocksPickupIncludingStaleOutput(GameTestHelper h) {
        boolean old = ThunderConfig.CRAFTING_ENABLED.get();
        try {
            ThunderConfig.CRAFTING_ENABLED.set(true);
            var p = player(h);
            var menu = new SmithingMenu(1, p.getInventory());
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.getSlot(1).set(new ItemStack(Items.ELYTRA));
            menu.getSlot(2).set(new ItemStack(CreeperPhantomMod.THUNDER_MEMBRANE.get(), 2));
            h.assertTrue(menu.getSlot(3).hasItem(), "Enabled recipe creates output");
            ThunderConfig.CRAFTING_ENABLED.set(false);
            h.assertTrue(!menu.getSlot(3).mayPickup(p), "Disabling must reject stale result");
            menu.clicked(3, 0, ClickType.PICKUP, p);
            menu.clicked(3, 0, ClickType.QUICK_MOVE, p);
            h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(2).getItem().getCount()==2
                && menu.getSlot(0).hasItem() && menu.getSlot(1).hasItem(), "Disabled crafting consumes nothing");
            menu.createResult();
            h.assertTrue(!menu.getSlot(3).hasItem(), "Recalculation hides disabled output");
            ThunderConfig.CRAFTING_ENABLED.set(true);
            menu.createResult();
            h.assertTrue(menu.getSlot(3).hasItem(), "Re-enabling restores recipe");
        } finally { ThunderConfig.CRAFTING_ENABLED.set(old); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void directLightningIsHarmlessAndRepairsBrokenWings(GameTestHelper h) {
        boolean old = ThunderConfig.LIGHTNING_REPAIR.get();
        try {
            ThunderConfig.LIGHTNING_REPAIR.set(true);
            var p = new Zombie(h.getLevel());
            var stack = enchantedWings(h);
            stack.setDamageValue(stack.getMaxDamage()-1);
            p.setItemSlot(EquipmentSlot.CHEST, stack);
            float health = p.getHealth();
            p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(p.getHealth()==health && !p.isOnFire(), "Actual thunderHit must not hurt or ignite wearer");
            h.assertTrue(stack.getDamageValue()==0, "Lightning repairs even broken wings to full");
            h.assertTrue(p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Lightning grants synchronized charge");
            int ticks = ThunderConfig.BOOST_SECONDS.get()*20;
            h.assertTrue(p.getEffect(CreeperPhantomMod.THUNDER_CHARGE).getDuration()==ticks, "Configured effect duration");
            p.addEffect(new MobEffectInstance(CreeperPhantomMod.THUNDER_CHARGE, ticks+50, 2));
            p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(p.getEffect(CreeperPhantomMod.THUNDER_CHARGE).getAmplifier()==0
                && p.getEffect(CreeperPhantomMod.THUNDER_CHARGE).getDuration()==ticks, "Repeated lightning resets duration and never stacks amplifier");
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            new ThunderElytraEvents().tick(new EntityTickEvent.Post(p));
            h.assertTrue(!p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Removing wings clears effect");
        } finally { ThunderConfig.LIGHTNING_REPAIR.set(old); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void disabledRepairStillProtectsAndCharges(GameTestHelper h) {
        boolean old = ThunderConfig.LIGHTNING_REPAIR.get();
        try {
            ThunderConfig.LIGHTNING_REPAIR.set(false);
            // NeoForge FakePlayer is intrinsically invulnerable; use a real damageable entity.
            var p = new Zombie(h.getLevel());
            var stack = enchantedWings(h);
            stack.setDamageValue(101);
            p.setItemSlot(EquipmentSlot.CHEST, stack);
            float health = p.getHealth();
            p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(stack.getDamageValue()==101, "Disabled repair preserves damage");
            h.assertTrue(p.getHealth()==health && p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE),
                "Repair toggle does not disable protection or boost");
            p.hurt(h.getLevel().damageSources().lightningBolt(), 5);
            h.assertTrue(p.getHealth()==health, "Lightning damage source also protected");
            p.hurt(h.getLevel().damageSources().generic(), 2);
            h.assertTrue(p.getHealth()<health, "Unrelated damage remains enabled");
        } finally { ThunderConfig.LIGHTNING_REPAIR.set(old); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void carryingWingsDoesNotProtect(GameTestHelper h) {
        var p = new Zombie(h.getLevel());
        p.setItemSlot(EquipmentSlot.MAINHAND, enchantedWings(h));
        float health = p.getHealth();
        p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
        h.assertTrue(p.getHealth()<health && !p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE),
            "Holding wings must not grant lightning immunity or charge");
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        h.assertTrue(ThunderElytraEvents.flightMovement(p, new Vec3(0,0,1)).z==1,
            "Vanilla elytra keeps vanilla speed");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void attractionNeedsStormOpenSkyAndEquippedWings(GameTestHelper h) {
        var level = h.getLevel();
        float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        var p = player(h);
        p.setPos(h.absoluteVec(new Vec3(1, 40, 1)));
        p.setItemSlot(EquipmentSlot.CHEST, enchantedWings(h));
        var events = new ThunderElytraEvents();
        int interval = ThunderConfig.ATTRACTION_SECONDS.get()*20;
        p.tickCount = Math.floorMod(-p.getId(), interval);
        var box = p.getBoundingBox().inflate(2);
        try {
            level.setRainLevel(0);
            level.setThunderLevel(0);
            events.tick(new EntityTickEvent.Post(p));
            h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "No attraction in clear weather");
            level.setRainLevel(1);
            level.setThunderLevel(0);
            events.tick(new EntityTickEvent.Post(p));
            h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "Rain alone is insufficient");
            level.setThunderLevel(1);
            h.assertTrue(level.isRainingAt(p.blockPosition()), "Test fixture must be rain-exposed");
            var roof = p.blockPosition().above(3);
            var previous = level.getBlockState(roof);
            level.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            try {
                events.tick(new EntityTickEvent.Post(p));
                h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "Roof blocks attraction");
            } finally { level.setBlockAndUpdate(roof, previous); }
            p.tickCount++;
            events.tick(new EntityTickEvent.Post(p));
            h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "No strike between intervals");
            p.tickCount--;
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            events.tick(new EntityTickEvent.Post(p));
            h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "No attraction without wearing wings");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()));
            events.tick(new EntityTickEvent.Post(p));
            h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(), "Bare wings do not attract lightning");
            var stack = enchantedWings(h);
            stack.setDamageValue(80);
            p.setItemSlot(EquipmentSlot.CHEST, stack);
            boolean continuance = ThunderConfig.CONTINUANCE_ENABLED.get();
            try {
                ThunderConfig.CONTINUANCE_ENABLED.set(false);
                events.tick(new EntityTickEvent.Post(p));
                h.assertTrue(level.getEntitiesOfClass(LightningBolt.class, box).isEmpty(),
                    "Disabled Continuance prevents attraction even with the enchantment and correct weather/interval");
            } finally { ThunderConfig.CONTINUANCE_ENABLED.set(continuance); }
            events.tick(new EntityTickEvent.Post(p));
            var bolts = level.getEntitiesOfClass(LightningBolt.class, box);
            h.assertTrue(bolts.size()==1 && bolts.getFirst().getPersistentData()
                .getBoolean(ThunderElytraEvents.ATTRACTED_BOLT), "Storm creates one marked bolt at wearer");
            h.assertTrue(stack.getDamageValue()==0 && p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE),
                "Attracted strike immediately repairs and boosts even a fast-moving player");
        } finally {
            level.getEntitiesOfClass(LightningBolt.class, box).forEach(Entity::discard);
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
        h.succeed();
    }

    /** Exercise the transformed travel method, not just the multiplier helper. */
    private static final class Glider extends Zombie {
        Glider(net.minecraft.world.level.Level level) { super(EntityType.ZOMBIE, level); }
        @Override public boolean isFallFlying() { return true; }
        @Override public boolean isControlledByLocalInstance() { return true; }
    }
    private static Vec3 travel(GameTestHelper h, Item item, boolean charged, int ticks) {
        var mob = new Glider(h.getLevel());
        Vec3 origin = h.absoluteVec(new Vec3(1, 100, 1));
        mob.setPos(origin);
        mob.setOnGround(false);
        mob.setNoGravity(true);
        mob.setYRot(0);
        mob.setXRot(0);
        mob.setItemSlot(EquipmentSlot.CHEST, item == CreeperPhantomMod.THUNDER_ELYTRA.get() ? enchantedWings(h) : new ItemStack(item));
        if (charged) mob.addEffect(new MobEffectInstance(CreeperPhantomMod.THUNDER_CHARGE, 200));
        mob.setDeltaMovement(0, 0, 0.4);
        for (int i=0; i<ticks; i++) mob.travel(Vec3.ZERO);
        return mob.position().subtract(origin);
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void travelMatchesVanillaRatiosWithoutRunawayAcceleration(GameTestHelper h) {
        double speed = ThunderConfig.FLIGHT_SPEED.get(), boost = ThunderConfig.LIGHTNING_BOOST.get();
        try {
            for (int ticks : new int[]{1, 60}) {
                double vanilla = travel(h, Items.ELYTRA, false, ticks).z;
                h.assertTrue(vanilla>0, "Fixture must really move");
                ThunderConfig.FLIGHT_SPEED.set(1.2);
                ThunderConfig.LIGHTNING_BOOST.set(50.0);
                h.assertTrue(Math.abs(travel(h, CreeperPhantomMod.THUNDER_ELYTRA.get(), false, ticks).z/vanilla-1.2)<0.00001,
                    "Base displacement is exactly 1.2x for "+ticks+" ticks");
                h.assertTrue(Math.abs(travel(h, CreeperPhantomMod.THUNDER_ELYTRA.get(), true, ticks).z/vanilla-1.8)<0.00001,
                    "Default charge is exactly 1.8x, never exponentiated");
                ThunderConfig.FLIGHT_SPEED.set(4.0);
                ThunderConfig.LIGHTNING_BOOST.set(200.0);
                double maximumRatio = travel(h, CreeperPhantomMod.THUNDER_ELYTRA.get(), true, ticks).z / vanilla;
                h.assertTrue(Math.abs(maximumRatio - 12.0) < 0.00001,
                    "Maximum boost means 4x times 3x; ticks=" + ticks + " ratio=" + maximumRatio
                        + " speed=" + ThunderConfig.FLIGHT_SPEED.get() + " boost=" + ThunderConfig.LIGHTNING_BOOST.get());
            }
            var p = player(h);
            p.setItemSlot(EquipmentSlot.CHEST, enchantedWings(h));
            h.assertTrue(ThunderElytraEvents.flightMovement(p, new Vec3(1,0,0)).x==1, "Walking is never accelerated");
        } finally {
            ThunderConfig.FLIGHT_SPEED.set(speed);
            ThunderConfig.LIGHTNING_BOOST.set(boost);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void continuanceToggleStopsEffectsAndCanBeReenabled(GameTestHelper h) {
        boolean old = ThunderConfig.CONTINUANCE_ENABLED.get();
        boolean repair = ThunderConfig.LIGHTNING_REPAIR.get();
        try {
            ThunderConfig.CONTINUANCE_ENABLED.set(true);
            ThunderConfig.LIGHTNING_REPAIR.set(true);
            var mob = new Glider(h.getLevel());
            var stack = enchantedWings(h);
            mob.setItemSlot(EquipmentSlot.CHEST, stack);
            mob.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(mob.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Enabled enchantment grants charge");
            ThunderConfig.CONTINUANCE_ENABLED.set(false);
            h.assertTrue(ThunderElytraEvents.flightMultiplier(mob) == ThunderConfig.FLIGHT_SPEED.get(),
                "Disabling immediately removes extra speed, even while an old effect exists");
            new ThunderElytraEvents().tick(new EntityTickEvent.Post(mob));
            h.assertTrue(!mob.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Next tick clears existing charge");
            stack.setDamageValue(80);
            float health = mob.getHealth();
            mob.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(!mob.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Disabled enchantment cannot charge again");
            h.assertTrue(mob.getHealth() == health && stack.getDamageValue() == 0,
                "Base lightning protection and repair remain independent");
            ThunderConfig.CONTINUANCE_ENABLED.set(true);
            h.assertTrue(ThunderElytraEvents.hasThunderContinuance(mob), "Toggle must preserve the item enchantment");
            mob.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(mob.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Re-enabling allows the next strike to charge");
        } finally {
            ThunderConfig.CONTINUANCE_ENABLED.set(old);
            ThunderConfig.LIGHTNING_REPAIR.set(repair);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void actualLightningBoltProtectsWornEquipment(GameTestHelper h) {
        var mob = h.spawn(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1,2,1));
        mob.setNoAi(true);
        var stack = enchantedWings(h);
        stack.setDamageValue(80);
        mob.setItemSlot(EquipmentSlot.CHEST, stack);
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        bolt.getPersistentData().putBoolean(ThunderElytraEvents.ATTRACTED_BOLT, true);
        bolt.moveTo(mob.position());
        float health = mob.getHealth();
        bolt.tick();
        h.assertTrue(mob.getHealth()==health && !mob.isOnFire() && mob.hasEffect(CreeperPhantomMod.THUNDER_CHARGE),
            "Real bolt collision invokes lightning protection and charge");
        h.assertTrue(stack.getDamageValue()==0, "Actual bolt repairs equipped stack");
        bolt.discard();
        mob.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void continuanceRequiredAndExistingConfigHonored(GameTestHelper h) {
        var p = new Glider(h.getLevel());
        var stack = new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get());
        p.setItemSlot(EquipmentSlot.CHEST, stack);
        float health = p.getHealth();
        p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
        h.assertTrue(p.getHealth()==health && !p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE),
            "Bare thunder elytra retains protection but cannot charge");
        double boost = ThunderConfig.LIGHTNING_BOOST.get();
        int seconds = ThunderConfig.BOOST_SECONDS.get();
        try {
            ThunderConfig.LIGHTNING_BOOST.set(50.0);
            ThunderConfig.BOOST_SECONDS.set(120);
            // Only Continuance is required; recharge remains independent.
            stack.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(ThunderSmithingRecipe.THUNDER_CONTINUANCE), 1);
            p.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            h.assertTrue(p.getEffect(CreeperPhantomMod.THUNDER_CHARGE).getDuration()==2400,
                "Existing 120-second setting must be honored");
            h.assertTrue(Math.abs(ThunderElytraEvents.flightMultiplier(p)
                - ThunderConfig.FLIGHT_SPEED.get()*1.5)<0.00001, "Existing boost percentage applies to Continuance");
            stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            h.assertTrue(ThunderElytraEvents.flightMultiplier(p)==ThunderConfig.FLIGHT_SPEED.get(),
                "Removing enchantment immediately disables boosted displacement");
            new ThunderElytraEvents().tick(new EntityTickEvent.Post(p));
            h.assertTrue(!p.hasEffect(CreeperPhantomMod.THUNDER_CHARGE), "Removing enchantment clears charge");
        } finally {
            ThunderConfig.LIGHTNING_BOOST.set(boost);
            ThunderConfig.BOOST_SECONDS.set(seconds);
        }
        h.succeed();
    }
}
