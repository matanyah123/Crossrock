package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Resolves pack-authored material inheritance to native draw capabilities, never by pack name. */
public final class BedrockMaterials {
    public record Stencil(int reference,int readMask,String function,boolean write,boolean replace) {}
    public record Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,boolean alphaTest,boolean glint,List<String> warnings,
                           Stencil stencil,boolean colorWrite,boolean colorMask,boolean depthWrite,boolean sourceAlpha,boolean bilinear,boolean repeat,boolean depthEqual) {
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,boolean alphaTest,boolean glint,List<String> warnings,
                        Stencil stencil,boolean colorWrite,boolean colorMask,boolean depthWrite,boolean sourceAlpha,boolean bilinear,boolean repeat) {
            this(blend,additive,emissive,cull,masked,alphaTest,glint,warnings,stencil,colorWrite,colorMask,depthWrite,sourceAlpha,bilinear,repeat,false);
        }
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,boolean alphaTest,boolean glint,List<String> warnings,Stencil stencil,boolean colorWrite) {
            this(blend,additive,emissive,cull,masked,alphaTest,glint,warnings,stencil,colorWrite,false,true,true,false,false);
        }
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,boolean alphaTest,boolean glint,List<String> warnings) {
            this(blend,additive,emissive,cull,masked,alphaTest,glint,warnings,null,true);
        }
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,boolean alphaTest,List<String> warnings) {
            this(blend,additive,emissive,cull,masked,alphaTest,false,warnings);
        }
        public Material(boolean blend,boolean additive,boolean emissive,boolean cull,boolean masked,List<String> warnings) {
            this(blend,additive,emissive,cull,masked,false,warnings);
        }
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
            if(parent.alphaTest)defines.add("ALPHA_TEST");
            if(parent.glint)defines.add("USE_GLINT");
            if(parent.colorMask)defines.add("USE_COLOR_MASK");
            if(!parent.depthWrite)states.add("DisableDepthWrite");
            if(parent.stencil!=null)states.add("EnableStencilTest");
            if(parent.stencil!=null&&parent.stencil.write)states.add("StencilWrite");
            if(!parent.colorWrite)states.add("DisableColorWrite");
            apply(source,"defines",defines);apply(source,"states",states);
            List<String> warnings=new ArrayList<>(parent.warnings);
            if(states.contains("StencilWrite")&&!states.contains("EnableStencilTest"))warnings.add("Material StencilWrite requires EnableStencilTest");
            for(String define:defines)if(!Set.of("ALPHA_TEST","USE_EMISSIVE","USE_UV_ANIM","USE_SKINNING","FANCY","USE_OVERLAY","MASKED_MULTITEXTURE","USE_GLINT","GLINT","USE_COLOR_MASK").contains(define))
                warnings.add("Material define not yet supported: "+define);
            for(String state:states)if(!Set.of("Blending","DisableCulling","EnableStencilTest","StencilWrite","DisableColorWrite","DisableAlphaWrite","DisableDepthWrite").contains(state))warnings.add("Material state not yet supported: "+state);
            for(String key:List.of("vertexShader","fragmentShader","geometryShader"))
                if(source.has(key))warnings.add("Material property not yet supported: "+key);
            Stencil stencil=null;
            if(states.contains("EnableStencilTest")) {
                var inherited=parent.stencil;
                var face=source.has("frontFace")?source.getAsJsonObject("frontFace"):new JsonObject();
                String function=face.has("stencilFunc")?face.get("stencilFunc").getAsString():inherited==null?"Always":inherited.function;
                boolean replace=face.has("stencilPassOp")?face.get("stencilPassOp").getAsString().equals("Replace"):inherited!=null&&inherited.replace;
                int ref=source.has("stencilRef")?source.get("stencilRef").getAsInt():inherited==null?0:inherited.reference;
                int mask=source.has("stencilReadMask")?source.get("stencilReadMask").getAsInt():inherited==null?255:inherited.readMask;
                stencil=new Stencil(ref,mask,function,states.contains("StencilWrite"),replace);
            }
            boolean additive=source.has("blendDst")?source.get("blendDst").getAsString().equals("One"):parent.additive;
            boolean sourceAlpha=source.has("blendSrc")?source.get("blendSrc").getAsString().equals("SourceAlpha"):parent.sourceAlpha;
            boolean bilinear=parent.bilinear,repeat=parent.repeat;
            for(String key:List.of("samplerStates","+samplerStates"))if(source.has(key))for(var value:source.getAsJsonArray(key)) {
                var sampler=value.getAsJsonObject();
                if(sampler.has("samplerIndex")&&sampler.get("samplerIndex").getAsInt()!=0)continue;
                if(sampler.has("textureFilter"))bilinear=sampler.get("textureFilter").getAsString().equals("Bilinear");
                if(sampler.has("textureWrap"))repeat=sampler.get("textureWrap").getAsString().equals("Repeat");
            }
            boolean depthEqual=source.has("depthFunc")?source.get("depthFunc").getAsString().equals("Equal"):parent.depthEqual;
            parent=new Material(states.contains("Blending"),additive,defines.contains("USE_EMISSIVE"),!states.contains("DisableCulling"),defines.contains("MASKED_MULTITEXTURE"),defines.contains("ALPHA_TEST"),defines.contains("USE_GLINT")||defines.contains("GLINT"),List.copyOf(new LinkedHashSet<>(warnings)),stencil,!states.contains("DisableColorWrite"),defines.contains("USE_COLOR_MASK"),!states.contains("DisableDepthWrite"),sourceAlpha,bilinear,repeat,depthEqual);
        }
        path.remove(name);cache.put(name,parent);return parent;
    }
    private static void apply(JsonObject source,String key,Set<String> values) {
        if(source.has(key)){values.clear();source.getAsJsonArray(key).forEach(v->values.add(v.getAsString()));}
        if(source.has("+"+key))source.getAsJsonArray("+"+key).forEach(v->values.add(v.getAsString()));
        if(source.has("-"+key))source.getAsJsonArray("-"+key).forEach(v->{
            String value=v.getAsString();values.remove(value);
            if(key.equals("defines")&&(value.equals("GLINT")||value.equals("USE_GLINT"))) {
                values.remove("GLINT");values.remove("USE_GLINT");
            }
        });
    }
    private static Material standard(String name) {
        if(name.equals("entity_alphatest_change_color")||name.equals("entity_change_color"))
            return new Material(false,false,false,false,false,name.contains("alphatest"),false,List.of(),null,true,true,true,true,false,false);
        if(VANILLA_CUTOUT.contains(name)||Set.of("entity","entity_static","entity_alphatest","entity_custom").contains(name))
            return new Material(false,false,false,name.equals("entity")||name.equals("entity_static"),false,VANILLA_CUTOUT.contains(name)||name.contains("alphatest"),List.of());
        return switch(name) {
            case "entity_nocull"->new Material(false,false,false,false,List.of());
            case "elytra"->new Material(false,false,false,false,false,true,List.of());
            case "armor_enchanted","elytra_glint"->new Material(false,false,false,false,false,true,true,List.of());
            case "entity_alphatest_one_sided","entity_alphatest_culling"->new Material(false,false,false,true,false,true,List.of());
            case "entity_base_culling"->new Material(false,false,false,true,List.of());
            case "entity_translucent","entity_alphablend","player_spectator","guardian_ghost","spider_invisible"->new Material(true,false,false,false,List.of());
            case "entity_emissive_alpha","spider","entity_emissive_layer_alpha_test"->new Material(false,false,true,false,false,true,List.of());
            case "entity_emissive"->new Material(false,false,true,true,List.of());
            case "warden_bioluminescent_layer"->new Material(true,true,true,false,List.of());
            case "entity_emissive_alpha_one_sided"->new Material(false,false,true,true,false,true,List.of());
            case "entity_beam_additive","entity_beam"->new Material(true,true,true,false,List.of());
            case "entity_multitexture_masked","villager_v2_masked","zombie_villager_v2_masked"->new Material(false,false,false,false,true,true,List.of());
            case "entity_multitexture_color_mask"->new Material(false,false,false,false,List.of("Multitexture color-mask composition not yet supported"));
            default->throw new IllegalArgumentException("Unknown native material: "+name);
        };
    }
}
