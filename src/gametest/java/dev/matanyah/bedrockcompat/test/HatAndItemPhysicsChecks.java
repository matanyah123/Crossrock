package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.*;
import java.util.function.Consumer;

/** Exercises actual imported physics scripts, not just mesh presence. */
final class HatAndItemPhysicsChecks {
    static void verify(ClientGameTestContext context,Consumer<String> command) {
        command.accept("time set day");
        command.accept("item replace entity @a weapon.mainhand with minecraft:bell");
        context.runOnClient(mc->mc.player.setXRot(0));
        context.waitTicks(30);
        double level=context.computeOnClient(mc->physicsPitch(mc.player));
        context.runOnClient(mc->mc.player.setXRot(60));
        context.waitTicks(30);
        double down=context.computeOnClient(mc->physicsPitch(mc.player));
        if(!Double.isFinite(down)||Math.abs(down-level)<5)
            throw new AssertionError("Bell physics did not respond to camera pitch: "+level+" -> "+down);
        context.takeScreenshot("reference-bell-physics-look-down");
        command.accept("item replace entity @a weapon.mainhand with minecraft:iron_chain");
        context.waitTicks(20);
        context.runOnClient(mc->mc.player.setXRot(-45));
        context.waitTicks(20);
        context.runOnClient(mc->{
            if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString().equals("minecraft:iron_chain"))
                throw new AssertionError("Chain fixture did not equip the requested item");
            var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
            var hands=NativeEntityRenderer.extract(mc.player,state,1,mc.player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
            if(hands==null||hands.nativeHands().isEmpty())throw new AssertionError("Chain fell back to vanilla");
            for(var draw:hands.draws())for(var quad:draw.mesh())for(var v:quad.vertices())
                if(!Float.isFinite(v.x())||!Float.isFinite(v.y())||!Float.isFinite(v.z()))throw new AssertionError("Chain physics produced non-finite geometry");
        });
        context.takeScreenshot("reference-chain-physics-look-up");
        command.accept("item replace entity @a weapon.mainhand with minecraft:air");
        for(String biome:List.of("plains","desert","snow","taiga"))for(String profession:List.of("none","farmer")) {
            command.accept("kill @e[type=minecraft:villager]");
            command.accept("summon minecraft:villager 0 99 0 {NoAI:1b,VillagerData:{type:'minecraft:"+biome+"',profession:'minecraft:"+profession+"',level:1}}");
            command.accept("tp @a 0 99 4 facing 0 100.5 0");
            context.waitTicks(10);
            context.runOnClient(mc->{
                for(var e:mc.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.npc.villager.Villager) {
                    var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    if(frame==null)throw new AssertionError("Villager fell back to vanilla");
                    for(var draw:frame.draws())if(draw.material().masked()&&!draw.material().alphaTest())
                        throw new AssertionError("Villager clothing/hat lost its alpha cutout");
                }
            });
            context.takeScreenshot("reference-villager-hat-"+biome+"-"+profession);
        }
        command.accept("kill @e[type=minecraft:villager]");
        command.accept("time set midnight");
    }
    private static double physicsPitch(net.minecraft.world.entity.Entity player) {
        try {
            var type=Class.forName("dev.matanyah.bedrockcompat.client.entity.NativeAttachableRenderer");
            var field=type.getDeclaredField("instances");field.setAccessible(true);
            var instances=(Map<?,?>)field.get(null);
            var states=(Map<?,?>)instances.get(player);
            var state=states==null?null:states.get("FIRST_PERSON:main_hand");
            if(state==null)throw new AssertionError("Missing first-person attachable runtime");
            var accessor=state.getClass().getDeclaredMethod("runtime");accessor.setAccessible(true);
            return ((BedrockEntityInstance)accessor.invoke(state)).withContext((q,a)->0d,
                c->Molang.number(c.variable("ukeclk")));
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
