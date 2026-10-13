package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PresentationResourcesTest {
    @Test void splashesReplaceAndMergeInPriorityOrder() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        BedrockPresentationTranslator.translate(Map.of("splashes.json",bytes("{\"splashes\":[\"Low\"]}")),store);
        BedrockPresentationTranslator.translate(Map.of("splashes.json",bytes("{\"splashes\":[\"§aHigh\",\"Two\\nlines\"],\"canMerge\":true}")),store);
        assertEquals("Low\n§aHigh\nTwo lines",text(store,"texts/splashes.txt"));
        BedrockPresentationTranslator.translate(Map.of("texts/splashes.json",bytes("{\"splashes\":[\"Replace\"]}")),store);
        assertEquals("Replace",text(store,"texts/splashes.txt"));
    }
    @Test void hudAndPanoramaBecomeValidJavaSpritesWithoutAnimationMetadata() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        var content=new HashMap<String,byte[]>();
        content.put("textures/ui/heart.png",png(9,9));
        content.put("textures/ui/poison_heart.png",png(9,9));
        content.put("textures/ui/panorama_0.png",png(32,32));
        content.put("textures/ui/experiencebarempty.png",png(13,5));
        for(int i=0;i<9;i++)content.put("textures/ui/hotbar_"+i+".png",png(40,44));
        BedrockPresentationTranslator.translate(content,store);
        assertNotNull(data(store,"textures/gui/title/background/panorama_0.png"));
        assertEquals(1024,image(store,"textures/gui/title/background/panorama_0.png").getWidth());
        assertNotNull(data(store,"textures/gui/sprites/hud/heart/poisoned_full.png"));
        var xp=image(store,"textures/gui/sprites/hud/experience_bar_background.png");
        assertEquals(182,xp.getWidth());
        assertEquals(0xff000005,xp.getRGB(5,0));
        assertEquals(0xff000006,xp.getRGB(100,0));
        assertEquals(0xff00000c,xp.getRGB(181,0));
        var hotbar=image(store,"textures/gui/sprites/hud/hotbar.png");
        assertEquals(364,hotbar.getWidth());assertEquals(44,hotbar.getHeight());
        assertFalse(store.snapshot().resources().keySet().stream().anyMatch(id->id.getPath().endsWith(".mcmeta")));
    }
    @Test void malformedPresentationDoesNotDiscardGoodAssets() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        assertThrows(IOException.class,()->BedrockPresentationTranslator.translate(Map.of(
            "textures/ui/panorama_0.png",bytes("invalid"),
            "textures/ui/heart.png",png(9,9),
            "splashes.json",bytes("{\"splashes\":[\"Still works\",{}]}")),store));
        assertEquals("Still works",text(store,"texts/splashes.txt"));
        assertNotNull(data(store,"textures/gui/sprites/hud/heart/full.png"));
    }
    @Test void doubleSlabOverrideLeavesVanillaSingleSlabsUntouched() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        BedrockBlockModelGenerator.generate(Map.of("blocks.json",bytes("""
            {"normal_stone_double_slab":{"textures":{"up":"my_top","down":"my_bottom","side":"my_side"}},
             "not_java_double_slab":{"textures":"unknown"}}
            """)),store);
        var state=JsonParser.parseString(text(store,"blockstates/stone_slab.json")).getAsJsonObject().getAsJsonObject("variants");
        assertEquals("minecraft:block/stone_slab",state.getAsJsonObject("type=bottom").get("model").getAsString());
        assertEquals("minecraft:block/stone_slab_top",state.getAsJsonObject("type=top").get("model").getAsString());
        assertEquals("minecraft:block/crossrock/stone_slab_double",state.getAsJsonObject("type=double").get("model").getAsString());
        assertTrue(text(store,"models/block/crossrock/stone_slab_double.json").contains("my_side"));
        assertNull(data(store,"blockstates/not_java_slab.json"));
    }
    @Test void textureOnlyDoubleSlabUsesDedicatedAtlasTiles() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        BedrockBlockModelGenerator.generate(Map.of("textures/terrain_texture.json",bytes("""
            {"texture_data":{"oak_double_slab":{"textures":"textures/blocks/custom/oak_full"},
             "oak_double_slab_side":{"textures":"textures/blocks/custom/oak_full_side"}}}
            """)),store);
        var model=JsonParser.parseString(text(store,"models/block/crossrock/oak_slab_double.json")).getAsJsonObject();
        assertEquals("minecraft:block/custom/oak_full",model.getAsJsonObject("textures").get("up").getAsString());
        assertEquals("minecraft:block/custom/oak_full_side",model.getAsJsonObject("textures").get("north").getAsString());
        var variants=JsonParser.parseString(text(store,"blockstates/oak_slab.json")).getAsJsonObject().getAsJsonObject("variants");
        assertEquals("minecraft:block/oak_slab",variants.getAsJsonObject("type=bottom").get("model").getAsString());
        assertEquals("minecraft:block/crossrock/oak_slab_double",variants.getAsJsonObject("type=double").get("model").getAsString());
    }
    @Test void customBedSplitsGeometryAndInterpolatesUvsAtBoundary() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        BedrockBedModelTranslator.translate(bedContent(false),store);
        var head=JsonParser.parseString(text(store,"models/block/red_bed_head.json")).getAsJsonObject();
        var foot=JsonParser.parseString(text(store,"models/block/red_bed_foot.json")).getAsJsonObject();
        assertTrue(head.getAsJsonArray("elements").size()>0);
        assertTrue(foot.getAsJsonArray("elements").size()>0);
        for(var model:List.of(head,foot)) for(var entry:model.getAsJsonArray("elements")) {
            var e=entry.getAsJsonObject();
            assertTrue(e.getAsJsonArray("from").get(2).getAsDouble()>=0);
            assertTrue(e.getAsJsonArray("to").get(2).getAsDouble()<=16);
            assertEquals(1,e.getAsJsonObject("faces").size());
        }
        var headUp=findFace(head,"up");var footUp=findFace(foot,"up");
        assertNotNull(findFace(head,"north"));
        assertNotNull(findFace(foot,"south"));
        assertNotEquals(headUp.get("uv"),footUp.get("uv"));
        assertNotNull(store.snapshot().resources().get(Identifier.fromNamespaceAndPath("bedrock_compat","textures/block/bed/red.png")));
    }
    @Test void unsupportedBedRotationDoesNotPublishBrokenModels() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        assertThrows(IOException.class,()->BedrockBedModelTranslator.translate(bedContent(true),store));
        assertTrue(store.snapshot().resources().isEmpty());
    }
    @Test void legacyBedGeometryDoesNotRequireRepeatingVanillaEntityDefinition() throws Exception {
        var store=new InMemoryGeneratedResourceStore();
        BedrockBedModelTranslator.translate(Map.of("models/mobs.json",bytes("""
            {"geometry.bed":{"texturewidth":64,"textureheight":64,"bones":[
              {"name":"bed","cubes":[{"origin":[0,0,0],"size":[16,32,6],"uv":[0,0]}]}]}}
            """),"textures/entity/bed/red.png",png(64,64)),store);
        assertNotNull(data(store,"models/block/red_bed_head.json"));
        assertNotNull(data(store,"models/block/red_bed_foot.json"));
    }
    private static Map<String,byte[]> bedContent(boolean rotated) throws Exception {
        return Map.of("entity/bed.entity.json",bytes("""
            {"minecraft:client_entity":{"description":{"identifier":"minecraft:bed","geometry":{"default":"geometry.bed"},"textures":{"red":"textures/entity/bed/red"}}}}
            """),"models/bed.geo.json",bytes("""
            {"minecraft:geometry":[{"description":{"identifier":"geometry.bed","texture_width":64,"texture_height":64},"bones":[{"name":"bed","rotation":[0,%s,0],"cubes":[{"origin":[0,0,0],"size":[16,32,6],"uv":[0,0]}]}]}]}
            """.formatted(rotated?"30":"0")),"textures/entity/bed/red.png",png(64,64));
    }
    private static JsonObject findFace(JsonObject model,String name) {
        for(var e:model.getAsJsonArray("elements")) {
            var faces=e.getAsJsonObject().getAsJsonObject("faces");if(faces.has(name))return faces.getAsJsonObject(name);
        }
        throw new AssertionError("Missing "+name);
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static byte[] data(InMemoryGeneratedResourceStore store,String path){return store.snapshot().resources().get(Identifier.withDefaultNamespace(path));}
    private static String text(InMemoryGeneratedResourceStore store,String path){return new String(data(store,path),StandardCharsets.UTF_8);}
    private static BufferedImage image(InMemoryGeneratedResourceStore store,String path)throws Exception{return ImageIO.read(new ByteArrayInputStream(data(store,path)));}
    private static byte[] png(int w,int h)throws Exception{
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,0xff000000|x|y<<8);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
}
