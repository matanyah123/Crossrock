package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MolangTest {
    @Test void loopsAreLazyAndSupportBreakContinueWithFiniteWorkBudget() {
        var context=new Molang.Context((q,a)->0d);
        assertEquals(3d,Molang.compile("v.i=0;loop(10,{v.i=v.i+1;v.i>=3?break;});return v.i;").evaluate(context));
        assertEquals(2d,Molang.compile("v.i=0;v.sum=0;loop(4,{v.i=v.i+1;v.i<3?continue;v.sum=v.sum+1;});return v.sum;").evaluate(context));
        context.bind(Map.of("array.values",List.of(1d,2d,3d)));
        assertEquals(6d,Molang.compile("v.sum=0;for_each(v.item,array.values,{v.sum=v.sum+v.item;});return v.sum;").evaluate(context));
        context.beginFrame();
        assertThrows(IllegalArgumentException.class,()->Molang.compile("loop(1024,{loop(1024,{v.x=1;});});").evaluate(context));
    }
    @Test void owningEntityArrowUsesOptionalSeparateValueContext() {
        var owner=new Molang.Context((q,a)->0d);owner.setVariable("angle",35d);
        var child=new Molang.Context((q,a)->0d);
        var expression=Molang.compile("c.owning_entity -> v.angle");
        assertEquals(0d,expression.evaluate(child));
        child.setEntityContext("owning_entity",owner);
        assertEquals(35d,expression.evaluate(child));
        assertEquals(-3d,Molang.compile("math.copy_sign(3,-1)").evaluate(child));
    }
    @Test void queryStructFieldsAndScopedThisDoNotLeak() {
        var ctx=new Molang.Context((q,a)->Map.of("r",Map.of("x",12d,"y",24d)));
        assertEquals(24d,Molang.compile("t.bone=q.bone_orientation_trs('body');return t.bone.r.y;").evaluate(ctx));
        assertEquals(10d,ctx.withThis(10,()->Molang.compile("this").evaluate(ctx)));
        assertEquals(0d,Molang.compile("this").evaluate(ctx));
    }
    @Test void seededRandomIsPerEntityAndIntegerEndpointsAreInclusive() {
        var a=new Molang.Context((q,v)->0d,42);var b=new Molang.Context((q,v)->0d,42);
        var expression=Molang.compile("math.random_integer(1,3)");Set<Double> values=new HashSet<>();
        for(int i=0;i<100;i++) {var value=expression.evaluate(a);assertEquals(value,expression.evaluate(b));values.add((Double)value);}
        assertEquals(Set.of(1d,2d,3d),values);
    }
    private Molang.Context context(String name) {
        return new Molang.Context(new EntityQueryContext(name,"minecraft:snowy_plains",Set.of("cold"),Map.of("life_time",2d)));
    }
    @Test void nameAndBiomeConditionsUseBedrockQueries() {
        var expression=Molang.compile("q.is_name_any('Dinnerbone', 'Grumm') && q.entity_biome_has_any_identifier('minecraft:ice_plains') ? 7 : 3");
        assertEquals(7d,expression.evaluate(context("Dinnerbone")));
        assertEquals(3d,expression.evaluate(context("Steve")));
    }
    @Test void persistentVariablesAreIsolatedAndTempsReset() {
        var a=context("A");var b=context("B");
        var script=Molang.compile("v.counter = v.counter + 1; t.tmp = 4; return v.counter;");
        assertEquals(1d,script.evaluate(a));assertEquals(2d,script.evaluate(a));assertEquals(1d,script.evaluate(b));
        a.beginFrame();assertEquals(0d,Molang.compile("t.tmp").evaluate(a));assertEquals(2d,a.variable("counter"));
    }
    @Test void branchesShortCircuitAndMathUsesDegrees() {
        assertEquals(7d,Molang.compile("v.missing ?? 7").evaluate(context("A")));
        assertEquals(0d,Molang.compile("v.x=0; return v.x ?? 7;").evaluate(context("A")));
        assertEquals(1d,Molang.compile("math.sin(90)").evaluate(context("A")));
        assertEquals(0d,Molang.compile("0 && q.unknown").evaluate(context("A")));
        assertEquals(8d,Molang.compile("v.x=0; 1 ? {v.x=8;}; return v.x;").evaluate(context("A")));
        assertThrows(IllegalArgumentException.class,()->Molang.compile("q.unknown").evaluate(context("A")));
    }
    @Test void rejectsUnterminatedStringsAndTrailingGarbage() {
        assertThrows(IllegalArgumentException.class,()->Molang.compile("'broken"));
        assertThrows(IllegalArgumentException.class,()->Molang.compile("1 garbage"));
    }
}
