package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockUiInteractionTest {
    private static final String BUTTON="\"type\":\"button\",\"button_mappings\":[{\"from_button_id\":\"button.menu_select\",\"to_button_id\":\"button.menu_exit\",\"mapping_type\":\"pressed\"},{\"from_button_id\":\"button.menu_ok\",\"to_button_id\":\"button.menu_exit\",\"mapping_type\":\"focused\"}]";
    private static BedrockUi.Program compile(String content){return BedrockUiTest.compile("{\"namespace\":\"test\",\"root\":{"+content+"}}");}
    private static BedrockUiLayout.Draw button(String more){return BedrockUiTest.layout(compile(BUTTON+",\"size\":[20,10]"+more),0).getFirst();}
    @Test void mouseAndKeyboardMappingsAreDistinct(){var p=button("").properties();assertEquals("button.menu_exit",BedrockUiInteraction.action(p,false));assertEquals("button.menu_exit",BedrockUiInteraction.action(p,true));}
    @Test void halfOpenHitBoundsAreInGuiUnits(){var d=button("");assertTrue(BedrockUiInteraction.hit(d,90,45));assertFalse(BedrockUiInteraction.hit(d,110,50));assertFalse(BedrockUiInteraction.hit(d,95,55));}
    @Test void clippingLimitsHitTargets(){var d=button("");var clipped=new BedrockUiLayout.Draw(d.name(),d.type(),d.bounds(),new BedrockUiLayout.Rect(95,45,5,10),1,d.properties());assertFalse(BedrockUiInteraction.hit(clipped,94,50));assertTrue(BedrockUiInteraction.hit(clipped,96,50));}
    @Test void disabledAndTransparentButtonsDoNotHit(){assertFalse(BedrockUiInteraction.hit(button(",\"enabled\":false"),95,50));assertFalse(BedrockUiInteraction.hit(button(",\"alpha\":0"),95,50));}
    @Test void hiddenButtonsNeverEnterDrawList(){assertTrue(BedrockUiTest.layout(compile(BUTTON+",\"visible\":false"),0).isEmpty());}
    @Test void disabledParentDisablesInteractiveChildren(){var p=compile("\"enabled\":false,\"controls\":[{\"b\":{"+BUTTON+"}}]");assertFalse(BedrockUiTest.layout(p,0).get(1).properties().get("enabled").getAsBoolean());}
    @Test void ambiguousAndNonConsumingMappingsReject(){assertFalse(compile(BUTTON+",\"consume_event\":false").errors().isEmpty());assertFalse(compile(BUTTON.replace("button.menu_ok","button.menu_select").replace("focused","pressed")).errors().isEmpty());}
    @Test void unknownMappingsRejectRatherThanGuess(){assertFalse(compile(BUTTON.replace("focused","double_pressed")).errors().isEmpty());assertFalse(BedrockUiInteraction.supportedAction("button.menu_quit","pause.pause_screen"));assertFalse(BedrockUiInteraction.supportedAction("button.menu_select","inventory.inventory_screen"));}
    @Test void actionsAreScreenScoped(){assertTrue(BedrockUiInteraction.supportedAction("button.menu_exit","chest.chest_screen"));assertFalse(BedrockUiInteraction.supportedAction("button.menu_exit","start.start_screen"));assertTrue(BedrockUiInteraction.supportedAction("button.menu_settings","pause.pause_screen"));assertFalse(BedrockUiInteraction.supportedAction("button.menu_settings","chest.chest_screen"));}
    @Test void stateChildrenSelectWithoutDestroyingCommonChildren(){var p=compile(BUTTON+",\"default_control\":\"normal\",\"hover_control\":\"over\",\"controls\":[{\"normal\":{}},{\"over\":{}},{\"label\":{}}]");var normal=BedrockUiTest.layout(p,0);assertEquals(List.of("root","normal","label"),normal.stream().map(BedrockUiLayout.Draw::name).toList());var over=BedrockUiLayout.layout(p,"test.root",200,100,Map.of("__state:test.root",new JsonPrimitive("hover")),0);assertEquals(List.of("root","over","label"),over.stream().map(BedrockUiLayout.Draw::name).toList());}
    @Test void buttonResizeUpdatesActualHitBounds(){var p=compile(BUTTON+",\"size\":[\"50%\",10]");assertEquals(100,BedrockUiTest.layout(p,0).getFirst().bounds().width());assertEquals(200,BedrockUiLayout.layout(p,"test.root",400,200,Map.of(),0).getFirst().bounds().width());}
    private static BedrockUi.Program grid(){return BedrockUiTest.compile("""
        {"namespace":"test","item":{"type":"custom","renderer":"inventory_item_renderer","size":[16,16]},
         "root":{"type":"grid","collection_name":"container_items","grid_dimensions":[2,1],"grid_item_template":"test.item","size":[36,18]}}
        """);}
    private static Map<String,List<Map<String,JsonElement>>> items(){return Map.of("container_items",List.of(Map.of("__slot",new JsonPrimitive(0)),Map.of("__slot",new JsonPrimitive(1))));}
    @Test void gridExpandsRealCollectionIndices(){var draws=BedrockUiLayout.layout(grid(),"test.root",200,100,Map.of(),items(),0);assertEquals(3,draws.size());assertEquals(0,draws.get(1).properties().get("__slot").getAsInt());assertEquals(1,draws.get(2).properties().get("__slot").getAsInt());assertEquals(18,draws.get(2).bounds().x()-draws.get(1).bounds().x());}
    @Test void unavailableAndWrongSizedCollectionsReject(){assertThrows(IllegalArgumentException.class,()->BedrockUiTest.layout(grid(),0));assertThrows(IllegalArgumentException.class,()->BedrockUiLayout.layout(grid(),"test.root",200,100,Map.of(),Map.of("container_items",List.of()),0));}
    @Test void collectionBindingsUseItemScopeNotGlobalFallback(){var p=BedrockUiTest.compile("""
        {"namespace":"test","item":{"type":"label","text":"#inventory_stack_count","bindings":[{"binding_type":"collection","binding_collection_name":"container_items","binding_name":"#inventory_stack_count"}]},
         "root":{"type":"grid","collection_name":"container_items","grid_dimensions":[1,1],"grid_item_template":"test.item"}}
        """);var draws=BedrockUiLayout.layout(p,"test.root",200,100,Map.of(),Map.of("container_items",List.of(Map.of("#inventory_stack_count",new JsonPrimitive(7)))),0);assertEquals("7",draws.get(1).properties().get("text").getAsString());}
    @Test void gridsAreBoundedAndCyclesRejected(){assertFalse(BedrockUiTest.compile("{\"namespace\":\"test\",\"root\":{\"type\":\"grid\",\"grid_item_template\":\"root\"}}").errors().isEmpty());}
    private static BedrockUiLayout.Draw slot(int index,int x,int y){return new BedrockUiLayout.Draw("slot","custom",new BedrockUiLayout.Rect(x,y,16,16),new BedrockUiLayout.Rect(0,0,200,200),1,Map.of("__slot",new JsonPrimitive(index)));}
    private static final BedrockUiLayout.Rect PANEL=new BedrockUiLayout.Rect(0,0,176,166);
    private static final List<BedrockUiSlots.Slot> SLOTS=List.of(new BedrockUiSlots.Slot(0,8,18),new BedrockUiSlots.Slot(1,26,18));
    @Test void slotPlanRetainsIndicesAndMovesPresentationOnly(){var plan=BedrockUiSlots.plan(List.of(slot(0,8,20),slot(1,26,20)),SLOTS,PANEL);assertEquals(new BedrockUiSlots.Slot(0,8,20),plan.get(0));assertEquals(18,SLOTS.getFirst().y());}
    @Test void slotPlanAllowsAtomicSwap(){var plan=BedrockUiSlots.plan(List.of(slot(0,26,18),slot(1,8,18)),SLOTS,PANEL);assertEquals(26,plan.get(0).x());}
    @Test void duplicateAndUnknownSlotsReject(){assertThrows(IllegalArgumentException.class,()->BedrockUiSlots.plan(List.of(slot(0,8,18),slot(0,26,18)),SLOTS,PANEL));assertThrows(IllegalArgumentException.class,()->BedrockUiSlots.plan(List.of(slot(5,8,18)),SLOTS,PANEL));}
    @Test void overlapAndOutsidePanelReject(){assertThrows(IllegalArgumentException.class,()->BedrockUiSlots.plan(List.of(slot(0,26,18)),SLOTS,PANEL));assertThrows(IllegalArgumentException.class,()->BedrockUiSlots.plan(List.of(slot(0,-1,18)),SLOTS,PANEL));}
    @Test void clippedSlotsReject(){var d=slot(0,8,18);assertThrows(IllegalArgumentException.class,()->BedrockUiSlots.plan(List.of(new BedrockUiLayout.Draw(d.name(),d.type(),d.bounds(),new BedrockUiLayout.Rect(8,18,8,8),1,d.properties())),SLOTS,PANEL));}
    @Test void noImportedSlotsLeaveNativeUntouched(){assertTrue(BedrockUiSlots.plan(List.of(),SLOTS,PANEL).isEmpty());}
    @Test void slotRoundingMatchesRendererOnOddViewport(){var d=slot(0,8,20);var half=new BedrockUiLayout.Draw(d.name(),d.type(),new BedrockUiLayout.Rect(8.5,20.5,16,16),d.clip(),1,d.properties());assertEquals(new BedrockUiSlots.Slot(0,9,21),BedrockUiSlots.plan(List.of(half),SLOTS,PANEL).get(0));}
    @Test void nestedGridRuntimeExpansionIsBounded(){var p=BedrockUiTest.compile("""
        {"namespace":"test","label":{"type":"label","text":"x"},
         "inner":{"type":"grid","collection_name":"many","grid_dimensions":[512,1],"grid_item_template":"test.label"},
         "root":{"type":"grid","collection_name":"many","grid_dimensions":[512,1],"grid_item_template":"test.inner"}}
        """);assertThrows(IllegalArgumentException.class,()->BedrockUiLayout.layout(p,"test.root",200,100,Map.of(),Map.of("many",Collections.nCopies(512,Map.of())),0));}
    @Test void buttonHitUsesSamePixelRoundingAsVisuals(){var d=button("");var half=new BedrockUiLayout.Draw(d.name(),d.type(),new BedrockUiLayout.Rect(90.5,45.5,20,10),d.clip(),1,d.properties());assertFalse(BedrockUiInteraction.hit(half,90.8,46));assertTrue(BedrockUiInteraction.hit(half,91,46));}
}
