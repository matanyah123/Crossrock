package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import java.util.*;

/** Client-visible passenger facts; no retained entities and no gameplay changes. */
final class NativeEntityRelations {
    private NativeEntityRelations() {}
    static void add(Entity entity,float partial,Map<String,Object> facts) {
        facts.put("has_rider",entity.isVehicle()?1d:0d);
        facts.put("has_player_rider",entity.getPassengers().stream().anyMatch(p->p instanceof net.minecraft.world.entity.player.Player)?1d:0d);
        List<Map<String,Object>> riders=new ArrayList<>();
        for(Entity rider:entity.getPassengers())riders.add(rotations(rider,partial));
        facts.put("riders",List.copyOf(riders));
        Map<String,Object> vehicle=entity.getVehicle()==null?Map.of():rotations(entity.getVehicle(),partial);
        for(String field:List.of("body_x_rotation","body_y_rotation","head_x_rotation","head_y_rotation"))
            facts.put("ride_"+field,vehicle.getOrDefault(field,0d));
    }
    private static Map<String,Object> rotations(Entity entity,float partial) {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        double body=entity instanceof LivingEntity living?Mth.rotLerp(partial,living.yBodyRotO,living.yBodyRot)
            :BedrockEntityIds.bodyYaw(id,entity.getYRot(partial));
        double head=entity instanceof LivingEntity living?Mth.rotLerp(partial,living.yHeadRotO,living.yHeadRot):body;
        double pitch=BedrockEntityIds.boat(id).isPresent()?0:entity.getXRot(partial);
        return Map.of("body_x_rotation",pitch,"body_y_rotation",body,"head_x_rotation",pitch,"head_y_rotation",head);
    }
}
