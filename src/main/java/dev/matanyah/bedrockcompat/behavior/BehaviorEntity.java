package dev.matanyah.bedrockcompat.behavior;

import com.google.gson.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
import net.minecraft.world.level.storage.*;

/** Permanent host for world-scoped Bedrock identities. No gameplay goals are invented. */
public final class BehaviorEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ID=SynchedEntityData.defineId(BehaviorEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> STATE=SynchedEntityData.defineId(BehaviorEntity.class,EntityDataSerializers.STRING);
    private JsonObject properties=new JsonObject();
    private JsonObject dynamic=new JsonObject();
    public BehaviorEntity(EntityType<? extends BehaviorEntity> type,Level level){super(type,level);setPersistenceRequired();setNoAi(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ID,"");builder.define(STATE,"{\"version\":1,\"generation\":\"\",\"properties\":{}}");}
    public String bedrockIdentifier(){return entityData.get(ID);}
    public JsonObject clientProperties(){var state=JsonParser.parseString(entityData.get(STATE)).getAsJsonObject();return state.has("properties")?state.getAsJsonObject("properties"):new JsonObject();}
    public String stateGeneration(){var state=JsonParser.parseString(entityData.get(STATE)).getAsJsonObject();return state.has("generation")?state.get("generation").getAsString():"";}
    public void initialize(String id,BehaviorDefinitions definitions){entityData.set(ID,id);properties=definitions.defaults(id);publish(definitions);}
    public void property(BehaviorDefinitions definitions,String key,JsonElement value){definitions.validate(bedrockIdentifier(),key,value);properties.add(key,value.deepCopy());publish(definitions);}
    public void reconcile(BehaviorDefinitions definitions){
        if(definitions.find("minecraft:entity",bedrockIdentifier()).isEmpty()){publish(definitions);return;}
        var defaults=definitions.defaults(bedrockIdentifier());
        for(var e:properties.entrySet())try{definitions.validate(bedrockIdentifier(),e.getKey(),e.getValue());defaults.add(e.getKey(),e.getValue().deepCopy());}catch(IllegalArgumentException ignored){}
        properties=defaults;publish(definitions);
    }
    private void publish(BehaviorDefinitions definitions){JsonObject envelope=new JsonObject();envelope.addProperty("version",1);envelope.addProperty("generation",level() instanceof net.minecraft.server.level.ServerLevel server?BehaviorRuntime.get(server.getServer()).session().toString():"");envelope.add("properties",definitions.clientProperties(bedrockIdentifier(),properties));String state=envelope.toString();if(state.length()>16384)throw new IllegalArgumentException("Actor state exceeds synchronization limit");entityData.set(STATE,state);}
    public JsonObject dynamic(){return dynamic.deepCopy();}
    public void dynamic(String key,JsonElement value){if(value==null)dynamic.remove(key);else dynamic.add(key,value.deepCopy());}
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putString("crossrock:identifier",bedrockIdentifier());out.putString("crossrock:properties",properties.toString());out.putString("crossrock:dynamic",dynamic.toString());}
    @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);entityData.set(ID,in.getStringOr("crossrock:identifier",""));properties=JsonParser.parseString(in.getStringOr("crossrock:properties","{}")).getAsJsonObject();dynamic=JsonParser.parseString(in.getStringOr("crossrock:dynamic","{}")).getAsJsonObject();if(level() instanceof net.minecraft.server.level.ServerLevel server)reconcile(BehaviorRuntime.get(server.getServer()).profile().definitions());}
}
