package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.entity.*;
import dev.matanyah.bedrockcompat.bedrock.entity.Molang;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.function.Consumer;

/** Read the imported player's actual pre-animation result on real Java collision shapes. */
final class PlayerBalanceChecks {
    static void verify(ClientGameTestContext context,Consumer<String> command) throws Exception {
        command.accept("gamemode survival @a");
        command.accept("fill -3 98 -3 3 98 3 minecraft:stone");
        context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        String[] supports={"oak_fence","cobblestone_wall","end_rod[facing=east]","iron_bars","stone","oak_fence","oak_fence"};
        double[] heights={100.5,100.5,99.625,100,100,100.5,100.5};
        for(int i=0;i<supports.length;i++) {
            String support=supports[i];boolean expected=i<4||i==5;
            AutoCloseable disabled=i==6?NativePlayerAnimationApi.register((player,partial,view,variables,queries)->
                queries.put("pack_settings",java.util.Map.of())):()->{};
            try(disabled) {
            command.accept("setblock 0 99 0 minecraft:"+support);
            command.accept("tp @p 0.5 "+heights[i]+" 0.5 0 0");
            context.waitTicks(20);
            context.runOnClient(mc->{
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                if(frame==null)throw new AssertionError("Player extraction failed on "+support+": "+NativeEntityResources.errors());
                try {
                    var field=dev.matanyah.bedrockcompat.client.particle.NativeParticles.class.getDeclaredField("owners");field.setAccessible(true);
                    var owner=((java.util.Map<?,?>)field.get(null)).get(mc.player);
                    var vf=owner.getClass().getDeclaredField("variables");vf.setAccessible(true);
                    var vars=(java.util.Map<?,?>)vf.get(owner);
                    boolean balanced=Molang.number(vars.get("rvwafl"))!=0;
                    if(balanced!=expected)throw new AssertionError("Balance on "+support+" expected="+expected+" got="+balanced+
                        " at="+mc.player.position()+" grounded="+mc.player.onGround()+" vars="+vars);
                }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
            });
            context.takeScreenshot("reference-balance-"+support.replaceAll("[^a-z_]",""));
            }
        }
    }
}
