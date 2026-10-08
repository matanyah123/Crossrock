package dev.matanyah.bedrockcompat.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Edition adapters expose client-visible vanilla facts; they never contain pack identifiers. */
final class NativeEntityFacts {
    private NativeEntityFacts() {}
    static void addCommon(net.minecraft.world.entity.Entity entity,float partial,Map<String,Object> facts) {
        facts.put("modified_distance_moved",(double)entity.moveDist);
        facts.put("walk_distance",(double)entity.moveDist);
        facts.put("body_x_rotation",(double)entity.getXRot(partial));
        facts.put("yaw_speed",(double)net.minecraft.util.Mth.wrapDegrees(entity.getYRot()-entity.yRotO)*20);
        facts.put("frame_alpha",(double)partial);
        facts.put("time_of_day",Math.floorMod(entity.level().getDefaultClockTime(),24000)/24000d);
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
        facts.put("is_wall_climbing",entity.onClimbable()?1d:0d);
        facts.put("is_using_item",entity.isUsingItem()?1d:0d);
        facts.put("blocking",entity.isBlocking()?1d:0d);facts.put("death_ticks",(double)entity.deathTime);
        facts.put("main_hand_item_use_duration",entity.getTicksUsingItem(partial)/20d);
        facts.put("item_in_use_duration",entity.getTicksUsingItem(partial)/20d);
        facts.put("is_saddled",entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty()?0d:1d);
        facts.put("has_target",entity instanceof net.minecraft.world.entity.Mob mob&&mob.getTarget()!=null?1d:0d);
        facts.put("is_angry",entity instanceof net.minecraft.world.entity.Mob mob&&mob.isAggressive()?1d:0d);
        Map<String,Object> properties=new HashMap<>();
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
