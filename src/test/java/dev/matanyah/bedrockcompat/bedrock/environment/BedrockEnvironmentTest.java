package dev.matanyah.bedrockcompat.bedrock.environment;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockEnvironmentTest {
    static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    static JsonObject envelope(String type,String id,String definition){var o=new JsonObject();o.addProperty("type",type);o.addProperty("id",id);o.add("definition",json(definition));return o;}
    static BedrockEnvironment load(JsonObject... entries){return BedrockEnvironment.load(List.of(entries),error->{throw new AssertionError(error);});}
    static BedrockEnvironment biome(String components){return load(envelope("client_biome","minecraft:plains","{\"components\":"+components+"}"));}
    @Test void missingContextRetainsFallback(){assertNull(BedrockEnvironment.EMPTY.sky("plains",0));assertNull(BedrockEnvironment.EMPTY.fog("plains","water"));assertNull(BedrockEnvironment.EMPTY.tint("plains","water"));}
    @Test void skyColorAndNamespace(){assertEquals(0x123456,biome("{\"minecraft:sky_color\":{\"sky_color\":\"#123456\"}}").sky("plains",0));}
    @Test void groundTintsAreSeparate(){var e=biome("""
        {"minecraft:grass_appearance":{"color":"#123456"},"minecraft:foliage_appearance":{"color":[4,5,6]},
         "minecraft:dry_foliage_color":{"color":"#987654"},"minecraft:water_appearance":{"surface_color":"#abcdef","surface_opacity":0.4}}
        """);assertEquals(0x123456,e.tint("plains","grass"));assertEquals(0x040506,e.tint("plains","foliage"));assertEquals(0x987654,e.tint("plains","dry_foliage"));assertEquals(0xabcdef,e.tint("plains","water"));}
    @Test void colormapIsNotMistakenForDirectColor(){assertNull(biome("{\"minecraft:grass_appearance\":{\"color\":{\"color_map\":\"swamp_grass\"}}}").tint("plains","grass"));}
    @Test void invalidColorsNeverBecomeBlack(){for(String s:List.of("\"red\"","\"#1234\"","[1,2]","[256,0,0]","[-1,0,0]","null","{}"))assertNull(BedrockEnvironment.color(JsonParser.parseString(s)),s);}
    @Test void fogUnitsAndMedia(){var e=load(envelope("client_biome","minecraft:plains","{\"components\":{\"minecraft:fog_appearance\":{\"fog_identifier\":\"test:fog\"}}}"),
        envelope("fog_settings","test:fog","""
        {"distance":{"air":{"fog_start":0.2,"fog_end":0.75,"fog_color":"#010203","render_distance_type":"render"},
        "water":{"fog_start":2,"fog_end":17,"fog_color":"#040506","render_distance_type":"fixed"}}}
        """));assertEquals(192,e.fog("plains","air").distance(true,256));assertEquals(51.2,e.fog("plains","air").distance(false,256));assertEquals(17,e.fog("plains","water").distance(true,256));assertEquals(0x040506,e.fog("plains","water").color());}
    @Test void missingMediumFallsThroughDefaultStack(){var e=load(envelope("client_biome","minecraft:plains","{\"components\":{\"minecraft:fog_appearance\":{\"fog_identifier\":\"test:dry\"}}}"),
        envelope("legacy","minecraft:default","{\"fog_identifier\":\"test:default\"}"),envelope("fog_settings","test:dry","{\"distance\":{\"air\":{\"fog_end\":30}}}"),
        envelope("fog_settings","test:default","{\"distance\":{\"water\":{\"fog_end\":8}}}"));assertEquals(8,e.fog("plains","water").end());assertNull(e.fog("plains","lava"));}
    @Test void malformedFogFallsBack(){var e=load(envelope("legacy","minecraft:default","{\"fog_identifier\":\"bad\"}"),envelope("fog_settings","bad","{\"distance\":{\"air\":{\"fog_start\":20,\"fog_end\":2}}}"));assertNull(e.fog("plains","air"));}
    @Test void overflowingFogDistanceRetainsJavaFallback(){var e=load(envelope("legacy","minecraft:default","{\"fog_identifier\":\"huge\"}"),envelope("fog_settings","huge","{\"distance\":{\"air\":{\"fog_end\":1e300}}}"));assertNull(e.fog("plains","air").distance(true,256));}
    @Test void incompleteDistancesDoNotMixUnits(){var e=load(envelope("legacy","minecraft:default","{\"fog_identifier\":\"default\"}"),envelope("fog_settings","default","{\"distance\":{\"air\":{\"fog_end\":100}}}"),envelope("client_biome","minecraft:plains","{\"components\":{\"minecraft:fog_appearance\":{\"fog_identifier\":\"local\"}}}"),envelope("fog_settings","local","{\"distance\":{\"air\":{\"fog_start\":0.2,\"render_distance_type\":\"render\"}}}"));assertNull(e.fog("plains","air").end());}
    @Test void unknownIdentifiersAreNotInvented(){assertNull(biome("{\"minecraft:fog_appearance\":{\"fog_identifier\":\"missing\"}}").fog("plains","air"));}
    @Test void musicValuesAndMuteRemainDistinct(){var e=biome("{\"minecraft:biome_music\":{\"volume_multiplier\":0.4,\"music_definition\":\"\"}}");assertEquals(.4,BedrockEnvironment.number(e.value("plains","biome_music","volume_multiplier")));assertEquals("",BedrockEnvironment.string(e.value("plains","biome_music","music_definition")));assertNull(e.value("forest","biome_music","music_definition"));}
    @Test void ambientLoopNameIsPreserved(){assertEquals("test:wind",BedrockEnvironment.string(biome("{\"minecraft:ambient_sounds\":{\"loop\":\"test:wind\"}}").value("plains","ambient_sounds","loop")));}
    @Test void vvNoonClockAndWrap(){assertEquals(0,BedrockEnvironment.vvTime(6000));assertEquals(.5,BedrockEnvironment.vvTime(18000));assertEquals(.75,BedrockEnvironment.vvTime(0));assertEquals(.75,BedrockEnvironment.vvTime(-24000));}
    @Test void vvLinearColorAndCyclicEndpoints(){var curve=JsonParser.parseString("{\"0\":\"#000000\",\"0.5\":\"#ffffff\"}");assertEquals(0x808080,BedrockEnvironment.colorAt(curve,.25));assertEquals(0x808080,BedrockEnvironment.colorAt(curve,.75));assertEquals(0,BedrockEnvironment.colorAt(curve,1));}
    @Test void vvConstantAndInvalidFrames(){assertEquals(0x010203,BedrockEnvironment.colorAt(JsonParser.parseString("[1,2,3]"),.2));assertNull(BedrockEnvironment.colorAt(json("{\"oops\":\"#ffffff\"}"),0));}
    @Test void vvAssignmentAndStandardPrecedence(){var e=load(envelope("client_biome","minecraft:plains","{\"components\":{\"minecraft:atmosphere_identifier\":{\"atmosphere_identifier\":\"test:sky\"}}}"),envelope("atmosphere_settings","test:sky","{\"sky_zenith_color\":\"#123456\",\"sky_horizon_color\":\"#654321\"}"));assertEquals(0x123456,e.sky("plains",0));assertEquals(0x654321,e.horizon("plains",0));assertNull(e.sky("forest",0));}
    @Test void vvGlobalIsOnlySpecialFile(){var global=envelope("atmosphere_settings","test:custom","{\"sky_zenith_color\":\"#abcdef\"}");global.addProperty("source","atmospherics/atmospherics.json");assertEquals(0xabcdef,load(global).sky("any:biome",0));}
    @Test void lightingSelectionDoesNotSynthesizeLux(){var e=load(envelope("client_biome","minecraft:plains","{\"components\":{\"minecraft:lighting_identifier\":{\"lighting_identifier\":\"test:light\"}}}"),envelope("lighting_settings","test:light","{\"ambient\":{\"color\":\"#123456\",\"illuminance\":1000}}"));var light=e.referenced("plains","lighting_identifier",e.lighting(),"@global");assertEquals(0x123456,BedrockEnvironment.color(light.getAsJsonObject("ambient").get("color")));assertNull(e.referenced("forest","lighting_identifier",e.lighting(),"@global"));}
    @Test void missingComponentsIsARecoverableDocumentError(){var errors=new ArrayList<String>();var e=BedrockEnvironment.load(List.of(envelope("client_biome","minecraft:plains","{}")),errors::add);assertTrue(e.empty());assertEquals(1,errors.size());}
    @Test void errorsAreCollectedAndOtherDocumentsSurvive(){var errors=new ArrayList<String>();var e=BedrockEnvironment.load(List.of(json("{}"),envelope("fog_settings","test:ok","{}")),errors::add);assertEquals(1,errors.size());assertTrue(e.fogs().containsKey("test:ok"));}
    @Test void replacementSnapshotDoesNotRetainRemovedDefinitions(){assertNotNull(biome("{\"minecraft:sky_color\":{\"sky_color\":\"#123456\"}}").sky("plains",0));assertNull(load().sky("plains",0));}
}
