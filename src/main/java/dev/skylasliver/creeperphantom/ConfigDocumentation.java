package dev.skylasliver.creeperphantom;

import java.util.Map;
import java.util.regex.Pattern;

/** Adds Chinese documentation without changing values or discarding user comments. */
final class ConfigDocumentation {
    private static final String MARKER = "// 苦力怕幻翼配置说明（内联注释 v1）";
    private static final Pattern FIELD = Pattern.compile("^(\\s*)\"([^\"]+)\"\\s*:");
    private static final Map<String, String> COMMENTS = Map.ofEntries(
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

    static String annotate(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        var root = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
        StringBuilder missing = new StringBuilder();
        for (String key : new String[]{"normalBurnsInDaylight", "chargedBurnsInDaylight"}) {
            if (!root.has(key)) {
                if (!missing.isEmpty()) missing.append(",\n");
                missing.append("  // ").append(COMMENTS.get(key)).append('\n')
                    .append("  \"").append(key).append("\": true");
            }
        }
        if (!missing.isEmpty()) {
            // Skip leading comments so braces inside user documentation are preserved.
            var opening = Pattern.compile("\\A(?:\\s|//[^\\r\\n]*(?:\\R|$)|/\\*[\\s\\S]*?\\*/)*\\{").matcher(text);
            if (!opening.find()) throw new IllegalArgumentException("Missing configuration object");
            int offset = opening.end();
            text = text.substring(0, offset) + "\n" + missing
                + (root.size() > 0 ? ",\n" : "\n") + text.substring(offset);
        }
        if (text.startsWith(MARKER)) return text;
        StringBuilder result = new StringBuilder(MARKER).append('\n')
            .append("// 本文件是实际加载的配置；保留 .json 文件名，支持 // 和 /* */ 注释。\n")
            .append("// 修改后执行 /creeperphantom reload（权限等级 2）；仅影响新生成实体。\n")
            .append("// 百分数范围 0–100；武器、护甲套装、力量各组概率合计不得超过 100。\n")
            .append("// 未知字段、错误类型、无效 ID 或越界数值会拒绝整份配置，继续使用上一份有效设置。\n");
        for (String line : text.split("\\R", -1)) {
            var match = FIELD.matcher(line);
            if (match.find()) {
                String comment = COMMENTS.get(match.group(2));
                if (comment != null) result.append(match.group(1)).append("// ").append(comment).append('\n');
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
