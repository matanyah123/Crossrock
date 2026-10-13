package dev.matanyah.bedrockcompat.client.environment;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.environment.BedrockEnvironment;
import dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.attribute.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.*;

/** Client-only layers; registries, server attributes and world data remain untouched. */
public final class NativeEnvironment {
    private static volatile BedrockEnvironment snapshot=BedrockEnvironment.EMPTY;
    private static volatile boolean shaderPack;
    private static boolean warnedShader;
    private NativeEnvironment(){}
    public static BedrockEnvironment snapshot(){return snapshot;}
    public static void register(){
        // No compile/runtime dependency on Iris; unknown Iris API means conservative visual fallback.
        refreshShaderGuard();
        ClientTickEvents.END_CLIENT_TICK.register(mc->refreshShaderGuard());
    }
    private static void refreshShaderGuard(){
        boolean active=false;
        if(FabricLoader.getInstance().isModLoaded("iris"))try {
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance=api.getMethod("getInstance").invoke(null);
            active=(boolean)api.getMethod("isShaderPackInUse").invoke(instance);
        }catch(ReflectiveOperationException|LinkageError ex){active=true;}
        if(active&&!warnedShader){BedrockCompat.LOGGER.info("Bedrock environment visual overrides suspended while Iris shader pack owns rendering");warnedShader=true;}
        if(active!=shaderPack){shaderPack=active;var mc=Minecraft.getInstance();if(mc.level!=null){
            mc.level.clearTintCaches();mc.level.environmentAttributes().invalidateTickCache();
            mc.levelRenderer.invalidateCompiledGeometry(mc.level,mc.options,mc.gameRenderer.mainCamera(),mc.getBlockColors());
        }}
    }
    public static boolean visuals(){return !shaderPack&&!snapshot.empty();}
    public static void reload(ResourceManager manager,List<String> errors){
        List<JsonObject> definitions=new ArrayList<>();
        for(var entry:manager.listResources("environment",id->id.getNamespace().equals("bedrock_native")&&id.getPath().endsWith(".json")).entrySet())
            try(var reader=entry.getValue().openAsReader()){definitions.add(JsonParser.parseReader(reader).getAsJsonObject());}
            catch(Exception ex){errors.add("Environment "+entry.getKey()+": "+ex.getMessage());}
        snapshot=BedrockEnvironment.load(definitions,errors::add);
        if(!snapshot.atmospheres().isEmpty()||!snapshot.lighting().isEmpty())
            BedrockCompat.LOGGER.info("Bedrock VV adapter: sky/horizon approximation only; Java lightmap colors and brightness retained; no scattering, lux, shadows, water optics or tone mapping");
        var mc=Minecraft.getInstance();
        if(mc.level!=null){mc.level.clearTintCaches();mc.level.environmentAttributes().invalidateTickCache();}
    }
    private static String biome(Level level,BlockPos pos){
        var key=level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value());
        return key==null?"":key.toString();
    }
    public static Integer tint(Biome biome,String kind){
        if(!visuals())return null;var level=Minecraft.getInstance().level;if(level==null)return null;
        var id=level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biome);
        return id==null?null:snapshot.tint(id.toString(),kind);
    }
    public static void addLayers(EnvironmentAttributeSystem.Builder builder,Level level){
        if(!level.isClientSide())return;
        colorLayer(builder,level,EnvironmentAttributes.SKY_COLOR,(s,id)->s.sky(id,time(level)));
        colorLayer(builder,level,EnvironmentAttributes.FOG_COLOR,(s,id)->{
            var fog=s.fog(id,"air");return fog!=null&&fog.color()!=null?fog.color():s.horizon(id,time(level));
        });
        colorLayer(builder,level,EnvironmentAttributes.WATER_FOG_COLOR,(s,id)->{var fog=s.fog(id,"water");return fog==null?null:fog.color();});
        distanceLayer(builder,level,EnvironmentAttributes.FOG_START_DISTANCE,"air",false);
        distanceLayer(builder,level,EnvironmentAttributes.FOG_END_DISTANCE,"air",true);
        distanceLayer(builder,level,EnvironmentAttributes.WATER_FOG_START_DISTANCE,"water",false);
        distanceLayer(builder,level,EnvironmentAttributes.WATER_FOG_END_DISTANCE,"water",true);
        // Preserve Java's skylight color too: copying VV sun chromaticity into the
        // lightmap applies an unwanted scene-wide tint without Bedrock exposure/transport.
        // Bedrock ambient.color is chromaticity, with a separate HDR lux strength.
        // Java AMBIENT_LIGHT_COLOR is an absolute lightmap floor, including at sky/block light 0.
        // Mapping white directly makes caves fullbright. Without HDR exposure/lux conversion,
        // retain Java's ambient attribute (including dimension/mod layers), not an invented floor.
        builder.addPositionalLayer(EnvironmentAttributes.MUSIC_VOLUME,(original,pos,interpolator)->{
            if(snapshot.empty())return original;
            double multiplier=blend(level,pos,1,(s,id)->{Double n=BedrockEnvironment.number(s.value(id,"biome_music","volume_multiplier"));return n!=null&&n>=0&&n<=1?n:null;},false);
            return (float)(original*multiplier);
        });
        builder.addPositionalLayer(EnvironmentAttributes.BACKGROUND_MUSIC,(original,pos,interpolator)->{
            String name=BedrockEnvironment.string(snapshot.value(biome(level,BlockPos.containing(pos)),"biome_music","music_definition"));
            return "".equals(name)?BackgroundMusic.EMPTY:original;
        });
        builder.addPositionalLayer(EnvironmentAttributes.AMBIENT_SOUNDS,(original,pos,interpolator)->{
            String loop=BedrockEnvironment.string(snapshot.value(biome(level,BlockPos.containing(pos)),"ambient_sounds","loop"));
            if(loop==null)return original;
            var id=NativeSoundEvents.id(loop);
            if(Minecraft.getInstance().getSoundManager().getSoundEvent(id)==null)return original;
            return new AmbientSounds(Optional.of(Holder.direct(SoundEvent.createVariableRangeEvent(id))),original.mood(),original.additions());
        });
    }
    private static double time(Level level){return BedrockEnvironment.vvTime(level.getDefaultClockTime());}
    private static void colorLayer(EnvironmentAttributeSystem.Builder builder,Level level,EnvironmentAttribute<Integer> attribute,BiFunction<BedrockEnvironment,String,Integer> sample){
        builder.addPositionalLayer(attribute,(original,pos,interpolator)->!visuals()?original:(int)blend(level,pos,original,(s,id)->{Integer c=sample.apply(s,id);return c==null?null:c.doubleValue();},true));
    }
    private static void distanceLayer(EnvironmentAttributeSystem.Builder builder,Level level,EnvironmentAttribute<Float> attribute,String medium,boolean ending){
        builder.addPositionalLayer(attribute,(original,pos,interpolator)->!visuals()?original:(float)blend(level,pos,original,(s,id)->{
            var fog=s.fog(id,medium);return fog==null?null:fog.distance(ending,Minecraft.getInstance().options.getEffectiveRenderDistance()*16.0);
        },false));
    }
    /** Four-block trilinear spatial interpolation. Missing samples retain the original Java attribute. */
    private static double blend(Level level,Vec3 pos,double fallback,BiFunction<BedrockEnvironment,String,Double> sample,boolean color){
        int x=(int)Math.floor(pos.x/4),y=(int)Math.floor(pos.y/4),z=(int)Math.floor(pos.z/4);
        double fx=pos.x/4-x,fy=pos.y/4-y,fz=pos.z/4-z,result=0,r=0,g=0,b=0;var state=snapshot;
        for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)for(int dz=0;dz<2;dz++){
            double weight=(dx==0?1-fx:fx)*(dy==0?1-fy:fy)*(dz==0?1-fz:fz);
            if(weight==0)continue;
            Double value=sample.apply(state,biome(level,new BlockPos((x+dx)*4,(y+dy)*4,(z+dz)*4)));
            double v=value==null?fallback:value;
            if(color){int c=(int)v;r+=((c>>16)&255)*weight;g+=((c>>8)&255)*weight;b+=(c&255)*weight;}else result+=v*weight;
        }
        // Color definitions contain RGB only. Keep the original Java attribute's alpha.
        return color?(int)(((long)fallback & 0xff000000L)|(Math.round(r)<<16)|(Math.round(g)<<8)|Math.round(b)):result;
    }
}
