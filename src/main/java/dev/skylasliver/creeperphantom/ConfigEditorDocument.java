package dev.skylasliver.creeperphantom;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Detached editor draft: no config values or files change until a validated save. */
public final class ConfigEditorDocument {
    private final Path path;
    private final String original;
    private final CommentedConfig source;
    public final JsonObject values;
    private final JsonObject initial;

    private ConfigEditorDocument(Path path, String original, CommentedConfig source) {
        this.path = path;
        this.original = original;
        this.source = source;
        values = UnifiedConfig.toJson(source).getAsJsonObject();
        // Expose omitted phantom settings too, without writing anything on opening/cancel.
        values.add("creeperPhantom", PhantomConfig.GSON.toJsonTree(
            PhantomConfig.parseStructure(values.get("creeperPhantom").toString())));
        initial = values.deepCopy();
    }

    public static ConfigEditorDocument open() throws IOException { return open(UnifiedConfig.path()); }

    static ConfigEditorDocument open(Path path) throws IOException {
        String text = Files.readString(path);
        CommentedConfig parsed = new TomlParser().parse(UnifiedConfig.stripBom(text));
        ThunderConfig.LOADER_SPEC.correct(parsed);
        return new ConfigEditorDocument(path, text, parsed);
    }

    public Path path() { return path; }
    public boolean changed() { return !initial.equals(values); }

    public static JsonObject defaults() {
        var root = CommentedConfig.of(LinkedHashMap::new, com.electronwill.nightconfig.toml.TomlFormat.instance());
        ThunderConfig.SPEC.correct(root);
        JsonObject result = UnifiedConfig.toJson(root).getAsJsonObject();
        result.add("creeperPhantom", PhantomConfig.GSON.toJsonTree(new PhantomConfig.Settings()));
        return result;
    }

    public static JsonElement newListEntry(String key) {
        return PhantomConfig.GSON.toJsonTree(switch (key) {
            case "weapons" -> new PhantomConfig.Option();
            case "armorSets" -> new PhantomConfig.Armor();
            case "enchantments", "weaponEnchantments" -> new PhantomConfig.Enchant("minecraft:unbreaking", 1, 0);
            case "powerLevels" -> new PhantomConfig.Power(1, 0);
            default -> throw new IllegalArgumentException("Unknown list: " + key);
        });
    }

    /** Delete only the selected draft; keep invalid input attached to surviving list entries. */
    public static void removeListEntry(JsonArray list, String path, int index, Map<String, String> invalidInputs) {
        list.remove(index);
        String prefix = path + "/";
        Map<String, String> retained = new LinkedHashMap<>();
        invalidInputs.forEach((key, text) -> {
            if (!key.startsWith(prefix)) {
                retained.put(key, text);
                return;
            }
            int separator = key.indexOf('/', prefix.length());
            String entryIndex = key.substring(prefix.length(), separator < 0 ? key.length() : separator);
            int previousIndex = Integer.parseInt(entryIndex);
            if (previousIndex == index) return;
            String suffix = separator < 0 ? "" : key.substring(separator);
            retained.put(prefix + (previousIndex > index ? previousIndex - 1 : previousIndex) + suffix, text);
        });
        invalidInputs.clear();
        invalidInputs.putAll(retained);
    }

    CommentedConfig validated(RegistryAccess registry) {
        var candidate = new TomlParser().parse(new TomlWriter().writeToString(source));
        for (var entry : values.entrySet()) candidate.set(entry.getKey(), UnifiedConfig.toToml(entry.getValue()));
        PhantomConfig.parse(values.get("creeperPhantom").toString(), registry);
        validateSpecValues(ThunderConfig.SPEC.getValues(), candidate);
        return candidate;
    }

    private static void validateSpecValues(UnmodifiableConfig section, CommentedConfig candidate) {
        for (var entry : section.entrySet()) {
            Object raw = entry.getValue();
            if (raw instanceof ModConfigSpec.ConfigValue<?> value) {
                Object supplied = candidate.get(value.getPath());
                if (value instanceof ModConfigSpec.IntValue && supplied instanceof Number number) {
                    try { candidate.set(value.getPath(), new java.math.BigDecimal(number.toString()).intValueExact()); }
                    catch (ArithmeticException e) { throw new IllegalArgumentException(String.join(".", value.getPath()) + ": 必须为整数 / expected integer"); }
                } else if (value instanceof ModConfigSpec.DoubleValue && supplied instanceof Number number) {
                    candidate.set(value.getPath(), number.doubleValue());
                }
                if (!value.getSpec().test(candidate.get(value.getPath())))
                    throw new IllegalArgumentException(String.join(".", value.getPath()) + ": 数值超出允许范围 / invalid value");
            } else if (raw instanceof UnmodifiableConfig child) validateSpecValues(child, candidate);
        }
    }

    /** Called on the owning server thread for a loaded world, or the client thread at the title screen. */
    public synchronized void save(RegistryAccess registry, boolean loadedWorld) throws IOException {
        if (!path.equals(UnifiedConfig.path())) throw new IOException("配置所属世界已改变，请重新打开界面。");
        saveToDisk(registry, loadedWorld);
    }

    // Separate path-based transaction is also exercised by isolated GameTests.
    synchronized void saveToDisk(RegistryAccess registry, boolean loadedWorld) throws IOException {
        CommentedConfig candidate = validated(registry);
        if (!Files.readString(path).equals(original))
            throw new IOException("配置文件已被其他操作修改，请取消并重新打开，避免覆盖新内容。");
        Files.copy(path, path.resolveSibling(path.getFileName() + ".before-gui.bak"), StandardCopyOption.REPLACE_EXISTING);
        if (loadedWorld) {
            if (!ThunderConfig.SPEC.isLoaded()) throw new IOException("世界配置已卸载，请重新打开界面。");
            Map<ModConfigSpec.ConfigValue<?>, Object> previous = new LinkedHashMap<>();
            collectValues(ThunderConfig.SPEC.getValues(), previous);
            try {
                previous.keySet().forEach(value -> set(value, candidate.get(value.getPath())));
                ThunderConfig.SPEC.save(); // Loader owns persistence, cache invalidation and reload events.
            } catch (RuntimeException e) {
                previous.forEach(ConfigEditorDocument::set);
                throw new IOException("保存失败；请检查配置文件权限。", e);
            }
        } else {
            Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "config-editor-", ".tmp");
            try {
                Files.writeString(temporary, new TomlWriter().writeToString(candidate));
                try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    private static void collectValues(UnmodifiableConfig section, Map<ModConfigSpec.ConfigValue<?>, Object> output) {
        for (var entry : section.entrySet()) {
            if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) output.put(value, value.getRaw());
            else if (entry.getValue() instanceof UnmodifiableConfig child) collectValues(child, output);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void set(ModConfigSpec.ConfigValue value, Object data) { value.set(data); }
}
