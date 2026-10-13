package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockUiRealPackTest {
    private static JsonElement eval(String value){return BedrockUiExpression.evaluate(value,Map.of("$a",new JsonPrimitive(3),"#name",new JsonPrimitive("Alex"))::get);}
    @Test void arithmeticPrecedenceUnaryAndParentheses(){assertEquals(11,eval("(3 + 4 * 2)").getAsDouble());assertEquals(-14,eval("(-(3 + 4) * 2)").getAsDouble());assertEquals(2.5,eval("(5 / 2)").getAsDouble());assertEquals(1,eval("(+3 - 2)").getAsDouble());}
    @Test void comparisonOperatorsAndStrictTypes(){for(String expression:List.of("($a = 3)","(3 == 3.0)","(3 != 4)","(3 < 4)","(3 <= 3)","(4 > 3)","(3 >= 3)","(not false)","(!false)"))assertTrue(eval(expression).getAsBoolean(),expression);assertFalse(eval("(3 = '3')").getAsBoolean());assertThrows(IllegalArgumentException.class,()->eval("('3' + 2)"));}
    @Test void stringExpressionsAndEscapes(){assertEquals("Hi Alex",eval("('Hi ' + #name)").getAsString());assertEquals("it's",eval("('it\\'s')").getAsString());assertTrue(eval("(#name = 'Alex')").getAsBoolean());}
    @Test void colonVariableNamesAndLiteralStringSubtraction(){
        var context=Map.<String,JsonElement>of("$_pack:_",new JsonPrimitive("prefix needle suffix"),"$part",new JsonPrimitive("needle"));
        assertTrue(BedrockUiExpression.evaluate("(not (($_pack:_ - $part) = $_pack:_))",context::get).getAsBoolean());
        assertEquals("prefix  suffix",BedrockUiExpression.evaluate("($_pack:_ - $part)",context::get).getAsString());
        assertEquals("a.b",eval("('a.b' - '[x]')").getAsString());
        assertEquals("ab",eval("('a[x]b' - '[x]')").getAsString());
        assertEquals("abc",eval("('abc' - '')").getAsString());
        assertEquals("",eval("('same' - 'same')").getAsString());
        assertEquals("",eval("('xx' - 'x')").getAsString());
        assertEquals(1,eval("(3-2)").getAsDouble());
        assertThrows(IllegalArgumentException.class,()->eval("('3' - 2)"));
        assertThrows(IllegalArgumentException.class,()->eval("($_missing:_ - 'x')"));
    }
    @Test void stringContainmentConditionSelectsAuthoredVariables(){
        var p=BedrockUiTest.compile("""
            {"namespace":"test","root":{"$name:_":"prefix marker suffix","$result":"absent",
            "variables":{"requires":"(not (($name:_ - 'marker') = $name:_))","$result":"present"},
            "type":"label","text":"$result"}}
            """);
        assertTrue(p.errors().isEmpty(),p.errors().toString());
        assertEquals("present",BedrockUiTest.layout(p,0).getFirst().properties().get("text").getAsString());
    }
    @Test void concatenationOutputIsBounded(){
        var context=Map.<String,JsonElement>of("$large",new JsonPrimitive("x".repeat(40000)));
        assertThrows(IllegalArgumentException.class,()->BedrockUiExpression.evaluate("($large + $large)",context::get));
    }
    @Test void logicalShortCircuitDoesNotReadUnavailableData(){assertFalse(eval("(false and #missing)").getAsBoolean());assertTrue(eval("(true or #missing)").getAsBoolean());assertTrue(eval("(true && not false || false)").getAsBoolean());}
    @Test void malformedNonfiniteMissingAndExcessiveExpressionsFailClosed(){for(String expression:List.of("(1 / 0)","(#missing = 0)","(true and 1)","(1 +)","('unterminated)","(2) trailing","(java.lang.Runtime())","(".repeat(60)+"1"+")".repeat(60),"("+"1+".repeat(600)+"1)"))assertThrows(IllegalArgumentException.class,()->eval(expression),expression);assertThrows(IllegalArgumentException.class,()->eval(" ".repeat(4097)));}
    @Test void knownVanillaPrimitivesResolveAndPackOverrideWins(){var p=BedrockUiTest.compile("""
        {"namespace":"test","root@common.horizontal_stack_panel":{"controls":[{"a@common.empty_panel":{"size":[10,10]}},{"b@common.empty_panel":{"size":[20,10]}}]}}
        """);assertTrue(p.errors().isEmpty(),p.errors().toString());assertEquals(10,BedrockUiTest.layout(p,0).get(2).bounds().x());
        var override=BedrockUiTest.source("ui/common.json","{\"namespace\":\"common\",\"empty_panel\":{\"size\":[7,8]}}");var consumer=BedrockUiTest.source("ui/test.json","{\"namespace\":\"test\",\"root@common.empty_panel\":{}}");assertEquals(7,BedrockUiTest.layout(BedrockUi.compile(List.of(override,consumer)),0).getFirst().bounds().width());}
    @Test void variableChildArraysPreserveLocalScopeAndConditionalOrder(){var p=BedrockUiTest.compile("""
        {"namespace":"test","root":{"$enabled":true,"$n":4,"variables":[{"requires":"($enabled and $n > 2)","$label":"selected"},{"requires":false,"$label":"wrong"}],"$children":[{"child":{"$label":"local","type":"label","text":"$label","size":["($n * 5)",10]}},{"other":{"type":"label","text":"$label"}}],"controls":"$children"}}
        """);assertTrue(p.errors().isEmpty(),p.errors().toString());var draws=BedrockUiTest.layout(p,0);assertEquals("local",draws.get(1).properties().get("text").getAsString());assertEquals("selected",draws.get(2).properties().get("text").getAsString());assertEquals(20,draws.get(1).bounds().width());}
    @Test void runtimeBindingExpressionsUpdateWithoutRecompile(){var p=BedrockUiTest.compile("""
        {"namespace":"test","root":{"type":"label","text":"('Hi ' + #player_name)","visible":"(#count > 0 and #hud_visible)","alpha":"(#count / 4)"}}
        """);var data=Map.<String,JsonElement>of("#player_name",new JsonPrimitive("Alex"),"#count",new JsonPrimitive(2),"#hud_visible",new JsonPrimitive(true));var d=BedrockUiLayout.layout(p,"test.root",200,100,data,0).getFirst();assertEquals("Hi Alex",d.properties().get("text").getAsString());assertEquals(.5,d.alpha());assertThrows(IllegalArgumentException.class,()->BedrockUiTest.layout(p,0));}
    @Test void localViewBindingsExecuteInOrderAndNamedControlsFailClosed(){var p=BedrockUiTest.compile("""
        {"namespace":"test","root":{"bindings":[{"binding_name":"#count"},{"binding_type":"view","source_property_name":"(#count > 0)","target_property_name":"#visible"}]}}
        """);assertTrue(BedrockUiLayout.layout(p,"test.root",100,100,Map.of("#count",new JsonPrimitive(0)),0).isEmpty());assertEquals(1,BedrockUiLayout.layout(p,"test.root",100,100,Map.of("#count",new JsonPrimitive(1)),0).size());
        var unsupported=BedrockUiTest.compile("""
        {"namespace":"test","root":{"bindings":[{"binding_type":"view","source_control_name":"other","source_property_name":"#visible","target_property_name":"#visible"}]}}
        """);assertThrows(IllegalArgumentException.class,()->BedrockUiTest.layout(unsupported,0));}
    @Test void nineSlicePreservesCornersAndAtlasUv(){var p=Map.<String,JsonElement>of("nineslice_size",new JsonPrimitive(2));var slices=BedrockUiNineSlice.regions(p,10,20,100,60,8,16,20,12);assertEquals(9,slices.size());assertEquals(new BedrockUiNineSlice.Slice(10,20,2,2,8,16,2,2),slices.getFirst());assertEquals(new BedrockUiNineSlice.Slice(12,22,96,56,10,18,16,8),slices.get(4));assertEquals(new BedrockUiNineSlice.Slice(108,78,2,2,26,26,2,2),slices.getLast());}
    @Test void asymmetricBordersCoverDestinationExactlyWithoutOverlap(){var p=Map.<String,JsonElement>of("nine_slice_left",new JsonPrimitive(1),"nine_slice_right",new JsonPrimitive(3),"nine_slice_top",new JsonPrimitive(2),"nine_slice_buttom",new JsonPrimitive(4));for(int w:List.of(0,1,3,4,5,100))for(int h:List.of(0,1,6,7,90)){var slices=BedrockUiNineSlice.regions(p,0,0,w,h,0,0,16,16);assertEquals(w*h,slices.stream().mapToInt(s->s.width()*s.height()).sum());for(var s:slices){assertTrue(s.x()>=0&&s.y()>=0&&s.x()+s.width()<=w&&s.y()+s.height()<=h);}}}
    @Test void noNineSliceKeepsSingleStretchAndZeroSizeDoesNotDraw(){assertEquals(List.of(new BedrockUiNineSlice.Slice(1,2,30,40,5,6,10,20)),BedrockUiNineSlice.regions(Map.of(),1,2,30,40,5,6,10,20));assertTrue(BedrockUiNineSlice.regions(Map.of(),0,0,0,0,0,0,16,16).isEmpty());}
    @Test void malformedBordersNeverProduceInvalidUv(){for(String border:List.of("-1","2.5","\"2\"","16","100000"))assertThrows(IllegalArgumentException.class,()->BedrockUiNineSlice.regions(Map.of("nineslice_size",JsonParser.parseString(border)),0,0,40,40,0,0,16,16));}
    @Test void slicedImagesRetainNestedClipAlphaAndGuiUnitResize(){var p=BedrockUiTest.compile("""
        {"namespace":"test","root":{"size":[40,30],"clips_children":true,"alpha":0.5,"propagate_alpha":true,"controls":[{"image":{"type":"image","texture":"textures/ui/frame","nineslice_size":2,"size":[80,60],"alpha":0.5}}]}}
        """);for(int width:List.of(200,400)){var d=BedrockUiLayout.layout(p,"test.root",width,100,Map.of(),0).get(1);assertEquals(40,d.clip().width());assertEquals(.25,d.alpha());assertEquals(80,d.bounds().width());assertEquals(2,BedrockUiNineSlice.regions(d.properties(),0,0,80,60,0,0,8,8).getFirst().width());}}
    @Test void controlArrayCyclesAndMissingConditionalContextsFailClosed(){for(String fields:List.of("\"$a\":\"$b\",\"$b\":\"$a\",\"controls\":\"$a\"","\"variables\":[{\"requires\":\"($unknown = 1)\",\"$x\":1}]")){var p=BedrockUiTest.compile("{\"namespace\":\"test\",\"root\":{"+fields+"}}");assertFalse(p.roots().containsKey("test.root"));assertFalse(p.errors().isEmpty());}}
}
