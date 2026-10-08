package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Converts Bedrock geometry (including modern geo.json files) to EMF/OptiFine JEM. */
public final class BedrockEntityModelTranslator implements BedrockResourceTranslator {
	private static final Set<String> KNOWN = Set.of("allay","axolotl","bat","bee","blaze","camel","cat","chicken","cow","creeper","dolphin","enderman","endermite","evoker","fox","frog","ghast","glow_squid","goat","guardian","hoglin","horse","iron_golem","llama","mooshroom","ocelot","panda","parrot","pig","piglin","polar_bear","rabbit","ravager","sheep","shulker","silverfish","skeleton","slime","sniffer","spider","squid","strider","tadpole","villager","vex","warden","witch","wither","wolf","zoglin","zombie","zombie_villager","armor_stand","bed","player","item","minecart","boat","fishing_bobber","tnt","arrow","trident","fireball","dragon","wither_skull");
	private final BedrockPackContentReader reader;
	public BedrockEntityModelTranslator(BedrockPackContentReader reader) { this.reader = reader; }

	@Override public void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		reader.visitAll(pack, configuration.selectedSubpack(), (path, data) -> {
			String p = path.replace('\\','/').toLowerCase(Locale.ROOT);
			if (p.equals("models/mobs.json") || (p.startsWith("models/entity/") && p.endsWith(".geo.json")) || (p.startsWith("entity/") && p.endsWith(".json"))) files.put(path, data);
		});
		Map<String,String> owners = new HashMap<>(); List<Geometry> geometries = new ArrayList<>();
		for (Map.Entry<String,byte[]> file : files.entrySet()) try {
			JsonElement root = JsonParser.parseString(new String(file.getValue(), StandardCharsets.UTF_8));
			if (file.getKey().equalsIgnoreCase("models/mobs.json")) parseLegacy(root, geometries);
			else if (file.getKey().toLowerCase(Locale.ROOT).startsWith("models/entity/")) parseModern(root, geometries);
			else parseClientEntity(root, owners);
		} catch (RuntimeException ignored) { }
		for (Geometry geometry : geometries) {
			String name = owners.getOrDefault(geometry.identifier.toLowerCase(Locale.ROOT), entityName(geometry.identifier));
			if (name == null) continue; JsonObject jem = convert(geometry, name); if (jem != null)
				// EMF 2.x scans its modern namespace at emf/cem (and keeps optifine/cem
				// as a legacy fallback).  Emit the modern location so current EMF builds
				// discover virtual models during their normal resource reload.
				sink.put(Identifier.fromNamespaceAndPath("minecraft", "emf/cem/" + name + ".jem"), jem.toString().getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void parseLegacy(JsonElement root, List<Geometry> out) {
		if (!root.isJsonObject()) return;
		for (Map.Entry<String,JsonElement> e : root.getAsJsonObject().entrySet()) {
			if (e.getKey().equals("format_version") || !e.getValue().isJsonObject()) continue;
			JsonObject o=e.getValue().getAsJsonObject(); if (o.has("description") && o.has("bones")) out.add(new Geometry(e.getKey(),o));
			else for (Map.Entry<String,JsonElement> n:o.entrySet()) if (n.getValue().isJsonObject() && n.getValue().getAsJsonObject().has("bones")) out.add(new Geometry(n.getKey(),n.getValue().getAsJsonObject()));
		}
	}
	private static void parseModern(JsonElement root,List<Geometry> out) {
		if (!root.isJsonObject()) return; JsonArray a=root.getAsJsonObject().getAsJsonArray("minecraft:geometry"); if(a==null)return;
		for(JsonElement e:a) if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();JsonObject d=o.getAsJsonObject("description");if(d!=null)out.add(new Geometry(text(d.get("identifier")),o));}
	}
	private static void parseClientEntity(JsonElement root,Map<String,String> owners) {
		if(!root.isJsonObject())return;JsonObject ce=root.getAsJsonObject().getAsJsonObject("minecraft:client_entity");if(ce==null)return;JsonObject d=ce.getAsJsonObject("description");if(d==null)return;String entity=entityName(text(d.get("identifier")));if(entity==null)return;JsonObject g=d.getAsJsonObject("geometry");if(g==null)return;
		for(Map.Entry<String,JsonElement> e:g.entrySet()){String id=text(e.getValue());if(id!=null)owners.put(id.toLowerCase(Locale.ROOT),entity);}
	}

	private static JsonObject convert(Geometry geometry,String entity) {
		JsonObject d=geometry.object.getAsJsonObject("description"), bones=geometry.object; if(d==null)return null; JsonArray ba=bones.getAsJsonArray("bones");if(ba==null||ba.isEmpty())return null;
		int tw=positive(d,"texture_width",positive(d,"texturewidth",64)),th=positive(d,"texture_height",positive(d,"textureheight",64));Map<String,Bone> all=new LinkedHashMap<>();for(JsonElement e:ba)if(e.isJsonObject()){JsonObject b=e.getAsJsonObject();String n=text(b.get("name"));if(n!=null&&!n.isBlank())all.put(n,new Bone(n,b));}
		Map<String,List<Bone>> children=new HashMap<>();for(Bone b:all.values())children.computeIfAbsent(text(b.object.get("parent")),k->new ArrayList<>()).add(b);JsonArray models=new JsonArray();
		for(Bone b:all.values()) if(partName(entity,b.name)!=null) { JsonObject m=part(b,partName(entity,b.name),false); addChildren(m,b,children,entity); models.add(m); }
		for(Bone b:all.values()) if(!hasParent(b,all) && partName(entity,b.name)==null) emitPromoted(b,children,models,tw,th,entity);
		if(models.isEmpty())return null;JsonObject jem=new JsonObject();JsonArray ts=new JsonArray();ts.add(tw);ts.add(th);jem.add("textureSize",ts);jem.add("models",models);return jem;
	}
	private static boolean hasParent(Bone b,Map<String,Bone> all){String p=text(b.object.get("parent"));return p!=null&&all.containsKey(p);}
	private static void emitPromoted(Bone b,Map<String,List<Bone>> children,JsonArray models,int tw,int th,String entity){String part=partName(entity,b.name);if(part!=null){JsonObject m=part(b,part,false);addChildren(m,b,children,entity);models.add(m);}else for(Bone c:children.getOrDefault(b.name,List.of()))emitPromoted(c,children,models,tw,th,entity);}
	private static void addChildren(JsonObject parent,Bone b,Map<String,List<Bone>> children,String entity){JsonArray sub=new JsonArray();for(Bone c:children.getOrDefault(b.name,List.of())){if(partName(entity,c.name)!=null)continue;JsonObject m=part(c,null,true,vec(b.object.get("pivot"),0,0,0));addChildren(m,c,children,entity);sub.add(m);}if(sub.size()>0)parent.add("submodels",sub);}
	private static JsonObject part(Bone b,String rootPart,boolean child){return part(b,rootPart,child, null);}
	private static JsonObject part(Bone b,String rootPart,boolean child,JsonArray parentPivot){JsonObject m=new JsonObject();m.addProperty("id",safe(b.name));if(rootPart!=null)m.addProperty("part",rootPart);m.addProperty("invertAxis","xy");JsonArray p=vec(b.object.get("pivot"),0,child?0:24,0),tr=new JsonArray();if(parentPivot==null){tr.add(p.get(0));tr.add(p.get(1).getAsDouble()-(child?0:24));tr.add(p.get(2));}else{for(int i=0;i<3;i++)tr.add(p.get(i).getAsDouble()-parentPivot.get(i).getAsDouble());}if(!zero(tr))m.add("translate",tr);JsonArray rot=vec(b.object.get("rotation"),0,0,0);if(!zero(rot)){JsonArray r=new JsonArray();r.add(-rot.get(0).getAsDouble());r.add(-rot.get(1).getAsDouble());r.add(rot.get(2));m.add("rotate",r);}JsonArray boxes=new JsonArray(),cubes=b.object.getAsJsonArray("cubes");if(cubes!=null)for(JsonElement ce:cubes)if(ce.isJsonObject()){JsonObject c=ce.getAsJsonObject();JsonArray o=vec(c.get("origin"),0,0,0),s=vec(c.get("size"),0,0,0);JsonObject box=new JsonObject();addUv(box,c.get("uv"));JsonArray co=new JsonArray();for(int i=0;i<3;i++)co.add(o.get(i).getAsDouble()-p.get(i).getAsDouble());for(JsonElement x:s)co.add(x);box.add("coordinates",co);if(c.has("inflate"))box.add("sizeAdd",c.get("inflate"));boxes.add(box);}if(boxes.size()>0)m.add("boxes",boxes);return m;}
	private static void addUv(JsonObject box,JsonElement uv){if(uv==null)return;if(uv.isJsonArray()&&uv.getAsJsonArray().size()>=2){JsonArray a=new JsonArray();a.add(uv.getAsJsonArray().get(0));a.add(uv.getAsJsonArray().get(1));box.add("textureOffset",a);}else if(uv.isJsonObject())for(Map.Entry<String,JsonElement>e:uv.getAsJsonObject().entrySet()){if(!e.getValue().isJsonObject())continue;JsonObject f=e.getValue().getAsJsonObject();JsonArray p=f.getAsJsonArray("uv"),z=f.getAsJsonArray("uv_size");if(p==null||z==null||p.size()<2||z.size()<2)continue;JsonArray a=new JsonArray();a.add(p.get(0));a.add(p.get(1));a.add(p.get(0).getAsDouble()+z.get(0).getAsDouble());a.add(p.get(1).getAsDouble()+z.get(1).getAsDouble());String k=switch(e.getKey().toLowerCase(Locale.ROOT)){case"north"->"uvNorth";case"south"->"uvSouth";case"east"->"uvEast";case"west"->"uvWest";case"up"->"uvUp";case"down"->"uvDown";default->null;};if(k!=null)box.add(k,a);}}
	private static String partName(String entity,String raw){String n=raw.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT).replace('-','_');if(entity.equals("armor_stand"))return switch(n){case"baseplate","base_plate"->"base";case"waist","shoulder_stick"->"waist";case"leftarm","left_arm","left_body_stick"->"left_arm";case"rightarm","right_arm","right_body_stick"->"right_arm";case"leftleg","left_leg"->"left_leg";case"rightleg","right_leg"->"right_leg";case"head"->"head";case"hat","headwear"->"hat";case"body"->"body";default->null;};return switch(n){case"head"->"head";case"hat","headwear"->"hat";case"body"->"body";case"leftarm","left_arm"->"left_arm";case"rightarm","right_arm"->"right_arm";case"leftleg","left_leg"->"left_leg";case"rightleg","right_leg"->"right_leg";default->null;};}
	private static String entityName(String id){if(id==null)return null;String n=id.toLowerCase(Locale.ROOT);int c=n.indexOf(':');if(c>=0)n=n.substring(c+1);if(n.startsWith("geometry."))n=n.substring(9);return n.matches("[a-z0-9_]+")&&KNOWN.contains(n)?n:null;}
	private static String text(JsonElement e){return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()?e.getAsString():null;}
	private static int positive(JsonObject o,String k,int f){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsInt()>0?e.getAsInt():f;}
	private static JsonArray vec(JsonElement e,double x,double y,double z){JsonArray a=new JsonArray();if(e!=null&&e.isJsonArray())for(JsonElement v:e.getAsJsonArray())a.add(v);while(a.size()<3)a.add(a.size()==0?x:a.size()==1?y:z);return a;}
	private static boolean zero(JsonArray a){for(JsonElement e:a)if(Math.abs(e.getAsDouble())>1e-6)return false;return true;}
	private static String safe(String s){return s.replaceAll("[^A-Za-z0-9_]","_");}
	private record Geometry(String identifier,JsonObject object){} private record Bone(String name,JsonObject object){}
}
