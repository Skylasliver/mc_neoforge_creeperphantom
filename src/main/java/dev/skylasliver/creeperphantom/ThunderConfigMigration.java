package dev.skylasliver.creeperphantom;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Migrate before NeoForge removes old keys or inserts defaults, including world overrides. */
final class ThunderConfigMigration implements IConfigSpec {
    private static final String[] MOVED_KEYS = {
        "lightningBoostPercent", "lightningBoostSeconds", "lightningAttractionIntervalSeconds"
    };
    private final ModConfigSpec delegate;

    ThunderConfigMigration(ModConfigSpec delegate) { this.delegate = delegate; }

    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public void validateSpec(ModConfig config) { delegate.validateSpec(config); }
    @Override public void acceptConfig(ILoadedConfig config) { delegate.acceptConfig(config); }

    @Override public boolean isCorrect(UnmodifiableCommentedConfig config) {
        for (String key : MOVED_KEYS) {
            if (config.contains("thunderElytra." + key)) return false;
        }
        return delegate.isCorrect(config);
    }

    @Override public void correct(CommentedConfig config) {
        // ConfigTracker backs up the original file before calling this method.
        for (String key : MOVED_KEYS) {
            String oldPath = "thunderElytra." + key;
            String newPath = "thunderContinuance." + key;
            if (!config.contains(oldPath)) continue;
            // An explicitly supplied new value always wins, including zero.
            if (!config.contains(newPath)) {
                Object value = config.get(oldPath);
                config.set(newPath, value);
                String comment = config.getComment(oldPath);
                if (comment != null) config.setComment(newPath, comment);
            }
            config.remove(oldPath);
            config.removeComment(oldPath);
        }
        delegate.correct(config);
    }
}
