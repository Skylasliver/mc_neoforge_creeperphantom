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
    public CreeperPhantomMod(IEventBus bus){
        ENTITY_TYPES.register(bus); ITEMS.register(bus); bus.addListener(CreeperPhantomMod::attributes);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(EGG.get());});
        NeoForge.EVENT_BUS.register(new CommonEvents());
    }
    private static void attributes(EntityAttributeCreationEvent e){e.put(CREEPER_PHANTOM.get(),Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.FLYING_SPEED,0.6).add(Attributes.FOLLOW_RANGE,128).build());}
    @net.neoforged.fml.common.EventBusSubscriber(modid=MOD_ID,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ClientEvents{
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e){dev.skylasliver.creeperphantom.client.AirliftHarnessLayer.install(e);}
        @SubscribeEvent public static void render(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(CREEPER_PHANTOM.get(),dev.skylasliver.creeperphantom.client.CreeperPhantomRenderer::new);}
    }
}
