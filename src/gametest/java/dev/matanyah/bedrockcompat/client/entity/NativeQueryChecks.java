package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.bedrock.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.*;
import java.util.*;

/** Real client-state adapters exercised inside an integrated-server world; not a Bedrock visual oracle. */
public final class NativeQueryChecks {
    private NativeQueryChecks() {}
    public static void verify(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var stand=fixture(EntityTypes.ARMOR_STAND,mc.level);
            if(stand==null)throw new AssertionError("Missing query fixture");
            for(String name:List.of("is_silent","is_fire_immune","is_in_water_or_rain","head_is_in_water","is_onfire","is_moving",
                "is_dancing","is_carrying_block","is_chested","is_ignited","is_resting","is_playing_dead","is_elder",
                "is_pregnant","is_laying_egg","is_interested","is_stalking","is_searching","is_croaking","is_emerging",
                "is_digging","is_roaring","is_sniffing","is_eating","is_rolling","is_scared","is_sneezing",
                "sneeze_counter","sit_amount","lie_amount","current_squish_value","previous_squish_value","player_level"))assertQuery(stand,name,0);
            assertQuery(stand,"has_gravity",1);stand.setNoGravity(true);assertQuery(stand,"has_gravity",0);
            stand.setSilent(true);assertQuery(stand,"is_silent",1);
            stand.setRemainingFireTicks(100);assertQuery(stand,"is_onfire",1);
            assertQuery(stand,"day",BedrockMotionInputs.day(mc.level.getDefaultClockTime()));
            assertQuery(stand,"moon_phase",BedrockMotionInputs.moonPhase(mc.level.getDefaultClockTime()));
            assertQuery(stand,"moon_brightness",BedrockMotionInputs.moonBrightness(mc.level.getDefaultClockTime()));
            assertQuery(stand,"client_max_render_distance",mc.options.getEffectiveRenderDistance());
            assertQuery(stand,"cardinal_facing",6);assertQuery(stand,"cardinal_facing_2d",6);assertQuery(stand,"cardinal_player_facing",6);
            stand.tickCount++;stand.setPos(1,2,3);assertQuery(stand,"is_moving",1);
            try(var registration=NativeMolangQueries.register(actor->(name,args)->
                    name.equals("property")&&args.equals(List.of("fixture:state"))?Optional.of("sleeping"):Optional.empty())) {
                var q=new EntityQueryContext("","",Set.of(),Map.of("query_provider",NativeMolangQueries.snapshot(stand)));
                if(!Molang.compile("q.property('fixture:state')").evaluate(new Molang.Context(q)).equals("sleeping"))throw new AssertionError("External property adapter");
            }catch(Exception error){throw new AssertionError(error);}
            if(NativeMolangQueries.snapshot(stand).query("property",List.of("fixture:state")).isPresent())throw new AssertionError("Integration registration leaked");
            var allay=fixture(EntityTypes.ALLAY,mc.level);
            // The public setter is server-only. Exercise the same synchronized field received by clients.
            dancing(allay,true);assertQuery(allay,"is_dancing",1);dancing(allay,false);assertQuery(allay,"is_dancing",0);
            var creeper=fixture(EntityTypes.CREEPER,mc.level);
            creeper.ignite();assertQuery(creeper,"is_ignited",1);
            var enderman=fixture(EntityTypes.ENDERMAN,mc.level);
            enderman.setCarriedBlock(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());assertQuery(enderman,"is_carrying_block",1);
            var bat=fixture(EntityTypes.BAT,mc.level);
            bat.setResting(true);assertQuery(bat,"is_resting",1);bat.setResting(false);assertQuery(bat,"is_resting",0);
            var slime=fixture(EntityTypes.SLIME,mc.level);
            slime.squish=.35f;slime.oSquish=-.2f;assertQuery(slime,"current_squish_value",.35f);assertQuery(slime,"previous_squish_value",-.2f);
            var elder=fixture(EntityTypes.ELDER_GUARDIAN,mc.level);assertQuery(elder,"is_elder",1);
            var warden=fixture(EntityTypes.WARDEN,mc.level);
            for(var state:Map.of("is_roaring",Pose.ROARING,"is_emerging",Pose.EMERGING,"is_digging",Pose.DIGGING).entrySet()) {
                warden.setPose(state.getValue());assertQuery(warden,state.getKey(),1);
            }
            var frog=fixture(EntityTypes.FROG,mc.level);frog.setPose(Pose.CROAKING);assertQuery(frog,"is_croaking",1);
            var sniffer=fixture(EntityTypes.SNIFFER,mc.level);sniffer.setPose(Pose.SNIFFING);assertQuery(sniffer,"is_sniffing",1);
            var panda=fixture(EntityTypes.PANDA,mc.level);panda.setSneezeCounter(8);assertQuery(panda,"sneeze_counter",8);
            var player=mc.player;float yaw=player.getYRot(),pitch=player.getXRot();int level=player.experienceLevel;
            try {
                player.experienceLevel=7;assertQuery(player,"player_level",7);
                for(var facing:Map.of(0f,3d,90f,4d,180f,2d,270f,5d).entrySet()) {
                    player.setYRot(facing.getKey());player.setXRot(0);assertQuery(player,"cardinal_facing_2d",facing.getValue());
                    assertQuery(player,"cardinal_facing",facing.getValue());assertQuery(player,"cardinal_player_facing",facing.getValue());
                }
                player.setXRot(90);assertQuery(player,"cardinal_facing",0);player.setXRot(-90);assertQuery(player,"cardinal_facing",1);
                var state=(net.minecraft.client.renderer.entity.state.LivingEntityRenderState)mc.getEntityRenderDispatcher().getRenderer(player).createRenderState(player,1);
                var facts=new HashMap<String,Object>();NativeEntityFacts.add(player,state,1,facts);
                if(!Objects.equals(facts.get("model_scale"),(double)player.getScale()))throw new AssertionError("Model scale adapter");
                if(!Objects.equals(facts.get("swim_amount"),(double)player.getSwimAmount(1)))throw new AssertionError("Swim blend adapter");
                if(!Objects.equals(facts.get("is_levitating"),0d)||!Objects.equals(facts.get("has_head_gear"),0d))throw new AssertionError("Living state baseline");
            } finally {player.setYRot(yaw);player.setXRot(pitch);player.experienceLevel=level;}
        });
    }
    private static int fixtureId=-10000;
    @SuppressWarnings("unchecked")
    private static void dancing(net.minecraft.world.entity.animal.allay.Allay allay,boolean value) {
        try {
            var field=allay.getClass().getDeclaredField("DATA_DANCING");field.setAccessible(true);
            allay.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Boolean>)field.get(null),value);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static <T extends Entity> T fixture(EntityType<T> type,net.minecraft.world.level.Level level) {
        T entity=type.create(level,EntitySpawnReason.COMMAND);
        if(entity==null)throw new AssertionError("Missing fixture "+type);
        entity.setId(fixtureId--);return entity;
    }
    private static void assertQuery(Entity entity,String query,double expected) {
        var facts=new HashMap<String,Object>();NativeEntityFacts.addCommon(entity,1,facts);
        Object value=Molang.compile("q."+query).evaluate(new Molang.Context(new EntityQueryContext("","",Set.of(),facts)));
        if(!(value instanceof Number number)||Math.abs(number.doubleValue()-expected)>1e-7)
            throw new AssertionError(query+": expected "+expected+", got "+value);
    }
}
