package dev.skylasliver.creeperphantom;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.Nullable;

public class CreeperPhantomEntity extends Phantom {
    private static final EntityDataAccessor<Boolean> CHARGED=SynchedEntityData.defineId(CreeperPhantomEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FUSE=SynchedEntityData.defineId(CreeperPhantomEntity.class,EntityDataSerializers.INT);
    private PhantomConfig.Settings settings;
    private boolean initialized, passengersCreated;
    private int nextTargetScan;
    private int nextDiveTick;
    public static final double ACQUIRE_RANGE=96, RETAIN_RANGE=128, VERTICAL_RANGE=128;
    private static final double RETAIN_VERTICAL_RANGE=160;
    private final Set<UUID> riderSkeletons=new HashSet<>();

    public CreeperPhantomEntity(EntityType<? extends Phantom> type,Level level) {
        super(type,level);
        settings=PhantomConfig.snapshot();
        // Keep vanilla idle flight, but use a stable three-dimensional pursuit while diving.
        MoveControl idleFlight=moveControl;
        moveControl=new MoveControl(this) {
            @Override public void tick() {
                if(attackPhase!=Phantom.AttackPhase.SWOOP || !eligible(getTarget())) {
                    idleFlight.tick();
                    return;
                }
                Vec3 offset=moveTargetPoint.subtract(position());
                double distance=offset.length();
                Vec3 desired=distance<0.05?Vec3.ZERO:offset.scale(Math.min(1.5,distance*0.65)/distance);
                setDeltaMovement(getDeltaMovement().lerp(desired,0.25));
                double horizontal=offset.horizontalDistance();
                if(horizontal>0.001) {
                    float yaw=(float)(Mth.atan2(offset.z,offset.x)*Mth.RAD_TO_DEG)-90;
                    setYRot(Mth.approachDegrees(getYRot(),yaw,12));
                    yBodyRot=getYRot();
                }
                setXRot((float)(Mth.atan2(offset.y,horizontal)*Mth.RAD_TO_DEG));
            }
        };
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);b.define(CHARGED,false);b.define(FUSE,0);
    }
    @Override protected void registerGoals() {
        super.registerGoals();
        targetSelector.removeAllGoals(g->true);
        // In 1.21.1 priorities 1/2 are attack strategy/sweep; keep priority 3 idle circling.
        for(var wrapped:goalSelector.getAvailableGoals().stream().filter(g->g.getPriority()<3).toList())
            goalSelector.removeGoal(wrapped.getGoal());
        goalSelector.addGoal(1,new CommittedDiveGoal());
    }
    private final class CommittedDiveGoal extends Goal {
        private CommittedDiveGoal(){setFlags(EnumSet.of(Goal.Flag.MOVE));}
        @Override public boolean canUse(){return eligible(getTarget()) && tickCount>=nextDiveTick;}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){
            attackPhase=Phantom.AttackPhase.SWOOP;
            playSound(SoundEvents.PHANTOM_SWOOP,1.0F,0.95F+random.nextFloat()*0.1F);
        }
        @Override public void tick(){
            LivingEntity target=getTarget();
            if(!eligible(target))return;
            // A wall allows only a brief reposition, not another 8-12 second wait.
            if(horizontalCollision && fuse()==0) {
                nextDiveTick=tickCount+20;
                attackPhase=Phantom.AttackPhase.CIRCLE;
                anchorPoint=blockPosition().above(6);
                moveTargetPoint=position().add(0,6,0);
                return;
            }
            attackPhase=Phantom.AttackPhase.SWOOP;
            moveTargetPoint=target.position().add(0,target.getBbHeight()/2,0);
        }
        @Override public void stop(){attackPhase=Phantom.AttackPhase.CIRCLE;}
    }
    public boolean isCharged(){return entityData.get(CHARGED);}
    public int fuse(){return entityData.get(FUSE);}
    @Override protected boolean isSunBurnTick() {
        return (isCharged() ? settings.chargedBurnsInDaylight : settings.normalBurnsInDaylight)
            && super.isSunBurnTick();
    }
    public void initialize() {
        if(initialized)return;
        initialized=true;
        settings=PhantomConfig.snapshot();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(settings.maxHealth);
        setHealth((float)settings.maxHealth);
        anchorPoint=blockPosition().above(15);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor l,DifficultyInstance d,MobSpawnType reason,@Nullable SpawnGroupData data){
        var result=super.finalizeSpawn(l,d,reason,data);
        initialize();return result;
    }
    @Override public void tick() {
        if(!level().isClientSide && isAlive()) {
            initialize();
            if(!passengersCreated){passengersCreated=true;createPassengers();}
            updateTarget();
        }
        super.tick();
        if(level().isClientSide || !isAlive())return;
        LivingEntity t=getTarget();
        // Avoid ray casts until a target is actually within fuse distance.
        if(eligible(t) && distanceToSqr(t)<(fuse()>0?49:9) && reachable(t)) {
            if(fuse()==0)playSound(SoundEvents.CREEPER_PRIMED,1,0.5F);
            entityData.set(FUSE,fuse()+1);
            setDeltaMovement(getDeltaMovement().scale(0.6));
            // Stay committed to contact throughout the fuse.
            moveTargetPoint=t.position().add(0,t.getBbHeight()/2,0);
            if(fuse()>=30)detonate();
        } else entityData.set(FUSE,Math.max(0,fuse()-1));
    }
    private void updateTarget() {
        LivingEntity old=getTarget();
        if(old!=null && (!eligible(old)||!withinTargetRange(old,RETAIN_RANGE,RETAIN_VERTICAL_RANGE))) {
            setTarget(null);entityData.set(FUSE,0);
            attackPhase=Phantom.AttackPhase.CIRCLE;
            anchorPoint=blockPosition().above(8);
            moveTargetPoint=position().add(0,8,0);
            nextTargetScan=tickCount;
            nextDiveTick=tickCount;
        }
        if(tickCount>=nextTargetScan && !(getTarget() instanceof Player)) {
            nextTargetScan=tickCount+10;
            LivingEntity candidate=bestTarget();
            if(candidate!=null && (getTarget()==null || candidate instanceof Player)) {
                setTarget(candidate);attackPhase=Phantom.AttackPhase.CIRCLE;
            }
        }
        LivingEntity target=eligible(getTarget())?getTarget():null;
        for(Entity rider:getPassengers()) if(rider instanceof Mob mob) {
            if(mob.getTarget()!=target)mob.setTarget(target);
        }
    }
    private LivingEntity bestTarget() {
        // Players are always present in this list, including before their entity section is queryable.
        Player player = nearestVisibleTarget(level().players());
        if (player != null) return player;
        return isCharged() && settings.lightningVariantTargetsVillagers
            ? nearestVisibleTarget(level().getEntitiesOfClass(Villager.class,
                getBoundingBox().inflate(ACQUIRE_RANGE, VERTICAL_RANGE, ACQUIRE_RANGE))) : null;
    }
    private <T extends LivingEntity> T nearestVisibleTarget(List<? extends T> nearby) {
        T nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (T candidate : nearby) {
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance && eligible(candidate)
                && withinTargetRange(candidate, ACQUIRE_RANGE, VERTICAL_RANGE)
                && visibleForAcquisition(candidate)) {
                nearest = candidate;
                bestDistance = distance;
            }
        }
        return nearest;
    }
    private boolean visibleForAcquisition(LivingEntity target) {
        // Our horizontal/vertical limits already bound this ray; vanilla LOS adds a 128-block sphere.
        return level().clip(new ClipContext(getEyePosition(),target.getEyePosition(),
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private boolean eligible(LivingEntity target) {
        if(target==null||target.level()!=level()||!target.isAlive()||target.isSpectator()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative();
        return isCharged() && settings.lightningVariantTargetsVillagers && target instanceof Villager;
    }
    public boolean withinTargetRange(LivingEntity target,double horizontal,double vertical) {
        double dx=target.getX()-getX(), dz=target.getZ()-getZ();
        return dx*dx+dz*dz<=horizontal*horizontal && Math.abs(target.getY()-getY())<=vertical;
    }
    /** Contact visibility is separate from long-range acquisition. */
    private boolean reachable(LivingEntity target) {
        if(!hasLineOfSight(target))return false;
        Vec3 from=position().add(0,getBbHeight()/2,0);
        Vec3 to=target.position().add(0,target.getBbHeight()/2,0);
        return level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    @Override public boolean doHurtTarget(Entity target) {
        // Contact starts the fuse instead of inflicting phantom melee damage.
        return false;
    }
    @Override public void move(MoverType type,Vec3 movement) {
        super.move(type,settings!=null && type==MoverType.SELF?movement.scale(settings.flightSpeed):movement);
    }
    private void createPassengers() {
        if(!(level() instanceof ServerLevel server))return;
        if(random.nextDouble()*100<settings.babyZombieSpawnChance){
            Zombie zombie=EntityType.ZOMBIE.create(server);
            if(zombie!=null){
                zombie.moveTo(position());zombie.setBaby(true);
                EquipmentFactory.equip(zombie,settings.babyZombie,random);
                addRider(server,zombie);
            }
        }
        if(random.nextDouble()*100<settings.skeletonSpawnChance){
            Skeleton skeleton=EntityType.SKELETON.create(server);
            if(skeleton!=null){
                skeleton.moveTo(position());EquipmentFactory.equip(skeleton,settings.skeleton,random);
                addRider(server,skeleton);
            }
        }
    }
    private void addRider(ServerLevel server,Mob rider) {
        if(!rider.startRiding(this,true))return;
        if(!server.addFreshEntity(rider)) {
            rider.stopRiding();
            riderSkeletons.remove(rider.getUUID());
            rider.discard();
        }
    }
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengers().size()<2;}
    // Carried mobs hang from their harness instead of adopting the seated riding pose.
    @Override public boolean shouldRiderSit(){return false;}
    @Nullable
    @Override public LivingEntity getControllingPassenger(){
        // Riders are carried combatants, not pilots. Mob otherwise disables our MOVE goal
        // and redirects a rider's ground navigation to this flying entity.
        return null;
    }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if(passenger instanceof AbstractSkeleton && riderSkeletons!=null)riderSkeletons.add(passenger.getUUID());
    }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale) {
        boolean skeleton=passenger instanceof AbstractSkeleton;
        double side=skeleton ? -0.65 : 0.65;
        // Cancel the offset subtracted by Entity.positionRider; keep both heads below us.
        Vec3 feet=new Vec3(side,-passenger.getBbHeight()-0.35,0.15)
            .yRot(-getYRot()*Mth.DEG_TO_RAD);
        return feet.add(passenger.getVehicleAttachmentPoint(this));
    }
    @Override public boolean canCollideWith(Entity other) {
        return !hasIndirectPassenger(other) && super.canCollideWith(other);
    }
    public void detonate() {
        if(!(level() instanceof ServerLevel)||!isAlive())return;
        List<Entity> passengers=new ArrayList<>();getIndirectPassengers().forEach(passengers::add);
        Set<UUID> protectedIds=new HashSet<>();Map<Mob,LivingEntity> targets=new HashMap<>();
        for(Entity rider:passengers){
            protectedIds.add(rider.getUUID());
            if(rider instanceof Mob mob)targets.put(mob,mob.getTarget());
        }
        float radius=(float)(isCharged()?settings.chargedExplosionRadius:settings.normalExplosionRadius);
        double maxDamage=isCharged()?settings.chargedExplosionDamage:settings.normalExplosionDamage;
        boolean blocks=isCharged()?settings.chargedExplosionBreakBlocks:settings.normalExplosionBreakBlocks;
        level().explode(this,null,new ExplosionDamageCalculator(){
            @Override public boolean shouldDamageEntity(Explosion explosion,Entity entity){return !protectedIds.contains(entity.getUUID());}
            @Override public float getKnockbackMultiplier(Entity e){return protectedIds.contains(e.getUUID())?0:1;}
            @Override public float getEntityDamageAmount(Explosion explosion,Entity entity){
                return (float)(super.getEntityDamageAmount(explosion,entity)*maxDamage/(14*radius+1));
            }
        },getX(),getY(),getZ(),radius,false,blocks?Level.ExplosionInteraction.MOB:Level.ExplosionInteraction.NONE);
        for(Entity rider:passengers){
            rider.stopRiding();rider.fallDistance=0;
            if(!rider.onGround())rider.getPersistentData().putBoolean(CommonEvents.FALL,true);
        }
        for(var entry:targets.entrySet()){
            LivingEntity target=entry.getValue();
            if(target!=null && target.isAlive() && entry.getKey().canAttack(target))entry.getKey().setTarget(target);
        }
        discard();
    }
    @Override public void thunderHit(ServerLevel level,LightningBolt bolt) {
        entityData.set(CHARGED,true);
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean hit) {
        super.dropCustomDeathLoot(level,source,hit);
        if(source.getEntity() instanceof AbstractSkeleton skeleton && riderSkeletons.contains(skeleton.getUUID()))
            BuiltInRegistries.ITEM.getTag(ItemTags.CREEPER_DROP_MUSIC_DISCS)
                .flatMap(tag->tag.getRandomElement(random)).ifPresent(item->spawnAtLocation(item.value()));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Charged",isCharged());tag.putInt("Fuse",fuse());
        tag.putBoolean("Initialized",initialized);tag.putBoolean("PassengersCreated",passengersCreated);
        tag.putString("Settings",PhantomConfig.GSON.toJson(settings));
        ListTag skeletons=new ListTag();for(UUID id:riderSkeletons)skeletons.add(StringTag.valueOf(id.toString()));
        tag.put("RiderSkeletons",skeletons);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CHARGED,tag.getBoolean("Charged"));entityData.set(FUSE,Mth.clamp(tag.getInt("Fuse"),0,30));
        initialized=tag.getBoolean("Initialized");passengersCreated=tag.getBoolean("PassengersCreated");
        if(tag.contains("Settings")){
            try{settings=PhantomConfig.parse(tag.getString("Settings"),registryAccess());}
            catch(RuntimeException ex){settings=PhantomConfig.snapshot();}
        }
        riderSkeletons.clear();
        for(Tag id:tag.getList("RiderSkeletons",Tag.TAG_STRING))try{riderSkeletons.add(UUID.fromString(id.getAsString()));}catch(IllegalArgumentException ignored){}
    }
}
