package dev.matanyah.bedrockcompat.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Edition adapters expose client-visible vanilla facts; they never contain pack identifiers. */
final class NativeEntityFacts {
    private static final Map<net.minecraft.world.entity.Entity,dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs> MOTION=new WeakHashMap<>();
    private NativeEntityFacts() {}
    static void addCommon(net.minecraft.world.entity.Entity entity,float partial,Map<String,Object> facts) {
        var position=entity.getPosition(partial);
        var motion=MOTION.computeIfAbsent(entity,key->new dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs())
            .sample((entity.tickCount+(double)partial)/20d,position.x,position.y,position.z,entity.onGround()&&!entity.isShiftKeyDown());
        facts.put("modified_distance_moved",motion.distance());
        facts.put("walk_distance",motion.walkDistance());
        facts.put("ground_speed",motion.groundSpeed());facts.put("vertical_speed",motion.verticalSpeed());
        facts.put("position_delta",List.of(motion.dx(),motion.dy(),motion.dz()));
        facts.put("body_x_rotation",(double)entity.getXRot(partial));
        facts.put("yaw_speed",(double)net.minecraft.util.Mth.wrapDegrees(entity.getYRot()-entity.yRotO)*20);
        facts.put("frame_alpha",(double)partial);
        facts.put("day",dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs.day(entity.level().getDefaultClockTime()));
        facts.put("moon_phase",dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs.moonPhase(entity.level().getDefaultClockTime()));
        facts.put("moon_brightness",dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs.moonBrightness(entity.level().getDefaultClockTime()));
        facts.put("cardinal_facing",entity instanceof net.minecraft.world.entity.player.Player?(double)entity.getNearestViewDirection().get3DDataValue():6d);
        facts.put("cardinal_player_facing",facts.get("cardinal_facing"));
        facts.put("cardinal_facing_2d",entity instanceof net.minecraft.world.entity.player.Player?(double)entity.getDirection().get3DDataValue():6d);
        facts.put("client_max_render_distance",(double)Minecraft.getInstance().options.getEffectiveRenderDistance());
        facts.put("has_gravity",entity.isNoGravity()?0d:1d);
        facts.put("is_silent",entity.isSilent()?1d:0d);
        facts.put("is_fire_immune",entity.fireImmune()?1d:0d);
        facts.put("is_in_water_or_rain",entity.isInWaterOrRain()?1d:0d);
        facts.put("head_is_in_water",entity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)?1d:0d);
        facts.put("is_onfire",entity.isOnFire()?1d:0d);
        facts.put("is_moving",motion.groundSpeed()!=0||motion.verticalSpeed()!=0?1d:0d);
        var direction=new net.minecraft.world.phys.Vec3(motion.dx(),motion.dy(),motion.dz()).normalize();
        facts.put("movement_direction",List.of(direction.x,direction.y,direction.z));
        facts.put("is_dancing",entity instanceof net.minecraft.world.entity.animal.allay.Allay allay&&allay.isDancing()
            ||entity instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot&&parrot.isPartyParrot()?1d:0d);
        facts.put("is_carrying_block",entity instanceof net.minecraft.world.entity.monster.EnderMan enderman&&enderman.getCarriedBlock()!=null?1d:0d);
        facts.put("is_chested",entity instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse horse&&horse.hasChest()?1d:0d);
        facts.put("is_ignited",entity instanceof net.minecraft.world.entity.monster.Creeper creeper&&creeper.isIgnited()?1d:0d);
        facts.put("is_resting",entity instanceof net.minecraft.world.entity.ambient.Bat bat&&bat.isResting()?1d:0d);
        facts.put("is_playing_dead",entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl&&axolotl.isPlayingDead()?1d:0d);
        facts.put("is_elder",entity instanceof net.minecraft.world.entity.monster.ElderGuardian?1d:0d);
        facts.put("is_pregnant",entity instanceof net.minecraft.world.entity.animal.turtle.Turtle turtle&&turtle.hasEgg()?1d:0d);
        facts.put("is_laying_egg",entity instanceof net.minecraft.world.entity.animal.turtle.Turtle turtle&&turtle.isLayingEgg()?1d:0d);
        facts.put("is_interested",entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf&&wolf.isInterested()
            ||entity instanceof net.minecraft.world.entity.animal.fox.Fox fox&&fox.isInterested()?1d:0d);
        facts.put("is_stalking",entity instanceof net.minecraft.world.entity.animal.fox.Fox fox&&fox.isCrouching()?1d:0d);
        facts.put("is_searching",entity instanceof net.minecraft.world.entity.animal.sniffer.Sniffer sniffer&&sniffer.isSearching()?1d:0d);
        facts.put("is_croaking",entity.hasPose(net.minecraft.world.entity.Pose.CROAKING)?1d:0d);
        facts.put("is_emerging",entity.hasPose(net.minecraft.world.entity.Pose.EMERGING)?1d:0d);
        facts.put("is_digging",entity.hasPose(net.minecraft.world.entity.Pose.DIGGING)?1d:0d);
        facts.put("is_roaring",entity.hasPose(net.minecraft.world.entity.Pose.ROARING)?1d:0d);
        facts.put("is_sniffing",entity.hasPose(net.minecraft.world.entity.Pose.SNIFFING)?1d:0d);
        facts.put("is_eating",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda&&panda.isEating()?1d:0d);
        facts.put("is_rolling",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda&&panda.isRolling()?1d:0d);
        facts.put("is_scared",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda&&panda.isScared()?1d:0d);
        facts.put("is_sneezing",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda&&panda.isSneezing()?1d:0d);
        facts.put("sneeze_counter",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda?(double)panda.getSneezeCounter():0d);
        facts.put("sit_amount",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda?(double)panda.getSitAmount(partial):0d);
        facts.put("lie_amount",entity instanceof net.minecraft.world.entity.animal.panda.Panda panda?(double)panda.getLieOnBackAmount(partial):0d);
        facts.put("current_squish_value",entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube?(double)cube.squish:0d);
        facts.put("previous_squish_value",entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube?(double)cube.oSquish:0d);
        facts.put("player_level",entity instanceof net.minecraft.world.entity.player.Player player?(double)player.experienceLevel:0d);
        facts.put("time_of_day",dev.matanyah.bedrockcompat.bedrock.entity.BedrockMotionInputs.timeOfDay(entity.level().getDefaultClockTime()));
        // Transition durations use elapsed ticks, never the settable/stoppable sky clock.
        facts.put("time_stamp",(double)entity.level().getGameTime());
        facts.put("is_swimming",entity.isSwimming()?1d:0d);
        facts.put("is_sprinting",entity.isSprinting()?1d:0d);
        facts.put("is_in_lava",entity.isInLava()?1d:0d);
        facts.put("is_in_contact_with_water",entity.isInWaterOrRain()?1d:0d);
        facts.put("is_invisible",entity.isInvisible()?1d:0d);
        facts.put("is_local_player",entity==Minecraft.getInstance().player?1d:0d);
        facts.put("is_spectator",entity.isSpectator()?1d:0d);
        facts.put("is_sitting",entity.hasPose(net.minecraft.world.entity.Pose.SITTING)?1d:0d);
        facts.put("is_sneaking",entity.isShiftKeyDown()||entity.isCrouching()?1d:0d);
        if(entity instanceof net.minecraft.world.entity.TamableAnimal pet)
            facts.put("is_sitting",pet.isInSittingPose()?1d:0d);
        if(entity instanceof net.minecraft.world.entity.animal.fox.Fox fox)
            facts.put("is_sitting",fox.isSitting()?1d:0d);
        if(entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity vehicle) {
            // Bedrock pre-animation scripts subtract frame_alpha themselves. Expose
            // the integer countdown, not seconds or an already interpolated value.
            facts.put("hurt_time",(double)vehicle.getHurtTime());
            facts.put("hurt_direction",(double)vehicle.getHurtDir());
            facts.put("structural_integrity",40d-vehicle.getDamage());
        }
    }
    static void add(LivingEntity entity,LivingEntityRenderState state,float partial,Map<String,Object> facts) {
        facts.put("modified_move_speed",(double)state.walkAnimationSpeed);
        facts.put("target_x_rotation",(double)state.xRot);facts.put("target_y_rotation",(double)state.yRot);
        facts.put("is_gliding",entity.isFallFlying()?1d:0d);
        facts.put("model_scale",(double)entity.getScale());
        facts.put("swim_amount",(double)entity.getSwimAmount(partial));
        facts.put("is_levitating",entity.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)?1d:0d);
        facts.put("has_head_gear",entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()?0d:1d);
        facts.put("is_wall_climbing",entity.onClimbable()?1d:0d);
        facts.put("is_using_item",entity.isUsingItem()?1d:0d);
        facts.put("blocking",entity.isBlocking()?1d:0d);facts.put("death_ticks",(double)entity.deathTime);
        facts.put("main_hand_item_use_duration",entity.getTicksUsingItem(partial)/20d);
        facts.put("item_in_use_duration",entity.getTicksUsingItem(partial)/20d);
        facts.put("is_saddled",entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty()?0d:1d);
        facts.put("has_target",entity instanceof net.minecraft.world.entity.Mob mob&&mob.getTarget()!=null?1d:0d);
        facts.put("is_angry",entity instanceof net.minecraft.world.entity.Mob mob&&mob.isAggressive()?1d:0d);
        if(entity instanceof net.minecraft.world.entity.monster.cubemob.Slime slime)
            facts.put("variant",(double)slime.getSize());
        if(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither) {
            facts.put("invulnerable_ticks",(double)wither.getInvulnerableTicks());
            facts.put("is_shield_powered",wither.isPowered()?1d:0d);
        }
        Map<String,Object> properties=new HashMap<>();
        facts.put("is_leashed",entity instanceof net.minecraft.world.entity.Mob mob&&mob.isLeashed()?1d:0d);
        if(entity instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube cube) {
            facts.put("is_baby",cube.isBaby()||cube.isTiny()?1d:0d);
            // Java synchronizes the swallowed item as BODY equipment. Bedrock exposes
            // it as the main-hand item and uses an archetype property to hide the core.
            var item=cube.getBodyArmorItem();
            var archetypes=cube.matchingArchetypes(item);
            var registry=cube.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.SULFUR_CUBE_ARCHETYPE);
            properties.put("minecraft:sulfur_cube_archetype",item.isEmpty()?"none":
                archetypes.isEmpty()?"regular":registry.getKey(archetypes.getFirst()).getPath());
            facts.put("fuse_time",cube.isPrimed()?(cube.getFuse()+1-partial)/20d:0d);
        }
        if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
            // Use the registry key, not its numeric ID: Bedrock orders coats differently.
            String identifier=cat.getVariant().unwrapKey().map(key->key.identifier().toString()).orElseThrow();
            try {facts.put("variant",(double)dev.matanyah.bedrockcompat.bedrock.entity.BedrockCatVariants.variant(identifier));}
            catch(IllegalArgumentException unsupported){NativeEntityResources.runtimeError(unsupported.getMessage());}
            facts.put("is_laying_down",cat.isLying()?1d:0d);
        }
        if(entity instanceof net.minecraft.world.entity.animal.cow.Cow cow)
            properties.put("minecraft:climate_variant",variant(cow.getVariant()));
        if(entity instanceof net.minecraft.world.entity.animal.pig.Pig pig)
            properties.put("minecraft:climate_variant",variant(pig.getVariant()));
        if(entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken)
            properties.put("minecraft:climate_variant",variant(chicken.getVariant()));
        if(entity instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            facts.put("swell_amount",(double)creeper.getSwelling(partial));facts.put("swelling_dir",(double)creeper.getSwellDir());
            facts.put("is_powered",creeper.isPowered()?1d:0d);
        }
        if(entity instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) {
            facts.put("is_sheared",sheep.isSheared()?1d:0d);facts.put("variant",(double)sheep.getColor().getId());
            facts.put("is_grazing",sheep.getHeadEatPositionScale(partial)>0?1d:0d);
        }
        if(entity instanceof net.minecraft.world.entity.npc.villager.VillagerDataHolder villager) {
            var data=villager.getVillagerData();
            try {
                facts.put("variant",(double)dev.matanyah.bedrockcompat.bedrock.entity.BedrockVillagerVariants.profession(variant(data.profession())));
                facts.put("mark_variant",(double)dev.matanyah.bedrockcompat.bedrock.entity.BedrockVillagerVariants.biome(variant(data.type())));
            }catch(IllegalArgumentException unsupported){NativeEntityResources.runtimeError(unsupported.getMessage());}
            facts.put("trade_tier",(double)Math.clamp(data.level()-1,0,4));
            // Java does not synchronize Bedrock's six random skin choices. Reconstruct a
            // deterministic choice from UUID so clients and reloads agree without pack hacks.
            facts.put("skin_id",(double)Math.floorMod(entity.getUUID().hashCode(),6));
        }
        facts.put("properties",Map.copyOf(properties));
    }
    private static String variant(net.minecraft.core.Holder<?> holder) {
        return holder.unwrapKey().map(key->key.identifier().getPath()).orElseThrow();
    }
}
