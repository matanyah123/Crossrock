package dev.matanyah.bedrockcompat.client.entity;

import net.minecraft.client.player.AbstractClientPlayer;
import java.util.*;

/** Client-visible player facts and Bedrock engine variables, independent of any resource pack. */
final class NativePlayerInputs {
    private NativePlayerInputs() {}
    static Map<String,Object> add(AbstractClientPlayer player,float partial,NativeEntityRenderer.View view,Map<String,Object> facts) {
        boolean first=view==NativeEntityRenderer.View.FIRST_PERSON;
        double speed=player.getDeltaMovement().lengthSqr();
        double glide=Math.max(1,Math.pow(speed/.2,3));
        var variables=new HashMap<String,Object>();
        variables.put("is_first_person",first?1d:0d);
        variables.put("is_paperdoll",view==NativeEntityRenderer.View.UI?1d:0d);
        variables.put("attack_time",(double)player.getAttackAnim(partial));
        variables.put("player_x_rotation",facts.getOrDefault("head_x_rotation",(double)player.getXRot(partial)));
        variables.put("swim_amount",(double)player.getSwimAmount(partial));
        variables.put("gliding_speed_value",player.isFallFlying()?glide:1d);
        variables.put("is_brandishing_spear",player.isUsingItem()&&player.getUseItem().getUseAnimation()==net.minecraft.world.item.ItemUseAnimation.SPEAR?1d:0d);
        facts.put("is_first_person",first?1d:0d);facts.put("is_in_ui",view==NativeEntityRenderer.View.UI?1d:0d);
        facts.put("has_cape",player.getSkin().cape()!=null?1d:0d);
        facts.put("is_persona_or_premium_skin",0d); // Java skin services do not have Bedrock persona skins.
        facts.put("is_crawling",player.isVisuallySwimming()&&!player.isInWater()?1d:0d);
        facts.put("is_flying",player.getAbilities().flying?1d:0d);
        facts.put("is_jumping",player.isJumping()?1d:0d);
        facts.put("is_scoping",player.isScoping()?1d:0d);
        facts.put("is_casting",player.fishing!=null?1d:0d);
        facts.put("is_emoting",0d); // Integrations may replace this through the public input API.
        // Bedrock player world queries use the standing origin, 1.62 blocks above
        // Java's feet. Keep this fixed while sneaking; it is not the camera eye height.
        // Only query data changes here, never the model/render transform.
        facts.put("position",List.of(player.getX(),player.getY()+1.62d,player.getZ()));
        facts.put("is_spin_attacking",player.isAutoSpinAttack()?1d:0d);
        facts.put("main_hand_item_max_duration",player.getMainHandItem().getUseDuration(player)/20d);
        facts.put("item_is_charged",player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
            net.minecraft.world.item.component.ChargedProjectiles.EMPTY).isEmpty()?0d:1d);
        facts.put("has_head_gear",player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()?0d:1d);
        facts.put("time_stamp",(double)player.level().getGameTime());
        var direction=player.getDeltaMovement().normalize();
        facts.put("movement_direction",List.of(direction.x,direction.y,direction.z));
        double draw=player.isUsingItem()?player.getTicksUsingItem(partial)/20d:0;
        facts.put("get_animation_frame",player.isUsingItem()&&player.getUseItem().getUseAnimation()==net.minecraft.world.item.ItemUseAnimation.BOW
            ?draw>=.9?2d:draw>=.65?1d:0d:0d);
        NativePlayerAnimationApi.collect(player,partial,view,variables,facts);
        return Map.copyOf(variables);
    }
}
