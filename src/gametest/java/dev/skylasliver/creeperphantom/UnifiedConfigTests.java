package dev.skylasliver.creeperphantom;

import java.nio.file.*;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreeperPhantomMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnifiedConfigTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void migrationPreservesBothConfigurations(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-migration-test-");
        Path toml = directory.resolve(ThunderConfig.FILE_NAME);
        Path previous = directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME);
        try {
            String thunder = "# 用户原有说明\n[thunderElytra]\ncraftingEnabled = false\nflightSpeedMultiplier = 2.75\n";
            Files.writeString(previous, thunder);
            var original = new PhantomConfig.Settings();
            original.normalBurnsInDaylight = false;
            original.chargedBurnsInDaylight = false;
            original.maxHealth = 73;
            original.babyZombie.weaponChance = 23.5;
            original.skeleton.powerLevels.clear();
            original.skeleton.weaponEnchantments.add(new PhantomConfig.Enchant("minecraft:power", 8, 22.5));
            String legacy = "// 旧版带注释的 JSON\n" + PhantomConfig.GSON.toJson(original);
            Files.writeString(toml, legacy);
            UnifiedConfig.migrate(toml, previous);
            String merged = Files.readString(toml);
            var loaded = UnifiedConfig.parse(merged, h.getLevel().registryAccess());
            h.assertTrue(PhantomConfig.GSON.toJsonTree(original).equals(PhantomConfig.GSON.toJsonTree(loaded)),
                "Migration must preserve all nested settings, empty arrays and non-default values");
            h.assertTrue(merged.startsWith(thunder.stripTrailing()), "Thunder values and user comments must remain intact");
            h.assertTrue(merged.contains("[[creeperPhantom.babyZombie.weapons]]"), "Equipment must use TOML array tables");
            h.assertTrue(merged.contains("最大生命值"), "TOML must include Chinese field documentation");
            h.assertTrue(Files.readString(previous).equals(thunder), "Previous TOML must remain intact");
            h.assertTrue(Files.readString(directory.resolve(ThunderConfig.FILE_NAME + ".before-merge.bak")).equals(legacy),
                "Legacy JSON must be backed up before conversion");
            Files.writeString(previous, "[creeperPhantom]\nmaxHealth = 99");
            UnifiedConfig.migrate(toml, previous);
            h.assertTrue(Files.readString(toml).equals(merged), "Repeated migration must never overwrite the unified TOML");
            Files.delete(toml);
            Files.writeString(previous, merged);
            UnifiedConfig.migrate(toml, previous);
            h.assertTrue(Files.readString(toml).equals(merged), "Renamed unified file must preserve every value and comment");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void newInstallGeneratesCompleteToml(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-default-test-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME));
            h.assertTrue(!Files.exists(directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME)),
                "Deleted previous configuration must not be recreated");
            var loaded = UnifiedConfig.parse(Files.readString(file), h.getLevel().registryAccess());
            h.assertTrue(PhantomConfig.GSON.toJsonTree(loaded).equals(
                PhantomConfig.GSON.toJsonTree(new PhantomConfig.Settings())), "New installation must preserve all defaults");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void invalidTomlSettingsAreRejected(GameTestHelper h) {
        String[] invalid = {
            "[creeperPhantom]\nmaxHealth = 0",
            "[creeperPhantom]\nnormalBurnsInDaylight = \"false\"",
            "[creeperPhantom]\nunknownSetting = true",
            "[creeperPhantom]\n[[creeperPhantom.skeleton.powerLevels]]\nlevel = 1.5\nchance = 20",
            "[creeperPhantom]\n[[creeperPhantom.babyZombie.weapons]]\nitem = \"missing:not_an_item\"\nchance = 100",
            "[creeperPhantom]\n[[creeperPhantom.skeleton.powerLevels]]\nlevel = 1\nchance = 60\n[[creeperPhantom.skeleton.powerLevels]]\nlevel = 2\nchance = 60"
        };
        for (String text : invalid) {
            boolean rejected = false;
            try { UnifiedConfig.parse(text, h.getLevel().registryAccess()); }
            catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected, "Invalid TOML must fail validation: " + text);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void runtimeLoadsUnifiedToml(GameTestHelper h) throws Exception {
        h.assertTrue(UnifiedConfig.path().getFileName().toString().equals("creeper-phantom.json"),
            "NeoForge must load the requested filename with TOML contents");
        h.assertTrue(ThunderConfig.SPEC.isLoaded(), "Unified SERVER spec must be loaded");
        var root = new TomlParser().parse(Files.readString(UnifiedConfig.path()));
        h.assertTrue(root.contains("thunderElytra") && root.contains("thunderContinuance") && root.contains("creeperPhantom"),
            "All three sections must exist in the active file");
        h.assertTrue(!root.contains("thunderElytra.lightningBoostPercent"), "Old enchantment keys must be migrated");
        h.assertTrue(root.<Boolean>get("thunderElytra.craftingEnabled") == ThunderConfig.CRAFTING_ENABLED.get()
            && root.<Boolean>get("thunderElytra.lightningRepairEnabled") == ThunderConfig.LIGHTNING_REPAIR.get()
            && root.<Boolean>get("thunderContinuance.enabled") == ThunderConfig.CONTINUANCE_ENABLED.get(),
            "Runtime toggles must match the active file");
        h.assertTrue(root.<Number>get("thunderElytra.flightSpeedMultiplier").doubleValue() == ThunderConfig.FLIGHT_SPEED.get()
            && root.<Number>get("thunderContinuance.lightningBoostPercent").doubleValue() == ThunderConfig.LIGHTNING_BOOST.get()
            && root.<Number>get("thunderContinuance.lightningBoostSeconds").intValue() == ThunderConfig.BOOST_SECONDS.get()
            && root.<Number>get("thunderContinuance.lightningAttractionIntervalSeconds").intValue() == ThunderConfig.ATTRACTION_SECONDS.get(),
            "Runtime speed, boost, duration and attraction interval must match the active file");
        var wearer = new net.minecraft.world.entity.monster.Zombie(h.getLevel());
        wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ThunderSmithingRecipe.enchantResult(
            new net.minecraft.world.item.ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()), h.getLevel().registryAccess()));
        wearer.thunderHit(h.getLevel(), net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(h.getLevel()));
        var charge = wearer.getEffect(CreeperPhantomMod.THUNDER_CHARGE);
        h.assertTrue(ThunderConfig.CONTINUANCE_ENABLED.get()
            ? charge != null && charge.getDuration() == root.<Number>get("thunderContinuance.lightningBoostSeconds").intValue() * 20
            : charge == null, "An actual lightning hit must use the file's toggle and duration without overriding the fixture");
        System.out.println("CONFIG_RUNTIME_VERIFIED path=" + UnifiedConfig.path().toAbsolutePath()
            + " durationSeconds=" + ThunderConfig.BOOST_SECONDS.get() + " effectTicks=" + (charge == null ? 0 : charge.getDuration()));
        var expected = UnifiedConfig.parse(Files.readString(UnifiedConfig.path()), h.getLevel().registryAccess());
        h.assertTrue(PhantomConfig.GSON.toJsonTree(expected).equals(PhantomConfig.GSON.toJsonTree(PhantomConfig.snapshot())),
            "Actual runtime settings must come from the unified TOML");
        h.succeed();
    }


    @GameTest(template="empty", timeoutTicks=40)
    public static void legacyDaylightDefaultsSurviveMigration(GameTestHelper h) throws Exception {
        for (String original : new String[]{
            "/* { user comment } */\n{}",
            "// 苦力怕幻翼配置说明（内联注释 v1）\n{\n\"flightSpeed\":1.25}",
            "{\"normalBurnsInDaylight\":false}",
            "{\"chargedBurnsInDaylight\":false}"
        }) {
            String migrated = migrateLegacy(h, original);
            var loaded = UnifiedConfig.parse(migrated, h.getLevel().registryAccess());
            h.assertTrue(migrated.contains("normalBurnsInDaylight") && migrated.contains("chargedBurnsInDaylight"),
                "Real migration must write both daylight switches");
            h.assertTrue(PhantomConfig.GSON.toJsonTree(PhantomConfig.parse(original, h.getLevel().registryAccess()))
                .equals(PhantomConfig.GSON.toJsonTree(loaded)), "Migration must preserve values and omitted defaults");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void legacyCommentsAndBomAreBackedUp(GameTestHelper h) throws Exception {
        String original = "\uFEFF/* 自定义说明 */\n{\n  // 保留这个数值\n  \"flightSpeed\": 1.25\n}";
        var loaded = UnifiedConfig.parse(migrateLegacy(h, original), h.getLevel().registryAccess());
        h.assertTrue(loaded.flightSpeed == 1.25 && loaded.maxHealth == 40,
            "BOM and comments must not prevent real legacy migration");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void malformedLegacyFileIsNotReplaced(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-invalid-test-");
        Path file = directory.resolve(ThunderConfig.FILE_NAME);
        String original = "{\"skeleton\":{\"powerLevels\":[{\"level\":1.5,\"chance\":100}]}}";
        try {
            Files.writeString(file, original);
            boolean rejected = false;
            try { UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME)); }
            catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected && Files.readString(file).equals(original),
                "Failed migration must leave the original file intact");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void continuanceMigrationPreservesCustomValuesAndNewSectionWins(GameTestHelper h) {
        for (boolean hasNewSection : new boolean[]{false, true}) {
            var config = new TomlParser().parse("""
                [thunderElytra]
                craftingEnabled = false
                flightSpeedMultiplier = 2.75
                lightningRepairEnabled = false
                lightningBoostPercent = 83.5
                lightningBoostSeconds = 37
                lightningAttractionIntervalSeconds = 73
                [creeperPhantom]
                maxHealth = 73
                normalBurnsInDaylight = false
                """ + (hasNewSection ? "\n[thunderContinuance]\nenabled = false\nlightningBoostPercent = 0.0\n" : ""));
            h.assertTrue(!ThunderConfig.LOADER_SPEC.isCorrect(config), "Legacy settings must trigger migration before correction");
            ThunderConfig.LOADER_SPEC.correct(config);
            h.assertTrue(ThunderConfig.LOADER_SPEC.isCorrect(config), "Migrated spec must pass NeoForge validation");
            Number boost = config.get("thunderContinuance.lightningBoostPercent");
            h.assertTrue(boost.doubleValue() == (hasNewSection ? 0.0 : 83.5), "New value wins; otherwise preserve custom old boost");
            h.assertTrue((int) config.get("thunderContinuance.lightningBoostSeconds") == 37
                && (int) config.get("thunderContinuance.lightningAttractionIntervalSeconds") == 73,
                "Migration must preserve duration and interval, including in world override files");
            h.assertTrue((boolean) config.get("thunderContinuance.enabled") == !hasNewSection,
                "New installations default enabled; an explicit false must survive");
            h.assertTrue(!(boolean) config.get("thunderElytra.lightningRepairEnabled")
                && (double) config.get("thunderElytra.flightSpeedMultiplier") == 2.75
                && (int) config.get("creeperPhantom.maxHealth") == 73,
                "Unrelated configuration values must survive");
            h.assertTrue(!config.contains("thunderElytra.lightningBoostPercent")
                && !config.contains("thunderElytra.lightningBoostSeconds")
                && !config.contains("thunderElytra.lightningAttractionIntervalSeconds"), "Remove migrated legacy keys");
            String migrated = new com.electronwill.nightconfig.toml.TomlWriter().writeToString(config);
            ThunderConfig.LOADER_SPEC.correct(config);
            h.assertTrue(new com.electronwill.nightconfig.toml.TomlWriter().writeToString(config).equals(migrated),
                "Repeated correction must be idempotent");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void failedReloadRetainsSettingsOnlyWithinSameServer(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-server-isolation-test-");
        var pathField = UnifiedConfig.class.getDeclaredField("loadedPath");
        pathField.setAccessible(true);
        Path originalPath = (Path) pathField.get(null);
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            pathField.set(null, file);
            Files.writeString(file, "[creeperPhantom]\nmaxHealth = 73\n");
            h.assertTrue(PhantomConfig.reload(h.getLevel().registryAccess()).success(), "Valid custom settings must load");
            h.assertTrue(PhantomConfig.snapshot().maxHealth == 73, "Custom health must become active");
            Files.writeString(file, "[creeperPhantom]\nmaxHealth = 0\n");
            h.assertTrue(!PhantomConfig.reload(h.getLevel().registryAccess()).success(), "Invalid reload must fail");
            h.assertTrue(PhantomConfig.snapshot().maxHealth == 73, "An in-world failed reload retains its previous valid settings");
            new CommonEvents().start(new net.neoforged.neoforge.event.server.ServerAboutToStartEvent(h.getLevel().getServer()));
            h.assertTrue(PhantomConfig.snapshot().maxHealth == new PhantomConfig.Settings().maxHealth,
                "A different server must not inherit the previous world's settings when its own config is invalid");
        } finally {
            pathField.set(null, originalPath);
            PhantomConfig.reload(h.getLevel().registryAccess());
            cleanup(directory);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void tomlBomDoesNotLoseSettings(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-toml-bom-test-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            String original = "\uFEFF[creeperPhantom]\nmaxHealth = 73\n";
            Files.writeString(file, original);
            UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME));
            h.assertTrue(UnifiedConfig.parse(Files.readString(file), h.getLevel().registryAccess()).maxHealth == 73,
                "A UTF-8 BOM from a Windows editor must not break the TOML configuration");
            h.assertTrue(Files.readString(file).equals(original.substring(1)), "Normalization preserves all text except the BOM");
            h.assertTrue(Files.readString(file.resolveSibling(ThunderConfig.FILE_NAME + ".before-merge.bak")).equals(original),
                "Keep the exact BOM-bearing original as a backup");
            h.assertTrue(new TomlParser().parse(Files.readString(file)).<Number>get("creeperPhantom.maxHealth").intValue() == 73,
                "The loader must be able to parse normalized text directly");
            h.assertTrue(UnifiedConfig.parse(original, h.getLevel().registryAccess()).maxHealth == 73,
                "The manual reload parser accepts a BOM too");
            UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME));
            h.assertTrue(Files.readString(file).equals(original.substring(1)), "Normalization is idempotent");
        } finally { cleanup(directory); }
        h.succeed();
    }

    private static String migrateLegacy(GameTestHelper h, String original) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-legacy-test-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            Path previous = directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME);
            Files.writeString(file, original);
            UnifiedConfig.migrate(file, previous);
            String result = Files.readString(file);
            h.assertTrue(Files.readString(directory.resolve(ThunderConfig.FILE_NAME + ".before-merge.bak")).equals(original),
                "Backup must preserve legacy values, comments and BOM exactly");
            UnifiedConfig.migrate(file, previous);
            h.assertTrue(Files.readString(file).equals(result), "Migration must be idempotent");
            return result;
        } finally { cleanup(directory); }
    }

    private static void cleanup(Path directory) throws Exception {
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) Files.delete(file);
        }
        Files.delete(directory);
    }
}
