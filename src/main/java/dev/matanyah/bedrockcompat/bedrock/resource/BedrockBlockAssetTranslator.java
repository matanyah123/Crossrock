package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import org.joml.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Static presentation bindings only: no registries, block state definitions or BP gameplay execution. */
public final class BedrockBlockAssetTranslator implements BedrockStackResourceTranslator {
    private final BedrockPackContentReader reader;
    private final java.util.function.Function<Identifier,List<BedrockBlockStates.Variant>> stateCatalog;
    public BedrockBlockAssetTranslator(BedrockPackContentReader reader) { this(reader,id->List.of(new BedrockBlockStates.Variant("",Map.of()))); }
    public BedrockBlockAssetTranslator(BedrockPackContentReader reader,java.util.function.Function<Identifier,List<BedrockBlockStates.Variant>> stateCatalog) { this.reader=reader;this.stateCatalog=stateCatalog; }
    @Override public void translate(BedrockPack pack,BedrockPackConfiguration config,GeneratedResourceSink sink) {
        List<String> errors=new ArrayList<>();translateStack(List.of(new BedrockTranslationPipeline.ConfiguredPack(pack,config)),sink,errors);
        if(!errors.isEmpty())throw new IllegalArgumentException(String.join("; ",errors));
    }
    private record Geometry(JsonObject source,String version) {}
    @Override public void translateStack(List<BedrockTranslationPipeline.ConfiguredPack> highToLow,GeneratedResourceSink sink,List<String> errors) {
        Map<String,byte[]> content=new LinkedHashMap<>();Map<String,Geometry> geometries=new LinkedHashMap<>();
        Map<String,JsonObject> definitions=new LinkedHashMap<>();Map<String,JsonObject> culling=new LinkedHashMap<>();JsonObject atlas=new JsonObject();Map<String,JsonElement> flipbooks=new LinkedHashMap<>();
        for(var selected:highToLow.reversed()) {
            var pack=selected.pack();boolean resource=pack.type()==BedrockPackType.RESOURCE||pack.type()==BedrockPackType.MIXED;
            boolean behavior=pack.type()==BedrockPackType.BEHAVIOR||pack.type()==BedrockPackType.MIXED;
            if(!resource&&!behavior)continue;
            try { reader.visitBlockAssets(pack,selected.configuration().selectedSubpack(),(path,data)->{
                String name=path.replace('\\','/').toLowerCase(Locale.ROOT);
                try {
                    if(behavior&&name.startsWith("blocks/")&&name.endsWith(".json")) {
                        JsonObject root=parse(data),block=object(root,"minecraft:block");
                        if(block!=null) {
                            String id=block.getAsJsonObject("description").get("identifier").getAsString();
                            JsonObject copy=block.deepCopy();copy.addProperty("crossrock_format",root.has("format_version")?root.get("format_version").getAsString():"");
                            definitions.put(id,copy);
                        }
                    } else if(resource&&name.startsWith("block_culling/")&&name.endsWith(".json")) {
                        JsonObject root=parse(data),rules=object(root,"minecraft:block_culling_rules");
                        if(rules!=null)culling.put(rules.getAsJsonObject("description").get("identifier").getAsString(),rules);
                    } else if(resource&&name.startsWith("models/blocks/")&&name.endsWith(".json")) {
                        JsonObject root=parse(data);String version=root.has("format_version")?root.get("format_version").getAsString():"";
                        if(root.has("minecraft:geometry"))for(var g:root.getAsJsonArray("minecraft:geometry")) {
                            var source=g.getAsJsonObject();geometries.put(source.getAsJsonObject("description").get("identifier").getAsString(),new Geometry(source,version));
                        }
                    } else if(resource&&name.equals("textures/terrain_texture.json")) {
                        var tiles=object(parse(data),"texture_data");if(tiles!=null)tiles.entrySet().forEach(e->atlas.add(e.getKey(),e.getValue()));
                    } else if(resource&&name.equals("textures/flipbook_textures.json")) {
                        for(var f:JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonArray())
                            flipbooks.put(f.getAsJsonObject().get("flipbook_texture").getAsString(),f);
                    } else if(resource) content.put(name,data);
                }catch(RuntimeException ex){errors.add(pack.displayName()+" "+name+": "+ex.getMessage());}
            }); }catch(IOException ex){errors.add(pack.displayName()+" block assets: "+ex.getMessage());}
        }
        JsonArray mergedFlipbooks=new JsonArray();flipbooks.values().forEach(mergedFlipbooks::add);
        content.put("textures/flipbook_textures.json",mergedFlipbooks.toString().getBytes(StandardCharsets.UTF_8));
        Map<String,byte[]> animations;
        try{animations=VanillaTextureTranslator.parseFlipbookAnimations(content);}catch(IOException ex){errors.add(ex.getMessage());animations=Map.of();}
        for(var entry:definitions.entrySet()) {
            var block=entry.getValue();JsonObject components=object(block,"components");
            if(components==null||!components.has("minecraft:geometry")&&!components.has("minecraft:item_visual"))continue;
            try {
                Identifier target=Identifier.parse(entry.getKey());
                if(!atLeast(block.get("crossrock_format").getAsString(),1,19,40))throw unsupported("block visual binding requires format 1.19.40+");
                List<BedrockBlockStates.Variant> javaStates=stateCatalog.apply(target);
                if(javaStates.isEmpty())throw unsupported("No matching registered Java block: "+target);
                if(javaStates.size()>4096)throw unsupported("State enumeration exceeds rendering budget");
                // Build all resources for this binding before publishing, so partial failures cannot replace vanilla.
                Map<Identifier,byte[]> pending=new LinkedHashMap<>();
                String base="block/assets/"+target.getNamespace()+"/"+target.getPath();
                JsonObject variants=new JsonObject();Visual defaultWorld=null;JsonObject defaultComponents=null;
                for(int stateIndex=0;stateIndex<javaStates.size();stateIndex++) {
                    var javaState=javaStates.get(stateIndex);
                    var stateValues=BedrockBlockStates.map(javaState,object(block.getAsJsonObject("description"),"states"));
                    var selected=BedrockBlockStates.components(block,stateValues);
                    String stateBase=javaStates.size()==1?base:base+"/states/s"+stateIndex;
                    Visual world=visual(selected.get("minecraft:geometry"),object(selected,"minecraft:material_instances"),geometries,atlas,content,animations,stateBase,block.get("crossrock_format").getAsString(),pending,culling);
                    if(selected.has("minecraft:transformation")) {
                        if(!atLeast(block.get("crossrock_format").getAsString(),1,19,80))throw unsupported("world transformation requires block 1.19.80+");
                        JsonObject transformation=selected.getAsJsonObject("minecraft:transformation");
                        if((transformation.has("rotation_pivot")||transformation.has("scale_pivot"))&&!atLeast(block.get("crossrock_format").getAsString(),1,21,0))throw unsupported("world pivots require block 1.21.0+");
                        world=transformed(world,BedrockWorldTransform.matrix(transformation),selected.get("minecraft:geometry"));
                    }
                    put(pending,Identifier.fromNamespaceAndPath("bedrock_compat","models/"+stateBase+"/world.json"),model(world,null));
                    JsonObject variant=new JsonObject();variant.addProperty("model","bedrock_compat:"+stateBase+"/world");variants.add(javaState.selector(),variant);
                    if(stateIndex==0){defaultWorld=world;defaultComponents=selected;}
                }
                JsonObject state=new JsonObject();state.add("variants",variants);
                put(pending,Identifier.fromNamespaceAndPath(target.getNamespace(),"blockstates/"+target.getPath()+".json"),state);
                Visual world=defaultWorld;components=defaultComponents;
                Visual item=world;
                if(components.has("minecraft:item_visual")) {
                    if(!atLeast(block.get("crossrock_format").getAsString(),1,21,60))throw unsupported("item_visual requires format 1.21.60+");
                    JsonObject iv=components.getAsJsonObject("minecraft:item_visual");
                    item=visual(iv.get("geometry"),object(iv,"material_instances"),geometries,atlas,content,animations,base+"/item",block.get("crossrock_format").getAsString(),pending,culling);
                }
                JsonObject fallback=new JsonObject();fallback.addProperty("type","minecraft:model");fallback.addProperty("model","bedrock_compat:"+base+"/item");
                put(pending,Identifier.fromNamespaceAndPath("bedrock_compat","models/"+base+"/item.json"),model(item,null));
                JsonArray cases=new JsonArray();
                JsonObject displays=object(item.geometry.source,"item_display_transforms");
                if(displays!=null) {
                    if(!atLeast(item.geometry.version,1,21,30))throw unsupported("display transforms require geometry 1.21.30+ in this adapter");
                    for(var d:displays.entrySet()) {
                        try {
                            JsonObject authored=d.getValue().getAsJsonObject();
                            Matrix4f matrix=BedrockItemDisplayTransforms.matrix(authored,d.getKey());
                            if(d.getKey().equals("gui")&&(!authored.has("fit_to_frame")||authored.get("fit_to_frame").getAsBoolean())) {
                                List<Vector3f> points=item.mesh.stream().flatMap(q->q.vertices().stream()).map(v->new Vector3f(v.x(),v.y()-8,v.z())).toList();
                                Vector3f translation=BedrockItemDisplayTransforms.vector(authored,"translation",new Vector3f(),-80,80);
                                matrix=BedrockItemDisplayTransforms.fitToFrame(matrix,points,translation);
                            }
                            String context=d.getKey();String selected="bedrock_compat:"+base+"/display/"+context;
                            put(pending,Identifier.fromNamespaceAndPath("bedrock_compat","models/"+base+"/display/"+context+".json"),model(item,matrix));
                            JsonObject c=new JsonObject(),m=new JsonObject();c.addProperty("when",context);m.addProperty("type","minecraft:model");m.addProperty("model",selected);c.add("model",m);cases.add(c);
                        }catch(RuntimeException ex){errors.add(target+" display "+d.getKey()+": "+ex.getMessage()+"; retaining Java display fallback");}
                    }
                }
                JsonObject itemDefinition=new JsonObject();
                if(cases.isEmpty())itemDefinition.add("model",fallback);
                else {JsonObject select=new JsonObject();select.addProperty("type","minecraft:select");select.addProperty("property","minecraft:display_context");select.add("cases",cases);select.add("fallback",fallback);itemDefinition.add("model",select);}
                put(pending,Identifier.fromNamespaceAndPath(target.getNamespace(),"items/"+target.getPath()+".json"),itemDefinition);
                pending.forEach(sink::put);
            }catch(RuntimeException|IOException ex){errors.add(entry.getKey()+" block visual: "+ex.getMessage()+"; retaining Java/lower resource fallback");}
        }
    }
    private record Material(String slot,String method,boolean shade,boolean ao) {}
    private record Visual(Geometry geometry,List<BedrockGeometryMesh.Quad> mesh,Map<String,Material> materials,JsonObject textures,boolean ao,JsonArray culling,String cullingLayer) {}
    private static Visual visual(JsonElement binding,JsonObject instances,Map<String,Geometry> geometries,JsonObject atlas,Map<String,byte[]> content,
                                 Map<String,byte[]> animations,String base,String blockVersion,Map<Identifier,byte[]> pending,Map<String,JsonObject> culling) throws IOException {
        if(binding==null||instances==null||instances.isEmpty()||instances.size()>64)throw unsupported("geometry/material_instances pair missing or too large");
        String id;JsonArray rules=new JsonArray();String layer="";
        List<BedrockRenderController.Visibility> visibility=new ArrayList<>();
        if(binding.isJsonPrimitive())id=binding.getAsString();
        else {
            JsonObject b=binding.getAsJsonObject();id=b.get("identifier").getAsString();
            for(String key:b.keySet())if(!Set.of("identifier","bone_visibility","uv_lock","culling","culling_layer").contains(key))throw unsupported("geometry field "+key);
            if(b.has("culling")) {
                String ruleId=b.get("culling").getAsString();if(!culling.containsKey(ruleId))throw unsupported("missing culling definition "+ruleId);
                rules=BedrockCulling.rules(culling.get(ruleId));
            }
            if(b.has("culling_layer"))layer=b.get("culling_layer").getAsString();
            if(b.has("uv_lock")&&(b.get("uv_lock").isJsonArray()?!b.getAsJsonArray("uv_lock").isEmpty():b.get("uv_lock").getAsBoolean())&&!atLeast(blockVersion,1,21,100))throw unsupported("UV locking requires block 1.21.100+");
            if(b.has("uv_lock")&&!b.get("uv_lock").isJsonArray()&&(!b.get("uv_lock").isJsonPrimitive()||!b.getAsJsonPrimitive("uv_lock").isBoolean()))throw unsupported("Invalid UV lock selector");
            if(b.has("bone_visibility"))for(var e:b.getAsJsonObject("bone_visibility").entrySet()) {
                if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isBoolean())throw unsupported("dynamic bone visibility");
                if(e.getKey().contains("*"))throw unsupported("bone_visibility requires exact bone names");
                visibility.add(new BedrockRenderController.Visibility(e.getKey(),e.getValue().getAsBoolean()));
            }
        }
        Geometry source=geometries.get(id);
        if(id.equals("minecraft:geometry.full_block"))source=fullBlock();
        if(source==null)throw unsupported("missing geometry "+id);
        if(!atLeast(source.version,1,12,0))throw unsupported("legacy block geometry version");
        BedrockGeometry parsed=BedrockGeometry.parse(source.source);
        for(var bone:parsed.bones())if(bone.binding()!=null||!bone.textureMeshes().isEmpty()||!bone.polygons().isEmpty())throw unsupported("dynamic binding/poly_mesh/texture_meshes");
        for(var element:rules) {
            JsonObject part=element.getAsJsonObject().getAsJsonObject("geometry_part");
            var bone=parsed.bones().stream().filter(b->b.name().equals(part.get("bone").getAsString())).findFirst().orElseThrow(()->unsupported("culling references missing bone"));
            if(part.has("cube")&&part.get("cube").getAsInt()>=bone.cubes().size())throw unsupported("culling references missing cube");
        }
        // Bind default UV faces before bone/cube rotation, so face aliases do not change on rotation.
        List<BedrockGeometry.Bone> bones=new ArrayList<>();
        for(var b:parsed.bones()) {
            List<BedrockGeometry.Cube> cubes=new ArrayList<>();
            for(var c:b.cubes()) {
                Map<String,BedrockGeometry.Uv> faces=new LinkedHashMap<>();
                c.faces().forEach((face,uv)->faces.put(face,new BedrockGeometry.Uv(uv.u(),uv.v(),uv.width(),uv.height(),uv.material().equals("*")&&instances.has(face)?face:uv.material(),uv.rotation())));
                cubes.add(new BedrockGeometry.Cube(c.origin(),c.size(),c.pivot(),c.rotation(),c.inflate(),c.mirror(),faces));
            }
            bones.add(new BedrockGeometry.Bone(b.name(),b.parent(),b.pivot(),b.rotation(),cubes,b.locators(),b.polygons(),b.neverRender(),b.binding(),b.textureMeshes()));
        }
        List<BedrockGeometryMesh.Quad> mesh=BedrockGeometryMesh.bake(new BedrockGeometry(parsed.textureWidth(),parsed.textureHeight(),bones),Map.of(),visibility);
        if(mesh.size()>16384)throw unsupported("oversized geometry mesh");
        List<BedrockGeometryMesh.Quad> materialMesh=mesh.isEmpty()?BedrockGeometryMesh.bake(new BedrockGeometry(parsed.textureWidth(),parsed.textureHeight(),bones),Map.of(),List.of()):mesh;
        if(materialMesh.isEmpty())throw unsupported("geometry has no material faces");
        Map<String,Material> materials=new LinkedHashMap<>();JsonObject textures=new JsonObject();Boolean ao=null;
        for(var quad:materialMesh) {
            if(materials.containsKey(quad.material()))continue;
            JsonObject m=resolveMaterial(instances,quad.material(),new HashSet<>());
            for(String key:m.keySet())if(!Set.of("texture","render_method","ambient_occlusion","face_dimming","isotropic").contains(key))throw unsupported("material field "+key);
            String method=m.has("render_method")?m.get("render_method").getAsString():"opaque";
            if(!Set.of("opaque","double_sided","alpha_test","alpha_test_single_sided","blend").contains(method))throw unsupported("material render method "+method);
            if(bool(m,"isotropic",false))throw unsupported("isotropic UVs");
            boolean ambient=true;
            if(m.has("ambient_occlusion")) {
                JsonElement v=m.get("ambient_occlusion");
                if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean()&&!v.getAsJsonPrimitive().isNumber())throw unsupported("invalid AO value");
                if(atLeast(blockVersion,1,26,20)&&v.getAsJsonPrimitive().isBoolean())throw unsupported("boolean AO removed in format 1.26.20");
                double n=v.getAsJsonPrimitive().isBoolean()?(v.getAsBoolean()?1:0):v.getAsDouble();
                if(n!=0&&n!=1)throw unsupported("AO exponent other than 0/1");ambient=n==1;
            }
            if(ao!=null&&ao!=ambient)throw unsupported("mixed per-material AO");ao=ambient;
            boolean shade=bool(m,"face_dimming",true);
            String tile=m.get("texture").getAsString();JsonElement def=atlas.get(tile);
            if(def==null||!def.isJsonObject())throw unsupported("missing terrain alias "+tile);
            JsonElement path=def.getAsJsonObject().get("textures");
            if(path!=null&&path.isJsonArray()&&path.getAsJsonArray().size()==1)path=path.getAsJsonArray().get(0);
            if(path==null||!path.isJsonPrimitive()||!path.getAsJsonPrimitive().isString())throw unsupported("indexed/weighted terrain alias "+tile);
            var image=VanillaTextureTranslator.resolveTexture(path,content);if(image==null)throw unsupported("missing texture "+path);
            String slot="m"+materials.size(),texture="bedrock_compat:"+base+"/textures/"+slot;
            byte[] pixels=materialPixels(image.data(),method);
            Identifier tex=Identifier.parse(texture);pending.put(Identifier.fromNamespaceAndPath(tex.getNamespace(),"textures/"+tex.getPath()+".png"),pixels);
            if(animations.containsKey(image.path()))pending.put(Identifier.fromNamespaceAndPath(tex.getNamespace(),"textures/"+tex.getPath()+".png.mcmeta"),animations.get(image.path()));
            textures.addProperty(slot,texture);materials.put(quad.material(),new Material(slot,method,shade,ambient));
        }
        textures.add("particle",textures.get("m0"));return new Visual(source,mesh,materials,textures,ao,rules,layer);
    }
    private static Visual transformed(Visual source,Matrix4f matrix,JsonElement geometryBinding) {
        JsonElement uvLock=geometryBinding.isJsonObject()?geometryBinding.getAsJsonObject().get("uv_lock"):null;
        List<BedrockGeometryMesh.Quad> result=new ArrayList<>();
        for(var q:source.mesh) {
            List<BedrockGeometryMesh.Vertex> vertices=new ArrayList<>();
            for(var v:q.vertices()) {
                Vector3f p=matrix.transformPosition(new Vector3f(v.x(),v.y()-8,v.z()));p.y+=8;
                vertices.add(new BedrockGeometryMesh.Vertex(p.x,p.y,p.z,v.u(),v.v()));
            }
            if(matrix.determinant3x3()<0)Collections.reverse(vertices);
            boolean lock=uvLock!=null&&(uvLock.isJsonPrimitive()?uvLock.getAsBoolean():java.util.stream.StreamSupport.stream(uvLock.getAsJsonArray().spliterator(),false).anyMatch(e->e.getAsString().equals(q.bone())));
            if(lock) {
                for(var element:source.geometry.source.getAsJsonArray("bones")) {
                    JsonObject bone=element.getAsJsonObject();if(!bone.get("name").getAsString().equals(q.bone())||!bone.has("cubes"))continue;
                    JsonObject cube=bone.getAsJsonArray("cubes").get(q.cubeIndex()).getAsJsonObject();
                    if(!cube.has("uv")||cube.get("uv").isJsonArray()) {
                        JsonArray size=cube.getAsJsonArray("size");double x=size.get(0).getAsDouble(),y=size.get(1).getAsDouble(),z=size.get(2).getAsDouble();
                        double width=q.face().equals("east")||q.face().equals("west")?z:x;
                        double height=q.face().equals("up")||q.face().equals("down")?z:y;
                        if(java.lang.Math.abs(width-height)>1e-6)throw unsupported("box UV locking requires square faces");
                    }
                }
                vertices=BedrockUvLock.lock(q,vertices,matrix);
            }
            Vector3f normal=matrix.normal(new Matrix3f()).transform(new Vector3f(q.nx(),q.ny(),q.nz()));
            if(!normal.isFinite())normal.set(0,1,0);
            result.add(new BedrockGeometryMesh.Quad(q.bone(),q.material(),List.copyOf(vertices),normal.x,normal.y,normal.z,q.cubeIndex(),q.face()));
        }
        JsonArray rotatedRules=source.culling.deepCopy();
        for(var rule:rotatedRules) {
            JsonObject r=rule.getAsJsonObject();String direction=r.get("direction").getAsString();
            Vector3f axis=switch(direction){case "east"->new Vector3f(1,0,0);case "west"->new Vector3f(-1,0,0);case "up"->new Vector3f(0,1,0);case "down"->new Vector3f(0,-1,0);case "south"->new Vector3f(0,0,1);default->new Vector3f(0,0,-1);};
            matrix.transformDirection(axis);
            String transformedDirection=java.lang.Math.abs(axis.x)>.0001?axis.x>0?"east":"west":java.lang.Math.abs(axis.y)>.0001?axis.y>0?"up":"down":axis.z>0?"south":"north";
            r.addProperty("direction",transformedDirection);
        }
        return new Visual(source.geometry,List.copyOf(result),source.materials,source.textures,source.ao,rotatedRules,source.cullingLayer);
    }
    private static JsonObject resolveMaterial(JsonObject all,String key,Set<String> seen) {
        if(!seen.add(key))throw unsupported("material reference cycle "+key);
        JsonElement value=all.get(key);if(value==null&&!key.equals("*"))return resolveMaterial(all,"*",seen);
        if(value==null)throw unsupported("unbound material "+key);
        return value.isJsonPrimitive()?resolveMaterial(all,value.getAsString(),seen):value.getAsJsonObject();
    }
    private static byte[] materialPixels(byte[] bytes,String method) throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw unsupported("unreadable texture");
        if(image.getWidth()>8192||image.getHeight()>8192)throw unsupported("oversized texture");
        if(method.equals("blend"))return bytes;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int p=image.getRGB(x,y),a=(method.equals("opaque")||method.equals("double_sided"))||((p>>>24)>=128)?255:0;image.setRGB(x,y,(a<<24)|(p&0xffffff));
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
    private static JsonObject model(Visual visual,Matrix4f display) {
        JsonObject out=new JsonObject();out.addProperty("fabric:type","bedrock_compat:static_block_mesh");
        out.add("textures",visual.textures.deepCopy());out.addProperty("ambientocclusion",visual.ao);out.addProperty("gui_light","side");
        out.addProperty("crossrock_culling_layer",visual.cullingLayer);
        if(display==null)out.addProperty("parent","minecraft:block/block");
        JsonArray quads=new JsonArray();
        for(var q:visual.mesh) {
            Material m=visual.materials.get(q.material());JsonObject quad=new JsonObject();quad.addProperty("texture",m.slot);
            quad.addProperty("render_method",m.method);quad.addProperty("shade",m.shade);
            if(display==null)quad.add("culling",BedrockCulling.forQuad(visual.culling,q));
            JsonArray vertices=new JsonArray();
            for(var v:q.vertices()) {
                if(v.u()<0||v.u()>1||v.v()<0||v.v()>1)throw unsupported("UVs outside texture atlas rectangle");
                Vector3f p=new Vector3f(v.x(),v.y(),v.z());
                if(display!=null){p.y-=8;display.transformPosition(p);p.y+=8;}
                p.add(8,0,8).div(16);
                if(!p.isFinite())throw unsupported("nonfinite vertex");
                JsonArray vertex=new JsonArray();for(float n:new float[]{p.x,p.y,p.z,v.u(),v.v()})vertex.add(n);vertices.add(vertex);
            }
            quad.add("vertices",vertices);quads.add(quad);
        }
        out.add("quads",quads);return out;
    }
    private static Geometry fullBlock() {
        JsonObject source=parse("{\"description\":{\"texture_width\":16,\"texture_height\":16},\"bones\":[{\"name\":\"root\",\"cubes\":[{\"origin\":[-8,0,-8],\"size\":[16,16,16],\"uv\":{}}]}]}".getBytes(StandardCharsets.UTF_8));
        JsonObject uv=source.getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonObject("uv");
        for(String face:List.of("up","down","north","south","east","west")){JsonObject f=new JsonObject();f.add("uv",JsonParser.parseString("[0,0]"));f.add("uv_size",JsonParser.parseString("[16,16]"));uv.add(face,f);}
        return new Geometry(source,"1.21.30");
    }
    private static boolean bool(JsonObject o,String key,boolean fallback) {
        if(!o.has(key))return fallback;
        JsonElement v=o.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw unsupported("Expected boolean "+key);
        return v.getAsBoolean();
    }
    private static boolean atLeast(String version,int a,int b,int c) {
        try{String[] s=version.split("\\.");int[] target={a,b,c};for(int i=0;i<3;i++){int n=Integer.parseInt(s[i]);if(n!=target[i])return n>target[i];}return true;}catch(RuntimeException ex){return false;}
    }
    private static IllegalArgumentException unsupported(String reason){return new IllegalArgumentException(reason);}
    private static JsonObject parse(byte[] data){return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static JsonObject object(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):null;}
    private static void put(Map<Identifier,byte[]> out,Identifier id,JsonObject json){out.put(id,json.toString().getBytes(StandardCharsets.UTF_8));}
}
