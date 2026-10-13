package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MolangQueryCompatibilityTest {
    private static final Molang.Queries UNAVAILABLE=(q,a)->{throw new IllegalArgumentException("Unavailable: "+q);};
    private static Object evaluate(String script,Molang.Queries provider){return Molang.compile(script).evaluate(new Molang.Context(provider));}
    @ParameterizedTest @CsvSource(delimiter='|',value={
        "q.count|0", "q.count(1,'a',0)|3", "q.any(1,0,1)|1", "q.any(1,0,2)|0",
        "q.all(1,1,1)|1", "q.all(1,1,0)|0", "q.in_range(2,2,5)|1", "q.in_range(5,2,5)|1",
        "q.in_range(6,2,5)|0", "q.in_range(3,5,2)|0", "q.unhappy_counter|0", "q.last_hit_by_player|0"
    }) void contextIndependentContracts(String expression,double expected) {
        assertEquals(expected,evaluate(expression,UNAVAILABLE));
    }
    @Test void countCountsArrayElementsWithoutRecursivelyFlattening() {
        var ctx=new Molang.Context(UNAVAILABLE);ctx.setVariable("items",List.of(1d,List.of(2d,3d)));
        assertEquals(3d,Molang.compile("q.count(v.items,'x')").evaluate(ctx));
        for(String expr:List.of("q.any(1,1)","q.all", "q.in_range(1,2)","q.not_implemented"))
            assertThrows(IllegalArgumentException.class,()->evaluate(expr,UNAVAILABLE));
    }
    @Test void integrationDistinguishesMissingFromFalseAndRoutesArgumentsAndTypedProperties() {
        MolangQueryProvider provider=(name,args)->switch(name) {
            case "property"->args.equals(List.of("custom:state"))?Optional.of("sleeping"):Optional.empty();
            case "has_property"->Optional.of(args.equals(List.of("custom:state"))?1d:0d);
            case "is_emoting"->Optional.of(0d);
            default->Optional.empty();
        };
        var q=new EntityQueryContext("","",Set.of(),Map.of("query_provider",provider,"is_emoting",1d));
        assertEquals("sleeping",evaluate("q.property('custom:state')",q));
        assertEquals(1d,evaluate("q.has_property('custom:state')",q));
        assertEquals(0d,evaluate("q.has_property('absent')",q));
        assertEquals(0d,evaluate("q.is_emoting",q));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.property('absent')",q));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.scoreboard('score')",q));
        assertEquals(7d,provider.orElse((name,args)->7d).get("future",List.of()));
    }
    @Test void positionAxesRejectFractionalNonFiniteMissingAndOutOfRangeArguments() {
        var q=new EntityQueryContext("","",Set.of(),Map.of("position",List.of(-3d,12d,8d),
            "position_delta",List.of(-.1d,0d,.2d),"movement_direction",List.of(-1d,0d,0d)));
        for(String name:List.of("position","position_delta","movement_direction")) {
            var vector=(List<?>)q.values().get(name);
            for(int axis=0;axis<3;axis++)assertEquals(vector.get(axis),evaluate("q."+name+"("+axis+")",q));
            for(double axis:new double[]{-.1,3,1.5,Double.NaN,Double.POSITIVE_INFINITY})
                assertThrows(IllegalArgumentException.class,()->q.get(name,List.of(axis)));
            assertThrows(IllegalArgumentException.class,()->q.get(name,List.of()));
            assertThrows(IllegalArgumentException.class,()->q.get(name,List.of(0d,1d)));
        }
        assertThrows(IllegalArgumentException.class,()->new EntityQueryContext("","",Set.of(),Map.of()).get("position",List.of(0d)));
    }
    @Test void sliderSettingsPreserveNumericValueAndDoNotCoerceToggleOrMissingSetting() {
        var q=new EntityQueryContext("","",Set.of(),Map.of("pack_settings",Map.of("size",.25,"toggle",true)));
        assertEquals(.25,evaluate("q.get_pack_setting('size')",q));
        for(String setting:List.of("missing","toggle"))assertThrows(IllegalArgumentException.class,()->q.get("get_pack_setting",List.of(setting)));
        assertThrows(IllegalArgumentException.class,()->q.get("get_pack_setting",List.of()));
    }
    @Test void renderedItemIsIndependentOfOwnerEquipmentAndSupportsDurabilityAndTags() {
        var q=new MolangItemQueries((name,args)->name.equals("max_durability")?999d:17d,Set.of("custom:metal","weapon"),250,30,true,false);
        assertEquals(250d,evaluate("q.max_durability",q));assertEquals(220d,evaluate("q.remaining_durability",q));
        assertEquals(1d,evaluate("q.is_enchanted",q));assertEquals(0d,evaluate("q.is_stackable",q));
        assertEquals(1d,evaluate("q.all_tags('weapon','custom:metal')",q));assertEquals(0d,evaluate("q.all_tags('weapon','absent')",q));
        assertEquals(1d,evaluate("q.any_tag('absent','weapon')",q));assertEquals(0d,evaluate("q.any_tag('absent')",q));
        assertEquals(17d,evaluate("q.health",q));
        var empty=new MolangItemQueries(UNAVAILABLE,Set.of(),0,0,false,true);
        assertEquals(0d,evaluate("q.max_durability",empty));assertEquals(0d,evaluate("q.remaining_durability",empty));
        assertEquals(0d,evaluate("q.is_enchanted",empty));assertEquals(1d,evaluate("q.is_stackable",empty));
        assertEquals(0d,evaluate("q.any_tag('weapon')",empty));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.all_tags",q));
        assertEquals(0d,new MolangItemQueries(UNAVAILABLE,Set.of(),10,20,false,false).get("remaining_durability",List.of()));
    }
    @Test void equipmentDamageIncludesBodyAndDurationTracksSelectedHandAndNormalization() {
        var q=new EntityFrameQueries(new EntityQueryContext("","",Set.of(),Map.of()),Map.of(
            "slot.armor.body",new EntityFrameQueries.Item("minecraft:leather_horse_armor",Set.of(),12,0),
            "slot.weapon.offhand",new EntityFrameQueries.Item("minecraft:crossbow",Set.of(),0,1.5)),0,0,"fancy");
        assertEquals(12d,evaluate("q.armor_damage_slot(4)",q));assertEquals(0d,evaluate("q.armor_damage_slot(0)",q));
        assertEquals(1.5,evaluate("q.item_remaining_use_duration('off_hand')",q));
        assertEquals(.5,evaluate("q.item_remaining_use_duration('off_hand',3)",q));
        assertEquals(0d,evaluate("q.item_remaining_use_duration('main_hand')",q));
        assertEquals(1d,evaluate("q.equipment_count",q));
        for(double index:new double[]{-1,5,1.5,Double.NaN})assertThrows(IllegalArgumentException.class,()->q.get("armor_damage_slot",List.of(index)));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.item_remaining_use_duration('off_hand',0)",q));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.item_remaining_use_duration('wrong')",q));
        assertThrows(IllegalArgumentException.class,()->evaluate("q.camera_rotation(.5)",q));
    }
    @Test void neighboringBlockCoordinatesAreBlockRelativeNotActorRelative() {
        var hits=new ArrayList<List<Integer>>();
        var q=new EntityEnvironmentQueries(UNAVAILABLE,(x,y,z,tag)->{hits.add(List.of(x,y,z));return tag.equals("solid");},-.2,5.8,2.7);
        assertEquals(1d,evaluate("q.block_neighbor_has_all_tags(.4,0,0,'solid')",q));
        assertEquals(List.of(-1,5,2),hits.getLast());
        assertEquals(0d,evaluate("q.block_neighbor_has_all_tags(0,0,0,'solid','missing')",q));
        assertEquals(1d,evaluate("q.block_neighbor_has_any_tag(0,0,0,'missing','solid')",q));
        assertEquals(0d,evaluate("q.block_neighbor_has_any_tag(0,0,0,'missing')",q));
        assertEquals(1d,evaluate("q.relative_block_has_any_tag(.4,0,0,'solid')",q));
        assertEquals(List.of(0,5,2),hits.getLast());
        assertThrows(IllegalArgumentException.class,()->evaluate("q.block_neighbor_has_any_tag(200,0,0,'solid')",q));
    }
    @Test void particleCountsAndDistanceRemainLiveAndDoNotReadActorRoot() {
        int[] counts={2,7};double[] p={3,4,0},camera={0,0,0};
        var q=new ParticleQueryContext((name,args)->name.equals("distance_from_camera")?999d:23d,
            ()->p,()->camera,()->counts[0],()->counts[1]);
        assertEquals(2d,evaluate("q.effect_emitter_count",q));assertEquals(7d,evaluate("q.effect_particle_count",q));
        assertEquals(5d,evaluate("q.distance_from_camera",q));assertEquals(23d,evaluate("q.health",q));
        counts[0]=0;counts[1]=0;p[0]=0;p[1]=0;
        assertEquals(0d,evaluate("q.effect_emitter_count",q));assertEquals(0d,evaluate("q.effect_particle_count",q));
        assertEquals(0d,evaluate("q.distance_from_camera",q));
    }
    @Test void clockBoundariesAndEightMoonPhases() {
        assertEquals(1,BedrockMotionInputs.day(0));assertEquals(1,BedrockMotionInputs.day(23999));
        assertEquals(2,BedrockMotionInputs.day(24000));assertEquals(0,BedrockMotionInputs.day(-1));
        double[] brightness={1,.75,.5,.25,0,.25,.5,.75};
        for(int phase=0;phase<8;phase++) {
            assertEquals(phase,BedrockMotionInputs.moonPhase(phase*24000L));
            assertEquals(brightness[phase],BedrockMotionInputs.moonBrightness(phase*24000L));
        }
        assertEquals(0,BedrockMotionInputs.moonPhase(8*24000));assertEquals(7,BedrockMotionInputs.moonPhase(-1));
    }
}
