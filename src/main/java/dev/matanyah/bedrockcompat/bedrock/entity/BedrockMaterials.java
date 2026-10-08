package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Resolves pack-authored material inheritance to native draw capabilities, never by pack name. */
public final class BedrockMaterials {
    public record Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,List<String> warnings) {
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,List<String> warnings) {
            this(blend,additive,emissive,cull,false,warnings);
        }
    }
    private final Map<String,JsonObject> definitions;
    private final Map<String,Material> cache=new HashMap<>();
    private static final Set<String> VANILLA_CUTOUT=Set.of("armor_stand","armor","horse","horse_leather_armor","zombie","drowned",
        "ghast","llama","guardian","wither_skull","frog","salmon","bat_v2","hoglin","parrot","strider","evoker","stray",
        "stray_clothes","dolphin","vindicator","ender_dragon","iron_golem","nautilus","sniffer","cod","skeleton","pillager",
        "pufferfish","magma_cube","zoglin","camel","breeze","witch","axolotl","axolotl_limbs","phantom","creaking","fang",
        "ocelot","polar_bear","husk","player_animated","bogged","bogged_clothes","goat","warden","snow_golem","piglin",
        "piglin_brute","shulker","wandering_trader","armadillo","ravager","silverfish","silverfish_layers","bee","cat");
    public BedrockMaterials(Map<String,JsonObject> definitions){this.definitions=Map.copyOf(definitions);}
    public Material resolve(String name){return resolve(name,new HashSet<>());}
    private Material resolve(String name,Set<String> path) {
        Material cached=cache.get(name);if(cached!=null)return cached;
        if(!path.add(name)||path.size()>64)throw new IllegalArgumentException("Cyclic material inheritance: "+name);
        JsonObject source=definitions.get(name);Material parent;
        if(source==null)parent=standard(name);
        else {
            parent=source.has("bedrock_parent")?resolve(source.get("bedrock_parent").getAsString(),path):standard("entity");
            Set<String> defines=new HashSet<>(),states=new HashSet<>();
            if(parent.blend)states.add("Blending");if(!parent.cull)states.add("DisableCulling");if(parent.emissive)defines.add("USE_EMISSIVE");
            if(parent.masked)defines.add("MASKED_MULTITEXTURE");
            apply(source,"defines",defines);apply(source,"states",states);
            List<String> warnings=new ArrayList<>(parent.warnings);
            for(String define:defines)if(!Set.of("ALPHA_TEST","USE_EMISSIVE","USE_UV_ANIM","USE_SKINNING","FANCY","USE_OVERLAY","MASKED_MULTITEXTURE").contains(define))
                warnings.add("Material define not yet supported: "+define);
            for(String state:states)if(!Set.of("Blending","DisableCulling").contains(state))warnings.add("Material state not yet supported: "+state);
            for(String key:List.of("vertexShader","fragmentShader","geometryShader","frontFace","backFace","stencilRef"))
                if(source.has(key))warnings.add("Material property not yet supported: "+key);
            boolean additive=source.has("blendDst")?source.get("blendDst").getAsString().equals("One"):parent.additive;
            parent=new Material(states.contains("Blending"),additive,defines.contains("USE_EMISSIVE"),!states.contains("DisableCulling"),defines.contains("MASKED_MULTITEXTURE"),List.copyOf(new LinkedHashSet<>(warnings)));
        }
        path.remove(name);cache.put(name,parent);return parent;
    }
    private static void apply(JsonObject source,String key,Set<String> values) {
        if(source.has(key)){values.clear();source.getAsJsonArray(key).forEach(v->values.add(v.getAsString()));}
        if(source.has("+"+key))source.getAsJsonArray("+"+key).forEach(v->values.add(v.getAsString()));
        if(source.has("-"+key))source.getAsJsonArray("-"+key).forEach(v->values.remove(v.getAsString()));
    }
    private static Material standard(String name) {
        if(VANILLA_CUTOUT.contains(name)||Set.of("entity","entity_static","entity_alphatest","entity_alphatest_change_color","entity_custom").contains(name))
            return new Material(false,false,false,false,List.of());
        return switch(name) {
            case "entity_alphatest_one_sided","entity_alphatest_culling","entity_base_culling"->new Material(false,false,false,true,List.of());
            case "entity_translucent","entity_alphablend","player_spectator","guardian_ghost","spider_invisible"->new Material(true,false,false,false,List.of());
            case "entity_emissive_alpha","entity_emissive","spider","warden_bioluminescent_layer","entity_emissive_layer_alpha_test"->new Material(true,false,true,false,List.of());
            case "entity_emissive_alpha_one_sided"->new Material(true,false,true,true,List.of());
            case "entity_beam_additive","entity_beam"->new Material(true,true,true,false,List.of());
            case "entity_multitexture_masked","villager_v2_masked","zombie_villager_v2_masked"->new Material(false,false,false,false,true,List.of());
            case "entity_multitexture_color_mask"->new Material(false,false,false,false,List.of("Multitexture color-mask composition not yet supported"));
            case "entity_change_color"->new Material(false,false,false,false,List.of("Entity dye-mask composition not yet supported"));
            default->throw new IllegalArgumentException("Unknown native material: "+name);
        };
    }
}
