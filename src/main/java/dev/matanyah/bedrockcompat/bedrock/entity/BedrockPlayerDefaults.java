package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Engine-owned skin meshes. Pack definitions always override these fallbacks. No pack assets. */
public final class BedrockPlayerDefaults {
    private BedrockPlayerDefaults() {}
    /** Engine poses used when a pack references vanilla clips without supplying its own.
     * Bed direction is applied by the living-renderer frame, not a second time here. */
    public static void animations(Map<String,BedrockAnimation> out) {
        elytraPose(out,"default",4.5,4,15,0,13);
        elytraPose(out,"sneaking",2,3,20,15,45);
        elytraPose(out,"sleeping",5,4,0,0,15);
        elytraPose(out,"gliding",2,2,20,0,90);
        out.putIfAbsent("animation.player.sleeping",new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"override_previous_animation":true,"bones":{
              "head":{"rotation":["30-this","-this","-this"]},
              "root":{"position":[0,0,24],"rotation":[-90,0,0]}}}
            """).getAsJsonObject()));
    }
    private static void elytraPose(Map<String,BedrockAnimation> out,String name,double x,double y,double pitch,double yaw,double roll) {
        JsonObject bones=new JsonObject();JsonObject body=new JsonObject();body.addProperty("scale",1.067);bones.add("body",body);
        for (int sign : new int[]{1,-1}) {
            JsonObject wing=new JsonObject();wing.add("position",array(sign*x,y,-2));
            wing.add("rotation",array(pitch,-sign*yaw,-sign*roll));wing.add("scale",array(1,1,2));
            bones.add(sign==1?"left_wing":"right_wing",wing);
        }
        JsonObject clip=new JsonObject();clip.addProperty("loop",true);clip.add("bones",bones);
        out.putIfAbsent("animation.elytra."+name,new BedrockAnimation(clip));
    }
    public static BedrockGeometry humanoid(boolean slim) {
        JsonObject model=new JsonObject();model.addProperty("texture_width",64);model.addProperty("texture_height",64);
        JsonArray bones=new JsonArray();model.add("bones",bones);
        bone(bones,"root",null,0,0,0,null,null,0,0,0);
        bone(bones,"waist","root",0,12,0,null,null,0,0,0);
        bone(bones,"body","waist",0,24,0,new double[]{-4,12,-2},new double[]{8,12,4},16,16,0);
        bone(bones,"jacket","body",0,24,0,new double[]{-4,12,-2},new double[]{8,12,4},16,32,.25);
        bone(bones,"head","body",0,24,0,new double[]{-4,24,-4},new double[]{8,8,8},0,0,0);
        bone(bones,"hat","head",0,24,0,new double[]{-4,24,-4},new double[]{8,8,8},32,0,.5);
        double width=slim?3:4;
        bone(bones,"rightarm","body",-5,22,0,new double[]{-4-width,12,-2},new double[]{width,12,4},40,16,0);
        bone(bones,"leftarm","body",5,22,0,new double[]{4,12,-2},new double[]{width,12,4},32,48,0);
        bone(bones,"rightsleeve","rightarm",-5,22,0,new double[]{-4-width,12,-2},new double[]{width,12,4},40,32,.25);
        bone(bones,"leftsleeve","leftarm",5,22,0,new double[]{4,12,-2},new double[]{width,12,4},48,48,.25);
        bone(bones,"rightleg","root",-1.9,12,0,new double[]{-3.9,0,-2},new double[]{4,12,4},0,16,0);
        bone(bones,"leftleg","root",1.9,12,0,new double[]{-.1,0,-2},new double[]{4,12,4},16,48,0);
        bone(bones,"rightpants","rightleg",-1.9,12,0,new double[]{-3.9,0,-2},new double[]{4,12,4},0,32,.25);
        bone(bones,"leftpants","leftleg",1.9,12,0,new double[]{-.1,0,-2},new double[]{4,12,4},0,48,.25);
        bone(bones,"rightitem","rightarm",-6,15,1,null,null,0,0,0);
        bone(bones,"leftitem","leftarm",6,15,1,null,null,0,0,0);
        return BedrockGeometry.parse(model);
    }
    private static void bone(JsonArray bones,String name,String parent,double x,double y,double z,
            double[] origin,double[] size,int u,int v,double inflate) {
        JsonObject b=new JsonObject();b.addProperty("name",name);if(parent!=null)b.addProperty("parent",parent);
        b.add("pivot",array(x,y,z));
        if(origin!=null){JsonObject cube=new JsonObject();cube.add("origin",array(origin));cube.add("size",array(size));
            cube.add("uv",array(u,v));cube.addProperty("inflate",inflate);JsonArray cubes=new JsonArray();cubes.add(cube);b.add("cubes",cubes);}
        bones.add(b);
    }
    private static JsonArray array(double... values){JsonArray a=new JsonArray();for(double v:values)a.add(v);return a;}
    public static void geometries(Map<String,BedrockGeometry> out) {
        out.putIfAbsent("geometry.elytra",BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":64,"texture_height":32,"bones":[
              {"name":"body","pivot":[0,24,0]},
              {"name":"left_wing","parent":"body","pivot":[0,24,0],
               "cubes":[{"origin":[-10,0,0],"size":[10,20,2],"uv":[22,0]}]},
              {"name":"right_wing","parent":"body","pivot":[0,24,0],"mirror":true,
               "cubes":[{"origin":[0,0,0],"size":[10,20,2],"uv":[22,0]}]}]}
            """).getAsJsonObject()));
        out.putIfAbsent("geometry.humanoid.custom",humanoid(false));
        out.putIfAbsent("bedrock_compat:humanoid_slim",humanoid(true));
        JsonObject cape=new JsonObject();cape.addProperty("texture_width",64);cape.addProperty("texture_height",32);
        JsonArray bones=new JsonArray();cape.add("bones",bones);
        bone(bones,"cape",null,0,24,2,new double[]{-5,8,2},new double[]{10,16,1},0,0,0);
        out.putIfAbsent("geometry.cape",BedrockGeometry.parse(cape));
    }
    public static void controllers(Map<String,BedrockRenderController> out) {
        controller(out,"controller.render.player.first_person","{\"geometry\":\"Geometry.default\",\"materials\":[{\"*\":\"Material.default\"}],\"textures\":[\"Texture.default\"],\"part_visibility\":[{\"*\":false},{\"rightarm*\":true},{\"rightsleeve*\":true},{\"leftarm*\":true},{\"leftsleeve*\":true}]}");
        controller(out,"controller.render.player.third_person","{\"geometry\":\"Geometry.default\",\"materials\":[{\"*\":\"Material.default\"}],\"textures\":[\"Texture.default\"]}");
        controller(out,"controller.render.player.third_person_spectator","{\"geometry\":\"Geometry.default\",\"materials\":[{\"*\":\"Material.default\"}],\"textures\":[\"Texture.default\"],\"part_visibility\":[{\"*\":false},{\"head*\":true},{\"hat*\":true}]}");
    }
    private static void controller(Map<String,BedrockRenderController> out,String id,String json) {
        out.putIfAbsent(id,new BedrockRenderController(JsonParser.parseString(json).getAsJsonObject()));
    }
}
