package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockUiTranslatorTest {
    @Test void rootRelativeJsoncRegistrationIsNotAnExternalPath()throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes("{\"ui_defs\":[\"notes.txt\"]}"),"notes.txt",bytes("/* data */ {\"namespace\":\"pagination\"}")),sink);
        assertEquals(1,sink.snapshot().resources().size());
        for(String path:List.of("../notes.txt","/notes.txt","a/../notes.txt","a//b","a/./b","C:/x","file:x","a\\b",""))assertFalse(BedrockUiTranslator.safe(path),path);
    }
    @Test void verifiedVanillaChestPatchIsImplicitlyLoaded()throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        BedrockUiTranslator.publish(Map.of("ui/chest_screen.json",bytes("{\"small_chest_screen\":{}}"),"ui/unknown_screen.json",bytes("{}")),sink);
        assertEquals(1,sink.snapshot().resources().size());
    }
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test void onlyRegisteredFilesAndGlobalsPublish()throws Exception {
        var sink=new InMemoryGeneratedResourceStore();BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes("{\"ui_defs\":[\"ui/screen.json\"]}"),"ui/screen.json",bytes("{\"namespace\":\"test\"}"),"ui/unregistered.json",bytes("{}"),"ui/_global_variables.json",bytes("{\"$v\":1}")),sink);
        assertEquals(2,sink.snapshot().resources().size());assertTrue(sink.snapshot().resources().keySet().stream().allMatch(id->id.getNamespace().equals("bedrock_native")));
    }
    @Test void malformedMissingAndTraversalPathsAreDiagnosed() {
        for(String defs:List.of("{}","{\"ui_defs\":[\"ui/missing.json\"]}","{\"ui_defs\":[\"ui/../outside.json\"]}","{\"ui_defs\":[\"https://external/ui.json\"]}"))assertThrows(IOException.class,()->BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes(defs)),new InMemoryGeneratedResourceStore()));
    }
    @Test void laterSameFileReplacesEarlierWithoutDuplicatingControls()throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        for(String value:List.of("low","high"))BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes("{\"ui_defs\":[\"ui/screen.json\"]}"),"ui/screen.json",bytes("{\"namespace\":\"test\",\"value\":\""+value+"\"}")),sink);
        assertEquals(1,sink.snapshot().resources().size());assertTrue(new String(sink.snapshot().resources().values().iterator().next(),StandardCharsets.UTF_8).contains("high"));
    }
    @Test void noUiLeavesGeneratedStoreUntouched()throws Exception{var sink=new InMemoryGeneratedResourceStore();BedrockUiTranslator.publish(Map.of(),sink);assertTrue(sink.snapshot().resources().isEmpty());}
    @Test void registeredJsonDocumentsNeedNotUseJsonExtension()throws Exception{var sink=new InMemoryGeneratedResourceStore();BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes("{\"ui_defs\":[\"ui/template.atemp\"]}"),"ui/template.atemp",bytes("// JSONC\n{\"namespace\":\"generic-template\"}")),sink);assertEquals(1,sink.snapshot().resources().size());}
    @Test void oneUnsafeRegistrationDoesNotSuppressSafeLaterFiles(){var sink=new InMemoryGeneratedResourceStore();assertThrows(IOException.class,()->BedrockUiTranslator.publish(Map.of("ui/_ui_defs.json",bytes("{\"ui_defs\":[\"../outside\",\"ui/good.json\"]}"),"ui/good.json",bytes("{\"namespace\":\"test\"}")),sink));assertEquals(1,sink.snapshot().resources().size());}
}
