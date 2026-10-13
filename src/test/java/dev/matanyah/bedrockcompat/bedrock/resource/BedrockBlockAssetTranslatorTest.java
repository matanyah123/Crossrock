package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Vector3f;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockBlockAssetTranslatorTest {
    @TempDir Path temp;
    private BedrockTranslationPipeline pipeline(){return new BedrockTranslationPipeline(List.of(new BedrockBlockAssetTranslator(new BedrockPackContentReader())));}
    private BedrockPack pack(Path dir,BedrockPackType type) {
        return new BedrockPack(dir.getFileName().toString(),"",dir.getFileName().toString(),new BedrockVersion(List.of(1,0,0)),Optional.empty(),List.of(),List.of(),dir,"manifest.json",type,Optional.empty(),List.of());
    }
    private void write(Path dir,String path,String text)throws Exception {Path p=dir.resolve(path);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    private void image(Path dir,int pixel)throws Exception {
        Files.createDirectories(dir.resolve("textures/art"));var im=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)im.setRGB(x,y,pixel);
        ImageIO.write(im,"PNG",dir.resolve("textures/art/sheet.png").toFile());
    }
    private Path resource(String name)throws Exception {
        Path rp=temp.resolve(name);image(rp,0x60123456);
        write(rp,"textures/terrain_texture.json","{\"texture_data\":{\"sheet\":{\"textures\":\"textures/art/sheet\"}}}");
        write(rp,"models/blocks/model.geo.json",geometry(""));return rp;
    }
    private String geometry(String extra) {
        return "{\"format_version\":\"1.21.30\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.fixture\",\"texture_width\":64,\"texture_height\":64},\"bones\":[{\"name\":\"root\",\"cubes\":[{\"origin\":[-4,0,-4],\"size\":[8,12,8],\"uv\":[0,0]}]}]"+extra+"}]}";
    }
    private Path behavior(String name,String material,String extra)throws Exception {
        Path bp=temp.resolve(name);write(bp,"blocks/visual.json",definition(material,extra));return bp;
    }
    private String definition(String material,String extra) {
        return "{\"format_version\":\"1.21.60\",\"minecraft:block\":{\"description\":{\"identifier\":\"minecraft:diamond_block\"},\"components\":{\"minecraft:geometry\":\"geometry.fixture\",\"minecraft:material_instances\":"+material+extra+"}}}";
    }
    private String materials(String method){return "{\"*\":{\"texture\":\"sheet\",\"render_method\":\""+method+"\"}}";}
    private JsonObject json(BedrockTranslationPipeline.GenerationResult result,String id) {
        byte[] data=result.resources().resources().get(Identifier.parse(id));assertNotNull(data,id);return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static final String MODEL="bedrock_compat:models/block/assets/minecraft/diamond_block/world.json";
    @Test void crossPackGeometryAndMaterialReferencesGenerateWorkingMeshResources()throws Exception {
        Path rp=resource("rp"),bp=behavior("bp","{\"*\":\"base\",\"base\":{\"texture\":\"sheet\",\"render_method\":\"blend\"}}","");
        var result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertTrue(result.errors().isEmpty(),result.errors().toString());
        var model=json(result,MODEL);assertEquals(6,model.getAsJsonArray("quads").size());
        var quad=model.getAsJsonArray("quads").get(0).getAsJsonObject();assertEquals("blend",quad.get("render_method").getAsString());
        double minX=Double.MAX_VALUE,maxY=0;for(var q:model.getAsJsonArray("quads"))for(var v:q.getAsJsonObject().getAsJsonArray("vertices")) {
            minX=Math.min(minX,v.getAsJsonArray().get(0).getAsDouble());maxY=Math.max(maxY,v.getAsJsonArray().get(1).getAsDouble());
        }
        assertEquals(.25,minX,1e-6);assertEquals(.75,maxY,1e-6);
        assertTrue(json(result,"minecraft:blockstates/diamond_block.json").getAsJsonObject("variants").has(""));
        assertFalse(result.resources().resources().containsKey(Identifier.parse("minecraft:loot_tables/blocks/diamond_block.json")));
    }
    @Test void materialAlphaMethodsAndAnimationsAreAppliedNotJustParsed()throws Exception {
        Path rp=resource("rp");write(rp,"textures/flipbook_textures.json","[{\"flipbook_texture\":\"textures/art/sheet\",\"ticks_per_frame\":5,\"frames\":[0]}]");
        for(String method:List.of("opaque","double_sided","alpha_test","alpha_test_single_sided","blend")) {
            Path bp=behavior("bp",materials(method),"");var result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
            assertTrue(result.errors().isEmpty(),result.errors().toString());
            var id=Identifier.parse("bedrock_compat:textures/block/assets/minecraft/diamond_block/textures/m0.png");
            var im=ImageIO.read(new ByteArrayInputStream(result.resources().resources().get(id)));
            assertEquals(method.equals("blend")?96:method.startsWith("alpha_test")?0:255,im.getRGB(0,0)>>>24);
            assertTrue(result.resources().resources().containsKey(Identifier.parse(id+".mcmeta")));
        }
    }
    @Test void namedFacesVisibilityAndRotatedBoneHierarchyUseNativeGeometry()throws Exception {
        Path rp=resource("rp"),bp=behavior("bp","{\"*\":{\"texture\":\"sheet\"},\"north\":{\"texture\":\"sheet\",\"face_dimming\":false}}","");
        String source=geometry("").replace("\"name\":\"root\"","\"name\":\"root\",\"rotation\":[0,90,0],\"pivot\":[0,0,0]");
        write(rp,"models/blocks/model.geo.json",source);
        var result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertTrue(result.errors().isEmpty(),result.errors().toString());
        var quads=json(result,MODEL).getAsJsonArray("quads");assertEquals(6,quads.size());
        assertEquals(1,java.util.stream.StreamSupport.stream(quads.spliterator(),false).filter(q->!q.getAsJsonObject().get("shade").getAsBoolean()).count());
        var north=quads.get(0).getAsJsonObject().getAsJsonArray("vertices");
        for(var v:north)assertEquals(.25,v.getAsJsonArray().get(0).getAsDouble(),1e-6);
    }
    @Test void highestBindingAndSubpackAssetsWinAndRemovalHasNoStaleCache()throws Exception {
        Path rp=resource("rp"),bp=behavior("lower",materials("opaque"),""),higher=behavior("higher",materials("blend"),"");
        image(rp.resolve("subpacks/selected"),0x40223344);
        var result=pipeline().generateConfigured(List.of(new BedrockTranslationPipeline.ConfiguredPack(pack(higher,BedrockPackType.BEHAVIOR),BedrockPackConfiguration.EMPTY),
            new BedrockTranslationPipeline.ConfiguredPack(pack(rp,BedrockPackType.RESOURCE),new BedrockPackConfiguration(Optional.of("selected"),Map.of())),
            new BedrockTranslationPipeline.ConfiguredPack(pack(bp,BedrockPackType.BEHAVIOR),BedrockPackConfiguration.EMPTY)));
        assertTrue(result.errors().isEmpty(),result.errors().toString());
        var bytes=result.resources().resources().get(Identifier.parse("bedrock_compat:textures/block/assets/minecraft/diamond_block/textures/m0.png"));
        assertEquals(0x40223344,ImageIO.read(new ByteArrayInputStream(bytes)).getRGB(0,0));
        assertTrue(pipeline().generate(List.of()).resources().resources().isEmpty());
        write(higher,"blocks/visual.json",definition(materials("unsupported"),""));
        result=pipeline().generate(List.of(pack(higher,BedrockPackType.BEHAVIOR),pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertFalse(result.errors().isEmpty());assertTrue(result.resources().resources().isEmpty(),"unsupported higher declaration must not resurrect lower generated geometry");
    }
    @Test void authoredDisplayAndSeparateItemVisualProduceContextSpecificBakedMeshes()throws Exception {
        Path rp=resource("rp"),bp=behavior("bp",materials("opaque"),",\"minecraft:item_visual\":{\"geometry\":\"geometry.fixture\",\"material_instances\":"+materials("blend")+"}");
        write(rp,"models/blocks/model.geo.json",geometry(",\"item_display_transforms\":{\"gui\":{\"translation\":[16,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1],\"fit_to_frame\":false},\"firstperson_lefthand\":{\"translation\":[-8,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1]}}"));
        var result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertTrue(result.errors().isEmpty(),result.errors().toString());
        var display=json(result,"bedrock_compat:models/block/assets/minecraft/diamond_block/display/gui.json");
        assertFalse(display.has("parent"),"authored transforms must not be multiplied by Java block transforms");
        float x=display.getAsJsonArray("quads").get(0).getAsJsonObject().getAsJsonArray("vertices").get(0).getAsJsonArray().get(0).getAsFloat();
        assertTrue(x>=1.25f&&x<=1.75f);
        var select=json(result,"minecraft:items/diamond_block.json").getAsJsonObject("model");assertEquals("minecraft:display_context",select.get("property").getAsString());assertEquals(2,select.getAsJsonArray("cases").size());
    }
    @Test void unsupportedPermutationMissingMaterialsAndCyclesRetainJavaFallback()throws Exception {
        Path rp=resource("rp"),bp=behavior("bp","{\"*\":\"other\",\"other\":\"*\"}","");
        var result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertFalse(result.errors().isEmpty());assertTrue(result.resources().resources().isEmpty());
        write(bp,"blocks/visual.json",definition(materials("opaque"),"").replace("\"components\":","\"permutations\":[{\"condition\":\"q.block_state('a')\",\"components\":{}}],\"components\":"));
        result=pipeline().generate(List.of(pack(bp,BedrockPackType.BEHAVIOR),pack(rp,BedrockPackType.RESOURCE)));
        assertTrue(result.errors().get(0).contains("Unmapped block state"));assertTrue(result.resources().resources().isEmpty());
    }
    @Test void unboundResourceGeometryDoesNotInventABlockBinding()throws Exception {
        Path rp=resource("rp");write(rp,"blocks/visual.json",definition(materials("opaque"),""));
        assertTrue(pipeline().generate(List.of(pack(rp,BedrockPackType.RESOURCE))).resources().resources().isEmpty());
    }
    @Test void pivotsRotationAndScaleHaveNumericalMeaning() {
        var transform=JsonParser.parseString("{\"rotation\":[0,0,90],\"translation\":[2,3,4],\"scale\":[2,1,1],\"rotation_pivot\":[1,0,0],\"scale_pivot\":[1,0,0]}").getAsJsonObject();
        Vector3f actual=BedrockItemDisplayTransforms.matrix(transform,"fixed").transformPosition(new Vector3f(2,0,0));
        assertEquals(3,actual.x,1e-5);assertEquals(1,actual.y,1e-5);assertEquals(4,actual.z,1e-5);
        assertThrows(IllegalArgumentException.class,()->BedrockItemDisplayTransforms.matrix(JsonParser.parseString("{\"scale\":[5,1,1]}").getAsJsonObject(),"fixed"));
        assertNotNull(BedrockItemDisplayTransforms.matrix(new JsonObject(),"gui"));
    }
}
