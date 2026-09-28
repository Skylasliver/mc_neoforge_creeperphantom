package dev.skylasliver.creeperphantom;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;

@Mod(CreeperPhantomMod.MOD_ID)
public final class CreeperPhantomMod {
    public static final String MOD_ID="skylasliver_creeperphantom";
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,MOD_ID);
    public static final DeferredHolder<EntityType<?>,EntityType<CreeperPhantomEntity>> CREEPER_PHANTOM=
        ENTITY_TYPES.register("creeper_phantom",()->EntityType.Builder.of(CreeperPhantomEntity::new,MobCategory.MONSTER)
            .sized(2.7F,1.05F).clientTrackingRange(10).updateInterval(2).build(MOD_ID+":creeper_phantom"));
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> EGG=ITEMS.register("creeper_phantom_spawn_egg",()->new net.neoforged.neoforge.common.DeferredSpawnEggItem(CREEPER_PHANTOM,0x428D36,0x142E16,new Item.Properties()));
    public static final DeferredItem<Item> THUNDER_MEMBRANE=ITEMS.register("thunder_membrane",()->new Item(new Item.Properties()));
    public static final DeferredItem<ThunderElytraItem> THUNDER_ELYTRA=ITEMS.register("thunder_elytra",()->new ThunderElytraItem(new Item.Properties().durability(432).rarity(Rarity.RARE)));
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,ThunderSmithingRecipe.Serializer> THUNDER_SMITHING=RECIPES.register("thunder_smithing",ThunderSmithingRecipe.Serializer::new);
    public static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,MOD_ID);
    public static final DeferredHolder<net.minecraft.world.effect.MobEffect,ThunderChargeEffect> THUNDER_CHARGE=
        EFFECTS.register("thunder_charge",ThunderChargeEffect::new);
    public CreeperPhantomMod(IEventBus bus, net.neoforged.fml.ModContainer container){
        UnifiedConfig.prepare();
        bus.addListener(UnifiedConfig::onLoading);
        bus.addListener(UnifiedConfig::onReloading);
        bus.addListener(UnifiedConfig::onUnloading);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,ThunderConfig.LOADER_SPEC,ThunderConfig.FILE_NAME);
        EFFECTS.register(bus);
        ENTITY_TYPES.register(bus); ITEMS.register(bus); RECIPES.register(bus); bus.addListener(CreeperPhantomMod::attributes);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey()==CreativeModeTabs.INGREDIENTS)e.accept(THUNDER_MEMBRANE.get());
            if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)e.accept(ThunderSmithingRecipe.enchantResult(new ItemStack(THUNDER_ELYTRA.get()),e.getParameters().holders()));
        });
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(EGG.get());});
        NeoForge.EVENT_BUS.register(new CommonEvents());
        NeoForge.EVENT_BUS.register(new ThunderElytraEvents());
    }
    private static void attributes(EntityAttributeCreationEvent e){e.put(CREEPER_PHANTOM.get(),Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.FLYING_SPEED,0.6).add(Attributes.FOLLOW_RANGE,128).build());}
    @net.neoforged.fml.common.EventBusSubscriber(modid=MOD_ID,value=Dist.CLIENT)
    public static final class ClientEvents{
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
            net.neoforged.fml.ModList.get().getModContainerById(MOD_ID).orElseThrow()
                .registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (container, parent) -> new dev.skylasliver.creeperphantom.client.PhantomConfigScreen(parent));
        }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e){dev.skylasliver.creeperphantom.client.AirliftHarnessLayer.install(e);}
        @SubscribeEvent public static void render(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(CREEPER_PHANTOM.get(),dev.skylasliver.creeperphantom.client.CreeperPhantomRenderer::new);}
    }
}
