package dev.skylasliver.creeperphantom;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World-specific SERVER configuration, synchronized to joining clients by NeoForge. */
public final class ThunderConfig {
    // The requested filename uses .json; NeoForge still reads/writes TOML explicitly.
    public static final String FILE_NAME = "creeper-phantom.json";
    public static final String PREVIOUS_FILE_NAME = "thunder-elytra-server.toml";
    public static final ModConfigSpec SPEC;
    public static final net.neoforged.fml.config.IConfigSpec LOADER_SPEC;
    public static final ModConfigSpec.BooleanValue CONTINUANCE_ENABLED;
    public static final ModConfigSpec.BooleanValue CRAFTING_ENABLED;
    public static final ModConfigSpec.DoubleValue FLIGHT_SPEED;
    public static final ModConfigSpec.DoubleValue LIGHTNING_BOOST;
    public static final ModConfigSpec.BooleanValue LIGHTNING_REPAIR;
    public static final ModConfigSpec.IntValue BOOST_SECONDS;
    public static final ModConfigSpec.IntValue ATTRACTION_SECONDS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("雷霆鞘翅：服务端设置自动同步客户端。").push("thunderElytra");
        CRAFTING_ENABLED = builder.comment("是否允许锻造雷霆鞘翅；关闭不影响已有物品。")
            .define("craftingEnabled", true);
        FLIGHT_SPEED = builder.comment("相对原版鞘翅的滑翔速度倍率，默认 1.2，范围 1–4。")
            .defineInRange("flightSpeedMultiplier", 1.2, 1.0, 4.0);
        LIGHTNING_REPAIR = builder.comment("被雷击中时是否将穿戴的雷霆鞘翅耐久补满；独立于雷霆续行附魔开关。")
            .define("lightningRepairEnabled", true);
        builder.pop();
        builder.comment("雷霆续行附魔：独立设置，服务端自动同步客户端。")
            .push("thunderContinuance");
        CONTINUANCE_ENABLED = builder.comment("关闭后停止主动引雷和雷击加速，并清除已有充能；保留物品上的附魔。")
            .define("enabled", true);
        LIGHTNING_BOOST = builder.comment("穿戴带此附魔的雷霆鞘翅被雷击后，滑翔额外加速百分比；50 表示基础飞行倍率再乘以 1.5，200 表示再乘以 3。")
            .defineInRange("lightningBoostPercent", 50.0, 0.0, 200.0);
        BOOST_SECONDS = builder.comment("雷击加速持续秒数；重复雷击刷新为此时长，不叠加倍率；脱下鞘翅或移除附魔后失效。")
            .defineInRange("lightningBoostSeconds", 120, 1, 300);
        ATTRACTION_SECONDS = builder.comment("穿戴带此附魔的雷霆鞘翅时，雷雨天露天且可降雨位置的主动引雷间隔秒数；未附魔时不主动引雷。")
            .defineInRange("lightningAttractionIntervalSeconds", 120, 1, 3600);
        builder.pop();
        // Validate the table transactionally after registries become available.
        builder.comment("苦力怕幻翼与乘客装备；修改后 /creeperphantom reload，仅影响新实体。")
            .define("creeperPhantom", UnifiedConfig::defaultSection,
                value -> value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig);
        SPEC = builder.build();
        LOADER_SPEC = new ThunderConfigMigration(SPEC);
    }

    private ThunderConfig() {}
    public static <T> T value(ModConfigSpec.ConfigValue<T> setting) {
        return SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }
}
