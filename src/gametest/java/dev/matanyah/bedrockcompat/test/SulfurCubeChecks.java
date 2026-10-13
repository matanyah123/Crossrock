package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import java.util.function.Consumer;

/** Exercise the real imported controllers, including their custom swallowed-block geometry. */
final class SulfurCubeChecks {
    static void verify(ClientGameTestContext context,Consumer<String> command) {
        command.accept("time set day");
        command.accept("tp @p 2 100.5 -4 facing 2 99.5 0");
        command.accept("data merge entity @e[type=minecraft:sulfur_cube,limit=1] {Size:1,Age:0}");
        // NBT size changes don't recompute attributes; use a consistent adult fixture.
        command.accept("attribute @e[type=minecraft:sulfur_cube,limit=1] minecraft:max_health base set 8");
        for(String item:java.util.List.of("air","stone","blue_ice","pumpkin","magma_block","cobblestone")) {
            command.accept("item replace entity @e[type=minecraft:sulfur_cube,limit=1] armor.body with minecraft:"+item);
            context.waitFor(mc->{
                for(var e:mc.level.entitiesForRendering())if(e instanceof SulfurCube cube)
                    return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cube.getBodyArmorItem().getItem()).getPath().equals(item);
                return false;
            });
            context.waitTicks(4);
            context.runOnClient(mc->{
                for(var e:mc.level.entitiesForRendering())if(e instanceof SulfurCube cube) {
                    var state=mc.getEntityRenderDispatcher().getRenderer(cube).createRenderState(cube,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    if(frame==null||frame.draws().stream().allMatch(d->d.mesh().isEmpty()))
                        throw new AssertionError("Sulfur cube / "+item+" failed: "+NativeEntityResources.errors());
                    if(item.equals("air")&&frame.draws().stream().noneMatch(d->!d.material().blend()&&!d.mesh().isEmpty()))
                        throw new AssertionError("Empty sulfur cube lost its opaque inner core");
                    if(item.equals("stone")||item.equals("cobblestone")) {
                        if(frame.attachments().stream().noneMatch(a->a.block()!=null&&!a.block().isEmpty()))
                            throw new AssertionError("Swallowed block lost its resolved block model: "+item);
                        var block=frame.attachments().stream().filter(a->a.block()!=null).findFirst().orElseThrow();
                        if(block.transform().determinant3x3()<=0)
                            throw new AssertionError("Contained Java block has inverted front faces: "+item);
                        var center=block.transform().transformPosition(new org.joml.Vector3f(.5f,.5f,.5f));
                        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
                        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
                        for(var draw:frame.draws())for(var quad:draw.mesh())for(var v:quad.vertices()) {
                            minX=Math.min(minX,v.x()/16d);maxX=Math.max(maxX,v.x()/16d);
                            minY=Math.min(minY,1.5-v.y()/16d);maxY=Math.max(maxY,1.5-v.y()/16d);
                        }
                        if(center.x<minX||center.x>maxX||center.y<minY||center.y>maxY)
                            throw new AssertionError("Contained block was placed outside the sulfur cube: "+center);
                    }
                    if(item.equals("pumpkin")||item.equals("magma_block")) {
                        if(!frame.attachments().isEmpty())throw new AssertionError("Pack-owned block model duplicated by Java fallback: "+item);
                    }
                }
            });
            context.takeScreenshot("reference-sulfur-cube-"+item);
        }
        // Reuse the same entity: stale endless magma emitters used to survive the swap to ice.
        for(String item:java.util.List.of("magma_block","blue_ice")) {
            command.accept("item replace entity @e[type=minecraft:sulfur_cube,limit=1] armor.body with minecraft:"+item);
            for(int i=0;i<30;i++) {
                command.accept("tp @e[type=minecraft:sulfur_cube,limit=1] "+(i*.10)+" 99 0");
                context.waitTicks(1);
            }
            context.takeScreenshot("reference-sulfur-particles-"+item);
        }
        context.runOnClient(mc->{
            try {
                var type=dev.matanyah.bedrockcompat.client.particle.NativeParticles.class;
                var field=type.getDeclaredField("emitters");field.setAccessible(true);
                boolean ice=false;
                for(var emitter:(java.util.List<?>)field.get(null)) {
                    var ef=emitter.getClass().getField("definition");ef.setAccessible(true);
                    var id=((dev.matanyah.bedrockcompat.bedrock.entity.BedrockParticleEffect)ef.get(emitter)).id;
                    var done=emitter.getClass().getField("finished");done.setAccessible(true);
                    if(done.getBoolean(emitter))continue;
                    if(id.equals("oreville_ans:qycthl")||id.equals("oreville_ans:nzjuzl"))
                        throw new AssertionError("Magma emitter survived switch to ice: "+id);
                    if(id.equals("oreville_ans:ujamov")) {
                        var cf=emitter.getClass().getField("context");cf.setAccessible(true);
                        var values=(dev.matanyah.bedrockcompat.bedrock.entity.Molang.Context)cf.get(emitter);
                        if(Math.abs(dev.matanyah.bedrockcompat.bedrock.entity.Molang.number(values.variable("jcalav"))-198/255d)>.001)
                            throw new AssertionError("Ice emitter retained the previous material's colour");
                        ice=true;
                    }
                }
                if(!ice)throw new AssertionError("Ice trail did not start after material swap");
            }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        });
        command.accept("item replace entity @e[type=minecraft:sulfur_cube,limit=1] armor.body with minecraft:stone");
        command.accept("setblock 0 98 0 minecraft:oak_trapdoor[half=top]");
        // Leaving and re-entering the detection area must produce a fresh instant burst.
        for(int pass=0;pass<2;pass++) {
            command.accept("tp @e[type=minecraft:sulfur_cube,limit=1] 5 99 0.5");
            context.waitTicks(5);
            int[] before={0};
            context.runOnClient(mc->before[0]=dev.matanyah.bedrockcompat.client.particle.NativeParticles.particleCount());
            command.accept("tp @e[type=minecraft:sulfur_cube,limit=1] 0.5 99 0.5");
            context.waitTicks(8);
            context.runOnClient(mc->{
                int after=dev.matanyah.bedrockcompat.client.particle.NativeParticles.particleCount();
                if(after-before[0]<40) {
                    try {
                        var of=dev.matanyah.bedrockcompat.client.particle.NativeParticles.class.getDeclaredField("owners");of.setAccessible(true);
                        for(var entry:((java.util.Map<?,?>)of.get(null)).entrySet())if(entry.getKey() instanceof SulfurCube cube) {
                            var vf=entry.getValue().getClass().getDeclaredField("variables");vf.setAccessible(true);
                            var vars=(java.util.Map<?,?>)vf.get(entry.getValue());
                            throw new AssertionError("Trapdoor confetti burst missing: "+before[0]+" -> "+after+" at "+cube.position()+
                                " detected="+vars.get("xefrlc")+" archetype="+vars.get("wujtlp")+" errors="+NativeEntityResources.errors());
                        }
                    }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
                    throw new AssertionError("Trapdoor confetti burst missing");
                }
            });
            context.takeScreenshot("reference-sulfur-confetti-"+pass);
        }
    }
}
