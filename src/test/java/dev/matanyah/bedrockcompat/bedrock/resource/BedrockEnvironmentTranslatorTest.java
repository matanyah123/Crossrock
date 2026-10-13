package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.environment.BedrockEnvironment;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockEnvironmentTranslatorTest {
    @TempDir Path directory;
    BedrockPack pack(String name)throws Exception {var v=new BedrockVersion(List.of(1,0,0));var path=directory.resolve(name);Files.createDirectories(path);return new BedrockPack(name,"",name,v,Optional.empty(),List.of(new BedrockModule(name,"resources",v,"")),List.of(),path,"manifest.json",BedrockPackType.RESOURCE,Optional.empty(),List.of());}
    void write(BedrockPack pack,String name,String data)throws Exception {var path=pack.source().resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,data);}
    static String modern(String color){return "{\"minecraft:client_biome\":{\"description\":{\"identifier\":\"plains\"},\"components\":{\"minecraft:sky_color\":{\"sky_color\":\""+color+"\"}}}}";}
    BedrockTranslationPipeline pipeline(){return new BedrockTranslationPipeline(List.of(new BedrockEnvironmentTranslator(new BedrockPackContentReader())));}
    BedrockEnvironment state(BedrockTranslationPipeline.GenerationResult result){return BedrockEnvironment.load(result.resources().resources().values().stream().map(bytes->JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()).toList(),error->fail(error));}
    @Test void packPriorityIsByDefinitionIdentityNotFilename()throws Exception {var low=pack("low");var high=pack("high");write(low,"biomes/a.client_biome.json",modern("#111111"));write(high,"biomes_client/z.json",modern("#222222"));assertEquals(0x222222,state(pipeline().generate(List.of(high,low))).sky("plains",0));}
    @Test void selectedSubpackReplacesBaseDocument()throws Exception {var pack=pack("sub");write(pack,"biomes/a.json",modern("#111111"));write(pack,"subpacks/alternate/biomes/a.json",modern("#222222"));var result=pipeline().generateConfigured(List.of(new BedrockTranslationPipeline.ConfiguredPack(pack,new BedrockPackConfiguration(Optional.of("alternate"),Map.of()))));assertEquals(0x222222,state(result).sky("plains",0));}
    @Test void higherLegacyReplacesLowerModern()throws Exception {var low=pack("low");var high=pack("high");write(low,"biomes/a.json",modern("#111111"));write(high,"biomes_client.json","{\"biomes\":{\"plains\":{\"sky_color\":\"#222222\",\"water_surface_color\":\"#abcdef\"}}}");var state=state(pipeline().generate(List.of(high,low)));assertEquals(0x222222,state.sky("plains",0));assertEquals(0xabcdef,state.tint("plains","water"));}
    @Test void modernBeatsLegacyWithinSamePack()throws Exception {var pack=pack("both");write(pack,"biomes/a.json",modern("#222222"));write(pack,"biomes_client.json","{\"biomes\":{\"plains\":{\"sky_color\":\"#111111\"}}}");assertEquals(0x222222,state(pipeline().generate(List.of(pack))).sky("plains",0));}
    @Test void malformedDocumentDoesNotDiscardValidOnes()throws Exception {var pack=pack("bad");write(pack,"biomes/a.json",modern("#222222"));write(pack,"fogs/bad.json","{");var result=pipeline().generate(List.of(pack));assertEquals(1,result.errors().size());assertEquals(0x222222,state(result).sky("plains",0));}
    @Test void globalIdentityDoesNotDependOnAuthoredIdentifier()throws Exception {var low=pack("low");var high=pack("high");write(low,"atmospherics/atmospherics.json","{\"minecraft:atmosphere_settings\":{\"description\":{\"identifier\":\"test:low\"},\"sky_zenith_color\":\"#111111\"}}");write(high,"atmospherics/atmospherics.json","{\"minecraft:atmosphere_settings\":{\"description\":{\"identifier\":\"test:high\"},\"sky_zenith_color\":\"#222222\"}}");assertEquals(0x222222,state(pipeline().generate(List.of(high,low))).sky("plains",0));}
}
