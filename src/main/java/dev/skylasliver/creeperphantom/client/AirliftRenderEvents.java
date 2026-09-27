package dev.skylasliver.creeperphantom.client;

import dev.skylasliver.creeperphantom.CreeperPhantomEntity;
import dev.skylasliver.creeperphantom.CreeperPhantomMod;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid=CreeperPhantomMod.MOD_ID, value=Dist.CLIENT)
public final class AirliftRenderEvents {
    // Render-thread only. Weak keys also cover a later listener cancelling a render.
    private static final Map<LivingEntity, Matrix4f> ROOTS = new WeakHashMap<>();

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if ((entity instanceof Zombie || entity instanceof Skeleton)
            && entity.getVehicle() instanceof CreeperPhantomEntity) {
            ROOTS.put(entity, new Matrix4f(event.getPoseStack().last().pose()));
        }
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        ROOTS.remove(event.getEntity());
    }

    static Matrix4f takeRoot(LivingEntity entity) { return ROOTS.remove(entity); }
}
