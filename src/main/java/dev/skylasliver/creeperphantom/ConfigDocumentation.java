package dev.skylasliver.creeperphantom;

import java.util.Map;

/** Field descriptions used by the unified TOML writer. */
public final class ConfigDocumentation {
    private static final Map<String, String> COMMENTS = Map.ofEntries(
        Map.entry("thunderElytra", "雷霆鞘翅的锻造、飞行与修复设置。"),
        Map.entry("thunderContinuance", "雷霆续行附魔的效果、引雷与加速设置。"),
        Map.entry("creeperPhantom", "苦力怕幻翼和乘客设置；保存后仅影响新生成的实体。"),
        Map.entry("craftingEnabled", "是否允许锻造雷霆鞘翅；关闭不影响已有物品。"),
        Map.entry("flightSpeedMultiplier", "相对原版鞘翅的滑翔速度倍率，1–4。"),
        Map.entry("lightningRepairEnabled", "被雷击中时是否修满雷霆鞘翅耐久。"),
        Map.entry("enabled", "雷霆续行总开关；关闭会停止主动引雷、雷击加速并清除充能。"),
        Map.entry("lightningBoostPercent", "雷击额外加速，0–200%；50 表示再乘以 1.5。"),
        Map.entry("lightningBoostSeconds", "雷击加速持续时间，1–300 秒，整数。"),
        Map.entry("lightningAttractionIntervalSeconds", "主动引雷间隔，1–3600 秒，整数。"),
        Map.entry("normalBurnsInDaylight", "普通苦力怕幻翼是否在白天受日照燃烧；true 开启，false 关闭。"),
        Map.entry("chargedBurnsInDaylight", "闪电苦力怕幻翼是否在白天受日照燃烧；true 开启，false 关闭。"),
        Map.entry("phantomReplacementChance", "夜间自然生成幻翼被替换的概率，0–100%。"),
        Map.entry("maxHealth", "最大生命值，1–1024；2 点生命值为 1 颗心。"),
        Map.entry("flightSpeed", "自主移动倍率，0.05–5；不是每秒移动的方块数。"),
        Map.entry("lightningVariantTargetsVillagers", "闪电变种是否攻击村民；玩家目标优先。"),
        Map.entry("normalExplosionBreakBlocks", "普通变种是否破坏方块；仍受 mobGriefing 游戏规则影响。"),
        Map.entry("chargedExplosionBreakBlocks", "闪电变种是否破坏方块；仍受 mobGriefing 游戏规则影响。"),
        Map.entry("normalExplosionRadius", "普通爆炸半径参数，0.1–32。"),
        Map.entry("chargedExplosionRadius", "闪电变种爆炸半径参数，0.1–32。"),
        Map.entry("normalExplosionDamage", "普通爆炸零距离原始最大伤害，0–1024；实际伤害受距离、遮挡、护甲和难度影响。"),
        Map.entry("chargedExplosionDamage", "闪电变种爆炸零距离原始最大伤害，0–1024；实际伤害受距离、遮挡、护甲和难度影响。"),
        Map.entry("babyZombieSpawnChance", "生成小僵尸乘客的概率，0–100%；与骷髅独立判定，可同时生成。"),
        Map.entry("skeletonSpawnChance", "生成骷髅乘客的概率，0–100%；与小僵尸独立判定，可同时生成。"),
        Map.entry("allowOverlevelEnchantments", "是否允许超过原版最高附魔等级（仍限 1–255）；不绕过物品适用性或附魔兼容性。"),
        Map.entry("babyZombie", "小僵尸乘客装备配置。"),
        Map.entry("skeleton", "骷髅乘客装备配置。"),
        Map.entry("weaponChance", "第一层武器装备概率，0–100%；成功后再从 weapons 中抽选。"),
        Map.entry("weapons", "武器互斥抽选表；chance 合计不得超过 100，剩余概率不装备武器。"),
        Map.entry("item", "物品注册 ID，格式为 命名空间:名称；minecraft:air 表示不装备。"),
        Map.entry("chance", "概率百分数，0–100；武器、护甲、力量同组互斥抽选，普通附魔逐条独立判定。"),
        Map.entry("armorSets", "护甲套装互斥抽选表；chance 合计不得超过 100，剩余概率不装备护甲。"),
        Map.entry("head", "头部物品 ID；minecraft:air 表示留空。"),
        Map.entry("chest", "胸部物品 ID；minecraft:air 表示留空。"),
        Map.entry("legs", "腿部物品 ID；minecraft:air 表示留空。"),
        Map.entry("feet", "脚部物品 ID；minecraft:air 表示留空。"),
        Map.entry("enchantments", "该武器或套装的附魔；各条独立抽选，套装按每件护甲独立判定。"),
        Map.entry("weaponEnchantments", "通用武器附魔，逐条独立判定；不适用或与已有附魔冲突时跳过。"),
        Map.entry("powerLevels", "仅对弓生效的力量等级互斥抽选；chance 合计不得超过 100，剩余概率不添加力量。"),
        Map.entry("id", "附魔注册 ID，例如 minecraft:protection；必须存在于当前注册表。"),
        Map.entry("level", "附魔等级，必须是 1–255 的整数；关闭超等级附魔时不得超过原版上限。"));

    private ConfigDocumentation() {}

    public static String comment(String key) { return COMMENTS.getOrDefault(key, key); }

}
