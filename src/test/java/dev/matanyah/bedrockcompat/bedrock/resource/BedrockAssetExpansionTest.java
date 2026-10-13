package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.joml.*;
import java.lang.Math;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAssetExpansionTest {
    @TempDir Path temp;
    private void write(String p,String text)throws Exception{Path f=temp.resolve(p);Files.createDirectories(f.getParent());Files.writeString(f,text);}
    private void fixture()throws Exception {
        Files.createDirectories(temp.resolve("textures/art"));var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xff112233);
        ImageIO.write(image,"PNG",temp.resolve("textures/art/sheet.png").toFile());
        write("textures/terrain_texture.json","{\"texture_data\":{\"sheet\":{\"textures\":\"textures/art/sheet\"}}}");
        write("textures/item_texture.json","{\"texture_data\":{\"icon\":{\"textures\":\"textures/art/sheet\"}}}");
    }
    private BedrockPack pack(){return new BedrockPack("test","","test",new BedrockVersion(List.of(1,0,0)),Optional.empty(),List.of(),List.of(),temp,"manifest.json",BedrockPackType.MIXED,Optional.empty(),List.of());}
    private JsonObject block(String extra) {
        return JsonParser.parseString("{\"format_version\":\"1.21.100\",\"minecraft:block\":{\"description\":{\"identifier\":\"test:stateful\",\"states\":{\"test:powered\":[false,true]}},\"components\":{\"minecraft:geometry\":\"minecraft:geometry.full_block\",\"minecraft:material_instances\":{\"*\":{\"texture\":\"sheet\"}}}"+extra+"}}").getAsJsonObject();
    }
    private BedrockTranslationPipeline.GenerationResult run(List<BedrockBlockStates.Variant> states)throws Exception {
        return new BedrockTranslationPipeline(List.of(new BedrockBlockAssetTranslator(new BedrockPackContentReader(),id->states))).generate(List.of(pack()));
    }
    private JsonObject json(BedrockTranslationPipeline.GenerationResult r,String id){byte[] data=r.resources().resources().get(Identifier.parse(id));assertNotNull(data,id);return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();}
    private JsonObject model(BedrockTranslationPipeline.GenerationResult r,String selector){String id=json(r,"test:blockstates/stateful.json").getAsJsonObject("variants").getAsJsonObject(selector).get("model").getAsString();Identifier parsed=Identifier.parse(id);return json(r,parsed.getNamespace()+":models/"+parsed.getPath()+".json");}
    @Test void orderedPermutationsMapRealStatesAndTransformGeometry()throws Exception {
        fixture();var root=block(",\"permutations\":[{\"condition\":\"q.block_state('test:powered')\",\"components\":{\"minecraft:transformation\":{\"translation\":[0.25,0,0],\"scale\":[0.5,1,0.5]}}},{\"condition\":\"q.block_state('test:powered')\",\"components\":{\"minecraft:material_instances\":{\"*\":{\"texture\":\"sheet\",\"face_dimming\":false}}}}]");
        write("blocks/test.json",root.toString());var result=run(List.of(new BedrockBlockStates.Variant("powered=false",Map.of("powered",false)),new BedrockBlockStates.Variant("powered=true",Map.of("powered",true))));
        assertTrue(result.errors().isEmpty(),result.errors().toString());assertEquals(2,json(result,"test:blockstates/stateful.json").getAsJsonObject("variants").size());
        var off=model(result,"powered=false");var on=model(result,"powered=true");
        assertTrue(off.getAsJsonArray("quads").get(0).getAsJsonObject().get("shade").getAsBoolean());assertFalse(on.getAsJsonArray("quads").get(0).getAsJsonObject().get("shade").getAsBoolean());
        double min=10,max=-10;for(var q:on.getAsJsonArray("quads"))for(var v:q.getAsJsonObject().getAsJsonArray("vertices")){double x=v.getAsJsonArray().get(0).getAsDouble();min=Math.min(min,x);max=Math.max(max,x);}
        assertEquals(.5,min,1e-6);assertEquals(1,max,1e-6);
    }
    @Test void stateExpressionVisibilityCanIntentionallyHideAllGeometry()throws Exception {
        fixture();var root=block("");root.getAsJsonObject("minecraft:block").getAsJsonObject("components").add("minecraft:geometry",JsonParser.parseString("{\"identifier\":\"minecraft:geometry.full_block\",\"bone_visibility\":{\"root\":\"!q.block_state('test:powered')\"}}"));
        write("blocks/test.json",root.toString());var result=run(List.of(new BedrockBlockStates.Variant("powered=false",Map.of("powered",false)),new BedrockBlockStates.Variant("powered=true",Map.of("powered",true))));
        assertTrue(result.errors().isEmpty(),result.errors().toString());assertEquals(0,model(result,"powered=true").getAsJsonArray("quads").size());assertEquals(6,model(result,"powered=false").getAsJsonArray("quads").size());
    }
    @Test void uvLockAndAuthoredCullingSurviveQuarterTurn()throws Exception {
        fixture();var root=block("");var c=root.getAsJsonObject("minecraft:block").getAsJsonObject("components");
        c.add("minecraft:geometry",JsonParser.parseString("{\"identifier\":\"minecraft:geometry.full_block\",\"uv_lock\":true,\"culling\":\"test:faces\",\"culling_layer\":\"test:glass\"}"));
        c.add("minecraft:transformation",JsonParser.parseString("{\"rotation\":[0,90,0]}"));
        write("blocks/test.json",root.toString());write("block_culling/faces.json","{\"format_version\":\"1.21.80\",\"minecraft:block_culling_rules\":{\"description\":{\"identifier\":\"test:faces\"},\"rules\":[{\"direction\":\"north\",\"condition\":\"same_block_permutation\",\"cull_against_full_and_opaque\":false,\"geometry_part\":{\"bone\":\"root\",\"cube\":0,\"face\":\"north\"}}]}}");
        var result=run(List.of(new BedrockBlockStates.Variant("powered=false",Map.of("powered",false))));assertTrue(result.errors().isEmpty(),result.errors().toString());
        var quads=model(result,"powered=false").getAsJsonArray("quads");var rule=quads.get(0).getAsJsonObject().getAsJsonArray("culling");assertEquals("west",rule.get(0).getAsJsonObject().get("direction").getAsString());
        assertEquals(1,java.util.stream.StreamSupport.stream(quads.spliterator(),false).filter(q->!q.getAsJsonObject().getAsJsonArray("culling").isEmpty()).count());
        // Up-face texture now compensates the world rotation instead of rotating with its vertices.
        var up=quads.get(4).getAsJsonObject().getAsJsonArray("vertices");assertNotEquals(up.get(0).getAsJsonArray().get(3),up.get(1).getAsJsonArray().get(3));
    }
    @Test void worldPivotsAndNegativeScaleAreFiniteAndNumerical() {
        var transform=JsonParser.parseString("{\"translation\":[0.25,0,0],\"scale\":[-1,2,1],\"scale_pivot\":[0.125,0,0]}").getAsJsonObject();
        var p=BedrockWorldTransform.matrix(transform).transformPosition(new Vector3f(4,2,0));
        assertEquals(4,p.x,1e-6);assertEquals(4,p.y,1e-6);
        assertThrows(IllegalArgumentException.class,()->BedrockWorldTransform.matrix(JsonParser.parseString("{\"rotation\":[0,45,0]}").getAsJsonObject()));
    }
    @Test void guiAutoFitBoundsAndAuthoredTranslationArePreserved() {
        var authored=JsonParser.parseString("{\"translation\":[2,3,0],\"rotation\":[0,0,0],\"scale\":[1,1,1]}").getAsJsonObject();
        var fit=BedrockItemDisplayTransforms.fitToFrame(BedrockItemDisplayTransforms.matrix(authored,"gui"),List.of(new Vector3f(-32,-16,0),new Vector3f(32,16,0)),new Vector3f(2,3,0));
        var left=fit.transformPosition(new Vector3f(-32,-16,0));var right=fit.transformPosition(new Vector3f(32,16,0));
        assertEquals(16,right.x-left.x,1e-6);assertEquals(2,(left.x+right.x)/2,1e-6);assertEquals(3,(left.y+right.y)/2,1e-6);
    }
    @Test void realStateDomainsAndUnavailableQueriesNeverInventValues() {
        var declared=JsonParser.parseString("{\"test:mode\":[0,1]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->BedrockBlockStates.map(new BedrockBlockStates.Variant("",Map.of()),declared));
        assertThrows(IllegalArgumentException.class,()->BedrockBlockStates.evaluate(new JsonPrimitive("q.time_of_day>0.5"),Map.of()));
        assertThrows(IllegalArgumentException.class,()->BedrockBlockStates.evaluate(new JsonPrimitive("v.unknown"),Map.of()));
        assertEquals(1d,BedrockBlockStates.evaluate(new JsonPrimitive(true),Map.of()));
    }
    @Test void versionedWorldPivotsUvLockAndMissingCullingPartsRetainFallback()throws Exception {
        fixture();var root=block("");root.addProperty("format_version","1.20.0");var components=root.getAsJsonObject("minecraft:block").getAsJsonObject("components");
        components.add("minecraft:geometry",JsonParser.parseString("{\"identifier\":\"minecraft:geometry.full_block\",\"uv_lock\":true}"));
        write("blocks/test.json",root.toString());var states=List.of(new BedrockBlockStates.Variant("powered=false",Map.<String,Object>of("powered",false)));
        var result=run(states);assertTrue(result.errors().getFirst().contains("1.21.100"));assertTrue(result.resources().resources().isEmpty());
        components.addProperty("minecraft:geometry","minecraft:geometry.full_block");components.add("minecraft:transformation",JsonParser.parseString("{\"rotation_pivot\":[0,0,0]}"));write("blocks/test.json",root.toString());
        result=run(states);assertTrue(result.errors().getFirst().contains("1.21.0"));assertTrue(result.resources().resources().isEmpty());
        root.addProperty("format_version","1.21.100");components.remove("minecraft:transformation");components.add("minecraft:geometry",JsonParser.parseString("{\"identifier\":\"minecraft:geometry.full_block\",\"culling\":\"test:invalid\"}"));
        write("blocks/test.json",root.toString());write("block_culling/invalid.json","{\"minecraft:block_culling_rules\":{\"description\":{\"identifier\":\"test:invalid\"},\"rules\":[{\"direction\":\"north\",\"geometry_part\":{\"bone\":\"root\",\"cube\":9}}]}}");
        result=run(states);assertTrue(result.errors().getFirst().contains("missing cube"));assertTrue(result.resources().resources().isEmpty());
    }
    @Test void legacyAndModernNonBlockItemDefinitionsConsumeCrossPackAtlas()throws Exception {
        fixture();write("items/stick.json","{\"format_version\":\"1.10.0\",\"minecraft:item\":{\"description\":{\"identifier\":\"minecraft:stick\"},\"components\":{\"minecraft:icon\":\"icon\"}}}");
        write("items/emerald.json","{\"format_version\":\"1.21.60\",\"minecraft:item\":{\"description\":{\"identifier\":\"minecraft:emerald\"},\"components\":{\"minecraft:icon\":{\"textures\":{\"default\":\"icon\"}}}}}");
        var pipeline=new BedrockTranslationPipeline(List.of(new BedrockItemAssetTranslator(new BedrockPackContentReader())));var result=pipeline.generate(List.of(pack()));
        assertTrue(result.errors().isEmpty(),result.errors().toString());assertNotNull(json(result,"minecraft:items/stick.json"));assertNotNull(json(result,"minecraft:items/emerald.json"));
        assertEquals("minecraft:item/generated",json(result,"bedrock_compat:models/item/assets/minecraft/stick.json").get("parent").getAsString());
        write("items/stick.json","{\"minecraft:item\":{\"description\":{\"identifier\":\"minecraft:stick\"},\"components\":{\"minecraft:icon\":\"missing\"}}}");
        result=pipeline.generate(List.of(pack()));assertFalse(result.resources().resources().containsKey(Identifier.parse("minecraft:items/stick.json")));
        assertTrue(pipeline.generate(List.of()).resources().resources().isEmpty());
    }
}
