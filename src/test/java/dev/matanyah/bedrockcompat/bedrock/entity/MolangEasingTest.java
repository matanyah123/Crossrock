package dev.matanyah.bedrockcompat.bedrock.entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MolangEasingTest {
    @Test void allBedrockCurvesHaveEndpointsAndFiniteInteriors() {
        var ctx=new Molang.Context((q,a)->0d);
        for(String curve:java.util.List.of("quad","cubic","quart","quint","sine","expo","circ","back","elastic","bounce"))
            for(String mode:java.util.List.of("in_","out_","in_out_")) {
                String call="math.ease_"+mode+curve;
                assertEquals(2d,Molang.compile(call+"(2,8,0)").evaluate(ctx));
                assertEquals(8d,Molang.compile(call+"(2,8,1)").evaluate(ctx));
                assertTrue(Double.isFinite(Molang.number(Molang.compile(call+"(2,8,0.4)").evaluate(ctx))));
            }
        assertEquals(3d,Molang.compile("math.ease_in_cubic(2,10,0.5)").evaluate(ctx));
        assertEquals(9d,Molang.compile("math.ease_out_cubic(2,10,0.5)").evaluate(ctx));
        assertEquals(6d,Molang.compile("math.ease_in_out_back(2,10,0.5)").evaluate(ctx));
    }
}
