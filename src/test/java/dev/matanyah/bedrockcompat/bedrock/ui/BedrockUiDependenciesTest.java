package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockUiTranslator;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class BedrockUiDependenciesTest {
    @Test void verifiedFileNamespaceDoesNotInventUnknownFileIdentities(){
        var p=BedrockUi.compile(List.of(BedrockUiTest.source("ui/chest_screen.json","{\"small_chest_screen@common.empty_panel\":{}}")));
        assertTrue(p.roots().containsKey("chest.small_chest_screen"));assertTrue(p.errors().isEmpty());
        assertFalse(BedrockUi.compile(List.of(BedrockUiTest.source("ui/arbitrary.json","{\"root\":{}}"))).errors().isEmpty());
    }
    @Test void traceRetainsPathsAndUnknownOwnershipAndDynamicReferences(){
        var report=BedrockUiDependencies.inspect(List.of(BedrockUiTest.source("ui/a.json","""
            {"namespace":"a","root@common.empty_panel":{"controls":[{"kid@missing.base":{}},{"dynamic@$template":{}}]}}
            """)));
        var missing=report.failures().getFirst();assertEquals("reference-resolution",missing.stage());assertTrue(missing.pointer().contains("controls/0/kid@missing.base"));assertEquals(List.of("a.root","missing.base"),missing.chain());
        assertEquals("unknown",report.edges().stream().filter(e->e.reference().equals("missing.base")).findFirst().orElseThrow().ownership());
        assertTrue(report.edges().stream().anyMatch(e->e.status().equals("dynamic")));
    }
    @Test void reachableGraphTerminatesCyclesAndRespectsPackOverride(){
        var report=BedrockUiDependencies.inspect(List.of(BedrockUiTest.source("ui/a.json","{\"namespace\":\"a\",\"one@two\":{},\"two@one\":{}}"),BedrockUiTest.source("ui/common.json","{\"namespace\":\"common\",\"empty_panel\":{}}")));
        assertEquals(2,report.reachable("a.one").size());assertEquals("pack",report.definitions().stream().filter(d->d.id().equals("common.empty_panel")).findFirst().orElseThrow().ownership());
    }
    @Test void structuralDepthIsBounded(){
        var root=new JsonObject();root.addProperty("namespace","a");var value=new JsonObject();root.add("root",value);
        for(int i=0;i<70;i++){var child=new JsonObject();value.add("child",child);value=child;}
        assertTrue(BedrockUiDependencies.inspect(List.of(new BedrockUi.Source("ui/a.json",root))).failures().stream().anyMatch(f->f.stage().equals("safety")));
    }
    @Test void dottedVariableValuesAreNotClaimedToBeMissingTemplates(){
        var report=BedrockUiDependencies.inspect(List.of(BedrockUiTest.source("ui/a.json","{\"namespace\":\"a\",\"root\":{\"$action\":\"button.menu_exit\"}}")));
        assertTrue(report.failures().isEmpty());assertEquals("unresolved-candidate",report.edges().getFirst().status());
    }
    @Test void expansionBudgetIsPerRootButStillRejectsOversizedRoots(){
        var doc=new JsonObject();doc.addProperty("namespace","budget");
        for(int root=0;root<5;root++){
            var value=new JsonObject();var controls=new JsonArray();
            for(int child=0;child<3400;child++){var named=new JsonObject();named.add("child"+child,new JsonObject());controls.add(named);}
            value.add("controls",controls);doc.add("root"+root,value);
        }
        var p=BedrockUi.compile(List.of(new BedrockUi.Source("ui/budget.json",doc)));
        assertEquals(5,p.roots().size());assertTrue(p.errors().isEmpty(),p.errors().toString());
        var oversized=new JsonObject();oversized.addProperty("namespace","budget");var root=new JsonObject();var controls=new JsonArray();
        for(int child=0;child<16385;child++){var named=new JsonObject();named.add("child"+child,new JsonObject());controls.add(named);}
        root.add("controls",controls);oversized.add("root",root);
        assertTrue(BedrockUi.compile(List.of(new BedrockUi.Source("ui/oversized.json",oversized))).roots().isEmpty());
    }
    @Test void rootDiagnosticsRemainAvailableAfterLogSummaryLimit(){
        var doc=new JsonObject();doc.addProperty("namespace","many");
        for(int i=0;i<300;i++)doc.add("root"+i+"@unknown.template",new JsonObject());
        var program=BedrockUi.compile(List.of(new BedrockUi.Source("ui/many.json",doc)));
        assertEquals(256,program.errors().size());assertEquals(300,program.rootFailures().size());
        assertTrue(program.rootFailures().get("many.root299").contains("ui/many.json#/root299@unknown.template"));
    }
    @Test void realPackRegistrationResolvesPackOwnedPaginationWithoutChangingArchive()throws Exception {
        String pack=System.getenv("BEDROCK_UI_REFERENCE_PACK");if(pack==null)return;
        Path path=Path.of(pack);byte[] before=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        var files=new LinkedHashMap<String,byte[]>();
        try(var zip=new ZipFile(path.toFile())){
            var defs=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("ui/_ui_defs.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            files.put("ui/_ui_defs.json",defs.toString().getBytes(StandardCharsets.UTF_8));
            for(var entry:defs.getAsJsonArray("ui_defs")){String name=entry.getAsString();assertTrue(BedrockUiTranslator.safe(name),name);var file=zip.getEntry(name);if(file!=null)files.put(name,zip.getInputStream(file).readAllBytes());}
            for(String name:List.of("ui/chest_screen.json","ui/_global_variables.json"))files.put(name,zip.getInputStream(zip.getEntry(name)).readAllBytes());
        }
        var sink=new InMemoryGeneratedResourceStore();String registrationFailure="";
        try{BedrockUiTranslator.publish(files,sink);}catch(java.io.IOException e){registrationFailure=e.getMessage();}
        var sources=new ArrayList<BedrockUi.Source>();
        for(var bytes:sink.snapshot().resources().values()){var envelope=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();sources.add(new BedrockUi.Source(envelope.get("source").getAsString(),envelope.getAsJsonObject("document")));}
        sources.sort(Comparator.comparing(BedrockUi.Source::file));
        var report=BedrockUiDependencies.inspect(sources);var program=BedrockUi.compile(sources);
        var pagination=report.definitions().stream().filter(d->d.id().equals("pagination._ui_screen_generator")).findFirst().orElseThrow();assertEquals("update-logs.txt",pagination.file());assertEquals("pack",pagination.ownership());
        assertTrue(report.reachable("chest.small_chest_screen").stream().anyMatch(e->e.reference().equals("pagination._ui_screen_generator")&&e.status().equals("resolved")));
        assertFalse(program.roots().containsKey("chest.small_chest_screen"),"Do not accidentally activate an incompletely supported real screen");
        assertTrue(program.rootFailures().get("chest.small_chest_screen").contains("Unsupported control field cache_screen"),program.rootFailures().get("chest.small_chest_screen"));
        assertArrayEquals(before,java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        String output=System.getenv("BEDROCK_UI_TRACE_OUTPUT");if(output!=null){var json=new JsonObject();var gson=new GsonBuilder().setPrettyPrinting().create();json.addProperty("scope","Authored static graph; variable candidates are not runtime reachability or semantic validation");json.addProperty("packSha256",HexFormat.of().formatHex(before));json.addProperty("registrationFailure",registrationFailure);json.add("graph",gson.toJsonTree(report));json.add("chestReachable",gson.toJsonTree(report.reachable("chest.small_chest_screen")));json.add("compileFailures",gson.toJsonTree(new TreeMap<>(program.rootFailures())));Files.writeString(Path.of(output),gson.toJson(json));}
    }
}
