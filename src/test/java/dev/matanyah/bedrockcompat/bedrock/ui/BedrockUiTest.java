package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockUiTest {
    static BedrockUi.Source source(String file,String json){return new BedrockUi.Source(file,JsonParser.parseString(json).getAsJsonObject());}
    static BedrockUi.Program compile(String json){return BedrockUi.compile(List.of(source("ui/test.json",json)));}
    static List<BedrockUiLayout.Draw> layout(BedrockUi.Program p,double seconds){return BedrockUiLayout.layout(p,"test.root",200,100,Map.of(),seconds);}
    @Test void namespacesAndQualifiedInheritanceExecute() {
        var p=BedrockUi.compile(List.of(source("ui/base.json","{\"namespace\":\"base\",\"label\":{\"type\":\"label\",\"text\":\"base\",\"size\":[20,10]}}"),source("ui/test.json","{\"namespace\":\"test\",\"root@base.label\":{\"text\":\"child\"}}")));
        var d=layout(p,0).getFirst();assertEquals("label",d.type());assertEquals("child",d.properties().get("text").getAsString());assertEquals(20,d.bounds().width());assertTrue(p.errors().isEmpty());
    }
    @Test void globalsDefaultsAndInstanceVariablesHaveScopedValues() {
        var p=BedrockUi.compile(List.of(source("ui/_global_variables.json","{\"$color\":[1,0,0]}"),source("ui/test.json","{\"namespace\":\"test\",\"root\":{\"$text|default\":\"parent\",\"controls\":[{\"a\":{\"type\":\"label\",\"$text\":\"local\",\"text\":\"$text\",\"color\":\"$color\"}},{\"b\":{\"type\":\"label\",\"text\":\"$text\"}}]}}")));
        var draws=layout(p,0);assertEquals("local",draws.get(1).properties().get("text").getAsString());assertEquals("parent",draws.get(2).properties().get("text").getAsString());assertEquals(1,draws.get(1).properties().get("color").getAsJsonArray().get(0).getAsInt());
    }
    @Test void namedChildrenOverrideWithoutFlatteningOrDroppingSiblings() {
        var p=compile("{\"namespace\":\"test\",\"base\":{\"controls\":[{\"a\":{\"type\":\"label\",\"text\":\"a\"}},{\"b\":{\"type\":\"label\",\"text\":\"b\"}}]},\"root@base\":{\"controls\":[{\"a\":{\"text\":\"override\"}}]}}");
        var d=layout(p,0);assertEquals(3,d.size());assertEquals("override",d.get(1).properties().get("text").getAsString());assertEquals("b",d.get(2).name());
    }
    @Test void laterDefinitionWinsWithoutMutatingInput() {
        var a=source("ui/a.json","{\"namespace\":\"test\",\"root\":{\"type\":\"label\",\"text\":\"first\"}}");
        var b=source("ui/b.json","{\"namespace\":\"test\",\"root\":{\"type\":\"label\",\"text\":\"second\"}}");
        assertEquals("second",layout(BedrockUi.compile(List.of(a,b)),0).getFirst().properties().get("text").getAsString());assertTrue(a.document().toString().contains("first"));
    }
    @Test void cyclesAndMissingBasesReportSourceAndPreserveOtherRoots() {
        var p=compile("{\"namespace\":\"test\",\"a@b\":{},\"b@a\":{},\"missing@unknown\":{},\"root\":{\"type\":\"panel\"}}");
        assertEquals(3,p.errors().size());assertTrue(p.errors().getFirst().contains("ui/test.json"));assertEquals(1,layout(p,0).size());
    }
    @Test void variableCyclesAndMissingVariablesFailClosed() {
        for(String variables:List.of("\"$a\":\"$b\",\"$b\":\"$a\"","\"$a\":\"$missing\"")) {
            var p=compile("{\"namespace\":\"test\",\"root\":{\"type\":\"label\","+variables+",\"text\":\"$a\"}}");assertFalse(p.roots().containsKey("test.root"));assertFalse(p.errors().isEmpty());
        }
    }
    @Test void recursiveTreeReferencesAreBounded() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"controls\":[{\"again@root\":{}}]}}");assertFalse(p.roots().containsKey("test.root"));assertFalse(p.errors().isEmpty());
    }
    @Test void unsupportedControlsAndUnsafeBehaviorAreNotPretendImplemented() {
        for(String field:List.of("\"type\":\"button\"","\"type\":\"custom\",\"renderer\":\"health_renderer\"","\"type\":\"panel\",\"modifications\":[]","\"type\":\"panel\",\"button_mappings\":[]","\"type\":\"grid\"")) {
            var p=compile("{\"namespace\":\"test\",\"root\":{"+field+"}}");assertFalse(p.roots().containsKey("test.root"));assertFalse(p.errors().isEmpty());
        }
    }
    @Test void anchorsPercentOffsetsAndResizeUseGuiUnits() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"size\":[\"50% - 2px\",20],\"anchor_from\":\"bottom_right\",\"anchor_to\":\"bottom_right\",\"offset\":[-3,-4]}}");
        assertEquals(new BedrockUiLayout.Rect(99,76,98,20),layout(p,0).getFirst().bounds());
        assertEquals(198,BedrockUiLayout.layout(p,"test.root",400,200,Map.of(),0).getFirst().bounds().width());
        assertArrayEquals(new double[]{0,.5},BedrockUiLayout.anchor("left_middle"));
    }
    @Test void sizeConstraintsAndUnsupportedDimensionsAreExplicit() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"size\":[\"fill\",5],\"max_size\":[80,60],\"min_size\":[20,10]}}");
        assertEquals(80,layout(p,0).getFirst().bounds().width());assertEquals(10,layout(p,0).getFirst().bounds().height());
        for(String value:List.of("100%c","100%cm","1/0","NaN"))assertThrows(IllegalArgumentException.class,()->BedrockUiLayout.dimension(new JsonPrimitive(value),100));
    }
    @Test void stackFlowAndLayerOrderAreIndependent() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"type\":\"stack_panel\",\"orientation\":\"horizontal\",\"controls\":[{\"a\":{\"size\":[20,10],\"layer\":2}},{\"b\":{\"size\":[30,10],\"layer\":1}}]}}");
        var d=layout(p,0);assertEquals("b",d.get(1).name());assertEquals(20,d.get(1).bounds().x());assertEquals("a",d.get(2).name());assertEquals(0,d.get(2).bounds().x());
    }
    @Test void clippingAndAlphaPropagateWithoutLeakingToSiblings() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"size\":[40,30],\"alpha\":0.5,\"propagate_alpha\":true,\"clips_children\":true,\"controls\":[{\"child\":{\"size\":[100,100],\"alpha\":0.5}}]}}");
        var d=layout(p,0);assertEquals(.25,d.get(1).alpha());assertEquals(d.getFirst().bounds(),d.get(1).clip());assertEquals(new BedrockUiLayout.Rect(10,10,0,0),new BedrockUiLayout.Rect(0,0,2,2).intersect(new BedrockUiLayout.Rect(10,10,2,2)));
    }
    @Test void invisibleAndIgnoredNodesDoNotOccupyStackFlow() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"type\":\"stack_panel\",\"controls\":[{\"hidden\":{\"visible\":false}},{\"ignored\":{\"ignored\":true}},{\"shown\":{\"size\":[10,10]}}]}}");assertEquals(2,layout(p,0).size());assertEquals(0,layout(p,0).get(1).bounds().y());
    }
    @Test void bindingUpdatesVisibilityAndTextWithoutRecompile() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"type\":\"label\",\"text\":\"#player_name\",\"bindings\":[{\"binding_name\":\"#hud_visible\",\"binding_name_override\":\"#visible\"}]}}");
        assertTrue(BedrockUiLayout.layout(p,"test.root",100,100,Map.of("#hud_visible",new JsonPrimitive(false)),0).isEmpty());
        var draw=BedrockUiLayout.layout(p,"test.root",100,100,Map.of("#hud_visible",new JsonPrimitive(true),"#player_name",new JsonPrimitive("name")),0).getFirst();assertEquals("name",draw.properties().get("text").getAsString());
    }
    @Test void missingBindingsAndExpressionsNeverFabricateValues() {
        var p=compile("{\"namespace\":\"test\",\"root\":{\"bindings\":[{\"binding_name\":\"#unknown\"}]}}");assertThrows(IllegalArgumentException.class,()->layout(p,0));
        var expression=compile("{\"namespace\":\"test\",\"root\":{\"visible\":\"(#unknown = 1)\"}}");assertThrows(IllegalArgumentException.class,()->layout(expression,0));
    }
    @Test void numericAnimationsUseElapsedSecondsAndResetOnNewTimeline() {
        var p=compile("{\"namespace\":\"test\",\"fade\":{\"anim_type\":\"alpha\",\"duration\":2,\"from\":0,\"to\":1},\"root\":{\"anims\":[\"@test.fade\"]}}");
        assertEquals(.5,layout(p,1).getFirst().alpha());assertEquals(1,layout(p,10).getFirst().alpha());assertEquals(0,layout(p,0).getFirst().alpha());assertEquals(layout(p,1),layout(p,1));
    }
    @Test void chainedLoopAndEasingAreDeterministicAtLongUptime() {
        var p=compile("{\"namespace\":\"test\",\"a\":{\"anim_type\":\"alpha\",\"duration\":1,\"from\":0,\"to\":1,\"easing\":\"in_quad\",\"next\":\"@test.b\"},\"b\":{\"anim_type\":\"alpha\",\"duration\":1,\"from\":1,\"to\":0,\"next\":\"@test.a\"},\"root\":{\"anims\":[\"@test.a\"]}}");
        assertEquals(.25,layout(p,.5).getFirst().alpha());assertEquals(.5,layout(p,1.5).getFirst().alpha());assertEquals(.25,layout(p,1_000_000.5).getFirst().alpha());
    }
    @Test void vectorAnimationsDriveActualBoundsAndColorProperties() {
        var p=compile("{\"namespace\":\"test\",\"move\":{\"anim_type\":\"offset\",\"duration\":2,\"from\":[0,0],\"to\":[20,10]},\"size\":{\"anim_type\":\"size\",\"duration\":2,\"from\":[10,10],\"to\":[30,30]},\"color\":{\"anim_type\":\"color\",\"duration\":2,\"from\":[0,0,0],\"to\":[1,1,1]},\"root\":{\"anchor_from\":\"top_left\",\"anchor_to\":\"top_left\",\"anims\":[\"@test.move\",\"@test.size\",\"@test.color\"]}}");
        var d=layout(p,1).getFirst();assertEquals(new BedrockUiLayout.Rect(10,5,20,20),d.bounds());assertEquals(.5,d.properties().get("color").getAsJsonArray().get(0).getAsDouble());
    }
    @Test void malformedAnimationAndUnknownReferencesFailClosed() {
        for(String duration:List.of("0","-1")){var p=compile("{\"namespace\":\"test\",\"a\":{\"anim_type\":\"alpha\",\"duration\":"+duration+",\"from\":0,\"to\":1}}");assertTrue(p.animations().isEmpty());assertFalse(p.errors().isEmpty());}
        assertThrows(IllegalArgumentException.class,()->layout(compile("{\"namespace\":\"test\",\"root\":{\"anims\":[\"@missing\"]}}"),1));
    }
    @Test void emptyProgramHasZeroDrawsAndNoVanillaMutation() {assertTrue(BedrockUiLayout.layout(BedrockUi.Program.EMPTY,"hud.hud_screen",100,100,Map.of(),0).isEmpty());}
    @Test void inheritedChildrenKeepTheirDefiningNamespace() {
        var p=BedrockUi.compile(List.of(source("ui/base.json","{\"namespace\":\"base\",\"label\":{\"type\":\"label\",\"text\":\"base\"},\"panel\":{\"controls\":[{\"child@label\":{}}]}}"),source("ui/test.json","{\"namespace\":\"test\",\"root@base.panel\":{}}")));
        assertEquals("base",layout(p,0).get(1).properties().get("text").getAsString());assertTrue(p.errors().isEmpty());
    }
    @Test void deeplyNestedUntrustedDocumentIsRejectedBeforeRecursiveCopies() {
        var root=new JsonObject();root.addProperty("namespace","test");var child=new JsonObject();root.add("root",child);
        for(int i=0;i<100;i++){var next=new JsonObject();child.add("nested",next);child=next;}
        var p=BedrockUi.compile(List.of(new BedrockUi.Source("ui/deep.json",root)));assertTrue(p.roots().isEmpty());assertTrue(p.errors().getFirst().contains("structure limit"));
    }
}
