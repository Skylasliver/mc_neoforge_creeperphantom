package dev.skylasliver.creeperphantom;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreeperPhantomMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhantomRegressionTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void decimalProbabilityTotals(GameTestHelper h) {
        for (String group : new String[]{"weapons", "armorSets", "powerLevels"}) {
            var settings = new PhantomConfig.Settings();
            var tree = PhantomConfig.GSON.toJsonTree(settings).getAsJsonObject();
            var entries = new com.google.gson.JsonArray();
            for (double chance : new double[]{0.2, 83.9, 15.9}) {
                var entry = new com.google.gson.JsonObject();
                entry.addProperty("chance", chance);
                if (group.equals("powerLevels")) entry.addProperty("level", 1);
                entries.add(entry);
            }
            tree.getAsJsonObject("skeleton").add(group, entries);
            PhantomConfig.parse(tree.toString(), h.getLevel().registryAccess());
            entries.get(2).getAsJsonObject().addProperty("chance", 15.900001);
            boolean rejected = false;
            try { PhantomConfig.parse(tree.toString(), h.getLevel().registryAccess()); }
            catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected, "Actual probability overflow must still fail: " + group);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void savedEntityPreservesState(GameTestHelper h) {
        var original = attackFixture(h);
        var restored = CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        var skeleton = EntityType.SKELETON.create(h.getLevel());
        try {
            original.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
            original.setHealth(13);
            skeleton.startRiding(original, true);
            var saved = new net.minecraft.nbt.CompoundTag();
            original.addAdditionalSaveData(saved);
            saved.putInt("Fuse", 17);
            restored.readAdditionalSaveData(saved);
            var result = new net.minecraft.nbt.CompoundTag();
            restored.addAdditionalSaveData(result);
            h.assertTrue(restored.isCharged() && restored.fuse() == 17 && restored.getHealth() == 13,
                "Reload must preserve charge, fuse and damage without healing");
            for (String key : new String[]{"Settings", "RiderSkeletons", "PassengersCreated", "Initialized"})
                h.assertTrue(saved.get(key).equals(result.get(key)), "Saved state changed: " + key);
        } finally { skeleton.discard(); original.discard(); restored.discard(); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void explosionProtectsPassengers(GameTestHelper h) {
        var phantom = attackFixture(h);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var skeleton = EntityType.SKELETON.create(h.getLevel());
        var victim = EntityType.ZOMBIE.create(h.getLevel());
        try {
            zombie.setBaby(true);
            zombie.startRiding(phantom, true);
            skeleton.startRiding(phantom, true);
            phantom.positionRider(zombie);
            phantom.positionRider(skeleton);
            victim.setPos(phantom.position().add(1, 0, 0));
            h.getLevel().addFreshEntity(zombie);
            h.getLevel().addFreshEntity(skeleton);
            h.getLevel().addFreshEntity(victim);
            float zombieHealth = zombie.getHealth(), skeletonHealth = skeleton.getHealth();
            var zombieMotion = zombie.getDeltaMovement();
            var skeletonMotion = skeleton.getDeltaMovement();
            phantom.detonate();
            h.assertTrue(phantom.isRemoved(), "Detonation must remove its source");
            h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "Explosion must damage nearby non-passengers");
            h.assertTrue(zombie.getHealth() == zombieHealth && skeleton.getHealth() == skeletonHealth,
                "Explosion must not hurt either passenger");
            h.assertTrue(zombie.getDeltaMovement().equals(zombieMotion) && skeleton.getDeltaMovement().equals(skeletonMotion),
                "Explosion must not knock back passengers");
            for (var rider : new net.minecraft.world.entity.Mob[]{zombie, skeleton}) {
                h.assertTrue(!rider.isPassenger() && rider.getPersistentData().getBoolean(CommonEvents.FALL),
                    "Airborne passengers must detach with fall protection");
            }
        } finally { zombie.discard(); skeleton.discard(); victim.discard(); phantom.discard(); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void fallProtectionIsConsumed(GameTestHelper h) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        try {
            zombie.getPersistentData().putBoolean(CommonEvents.FALL, true);
            float health = zombie.getHealth();
            zombie.hurt(h.getLevel().damageSources().fall(), 5);
            h.assertTrue(zombie.getHealth() == health && !zombie.getPersistentData().getBoolean(CommonEvents.FALL),
                "First fall must be cancelled and consume protection");
            zombie.invulnerableTime = 0;
            zombie.hurt(h.getLevel().damageSources().fall(), 5);
            h.assertTrue(zombie.getHealth() < health, "A later fall must inflict damage normally");
            zombie.getPersistentData().putBoolean(CommonEvents.FALL, true);
            zombie.setOnGround(true);
            zombie.fallDistance = 12;
            new CommonEvents().landed(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(zombie));
            h.assertTrue(zombie.fallDistance == 0 && !zombie.getPersistentData().getBoolean(CommonEvents.FALL),
                "A harmless landing must also clear the protection");
        } finally { zombie.discard(); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60)
    public static void daylightBurningVariants(GameTestHelper h) {
        var level = h.getLevel();
        long previousTime = level.getDayTime();
        level.setDayTime(6000);
        level.updateSkyBrightness();
        try {
            for (boolean normal : new boolean[]{false, true}) {
                for (boolean charged : new boolean[]{false, true}) {
                    var phantom = attackFixture(h);
                    phantom.setNoAi(true);
                    phantom.getRandom().setSeed(42);
                    var saved = new net.minecraft.nbt.CompoundTag();
                    phantom.addAdditionalSaveData(saved);
                    var settings = PhantomConfig.parse(saved.getString("Settings"), level.registryAccess());
                    settings.normalBurnsInDaylight = normal;
                    settings.chargedBurnsInDaylight = charged;
                    saved.putString("Settings", PhantomConfig.GSON.toJson(settings));
                    phantom.readAdditionalSaveData(saved);
                    try {
                        h.assertTrue(level.canSeeSky(phantom.blockPosition()), "Fixture must have open sky");
                        for (int i = 0; i < 200; i++) phantom.aiStep();
                        h.assertTrue(phantom.isOnFire() == normal, "Normal daylight switch: " + normal);
                        phantom.clearFire();
                        phantom.thunderHit(level, EntityType.LIGHTNING_BOLT.create(level));
                        for (int i = 0; i < 200; i++) phantom.aiStep();
                        h.assertTrue(phantom.isOnFire() == charged, "Charged daylight switch: " + charged);
                        phantom.clearFire();
                        level.setDayTime(18000);
                        level.updateSkyBrightness();
                        for (int i = 0; i < 200; i++) phantom.aiStep();
                        h.assertTrue(!phantom.isOnFire(), "Night must not ignite the phantom");
                        level.setDayTime(6000);
                        level.updateSkyBrightness();
                    } finally { phantom.discard(); }
                }
            }
        } finally {
            level.setDayTime(previousTime);
            level.updateSkyBrightness();
        }
        h.succeed();
    }


    @GameTest(template="empty", timeoutTicks=60)
    public static void autonomousHighAltitudeAttack(GameTestHelper h) {
        assertAutonomousAttack(h,new net.minecraft.world.phys.Vec3(80,-110,0),false,false);
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60)
    public static void targetDirectlyBelow(GameTestHelper h) {
        assertAutonomousAttack(h,new net.minecraft.world.phys.Vec3(0,-40,0),false,false);
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60)
    public static void mountedAttackTracksMovingPlayer(GameTestHelper h) {
        assertAutonomousAttack(h,new net.minecraft.world.phys.Vec3(24,-25,0),true,true);
        h.succeed();
    }

    private static CreeperPhantomEntity attackFixture(GameTestHelper h) {
        var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        phantom.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,170,2)));
        phantom.initialize();
        var saved=new net.minecraft.nbt.CompoundTag();
        phantom.addAdditionalSaveData(saved);
        saved.putBoolean("PassengersCreated",true);
        var settings=PhantomConfig.parse(saved.getString("Settings"),h.getLevel().registryAccess());
        settings.flightSpeed=0.6;
        settings.normalExplosionBreakBlocks=false;
        saved.putString("Settings",PhantomConfig.GSON.toJson(settings));
        phantom.readAdditionalSaveData(saved);
        return phantom;
    }

    private static net.neoforged.neoforge.common.util.FakePlayer targetPlayer(GameTestHelper h,
            net.minecraft.world.phys.Vec3 position,GameType mode) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"phantom-test"));
        player.setGameMode(mode);
        player.setPos(position);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void assertAutonomousAttack(GameTestHelper h,net.minecraft.world.phys.Vec3 offset,
            boolean mounted,boolean moving) {
        h.setNight();
        var phantom=attackFixture(h);
        var player=targetPlayer(h,phantom.position().add(offset),GameType.SURVIVAL);
        var riders=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        if(mounted) {
            var zombie=EntityType.ZOMBIE.create(h.getLevel()); zombie.setBaby(true);
            var skeleton=EntityType.SKELETON.create(h.getLevel());
            zombie.startRiding(phantom,true); skeleton.startRiding(phantom,true);
            riders.add(zombie); riders.add(skeleton);
        }
        double initialDistance=phantom.distanceToSqr(player);
        System.out.println("PHANTOM_TARGET_FIXTURE offset=" + offset
            + " playerList=" + h.getLevel().players().contains(player)
            + " spatialQuery=" + h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                phantom.getBoundingBox().inflate(96, 128, 96)).contains(player));
        int acquired=-1,primed=-1,detonated=-1;
        try {
            // Exercise real server-side entity AI/physics. Only the tick clock is driven here;
            h.assertTrue(phantom.getControllingPassenger()==null,"Riders must not disable the phantom flight AI");
            // never inject a target, attack phase, velocity or fuse into the entity under test.
            for(int tick=1;tick<=260 && !phantom.isRemoved();tick++) {
                if(moving && tick==20)player.setPos(player.position().add(12,0,9));
                if(moving && tick==25)phantom.hurt(h.getLevel().damageSources().generic(),1);
                phantom.tickCount++;
                phantom.tick();
                if(acquired<0 && phantom.getTarget()==player)acquired=tick;
                if(primed<0 && phantom.fuse()>0)primed=tick;
                if(tick==10)h.assertTrue(phantom.distanceToSqr(player)<initialDistance,
                    "Must approach the automatically acquired player within ten ticks; acquired=" + acquired
                        + " initial=" + initialDistance + " current=" + phantom.distanceToSqr(player)
                        + " target=" + phantom.getTarget() + " motion=" + phantom.getDeltaMovement());
                if(phantom.isRemoved())detonated=tick;
            }
            h.assertTrue(acquired>0 && acquired<=10,"Must automatically acquire survival player, got tick "+acquired);
            h.assertTrue(primed>0,"Must reach player and start fuse instead of endlessly circling");
            h.assertTrue(detonated>0 && phantom.fuse()>=30,"Must complete contact fuse and detonate");
            System.out.println("PHANTOM_ATTACK_PASS offset="+offset+" mounted="+mounted+
                " moving="+moving+" acquired="+acquired+" primed="+primed+" detonated="+detonated);
        } finally {
            riders.forEach(net.minecraft.world.entity.Entity::discard);
            phantom.discard(); player.discard();
        }
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void creativeAndSpectatorAreNotTargets(GameTestHelper h) {
        var phantom=attackFixture(h);
        var creative=targetPlayer(h,phantom.position().add(5,0,0),GameType.CREATIVE);
        var spectator=targetPlayer(h,phantom.position().add(-5,0,0),GameType.SPECTATOR);
        try {
            for(int tick=0;tick<20;tick++){phantom.tickCount++;phantom.tick();}
            h.assertTrue(phantom.getTarget()==null && phantom.fuse()==0,"Do not attack creative or spectator players");
        } finally {phantom.discard();creative.discard();spectator.discard();}
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void elevatedTargetRetention(GameTestHelper h) {
        var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        phantom.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,110,2)));
        phantom.setNoAi(true);
        phantom.initialize();
        var saved=new net.minecraft.nbt.CompoundTag();
        phantom.addAdditionalSaveData(saved);
        saved.putBoolean("PassengersCreated",true);
        phantom.readAdditionalSaveData(saved);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(phantom.position().add(80,-70,0));
        h.assertTrue(phantom.withinTargetRange(player,96,128),"High-altitude target must be inside acquisition range");
        phantom.setTarget(player);
        phantom.tick();
        h.assertTrue(phantom.getTarget()==player,"Target beyond old 64-block sphere must remain locked");
        player.setPos(phantom.position().add(129,0,0));
        phantom.tick();
        h.assertTrue(phantom.getTarget()==null,"Target beyond retention radius must be released");
        phantom.getPassengers().forEach(e->e.discard());
        phantom.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void hangingPassengers(GameTestHelper h) {
        var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        h.assertTrue(!phantom.shouldRiderSit(),"Suspended passengers must keep a hanging, not seated, pose");
        phantom.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,6,2)));
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        zombie.setBaby(true);
        var skeleton=EntityType.SKELETON.create(h.getLevel());
        zombie.startRiding(phantom,true);
        skeleton.startRiding(phantom,true);
        for(float yaw:new float[]{0,90,180,270}) {
            phantom.setYRot(yaw);
            phantom.positionRider(zombie);
            phantom.positionRider(skeleton);
            h.assertTrue(zombie.getBoundingBox().maxY<phantom.getY()-0.3,"Baby zombie head intersects phantom");
            h.assertTrue(skeleton.getBoundingBox().maxY<phantom.getY()-0.3,"Skeleton head intersects phantom");
            h.assertTrue(!zombie.getBoundingBox().intersects(skeleton.getBoundingBox()),"Passengers overlap");
        }
        zombie.discard(); skeleton.discard(); phantom.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void zombieUsesNetheriteAxe(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var equipment=PhantomConfig.Equipment.zombie();
        equipment.weaponChance=100;
        EquipmentFactory.equip(zombie,equipment,net.minecraft.util.RandomSource.create(42));
        h.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_AXE),"Zombie must receive netherite axe");
        zombie.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void corruptSavedSettings(GameTestHelper h) {
        for(String invalid:new String[]{"null","{\"flightSpeed\":-1}","{\"babyZombie\":null}"}) {
            var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
            var saved=new net.minecraft.nbt.CompoundTag();
            phantom.initialize();
            phantom.addAdditionalSaveData(saved);
            saved.putString("Settings",invalid);
            saved.putInt("Fuse",Integer.MIN_VALUE);
            saved.putBoolean("PassengersCreated",true);
            phantom.readAdditionalSaveData(saved);
            h.assertTrue(phantom.fuse()==0,"Invalid fuse must be clamped");
            var roundTrip=new net.minecraft.nbt.CompoundTag();
            phantom.addAdditionalSaveData(roundTrip);
            PhantomConfig.parse(roundTrip.getString("Settings"),h.getLevel().registryAccess());
            phantom.discard();
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void mountedTargetSync(GameTestHelper h) {
        var phantom=CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        phantom.setNoAi(true);
        phantom.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,80,2)));
        phantom.initialize();
        var saved=new net.minecraft.nbt.CompoundTag();
        phantom.addAdditionalSaveData(saved);
        saved.putBoolean("PassengersCreated",true);
        phantom.readAdditionalSaveData(saved);
        var skeleton=EntityType.SKELETON.create(h.getLevel());
        skeleton.startRiding(phantom,true);
        var oldTarget=h.makeMockPlayer(GameType.SURVIVAL);
        var newTarget=h.makeMockPlayer(GameType.SURVIVAL);
        newTarget.setPos(phantom.position().add(20,0,0));
        skeleton.setTarget(oldTarget);
        phantom.setTarget(newTarget);
        phantom.tick();
        h.assertTrue(skeleton.getTarget()==newTarget,"Rider must follow target changes");
        newTarget.setPos(phantom.position().add(150,0,0));
        phantom.tick();
        h.assertTrue(skeleton.getTarget()==null,"Rider must release invalid target");
        skeleton.discard(); phantom.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void rejectMalformedEquipment(GameTestHelper h) {
        for(String json:new String[]{
            "{\"babyZombie\":{\"weapons\":[{\"chancce\":100}]}}",
            "{\"skeleton\":{\"powerLevels\":[{\"level\":\"5\",\"chance\":100}]}}",
            "{\"babyZombie\":{\"weapons\":[null]}}",
            "{\"skeleton\":{\"powerLevels\":[{\"level\":1.5,\"chance\":100}]}}",
            "{\"skeleton\":{\"powerLevels\":[{\"level\":4294967297,\"chance\":100}]}}",
            "{\"babyZombie\":{\"weaponEnchantments\":[{\"id\":\"minecraft:unbreaking\",\"level\":1.5,\"chance\":100}]}}"}) {
            boolean rejected=false;
            try {PhantomConfig.parse(json,h.getLevel().registryAccess());}
            catch(RuntimeException expected) {rejected=true;}
            h.assertTrue(rejected,"Malformed equipment must be rejected: "+json);
        }
        h.succeed();
    }



    @GameTest(template="empty", timeoutTicks=40)
    public static void partialEquipmentRetainsDefaults(GameTestHelper h) {
        var loaded = PhantomConfig.parse("{\"skeleton\":{\"weaponChance\":75},\"babyZombie\":{\"weaponChance\":25}}",
            h.getLevel().registryAccess());
        h.assertTrue(loaded.skeleton.weaponChance == 75 && loaded.babyZombie.weaponChance == 25,
            "Explicit nested values must override defaults");
        h.assertTrue(PhantomConfig.GSON.toJsonTree(loaded.skeleton.weapons).equals(
            PhantomConfig.GSON.toJsonTree(PhantomConfig.Equipment.skeleton().weapons)),
            "Partial skeleton settings must retain the default bow");
        h.assertTrue(loaded.skeleton.powerLevels.size() == 5
            && loaded.babyZombie.weapons.getFirst().item.equals("minecraft:netherite_axe"),
            "Each equipment section must retain its own omitted defaults");
        var empty = PhantomConfig.parse("{\"skeleton\":{\"weapons\":[],\"powerLevels\":[]}}",
            h.getLevel().registryAccess());
        h.assertTrue(empty.skeleton.weapons.isEmpty() && empty.skeleton.powerLevels.isEmpty(),
            "Explicit empty lists must not be filled with defaults");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void deadPhantomDoesNotInitialize(GameTestHelper h) {
        var phantom = CreeperPhantomMod.CREEPER_PHANTOM.get().create(h.getLevel());
        try {
            phantom.setHealth(0);
            phantom.tick();
            var saved = new net.minecraft.nbt.CompoundTag();
            phantom.addAdditionalSaveData(saved);
            h.assertTrue(!phantom.isAlive() && !saved.getBoolean("Initialized"),
                "A phantom killed before its first tick must not be revived by initialization");
            h.assertTrue(!saved.getBoolean("PassengersCreated") && phantom.getPassengers().isEmpty(),
                "Dead phantoms must not create passengers");
        } finally { phantom.discard(); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void configSnapshotsAreIndependent(GameTestHelper h) {
        String before = PhantomConfig.GSON.toJson(PhantomConfig.snapshot());
        var changed = PhantomConfig.snapshot();
        changed.maxHealth = 1;
        changed.babyZombie.weapons.clear();
        changed.skeleton.armorSets.getFirst().head = "minecraft:air";
        h.assertTrue(before.equals(PhantomConfig.GSON.toJson(PhantomConfig.snapshot())),
            "Mutating entity settings must not change the active configuration");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void targetingPrefersNearestVisiblePlayer(GameTestHelper h) {
        var phantom = attackFixture(h);
        phantom.setNoAi(true);
        phantom.thunderHit(h.getLevel(), EntityType.LIGHTNING_BOLT.create(h.getLevel()));
        var far = targetPlayer(h, phantom.position().add(30, 0, 0), GameType.SURVIVAL);
        var near = targetPlayer(h, phantom.position().add(12, 0, 0), GameType.SURVIVAL);
        var villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setNoAi(true);
        villager.setPos(phantom.position().add(2, 0, 0));
        h.getLevel().addFreshEntity(villager);
        try {
            phantom.tickCount++;
            phantom.tick();
            h.assertTrue(phantom.getTarget() == near, "Nearest visible player must win over a closer villager");
            near.setGameMode(GameType.CREATIVE);
            phantom.tickCount++;
            phantom.tick();
            h.assertTrue(phantom.getTarget() == far, "Must reacquire the remaining survival player");
            far.setGameMode(GameType.SPECTATOR);
            phantom.tickCount++;
            phantom.tick();
            h.assertTrue(phantom.getTarget() == villager, "Charged phantom must fall back to an eligible villager");
        } finally {
            phantom.discard(); far.discard(); near.discard(); villager.discard();
        }
        h.succeed();
    }
}
