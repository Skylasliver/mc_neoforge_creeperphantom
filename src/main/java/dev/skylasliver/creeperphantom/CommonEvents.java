package dev.skylasliver.creeperphantom;

import java.util.Comparator;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class CommonEvents {
    public static final String FALL="creeper_phantom_safe_fall";
    @SubscribeEvent public void start(ServerAboutToStartEvent e) {
        PhantomConfig.loadForServer(e.getServer().registryAccess());
        com.mojang.logging.LogUtils.getLogger().info(
            "模组配置路径：{}；雷霆续行 enabled={}，加速={}%，持续={} 秒，引雷间隔={} 秒",
            UnifiedConfig.path().toAbsolutePath(), ThunderConfig.value(ThunderConfig.CONTINUANCE_ENABLED),
            ThunderConfig.value(ThunderConfig.LIGHTNING_BOOST), ThunderConfig.value(ThunderConfig.BOOST_SECONDS),
            ThunderConfig.value(ThunderConfig.ATTRACTION_SECONDS));
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("creeperphantom").requires(s->s.hasPermission(2))
            .then(Commands.literal("reload").executes(c->{
                PhantomConfig.ReloadResult result=PhantomConfig.reload(c.getSource().registryAccess());
                if(result.success())c.getSource().sendSuccess(()->Component.literal(result.message()),true);
                else c.getSource().sendFailure(Component.literal(result.message()));
                return result.success()?1:0;
            })));
    }
    @SubscribeEvent public void naturalSpawn(FinalizeSpawnEvent e) {
        if(e.getEntity().getType()!=EntityType.PHANTOM || e.getSpawnType()!=MobSpawnType.NATURAL
                || e.isSpawnCancelled() || !(e.getLevel() instanceof ServerLevel level) || !level.isNight())return;
        if(level.random.nextDouble()>=PhantomConfig.replacementChance())return;
        CreeperPhantomEntity replacement=CreeperPhantomMod.CREEPER_PHANTOM.get().create(level);
        if(replacement==null)return;
        replacement.moveTo(e.getX(),e.getY(),e.getZ(),e.getEntity().getYRot(),0);
        replacement.finalizeSpawn(level,e.getDifficulty(),MobSpawnType.NATURAL,e.getSpawnData());
        if(level.addFreshEntity(replacement))e.setSpawnCancelled(true);
    }
    @SubscribeEvent public void lightning(EntityJoinLevelEvent e) {
        if(e.loadedFromDisk() || !(e.getLevel() instanceof ServerLevel level) || !level.isThundering()
            || !(e.getEntity() instanceof LightningBolt bolt) || bolt.getCause()!=null
            || bolt.getPersistentData().getBoolean(ThunderElytraEvents.ATTRACTED_BOLT))return;
        level.getEntitiesOfClass(CreeperPhantomEntity.class,bolt.getBoundingBox().inflate(128),
            p->!p.isCharged() && p.isAlive() && level.canSeeSky(p.blockPosition()))
            .stream().min(Comparator.comparingDouble(p->p.distanceToSqr(bolt)))
            .ifPresent(p->bolt.moveTo(p.position()));
    }
    @SubscribeEvent public void friendlyArrow(ProjectileImpactEvent e) {
        if(!(e.getProjectile() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof AbstractSkeleton skeleton)
            || !(skeleton.getVehicle() instanceof CreeperPhantomEntity mount)
            || !(e.getRayTraceResult() instanceof EntityHitResult hit))return;
        // Do not suppress hits on the mount: the riding skeleton can still earn a record.
        if(hit.getEntity()!=mount && mount.hasIndirectPassenger(hit.getEntity()))e.setCanceled(true);
    }
    @SubscribeEvent public void fall(LivingIncomingDamageEvent e) {
        if(e.getSource().is(DamageTypes.FALL) && e.getEntity().getPersistentData().getBoolean(FALL)){
            e.getEntity().getPersistentData().remove(FALL);
            e.setCanceled(true);
        }
    }
    @SubscribeEvent public void landed(EntityTickEvent.Post e) {
        Entity entity=e.getEntity();
        if(entity instanceof LivingEntity && !entity.level().isClientSide && entity.getPersistentData().getBoolean(FALL)
            && (entity.onGround() || entity.isInWater() || entity.isInLava() || entity.isPassenger())){
            entity.fallDistance=0;
            entity.getPersistentData().remove(FALL);
        }
    }
}
