package dev.skylasliver.creeperphantom;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.*;
import net.minecraft.core.RegistryAccess;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** One SERVER TOML; legacy JSON is only an import source and entity-save format. */
final class UnifiedConfig {
    private static volatile Path loadedPath;
    private static JsonElement initialSection;
    private UnifiedConfig() {}

    static Path globalPath() { return FMLPaths.CONFIGDIR.get().resolve(ThunderConfig.FILE_NAME); }
    static Path path() { return loadedPath == null ? globalPath() : loadedPath; }

    static void prepare() {
        try {
            Path file = globalPath();
            migrate(file, file.resolveSibling(ThunderConfig.PREVIOUS_FILE_NAME));
            initialSection = toJson(new TomlParser().parse(Files.readString(file)).get("creeperPhantom"));
        } catch (Exception e) {
            // Never let the loader overwrite an unimported, unreadable legacy configuration.
            throw new IllegalStateException("无法合并配置；原文件已保留，请检查 " + globalPath(), e);
        }
    }

    static CommentedConfig defaultSection() {
        JsonElement defaults = initialSection == null
            ? PhantomConfig.GSON.toJsonTree(new PhantomConfig.Settings()) : initialSection;
        return (CommentedConfig) toToml(defaults);
    }

    /** The target wins; old files are read only when importing or converting legacy JSON. */
    static void migrate(Path file, Path previous) throws IOException {
        boolean targetExists = Files.exists(file);
        String original = targetExists ? Files.readString(file) : "";
        String existing = stripBom(original);
        PhantomConfig.Settings legacySettings = null;
        if (targetExists) {
            try {
                var target = new TomlParser().parse(existing);
                if (target.contains("creeperPhantom")) {
                    // NeoForge's TOML parser also rejects a UTF-8 BOM. Normalize before it loads.
                    if (!existing.equals(original)) replaceWithBackup(file, existing);
                    return;
                }
            } catch (com.electronwill.nightconfig.core.io.ParsingException e) {
                // Old JSON and the new TOML share a filename, so detect the content.
                legacySettings = PhantomConfig.parseStructure(existing);
            }
        }
        if ((!targetExists || legacySettings != null) && Files.exists(previous))
            existing = stripBom(Files.readString(previous));
        else if (legacySettings != null) existing = "";
        var config = new TomlParser().parse(existing);
        String merged = existing;
        if (!config.contains("creeperPhantom")) {
            var settings = legacySettings == null ? new PhantomConfig.Settings() : legacySettings;
            var section = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
            section.set("creeperPhantom", toToml(PhantomConfig.GSON.toJsonTree(settings)));
            section.setComment("creeperPhantom", "苦力怕幻翼与乘客装备；修改后 /creeperphantom reload，仅影响新实体。");
            merged = existing.stripTrailing() + "\n\n" + new TomlWriter().writeToString(section);
        }
        // Parse the full result before replacing the file.
        new TomlParser().parse(merged);
        replaceWithBackup(file, merged);
    }

    static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static void replaceWithBackup(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.exists(file)) {
            Path backup = file.resolveSibling(file.getFileName() + ".before-merge.bak");
            if (!Files.exists(backup)) Files.copy(file, backup);
        }
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "unified-config-", ".tmp");
        try {
            Files.writeString(temporary, text);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static PhantomConfig.Settings parse(String text, RegistryAccess registry) {
        var root = new TomlParser().parse(stripBom(text));
        Object section = root.get("creeperPhantom");
        if (!(section instanceof UnmodifiableConfig))
            throw new IllegalArgumentException("缺少 [creeperPhantom] 配置节");
        return PhantomConfig.parse(toJson(section).toString(), registry);
    }


    static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ThunderConfig.LOADER_SPEC)
            loadedPath = event.getConfig().getFullPath();
    }

    static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ThunderConfig.LOADER_SPEC) return;
        loadedPath = event.getConfig().getFullPath();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> PhantomConfig.reload(server.registryAccess()));
    }

    static void onUnloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == ThunderConfig.LOADER_SPEC) loadedPath = null;
    }

    static Object toToml(JsonElement value) {
        if (value.isJsonObject()) {
            var config = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
            for (var entry : value.getAsJsonObject().entrySet()) {
                config.set(entry.getKey(), toToml(entry.getValue()));
                config.setComment(entry.getKey(), ConfigDocumentation.comment(entry.getKey()));
            }
            return config;
        }
        if (value.isJsonArray()) {
            var list = new ArrayList<Object>();
            value.getAsJsonArray().forEach(element -> list.add(toToml(element)));
            return list;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) return primitive.getAsString();
        // Preserve integer levels and floating-point percentages without stringifying numbers.
        try { return primitive.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { return primitive.getAsDouble(); }
    }

    static JsonElement toJson(Object value) {
        if (value instanceof UnmodifiableConfig config) {
            var object = new JsonObject();
            for (var entry : config.entrySet()) object.add(entry.getKey(), toJson(entry.getValue()));
            return object;
        }
        if (value instanceof Iterable<?> list) {
            var array = new JsonArray();
            for (Object element : list) array.add(toJson(element));
            return array;
        }
        return PhantomConfig.GSON.toJsonTree(value);
    }
}
