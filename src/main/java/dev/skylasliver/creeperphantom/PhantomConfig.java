package dev.skylasliver.creeperphantom;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Validated whole-file transaction. Active objects are never mutated after publication. */
public final class PhantomConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonElement SCHEMA = GSON.toJsonTree(new Settings());
    private static final Map<String, JsonElement> ARRAY_SCHEMAS = Map.of(
        "weapons", GSON.toJsonTree(new Option()),
        "armorSets", GSON.toJsonTree(new Armor()),
        "powerLevels", GSON.toJsonTree(new Power(1, 100)),
        "enchantments", GSON.toJsonTree(new Enchant("minecraft:unbreaking", 1, 100)),
        "weaponEnchantments", GSON.toJsonTree(new Enchant("minecraft:unbreaking", 1, 100)));
    private static volatile Settings active = new Settings();
    public static final String FILE_NAME = "creeper-phantom.json";
    private PhantomConfig() {}

    public static Settings snapshot() { return GSON.fromJson(GSON.toJsonTree(active), Settings.class); }
    public static double replacementChance() { return active.phantomReplacementChance / 100.0; }

    public record ReloadResult(boolean success, String message) {}

    public static synchronized ReloadResult reload(RegistryAccess registry) {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, ConfigDocumentation.annotate(GSON.toJson(new Settings())));
            }
            String text = Files.readString(file);
            Settings candidate = parse(text, registry);
            // Validate first; a documentation write failure must not reject valid settings.
            String documented = ConfigDocumentation.annotate(text);
            if (!documented.equals(text)) {
                try { writeDocumented(file, documented); }
                catch (java.io.IOException e) {
                    LogUtils.getLogger().warn("配置有效，但未能补充中文注释：{}", file, e);
                }
            }
            active = candidate;
            return new ReloadResult(true, "苦力怕幻翼配置已重载；仅影响新生成实体。");
        } catch (Exception e) {
            String message = "配置加载失败，继续使用上一份有效配置：" + e.getMessage();
            LogUtils.getLogger().error(message);
            return new ReloadResult(false, message);
        }
    }

    private static void writeDocumented(Path file, String text) throws java.io.IOException {
        Path temporary = Files.createTempFile(file.getParent(), "creeper-phantom-", ".tmp");
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

    public static Settings parse(String text, RegistryAccess registry) {
        JsonElement tree = JsonParser.parseString(text);
        if (!tree.isJsonObject()) throw new IllegalArgumentException("root: expected JSON object");
        rejectUnknown(tree, SCHEMA, "root");
        Settings s = GSON.fromJson(withDefaults(tree.getAsJsonObject(), SCHEMA.getAsJsonObject()), Settings.class);
        validate(s, registry);
        return s;
    }
    // Merge objects against their own defaults; explicit arrays (including []) replace defaults.
    private static JsonObject withDefaults(JsonObject supplied, JsonObject defaults) {
        JsonObject result = defaults.deepCopy();
        for (var entry : supplied.entrySet()) {
            JsonElement fallback = defaults.get(entry.getKey());
            JsonElement value = entry.getValue();
            result.add(entry.getKey(), value.isJsonObject() && fallback != null && fallback.isJsonObject()
                ? withDefaults(value.getAsJsonObject(), fallback.getAsJsonObject()) : value.deepCopy());
        }
        return result;
    }

    private static void rejectUnknown(JsonElement value, JsonElement example, String path) {
        if (value.isJsonNull()) throw new IllegalArgumentException(path + ": null is not allowed");
        if (example.isJsonObject()) {
            if (!value.isJsonObject()) throw new IllegalArgumentException(path + ": expected object");
            for (var e : value.getAsJsonObject().entrySet()) {
                JsonElement expected = example.getAsJsonObject().get(e.getKey());
                if (expected == null) throw new IllegalArgumentException(path + "." + e.getKey() + ": unknown field");
                rejectUnknown(e.getValue(), expected, path + "." + e.getKey());
            }
        } else if (example.isJsonArray()) {
            if (!value.isJsonArray()) throw new IllegalArgumentException(path + ": expected array");
            JsonElement elementSchema = ARRAY_SCHEMAS.get(path.substring(path.lastIndexOf('.') + 1));
            if (elementSchema == null) throw new IllegalArgumentException(path + ": unknown array schema");
            for(int i=0;i<value.getAsJsonArray().size();i++)
                rejectUnknown(value.getAsJsonArray().get(i),elementSchema,path+"["+i+"]");
        } else if (example.isJsonPrimitive()) {
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException(path + ": expected primitive");
            var a = value.getAsJsonPrimitive(); var b = example.getAsJsonPrimitive();
            if (a.isBoolean() != b.isBoolean() || a.isNumber() != b.isNumber() || a.isString() != b.isString())
                throw new IllegalArgumentException(path + ": primitive type mismatch");
            if (path.endsWith(".level")) {
                try { a.getAsBigDecimal().intValueExact(); }
                catch (ArithmeticException | NumberFormatException e) {
                    throw new IllegalArgumentException(path + ": expected a 32-bit integer");
                }
            }
        }
    }
    public static void validate(Settings s, RegistryAccess registry) {
        range("phantomReplacementChance",s.phantomReplacementChance,0,100);
        range("maxHealth",s.maxHealth,1,1024); range("flightSpeed",s.flightSpeed,0.05,5);
        range("normalExplosionRadius",s.normalExplosionRadius,0.1,32);
        range("chargedExplosionRadius",s.chargedExplosionRadius,0.1,32);
        range("normalExplosionDamage",s.normalExplosionDamage,0,1024);
        range("chargedExplosionDamage",s.chargedExplosionDamage,0,1024);
        range("babyZombieSpawnChance",s.babyZombieSpawnChance,0,100);
        range("skeletonSpawnChance",s.skeletonSpawnChance,0,100);
        equipment(s.babyZombie,"babyZombie",registry,s.allowOverlevelEnchantments);
        equipment(s.skeleton,"skeleton",registry,s.allowOverlevelEnchantments);
    }
    private static void equipment(Equipment e,String p,RegistryAccess registry,boolean over) {
        if(e==null || e.weapons==null || e.armorSets==null || e.weaponEnchantments==null || e.powerLevels==null)
            throw new IllegalArgumentException(p + ": missing equipment lists");
        range(p+".weaponChance",e.weaponChance,0,100);
        // Sum decimal percentages exactly; binary addition can reject a valid total of 100.
        BigDecimal sum=BigDecimal.ZERO;
        for(int i=0;i<e.weapons.size();i++) {
            Option o=e.weapons.get(i); String f=p+".weapons["+i+"]";
            if(o==null) throw new IllegalArgumentException(f+": null");
            range(f+".chance",o.chance,0,100); sum=sum.add(BigDecimal.valueOf(o.chance));
            item(f+".item",o.item);
            enchantments(o.enchantments,f+".enchantments",registry,over);
        }
        probabilityTotal(p+".weapons",sum); sum=BigDecimal.ZERO;
        for(int i=0;i<e.armorSets.size();i++) {
            Armor a=e.armorSets.get(i); String f=p+".armorSets["+i+"]";
            if(a==null) throw new IllegalArgumentException(f+": null");
            range(f+".chance",a.chance,0,100); sum=sum.add(BigDecimal.valueOf(a.chance));
            item(f+".head",a.head); item(f+".chest",a.chest); item(f+".legs",a.legs); item(f+".feet",a.feet);
            enchantments(a.enchantments,f+".enchantments",registry,over);
        }
        probabilityTotal(p+".armorSets",sum);
        enchantments(e.weaponEnchantments,p+".weaponEnchantments",registry,over);
        sum=BigDecimal.ZERO;
        for(int i=0;i<e.powerLevels.size();i++) {
            Power power=e.powerLevels.get(i); String f=p+".powerLevels["+i+"]";
            if(power==null) throw new IllegalArgumentException(f+": null is not allowed");
            range(f+".chance",power.chance,0,100); sum=sum.add(BigDecimal.valueOf(power.chance));
            range(f+".level",power.level,1,over?255:5);
        }
        probabilityTotal(p+".powerLevels",sum);
    }
    private static void probabilityTotal(String p,BigDecimal total) {
        if(total.compareTo(BigDecimal.valueOf(100))>0)
            throw new IllegalArgumentException(p+" probability total = "+total+", maximum 100");
    }
    private static void enchantments(List<Enchant> list,String p,RegistryAccess registry,boolean over) {
        if(list==null) throw new IllegalArgumentException(p+": null");
        // Each enchantment is an independent roll; alternative levels are the powerLevels group.
        for(int i=0;i<list.size();i++) {
            Enchant e=list.get(i); String f=p+"["+i+"]";
            if(e==null) throw new IllegalArgumentException(f+": null");
            range(f+".chance",e.chance,0,100); range(f+".level",e.level,1,255);
            ResourceLocation id=ResourceLocation.tryParse(e.id == null ? "" : e.id);
            if(id==null) throw new IllegalArgumentException(f+".id: invalid enchantment id");
            if(registry!=null) {
                var r=registry.registryOrThrow(Registries.ENCHANTMENT);
                var found=r.get(id);
                if(found==null) throw new IllegalArgumentException(f+".id: unknown enchantment "+id);
                if(!over) range(f+".level",e.level,1,found.getMaxLevel());
            }
        }
    }
    private static void item(String p,String value) {
        ResourceLocation id=ResourceLocation.tryParse(value == null ? "" : value);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException(p+": unknown item "+value);
    }
    private static void range(String p,double n,double min,double max) {
        if(!Double.isFinite(n)||n<min||n>max) throw new IllegalArgumentException(p+" = "+n+", allowed range ["+min+", "+max+"]");
    }

    public static final class Settings {
        public double phantomReplacementChance=50, maxHealth=40, flightSpeed=0.6;
        public boolean lightningVariantTargetsVillagers=true;
        public boolean normalBurnsInDaylight=true, chargedBurnsInDaylight=true;
        public boolean normalExplosionBreakBlocks=true, chargedExplosionBreakBlocks=true;
        public double normalExplosionRadius=3, chargedExplosionRadius=6;
        // Raw maximum at zero distance, before armor/difficulty/exposure. Vanilla radius 3/6 => 43/85.
        public double normalExplosionDamage=43, chargedExplosionDamage=85;
        public double babyZombieSpawnChance=40, skeletonSpawnChance=50;
        public boolean allowOverlevelEnchantments=true;
        public Equipment babyZombie=Equipment.zombie(), skeleton=Equipment.skeleton();
    }
    public static final class Equipment {
        public double weaponChance=50;
        public List<Option> weapons=new ArrayList<>();
        public List<Armor> armorSets=armorDefaults();
        public List<Enchant> weaponEnchantments=new ArrayList<>();
        public List<Power> powerLevels=new ArrayList<>();
        static Equipment zombie() {
            Equipment e=new Equipment();
            Option axe=new Option(); axe.item="minecraft:netherite_axe"; axe.chance=100;
            e.weapons.add(axe);
            return e;
        }
        static Equipment skeleton() {
            Equipment e=new Equipment(); e.weaponChance=100;
            Option bow=new Option(); bow.item="minecraft:bow"; bow.chance=100; e.weapons.add(bow);
            for(int level=1;level<=5;level++)e.powerLevels.add(new Power(level,20));
            for(String id:List.of("flame","punch","infinity","unbreaking","mending"))
                e.weaponEnchantments.add(new Enchant("minecraft:"+id,1,0));
            return e;
        }
    }
    public static final class Option {
        public String item="minecraft:bow";
        public double chance=100;
        public List<Enchant> enchantments=new ArrayList<>();
    }
    public static final class Armor {
        public double chance=0;
        public String head="minecraft:air",chest="minecraft:air",legs="minecraft:air",feet="minecraft:air";
        public List<Enchant> enchantments=new ArrayList<>();
    }
    public record Enchant(String id,int level,double chance) {}
    public record Power(int level,double chance) {}
    private static List<Armor> armorDefaults() {
        List<Armor> result=new ArrayList<>();
        String[] materials = {"leather", "iron", "diamond", "netherite"};
        int[] chances = {50, 20, 20, 10};
        for(int i=0;i<materials.length;i++){
            String material=materials[i];
            Armor a=new Armor();a.chance=chances[i];
            a.head="minecraft:"+material+"_helmet";a.chest="minecraft:"+material+"_chestplate";
            a.legs="minecraft:"+material+"_leggings";a.feet="minecraft:"+material+"_boots";
            if(i==3)a.enchantments.add(new Enchant("minecraft:protection",3,50));
            result.add(a);
        }
        return result;
    }
}
