package dev.matanyah.bedrockcompat.bedrock.resource;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import net.minecraft.resources.Identifier;
import static org.junit.jupiter.api.Assertions.*;

class GrassTintTest {
    @org.junit.jupiter.api.io.TempDir Path folder;
    @Test void sideGrassAndDirtPartitionWithoutCoplanarOverlap() throws Exception {
        var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)image.setRGB(x,y,(x+y)%3==0?0xffcccccc:0x018a5737);
        assertPartition(image);
    }
    @Test void fullyOpaqueGrassHasNoHiddenBaseSide() throws Exception {
        var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)image.setRGB(x,y,0xffcccccc);
        assertPartition(image);
    }
    @Test void fullyUntintedDirtHasNoTintedSide() throws Exception {
        assertPartition(new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB));
    }
    private static void assertPartition(BufferedImage image)throws Exception {
        var model=new com.google.gson.JsonObject();var elements=new com.google.gson.JsonArray();model.add("elements",elements);
        for(String texture:List.of("#side","#base")) {
            var element=new com.google.gson.JsonObject();element.add("from",com.google.gson.JsonParser.parseString("[0,0,0]"));element.add("to",com.google.gson.JsonParser.parseString("[16,16,16]"));
            var faces=new com.google.gson.JsonObject();element.add("faces",faces);
            for(String direction:List.of("north","south","east","west")){var face=new com.google.gson.JsonObject();face.addProperty("texture",texture);if(texture.equals("#side"))face.addProperty("tintindex",0);faces.add(direction,face);}elements.add(element);
        }
        var png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);GrassTintGeometry.clipOverlay(model,png.toByteArray());
        for(var element:elements)assertFalse(element.getAsJsonObject().getAsJsonObject("faces").isEmpty(),"Java rejects empty model elements");
        int size=image.getWidth();
        for(String direction:List.of("north","south","east","west")) {
            int[][] covered=new int[size][size];
            for(var node:elements) {
                var element=node.getAsJsonObject();var face=element.getAsJsonObject("faces").getAsJsonObject(direction);if(face==null)continue;
                var uv=face.getAsJsonArray("uv");assertNotNull(uv,"Full base face survived clipping");
                int x0=(int)Math.round(uv.get(0).getAsDouble()*size/16),y0=(int)Math.round(uv.get(1).getAsDouble()*size/16);
                int x1=(int)Math.round(uv.get(2).getAsDouble()*size/16),y1=(int)Math.round(uv.get(3).getAsDouble()*size/16);
                boolean tinted=face.has("tintindex");assertEquals(tinted?"#side":"#base",face.get("texture").getAsString());assertEquals(direction,face.get("cullface").getAsString());
                var from=element.getAsJsonArray("from");var to=element.getAsJsonArray("to");int normal=direction.equals("east")||direction.equals("west")?0:2;
                assertEquals(direction.equals("east")||direction.equals("south")?16d:0d,from.get(normal).getAsDouble());assertEquals(from.get(normal),to.get(normal));
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){covered[y][x]++;assertEquals((image.getRGB(x,y)>>>24)>1,tinted,"Wrong region tint");}
            }
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)assertEquals(1,covered[y][x],direction+": hole or overlapping side faces");
        }
    }
    @Test void textureOnlyTgaAliasGeneratesNoTintedGeometryOverDirt() throws Exception {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffcccccc);image.setRGB(1,0,0x018a5737);
        image.setRGB(0,1,0x018a5737);image.setRGB(1,1,0xffcccccc);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        Files.createDirectories(folder.resolve("textures/blocks"));
        // This also covers PNG bytes under a TGA extension and a terrain alias to TGA.
        Files.write(folder.resolve("textures/blocks/grass_side.tga"),bytes.toByteArray());
        Files.writeString(folder.resolve("textures/terrain_texture.json"),"""
            {"texture_data":{"grass_side":{"textures":"textures/blocks/grass_side"}}}
            """);
        var version=new BedrockVersion(List.of(1,0,0));
        var pack=new BedrockPack("test","","test",version,Optional.empty(),List.of(),List.of(),folder,
            "manifest.json",BedrockPackType.RESOURCE,Optional.empty(),List.of());
        var result=new BedrockTranslationPipeline(List.of(new VanillaTextureTranslator(new BedrockPackContentReader()))).generate(List.of(pack));
        assertTrue(result.errors().isEmpty(),result.errors().toString());
        var resources=result.resources().resources();
        var overlay=ImageIO.read(new ByteArrayInputStream(resources.get(Identifier.withDefaultNamespace("textures/block/grass_block_side_overlay.png"))));
        assertEquals(0,overlay.getRGB(1,0)>>>24,"Alias pass restored the original dirt mask");
        var model=com.google.gson.JsonParser.parseString(new String(resources.get(Identifier.withDefaultNamespace("models/block/grass_block.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        int tintedSides=0;
        for(var element:model.getAsJsonArray("elements"))for(var entry:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
            var face=entry.getValue().getAsJsonObject();
            if(entry.getKey().equals("down"))assertFalse(face.has("tintindex"));
            if(!face.has("tintindex")||entry.getKey().equals("up"))continue;
            tintedSides++;
            var uv=face.getAsJsonArray("uv");
            int x=uv.get(0).getAsInt()/8,y=uv.get(1).getAsInt()/8;
            assertEquals(255,image.getRGB(x,y)>>>24,"Tinted rectangle overlaps dirt");
            assertEquals(8,uv.get(2).getAsInt()-uv.get(0).getAsInt());
            assertEquals(8,uv.get(3).getAsInt()-uv.get(1).getAsInt());
        }
        assertEquals(8,tintedSides);
    }
    @Test void bedrockUntintedDirtIsAbsentFromJavaTintOverlay() throws Exception {
        var image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffcccccc);image.setRGB(1,0,0x018a5737);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        var result=ImageIO.read(new ByteArrayInputStream(JavaTextureOpacity.conformToVanilla(
            "textures/block/grass_block_side_overlay.png",bytes.toByteArray())));
        assertEquals(255,result.getRGB(0,0)>>>24);
        assertEquals(0,result.getRGB(1,0)>>>24);
    }
}
