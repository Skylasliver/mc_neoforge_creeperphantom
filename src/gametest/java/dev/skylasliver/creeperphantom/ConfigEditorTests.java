package dev.skylasliver.creeperphantom;

import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.*;

@GameTestHolder(CreeperPhantomMod.MOD_ID)
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = CreeperPhantomMod.MOD_ID)
public final class ConfigEditorTests {
    private static volatile long lastReloadNanos;

    @net.neoforged.bus.api.SubscribeEvent
    public static void observeReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ThunderConfig.LOADER_SPEC) lastReloadNanos = System.nanoTime();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void draftSaveRoundTripAndCancel(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-editor-test-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME));
            String original = Files.readString(file);
            var cancelled = ConfigEditorDocument.open(file);
            cancelled.values.getAsJsonObject("creeperPhantom").addProperty("maxHealth", 82);
            h.assertTrue(Files.readString(file).equals(original), "Opening and editing a detached draft must not write to disk");
            var draft = ConfigEditorDocument.open(file);
            var phantom = draft.values.getAsJsonObject("creeperPhantom");
            phantom.addProperty("maxHealth", 67);
            phantom.addProperty("normalBurnsInDaylight", true);
            var enchants = phantom.getAsJsonObject("skeleton").getAsJsonArray("weaponEnchantments");
            enchants.add(ConfigEditorDocument.newListEntry("weaponEnchantments"));
            enchants.get(enchants.size() - 1).getAsJsonObject().addProperty("chance", 42.5);
            phantom.getAsJsonObject("babyZombie").getAsJsonArray("weapons").remove(0);
            draft.values.getAsJsonObject("thunderElytra").addProperty("flightSpeedMultiplier", 2);
            draft.values.getAsJsonObject("thunderContinuance").addProperty("lightningBoostSeconds", 41);
            var normalized = draft.validated(h.getLevel().registryAccess());
            h.assertTrue(normalized.get("thunderElytra.flightSpeedMultiplier") instanceof Double,
                "Integer-looking DoubleValue input must be normalized before applying to live config");
            draft.saveToDisk(h.getLevel().registryAccess(), false);
            h.assertTrue(Files.readString(file.resolveSibling(file.getFileName() + ".before-gui.bak")).equals(original),
                "A successful GUI save must back up the previous file");
            var loaded = UnifiedConfig.parse(Files.readString(file), h.getLevel().registryAccess());
            h.assertTrue(loaded.maxHealth == 67 && loaded.normalBurnsInDaylight && loaded.babyZombie.weapons.isEmpty(),
                "Numbers, toggles and deleting the last array element must round-trip");
            h.assertTrue(loaded.skeleton.weaponEnchantments.getLast().chance() == 42.5,
                "Nested list additions must round-trip");
            var reopened = ConfigEditorDocument.open(file);
            h.assertTrue(reopened.values.equals(draft.values), "Reopening the saved file must retain every editor value");
            h.assertTrue(!reopened.changed(), "A fresh draft must not be dirty");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void invalidDraftAndExternalChangesNeverOverwrite(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-editor-invalid-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            UnifiedConfig.migrate(file, directory.resolve(ThunderConfig.PREVIOUS_FILE_NAME));
            String original = Files.readString(file);
            for (int scenario = 0; scenario < 6; scenario++) {
                var draft = ConfigEditorDocument.open(file);
                var phantom = draft.values.getAsJsonObject("creeperPhantom");
                switch (scenario) {
                    case 0 -> phantom.addProperty("maxHealth", 0);
                    case 1 -> draft.values.getAsJsonObject("thunderElytra").addProperty("flightSpeedMultiplier", 5);
                    case 2 -> draft.values.getAsJsonObject("thunderContinuance").addProperty("lightningBoostSeconds", 1.5);
                    case 3 -> phantom.getAsJsonObject("skeleton").getAsJsonArray("powerLevels").get(0).getAsJsonObject().addProperty("chance", 100);
                    case 4 -> phantom.getAsJsonObject("babyZombie").getAsJsonArray("weapons").get(0).getAsJsonObject().addProperty("item", "missing:no_item");
                    case 5 -> phantom.getAsJsonObject("skeleton").getAsJsonArray("weaponEnchantments").get(0).getAsJsonObject().addProperty("id", "missing:no_enchantment");
                }
                boolean rejected = false;
                try { draft.saveToDisk(h.getLevel().registryAccess(), false); }
                catch (Exception expected) { rejected = true; }
                h.assertTrue(rejected && Files.readString(file).equals(original), "Invalid draft must not change file: " + scenario);
            }
            var stale = ConfigEditorDocument.open(file);
            Files.writeString(file, original + "\n# External edit\n");
            boolean rejected = false;
            try { stale.saveToDisk(h.getLevel().registryAccess(), false); }
            catch (java.io.IOException expected) { rejected = true; }
            h.assertTrue(rejected && Files.readString(file).endsWith("# External edit\n"),
                "Stale drafts must not overwrite external edits");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void editorAcceptsBomWithoutWritingOnOpen(GameTestHelper h) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "config-editor-bom-");
        try {
            Path file = directory.resolve(ThunderConfig.FILE_NAME);
            String original = "\uFEFF[creeperPhantom]\nmaxHealth = 73\n";
            Files.writeString(file, original);
            var draft = ConfigEditorDocument.open(file);
            h.assertTrue(draft.values.getAsJsonObject("creeperPhantom").get("maxHealth").getAsInt() == 73,
                "The editor must accept the same BOM-bearing TOML as manual reload");
            h.assertTrue(Files.readString(file).equals(original), "Opening a BOM file must not rewrite it");
            draft.values.getAsJsonObject("thunderContinuance").addProperty("lightningBoostSeconds", 120);
            draft.saveToDisk(h.getLevel().registryAccess(), false);
            h.assertTrue(Files.readString(file.resolveSibling(file.getFileName() + ".before-gui.bak")).equals(original),
                "GUI backup must retain the exact original BOM-bearing file");
            h.assertTrue(UnifiedConfig.parse(Files.readString(file), h.getLevel().registryAccess()).maxHealth == 73,
                "Saving must retain custom settings and produce loader-readable TOML");
            h.succeed();
        } finally { cleanup(directory); }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void documentedDefaultsMatchCompiledDefaults(GameTestHelper h) throws Exception {
        try (var resource = ConfigEditorTests.class.getResourceAsStream("/" + ThunderConfig.FILE_NAME)) {
            h.assertTrue(resource != null, "The default template must be available as a test-only resource");
            String text = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var documented = UnifiedConfig.toJson(new TomlParser().parse(text)).getAsJsonObject();
            h.assertTrue(documented.equals(ConfigEditorDocument.defaults()),
                "Every documented default, including nested equipment, must match reset-to-default values");
            UnifiedConfig.parse(text, h.getLevel().registryAccess());
            h.assertTrue(documented.getAsJsonObject("thunderContinuance").get("lightningBoostSeconds").getAsInt() == 120
                && documented.getAsJsonObject("thunderContinuance").get("lightningAttractionIntervalSeconds").getAsInt() == 120,
                "Both documented durations must remain 120 seconds");
            System.out.println("CONFIG_DEFAULTS_VERIFIED template matches compiled defaults, duration=120 interval=120");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void listDeletionPreservesOtherInvalidDrafts(GameTestHelper h) {
        var entries = new com.google.gson.JsonArray();
        for (int i = 0; i < 12; i++) entries.add(ConfigEditorDocument.newListEntry("armorSets"));
        String path = "/creeperPhantom/skeleton/armorSets";
        var invalid = new java.util.LinkedHashMap<String, String>();
        invalid.put(path + "/0/chance", "-");
        invalid.put(path + "/1/chance", "1e");
        invalid.put(path + "/1/enchantments/0/level", "");
        invalid.put(path + "/2/enchantments/0/level", "2e");
        invalid.put(path + "/10/chance", ".");
        invalid.put("/creeperPhantom/maxHealth", "+");
        ConfigEditorDocument.removeListEntry(entries, path, 1, invalid);
        h.assertTrue(entries.size() == 11 && invalid.size() == 4, "Remove only the selected entry and its invalid fields");
        h.assertTrue("-".equals(invalid.get(path + "/0/chance")), "Earlier entry input must survive");
        h.assertTrue("2e".equals(invalid.get(path + "/1/enchantments/0/level")), "Nested errors follow the shifted entry");
        h.assertTrue(".".equals(invalid.get(path + "/9/chance")), "Multi-digit indices must shift correctly");
        h.assertTrue("+".equals(invalid.get("/creeperPhantom/maxHealth")), "Other groups remain untouched");
        h.succeed();
    }

    // Own batch: this test briefly changes the live config and must not overlap gameplay tests.
    @GameTest(template = "empty", batch = "config_editor_live", timeoutTicks = 1000)
    public static void liveSaveReloadsBothSections(GameTestHelper h) throws Exception {
        Path file = UnifiedConfig.path();
        String original = Files.readString(file);
        Path backup = file.resolveSibling(file.getFileName() + ".before-gui.bak");
        byte[] previousBackup = Files.exists(backup) ? Files.readAllBytes(backup) : null;
        var restore = ConfigEditorDocument.open();
        try {
            var draft = ConfigEditorDocument.open();
            draft.values.getAsJsonObject("thunderElytra").addProperty("flightSpeedMultiplier", 2);
            draft.values.getAsJsonObject("thunderContinuance").addProperty("lightningBoostSeconds", 43);
            draft.values.getAsJsonObject("creeperPhantom").addProperty("maxHealth", 79);
            draft.save(h.getLevel().registryAccess(), true);
            h.assertTrue(ThunderConfig.FLIGHT_SPEED.get() == 2.0 && ThunderConfig.BOOST_SECONDS.get() == 43,
                "Live GUI save must update typed config values");
            h.assertTrue(PhantomConfig.snapshot().maxHealth == 79, "Loader save event must reload phantom settings too");
            h.assertTrue(new TomlParser().parse(Files.readString(file)).<Number>get("creeperPhantom.maxHealth").intValue() == 79,
                "Live GUI save must persist the same settings");
            System.out.println("CONFIG_EDITOR_LIVE_SAVE_VERIFIED health=79 speed=2.0 duration=43");
        } finally {
            var rollback = ConfigEditorDocument.open();
            rollback.values.entrySet().clear();
            restore.values.entrySet().forEach(e -> rollback.values.add(e.getKey(), e.getValue()));
            rollback.save(h.getLevel().registryAccess(), true);
            Files.writeString(file, original);
            if (previousBackup == null) Files.deleteIfExists(backup);
            else Files.write(backup, previousBackup);
        }
        // GameTest ticks can run faster than wall time. Observe actual loader reloads
        // and wait for a quiet period before gameplay tests temporarily set live values.
        long restoredAt = System.nanoTime();
        h.succeedWhen(() -> {
            // Bound wall-time waiting without spinning thousands of accelerated server ticks.
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
            h.assertTrue(System.nanoTime() - Math.max(restoredAt, lastReloadNanos) >= 2_000_000_000L,
                "Waiting for the configuration file watcher to settle");
            h.assertTrue(ThunderConfig.FLIGHT_SPEED.get() == restore.values.getAsJsonObject("thunderElytra")
                .get("flightSpeedMultiplier").getAsDouble(), "Restore flight speed before the next batch");
            h.assertTrue(ThunderConfig.BOOST_SECONDS.get() == restore.values.getAsJsonObject("thunderContinuance")
                .get("lightningBoostSeconds").getAsInt(), "Restore effect duration before the next batch");
        });
    }

    private static void cleanup(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
