package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Static Bedrock bed geometry is split at the head/foot boundary into native 26.2 block models. */
public final class BedrockBedModelTranslator implements BedrockResourceTranslator {
    private final BedrockPackContentReader reader;
    public BedrockBedModelTranslator(BedrockPackContentReader reader) { this.reader = reader; }
    @Override public void translate(BedrockPack pack, BedrockPackConfiguration configuration,
                                    GeneratedResourceSink sink) throws IOException {
        Map<String, byte[]> content = new LinkedHashMap<>();
        Set<String> texturePaths=new HashSet<>();
        boolean[] hasBed={false};
        reader.visitAll(pack, configuration.selectedSubpack(), (path, data) -> {
            String name=path.replace('\\','/');
            if(!name.endsWith(".json")||!(name.startsWith("entity/")||name.startsWith("models/")))return;
            content.put(name,data);
            try {
                var definitions=NativeEntityResourceTranslator.definitions(JsonParser.parseString(
                    new String(data,StandardCharsets.UTF_8)).getAsJsonObject());
                if(definitions.containsKey("geometry.bed"))hasBed[0]=true;
                JsonObject bed=definitions.get("minecraft:bed");
                if(bed!=null) {
                    hasBed[0]=true;
                    JsonObject textures=bed.getAsJsonObject("description").getAsJsonObject("textures");
                    if(textures!=null)for(var entry:textures.entrySet()) {
                        String texture=entry.getValue().getAsString().replace('\\','/');
                        texturePaths.add(texture.endsWith(".png")?texture:texture+".png");
                    }
                }
            }catch(RuntimeException ignored){ /* Reported by the native entity parser. */ }
        });
        if(!hasBed[0])return;
        for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
                "silver","light_gray","cyan","purple","blue","brown","green","red","black"))
            texturePaths.add("textures/entity/bed/"+color+".png");
        // Keep only textures referenced by beds, not whole sound archives and unrelated entity sheets.
        reader.visit(pack,configuration.selectedSubpack(),(path,data)->{
            String name=path.replace('\\','/');
            if(texturePaths.contains(name))content.put(name,data);
            else if(name.endsWith(".tga")) {
                String png=name.substring(0,name.length()-4)+".png";
                if(texturePaths.contains(png)) {
                    byte[] converted=VanillaTextureTranslator.decodeTga(data);
                    if(converted!=null)content.put(png,converted);
                }
            }
        });
        translate(content, sink);
    }
    static void translate(Map<String, byte[]> content, GeneratedResourceSink sink) throws IOException {
        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        for (var entry : content.entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(".json") || !(path.startsWith("entity/") || path.startsWith("models/"))) continue;
            try { definitions.putAll(NativeEntityResourceTranslator.definitions(
                JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8)).getAsJsonObject())); }
            catch (RuntimeException ignored) { /* The native entity translator reports malformed documents. */ }
        }
        JsonObject bed = definitions.get("minecraft:bed");
        if (bed == null && definitions.containsKey("geometry.bed")) {
            // Packs may override vanilla geometry without repeating the vanilla client-entity file.
            JsonObject description=new JsonObject(),aliases=new JsonObject(),textures=new JsonObject();
            aliases.addProperty("default","geometry.bed");
            for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
                    "silver","cyan","purple","blue","brown","green","red","black"))
                textures.addProperty(color,"textures/entity/bed/"+color);
            description.add("geometry",aliases);description.add("textures",textures);
            bed=new JsonObject();bed.add("description",description);
        }
        if (bed == null) return;
        try {
            JsonObject description = bed.getAsJsonObject("description");
            JsonObject aliases = description.getAsJsonObject("geometry");
            if (aliases == null || !aliases.has("default")) return;
            String id = aliases.get("default").getAsString();
            if (!definitions.containsKey(id)) return; // Texture-only packs use the existing splitter.
            Map<String,JsonObject> geometries = new LinkedHashMap<>();
            definitions.forEach((name,value) -> { if (name.startsWith("geometry.")) geometries.put(name,value); });
            List<String> errors = new ArrayList<>();
            BedrockGeometry geometry = BedrockGeometryResolver.resolve(geometries, errors::add).get(id);
            if (geometry == null) throw new IOException("Bed geometry " + id + " could not be resolved");
            List<BedrockGeometryMesh.Quad> mesh = BedrockGeometryMesh.bake(geometry, Map.of(), List.of());
            // Build both halves before publishing anything: unsupported geometry must retain vanilla beds.
            JsonArray head = elements(mesh, 0), foot = elements(mesh, 16);
            JsonObject textures = description.getAsJsonObject("textures");
            if (textures == null) return;
            for (String color : List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
                    "light_gray","cyan","purple","blue","brown","green","red","black")) {
                String alias = color.equals("light_gray") && textures.has("silver") ? "silver" : color;
                if (!textures.has(alias)) alias="default";
                if (!textures.has(alias)) continue;
                String path = textures.get(alias).getAsString().replace('\\','/');
                if (!path.endsWith(".png")) path += ".png";
                byte[] png = content.get(path);
                if (png == null) continue;
                String texture = "bedrock_compat:block/bed/" + color;
                sink.put(Identifier.fromNamespaceAndPath("bedrock_compat", "textures/block/bed/" + color + ".png"), png);
                model(sink, color + "_bed_head", texture, head);
                model(sink, color + "_bed_foot", texture, foot);
            }
        } catch (RuntimeException ex) { throw new IOException("Unsupported Bedrock bed model: " + ex.getMessage(), ex); }
    }

    private record Point(double x,double y,double z,double u,double v) {}
    private static JsonArray elements(List<BedrockGeometryMesh.Quad> mesh, int start) {
        JsonArray result = new JsonArray();
        for (var quad : mesh) {
            if (!quad.material().isEmpty() && !quad.material().equals("*")) throw new IllegalArgumentException("Bed material instances are not block textures");
            List<Point> points = quad.vertices().stream().map(v -> new Point(16-v.x(),9-v.z(),32-v.y(),16*v.u(),16*v.v())).toList();
            double nx=-quad.nx(), ny=-quad.nz(), nz=-quad.ny();
            int axis = Math.abs(nx) > .9999 ? 0 : Math.abs(ny) > .9999 ? 1 : Math.abs(nz) > .9999 ? 2 : -1;
            if (axis < 0 || points.size()!=4) throw new IllegalArgumentException("Non-axis-aligned bed faces need a native block renderer");
            double minZ=points.stream().mapToDouble(Point::z).min().orElseThrow();
            double maxZ=points.stream().mapToDouble(Point::z).max().orElseThrow();
            if (maxZ < start-1e-6 || minZ > start+16+1e-6) continue;
            if (axis==2 && Math.abs(minZ-16)<1e-6) continue; // Internal seam, never an exterior bed end.
            double x0=points.stream().mapToDouble(Point::x).min().orElseThrow(), x1=points.stream().mapToDouble(Point::x).max().orElseThrow();
            double y0=points.stream().mapToDouble(Point::y).min().orElseThrow(), y1=points.stream().mapToDouble(Point::y).max().orElseThrow();
            double z0=Math.max(start,minZ), z1=Math.min(start+16,maxZ);
            if (axis!=2 && z1-z0<1e-6) continue;
            for(double value:new double[]{x0,x1,y0,y1,z0-start,z1-start})
                if(!Double.isFinite(value)||value < -16 || value > 32)
                    throw new IllegalArgumentException("Bed model exceeds Java block-model coordinate limits");
            // Canonical Java FaceInfo vertices, matching FaceBakery UV order.
            String face;
            double[][] corners;
            if(axis==0 && nx<0) {face="west";corners=new double[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}};}
            else if(axis==0) {face="east";corners=new double[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}};}
            else if(axis==1 && ny<0) {face="down";corners=new double[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}};}
            else if(axis==1) {face="up";corners=new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}};}
            else if(nz<0) {face="north";corners=new double[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}};}
            else {face="south";corners=new double[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}};}
            double[][] uv=new double[4][];
            for(int i=0;i<4;i++) uv[i]=sample(points,corners[i]);
            JsonObject f=new JsonObject();
            boolean found=false;
            for(int rotation=0;rotation<4;rotation++) {
                double[] a=uv[(4-rotation)%4], b=uv[(6-rotation)%4];
                double[][] expected={{a[0],a[1]},{a[0],b[1]},{b[0],b[1]},{b[0],a[1]}};
                boolean match=true;
                for(int i=0;i<4;i++) for(int j=0;j<2;j++)
                    if(Math.abs(uv[i][j]-expected[(i+rotation)%4][j])>.001) match=false;
                if(match) {
                    f.add("uv",array(a[0],a[1],b[0],b[1]));
                    if(rotation!=0) f.addProperty("rotation",rotation*90);
                    found=true;break;
                }
            }
            if(!found) throw new IllegalArgumentException("Skewed bed UVs cannot be represented by Java block models");
            f.addProperty("texture","#bed");
            JsonObject faces=new JsonObject();faces.add(face,f);
            JsonObject element=new JsonObject();
            element.add("from",array(x0,y0,z0-start));element.add("to",array(x1,y1,z1-start));element.add("faces",faces);
            result.add(element);
        }
        return result;
    }
    private static double[] sample(List<Point> p,double[] q) {
        Point a=p.get(0), b=p.get(1), d=p.get(3);
        double[] e={b.x-a.x,b.y-a.y,b.z-a.z}, f={d.x-a.x,d.y-a.y,d.z-a.z}, v={q[0]-a.x,q[1]-a.y,q[2]-a.z};
        double ee=0,ff=0,ef=0,ve=0,vf=0;
        for(int i=0;i<3;i++){ee+=e[i]*e[i];ff+=f[i]*f[i];ef+=e[i]*f[i];ve+=v[i]*e[i];vf+=v[i]*f[i];}
        double determinant=ee*ff-ef*ef;
        if(Math.abs(determinant)<1e-10)throw new IllegalArgumentException("Degenerate bed face");
        double s=(ve*ff-vf*ef)/determinant,t=(vf*ee-ve*ef)/determinant;
        return new double[]{a.u+s*(b.u-a.u)+t*(d.u-a.u),a.v+s*(b.v-a.v)+t*(d.v-a.v)};
    }
    private static JsonArray array(double... values){JsonArray a=new JsonArray();for(double value:values)a.add(value);return a;}
    private static void model(GeneratedResourceSink sink,String name,String texture,JsonArray elements) {
        JsonObject root=new JsonObject(),textures=new JsonObject();
        root.addProperty("parent","minecraft:block/template_bed");textures.addProperty("bed",texture);textures.addProperty("particle",texture);
        root.add("textures",textures);root.add("elements",elements);
        sink.put(Identifier.withDefaultNamespace("models/block/"+name+".json"),root.toString().getBytes(StandardCharsets.UTF_8));
    }
}
