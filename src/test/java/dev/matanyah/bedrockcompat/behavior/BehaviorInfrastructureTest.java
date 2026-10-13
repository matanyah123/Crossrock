package dev.matanyah.bedrockcompat.behavior;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import dev.matanyah.bedrockcompat.bedrock.format.BedrockManifestParser;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.network.chat.Component;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
class BehaviorInfrastructureTest {
    @TempDir Path directory;
    private BedrockPack pack(String id,String type,String dependency,String version){
        String deps=dependency==null?"[]":"[{\"uuid\":\""+dependency+"\",\"version\":[1,0,0]}]";
        return new BedrockManifestParser().parse(("{\"format_version\":2,\"header\":{\"name\":\"test\",\"description\":\"test\",\"uuid\":\""+id+"\",\"version\":"+version+"},\"modules\":[{\"type\":\""+type+"\",\"uuid\":\"00000000-0000-0000-0000-000000000009\",\"version\":[1,0,0]}],\"dependencies\":"+deps+"}").getBytes(),directory.resolve(id),"manifest.json",null);
    }
    private static final String A="00000000-0000-0000-0000-000000000001",B="00000000-0000-0000-0000-000000000002";
    @Test void mutualDependenciesResolveAndVersionMismatchIsRejected(){
        var a=pack(A,"data",B,"[1,0,0]");var b=pack(B,"resources",A,"[1,0,0]");assertEquals(2,new PackCatalog(List.of(a,b)).closure(List.of(a)).size());
        assertThrows(IllegalArgumentException.class,()->new PackCatalog(List.of(a,pack(B,"resources",null,"[2,0,0]"))).closure(List.of(a)));
        assertThrows(IllegalArgumentException.class,()->new PackCatalog(List.of(a)).closure(List.of(a)));
    }
    @Test void contentHashIsOrderedAndIncludesBoundaries(){assertEquals(PackCatalog.hash(Map.of("a",new byte[]{1},"b",new byte[]{2})),PackCatalog.hash(new LinkedHashMap<>(Map.of("b",new byte[]{2},"a",new byte[]{1}))));assertNotEquals(PackCatalog.hash(Map.of("a",new byte[]{1,2})),PackCatalog.hash(Map.of("a",new byte[]{1},"b",new byte[]{2})));}
    @Test void pathsCannotEscapeArchiveRoot(){for(String p:List.of("../x","/x","a/../x","a\\x","C:/x"))assertThrows(IllegalArgumentException.class,()->PackCatalog.safePath(p));}
    private BehaviorPackResources.Content content(String uuid,int value){
        var p=pack(uuid,"data",null,"[1,0,0]");String json="{\"minecraft:entity\":{\"description\":{\"identifier\":\"test:actor\",\"properties\":{\"test:value\":{\"type\":\"int\",\"range\":[0,4],\"default\":"+value+",\"client_sync\":true},\"test:private\":{\"type\":\"bool\",\"default\":false}}},\"components\":{\"future:component\":{\"arbitrary\":[1,2,3]}}}}";
        Map<String,byte[]> files=Map.of("entities/actor.json",json.getBytes());return new BehaviorPackResources.Content(p,files,PackCatalog.hash(files));
    }
    @Test void stackOverridesAreTypedAndLossless(){var low=content(A,1);var high=content(B,3);var defs=new BehaviorDefinitions(List.of(low,high));assertEquals(3,defs.defaults("test:actor").get("test:value").getAsInt());assertTrue(defs.registry("minecraft:entity").get("test:actor").body().getAsJsonObject("components").has("future:component"));assertThrows(IllegalArgumentException.class,()->defs.validate("test:actor","test:value",new JsonPrimitive(5)));assertThrows(IllegalArgumentException.class,()->defs.validate("test:actor","test:value",new JsonPrimitive("3")));assertEquals(Set.of("test:value"),defs.clientProperties("test:actor",defs.defaults("test:actor")).keySet());}
    @Test void returnedDefinitionsCannotMutatePublishedGeneration(){var defs=new BehaviorDefinitions(List.of(content(A,1)));defs.registry("minecraft:entity").get("test:actor").body().remove("description");assertEquals(1,defs.defaults("test:actor").get("test:value").getAsInt());}
    @Test void worldStatePersistsWithoutTouchingOtherNamespaces()throws Exception{Path path=directory.resolve("world/data/state.json");var state=new BehaviorStateStore(path);state.put("test:one","value",new JsonPrimitive(7));state.put("test:two","value",new JsonPrimitive(true));state.save();var loaded=new BehaviorStateStore(path);assertEquals(7,loaded.snapshot().getAsJsonObject("test:one").get("value").getAsInt());loaded.put("test:one","value",null);loaded.save();assertFalse(new BehaviorStateStore(path).snapshot().getAsJsonObject("test:one").has("value"));assertTrue(loaded.snapshot().getAsJsonObject("test:two").get("value").getAsBoolean());}
    @Test void resourceProfileIsAnIndividualPackAndRejectsInvalidDefinitions(){var c=content(A,2);var location=new PackLocationInfo(PackCatalog.datapackId(c.pack()),Component.literal("test"),net.minecraft.server.packs.repository.PackSource.DEFAULT,Optional.empty());try(var manager=new MultiPackResourceManager(PackType.SERVER_DATA,List.of(new BehaviorPackResources(location,c.pack(),List.of(c))))){var profile=BehaviorProfile.compile(manager);assertEquals(1,profile.packs().size());assertEquals(2,profile.definitions().defaults("test:actor").get("test:value").getAsInt());assertFalse(profile.hash().isBlank());assertTrue(profile.bundle().length>0);}}
    @Test void importedPacksAreAtomicAndCannotOverwrite()throws Exception {
        Path source=directory.resolve("source.mcpack");Files.write(source,new byte[]{1,2,3});Path target=directory.resolve("installed");
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(BedrockPackImporter.copy(source,target)));
        assertThrows(java.io.IOException.class,()->BedrockPackImporter.copy(source,target));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(target.resolve("source.mcpack")));
    }
    @Test void bundleValidationRejectsUnsafeRootsAndAcceptsRealProfile()throws Exception {
        var c=content(A,2);var location=new PackLocationInfo(PackCatalog.datapackId(c.pack()),Component.literal("test"),net.minecraft.server.packs.repository.PackSource.DEFAULT,Optional.empty());
        try(var manager=new MultiPackResourceManager(PackType.SERVER_DATA,List.of(new BehaviorPackResources(location,c.pack(),List.of(c))))){BehaviorBundles.validate(BehaviorProfile.compile(manager).bundle());}
        var bytes=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(bytes)){zip.putNextEntry(new java.util.zip.ZipEntry("../manifest.json"));zip.write("{}".getBytes());zip.closeEntry();}
        assertThrows(IllegalArgumentException.class,()->BehaviorBundles.validate(bytes.toByteArray()));
        assertThrows(java.io.IOException.class,()->BehaviorBundles.validate(new byte[0]));
    }
}
