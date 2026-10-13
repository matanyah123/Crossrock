package dev.matanyah.bedrockcompat.bedrock.environment;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockBiomes;
import java.util.*;
import java.util.function.Consumer;

/** Immutable, renderer-independent environment state. Absent values always mean Java fallback. */
public record BedrockEnvironment(Map<String,JsonObject> biomes,Map<String,JsonObject> legacy,
                                 Map<String,JsonObject> fogs,Map<String,JsonObject> atmospheres,
                                 Map<String,JsonObject> lighting) {
    public static final BedrockEnvironment EMPTY=new BedrockEnvironment(Map.of(),Map.of(),Map.of(),Map.of(),Map.of());
    public static BedrockEnvironment load(Collection<JsonObject> envelopes,Consumer<String> errors) {
        Map<String,JsonObject> biomes=new HashMap<>(),legacy=new HashMap<>(),fogs=new HashMap<>(),atmospheres=new HashMap<>(),lighting=new HashMap<>();
        for(var envelope:envelopes)try {
            String type=envelope.get("type").getAsString(),id=envelope.get("id").getAsString();
            var definition=envelope.getAsJsonObject("definition").deepCopy();
            switch(type) {
                case "client_biome"->{var components=definition.getAsJsonObject("components");if(components==null)throw new IllegalArgumentException("Missing client-biome components");biomes.put(id,components);}
                case "legacy"->legacy.put(id,definition);
                case "fog_settings"->fogs.put(id,definition);
                case "atmosphere_settings"->{atmospheres.put(id,definition);if("atmospherics/atmospherics.json".equals(string(envelope.get("source"))))atmospheres.put("@global",definition);}
                case "lighting_settings"->{lighting.put(id,definition);if("lighting/global.json".equals(string(envelope.get("source"))))lighting.put("@global",definition);}
                default->throw new IllegalArgumentException("Unknown environment type "+type);
            }
        }catch(RuntimeException ex){errors.accept(envelope+": "+ex.getMessage());}
        return new BedrockEnvironment(Map.copyOf(biomes),Map.copyOf(legacy),Map.copyOf(fogs),Map.copyOf(atmospheres),Map.copyOf(lighting));
    }
    public boolean empty(){return biomes.isEmpty()&&legacy.isEmpty()&&fogs.isEmpty()&&atmospheres.isEmpty()&&lighting.isEmpty();}
    public JsonObject component(String biome,String name) {return object(biomes.get(BedrockBiomes.identifier(biome)),"minecraft:"+name);}
    public JsonElement value(String biome,String name,String field) {var c=component(biome,name);return c==null?null:c.get(field);}
    public Integer tint(String biome,String kind) {
        JsonElement value=value(biome,kind.equals("water")?"water_appearance":kind.equals("dry_foliage")?"dry_foliage_color":kind+"_appearance",
            kind.equals("water")?"surface_color":"color");
        if(value==null) {var old=legacy.get(BedrockBiomes.identifier(biome));value=old==null?null:old.get(kind+"_color");}
        return color(value);
    }
    public Integer sky(String biome,double time) {
        Integer direct=color(value(biome,"sky_color","sky_color"));
        if(direct!=null)return direct;
        var old=legacy.get(BedrockBiomes.identifier(biome));
        direct=color(old==null?null:old.get("sky_color"));
        if(direct!=null)return direct;
        var atmosphere=referenced(biome,"atmosphere_identifier",atmospheres,"@global");
        return atmosphere==null?null:colorAt(atmosphere.get("sky_zenith_color"),time);
    }
    public Integer horizon(String biome,double time) {
        var atmosphere=referenced(biome,"atmosphere_identifier",atmospheres,"@global");
        return atmosphere==null?null:colorAt(atmosphere.get("sky_horizon_color"),time);
    }
    public JsonObject referenced(String biome,String component,Map<String,JsonObject> definitions,String fallback) {
        var c=component(biome,component);
        String id=c==null?null:string(c.get("identifier"));
        // Identifier components use their component name as the field (not "identifier").
        if(id==null&&c!=null)id=string(c.get(component));
        return id==null?definitions.get(fallback):definitions.get(id);
    }
    public Fog fog(String biome,String medium) {
        String id=string(value(biome,"fog_appearance","fog_identifier"));
        var old=legacy.get(BedrockBiomes.identifier(biome));
        if(id==null&&old!=null)id=string(old.get("fog_identifier"));
        Fog selected=fogById(id,medium);
        var defaults=legacy.get("minecraft:default");
        Fog base=fogById(defaults==null?null:string(defaults.get("fog_identifier")),medium);
        return selected==null?base:selected.withFallback(base);
    }
    private Fog fogById(String id,String medium) {
        var definition=id==null?null:fogs.get(id);var distance=object(definition,"distance");var data=object(distance,medium);
        return data==null?null:Fog.parse(data);
    }
    public record Fog(Double start,Double end,Integer color,boolean renderDistance) {
        static Fog parse(JsonObject json) {
            Double start=number(json.get("fog_start")),end=number(json.get("fog_end"));
            if(start!=null&&start<0||end!=null&&end<0||start!=null&&end!=null&&end<start)return null;
            String type=string(json.get("render_distance_type"));
            if(type!=null&&!type.equals("render")&&!type.equals("fixed"))return null;
            return new Fog(start,end,BedrockEnvironment.color(json.get("fog_color")),"render".equals(type));
        }
        Fog withFallback(Fog other) {
            // Distances share a unit; don't mix incomplete authored distance pairs with another unit system.
            return other==null?this:new Fog(start,end,color==null?other.color:color,renderDistance);
        }
        public Double distance(boolean ending,double viewBlocks){
            Double v=ending?end:start;if(v==null)return null;double blocks=v*(renderDistance?viewBlocks:1);
            return Double.isFinite(blocks)&&blocks>=0&&blocks<=Float.MAX_VALUE?blocks:null;
        }
    }
    public static JsonObject object(JsonObject parent,String name){return parent!=null&&parent.has(name)&&parent.get(name).isJsonObject()?parent.getAsJsonObject(name):null;}
    public static String string(JsonElement value){return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?value.getAsString():null;}
    public static Double number(JsonElement value){try{double n=value.getAsDouble();return Double.isFinite(n)?n:null;}catch(Exception ex){return null;}}
    public static Integer color(JsonElement value) {
        try {
            if(value==null)return null;
            if(value.isJsonPrimitive()){String s=value.getAsString();return s.matches("#[0-9a-fA-F]{6}")?Integer.parseInt(s.substring(1),16):null;}
            if(!value.isJsonArray()||value.getAsJsonArray().size()!=3)return null;
            int rgb=0;for(var channel:value.getAsJsonArray()){Double n=number(channel);if(n==null||n<0||n>255)return null;rgb=rgb<<8|(int)Math.round(n);}return rgb;
        }catch(RuntimeException ex){return null;}
    }
    /** VV frame zero is noon, not Java midnight. Cyclic interpolation is a deliberate adapter approximation. */
    public static double vvTime(long javaTime){return Math.floorMod(javaTime-6000,24000)/24000.0;}
    public static Integer colorAt(JsonElement value,double time) {
        Integer constant=color(value);if(constant!=null)return constant;
        if(value==null||!value.isJsonObject())return null;
        TreeMap<Double,Integer> frames=new TreeMap<>();
        for(var e:value.getAsJsonObject().entrySet())try{double t=Double.parseDouble(e.getKey());Integer c=color(e.getValue());if(t>=0&&t<=1&&c!=null)frames.put(t,c);}catch(NumberFormatException ignored){}
        if(frames.isEmpty())return null;if(frames.size()==1)return frames.firstEntry().getValue();
        time=time-Math.floor(time);var a=frames.floorEntry(time);var b=frames.ceilingEntry(time);
        double at=a==null?frames.lastKey()-1:a.getKey(),bt=b==null?frames.firstKey()+1:b.getKey();
        int ac=a==null?frames.lastEntry().getValue():a.getValue(),bc=b==null?frames.firstEntry().getValue():b.getValue();
        return mixColor(ac,bc,bt==at?0:(time-at)/(bt-at));
    }
    public static int mixColor(int a,int b,double weight) {
        int result=0;for(int shift:List.of(16,8,0))result|=(int)Math.round(((a>>shift)&255)*(1-weight)+((b>>shift)&255)*weight)<<shift;return result;
    }
}
