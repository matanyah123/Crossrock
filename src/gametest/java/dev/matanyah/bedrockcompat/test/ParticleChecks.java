package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.client.particle.NativeParticles;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class ParticleChecks {
    static void fixture(Path pack)throws Exception {
        Files.createDirectories(pack.resolve("particles"));Files.createDirectories(pack.resolve("textures/particle"));
        var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xffff6600);
        ImageIO.write(image,"PNG",pack.resolve("textures/particle/fixture.png").toFile());
        String effect="""
            {"particle_effect":{"description":{"identifier":"fixture:fire","basic_render_parameters":{"material":"particles_add","texture":"textures/particle/fixture"}},
            "components":{"minecraft:emitter_rate_steady":{"spawn_rate":20,"max_particles":20},
              "minecraft:emitter_lifetime_looping":{"active_time":1},"minecraft:emitter_shape_point":{"direction":[0,1,0]},
              "minecraft:particle_initial_speed":1,"minecraft:particle_lifetime_expression":{"max_lifetime":0.5},
              "minecraft:particle_appearance_billboard":{"size":[0.1,0.1],"facing_camera_mode":"lookat_xyz","uv":{"texture_width":16,"texture_height":16,"uv":[0,0],"uv_size":[16,16]}}}}}
            """;
        Files.writeString(pack.resolve("particles/fire.json"),effect);
        Files.writeString(pack.resolve("particles/crit.json"),effect.replace("fixture:fire","minecraft:basic_crit_particle"));
        // Unmodified component bodies from the pinned Mojang sample corpus; only asset IDs/texture are remapped.
        for(String name:new String[]{"basic_flame","basic_smoke"}) {
            try(var stream=ParticleChecks.class.getResourceAsStream("/particles/reference/"+name+".json")) {
                if(stream==null)throw new AssertionError("Missing reference particle fixture "+name);
                var reference=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var desc=reference.getAsJsonObject("particle_effect").getAsJsonObject("description");
                desc.addProperty("identifier","fixture:mojang_"+name);desc.getAsJsonObject("basic_render_parameters").addProperty("texture","textures/particle/fixture");
                Files.writeString(pack.resolve("particles/"+name+".json"),reference.toString());
            }
        }
        Files.writeString(pack.resolve("particles/seed.json"),"""
            {"particle_effect":{"description":{"identifier":"fixture:seed","basic_render_parameters":{"material":"particles_blend","texture":"textures/particle/fixture"}},
            "components":{"minecraft:emitter_rate_instant":{"num_particles":1},"minecraft:emitter_lifetime_once":{"active_time":0},
            "minecraft:particle_lifetime_expression":{"max_lifetime":0},"minecraft:particle_lifetime_events":{"expiration_event":"child"}},
            "events":{"child":{"particle_effect":{"effect":"fixture:mojang_basic_smoke","type":"particle","pre_effect_expression":"v.from_animation=1;"},
              "sound_effect":{"event_name":"minecraft:ui.button.click"},"log":"Particle regression event"}}}}
            """);
        Files.writeString(pack.resolve("particles/bound.json"),effect.replace("fixture:fire","fixture:bound"));
        Files.writeString(pack.resolve("particles/unbound.json"),effect.replace("fixture:fire","fixture:unbound"));
        var root=JsonParser.parseString(Files.readString(pack.resolve("entity/stand.json"))).getAsJsonObject();
        var description=root.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        var particles=new JsonObject();particles.addProperty("fire","fixture:fire");particles.addProperty("seed","fixture:seed");description.add("particle_effects",particles);
        var animations=new JsonObject();animations.addProperty("fire","controller.animation.fixture.fire");animations.addProperty("particles","animation.fixture.particles");description.add("animations",animations);
        var scripts=new JsonObject();var animate=new JsonArray();animate.add("fire");animate.add("particles");scripts.add("animate",animate);description.add("scripts",scripts);
        Files.writeString(pack.resolve("entity/stand.json"),root.toString());
        Files.writeString(pack.resolve("animation_controllers/fire.json"),"""
            {"animation_controllers":{"controller.animation.fixture.fire":{"states":{
            "default":{"transitions":[{"burning":"q.is_on_fire"}]},
            "burning":{"particle_effects":[{"effect":"fire"}],"transitions":[{"default":"!q.is_on_fire"}]}}}}}
            """);
        Files.writeString(pack.resolve("animations/particles.json"),"""
            {"animations":{"animation.fixture.particles":{"animation_length":1,"loop":true,"particle_effects":{"0":{"effect":"seed"}}}}}
            """);
    }
    static void verify(ClientGameTestContext context) {
        context.waitTicks(3);
        context.runOnClient(mc->{
            if(NativeParticles.snapshots().stream().noneMatch(s->s.effect().equals("fixture:mojang_basic_smoke")
                    &&dev.matanyah.bedrockcompat.bedrock.entity.Molang.number(s.variables().get("from_animation"))==1))
                throw new AssertionError("Animation timeline did not dispatch the real smoke definition through a child event");
            if(!NativeParticles.emitAt("fixture:mojang_basic_flame",new org.joml.Matrix4f().translation(1,100,0),true))
                throw new AssertionError("Standalone real flame emitter failed");
            var stand=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e instanceof ArmorStand).findFirst().orElseThrow();
            double x=stand.getX(),y=stand.getY(),z=stand.getZ();
            var anchors=java.util.Map.of("fixture_hand",new org.joml.Matrix4f().translation(16,32,48));
            var scale=new dev.matanyah.bedrockcompat.bedrock.entity.BedrockAnimation.Vector(1,1,1);
            NativeParticles.observe(stand,(q,a)->0d,java.util.Map.of(),anchors,0,scale,0);
            var kind=dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect.Kind.PARTICLE;
            NativeParticles.emit(stand,new dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect(kind,"fixture:bound","fixture_hand","",true,true),false);
            NativeParticles.emit(stand,new dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect(kind,"fixture:unbound","fixture_hand","",false,true),false);
            stand.setPos(x+4,y,z);NativeParticles.observe(stand,(q,a)->0d,java.util.Map.of(),anchors,0,scale,0);
            if(NativeParticles.snapshots().stream().filter(s->s.effect().equals("fixture:bound")||s.effect().equals("fixture:unbound")).count()!=2)
                throw new AssertionError("Expected exactly two binding regression emitters");
            for(var s:NativeParticles.snapshots())if(s.effect().equals("fixture:bound")||s.effect().equals("fixture:unbound")) {
                boolean bound=s.effect().equals("fixture:bound");
                if(s.bound()!=bound||Math.abs(s.position().x-(x+1+(bound?4:0)))>.001||Math.abs(s.position().y-(y+2))>.001||Math.abs(s.position().z-(z-3))>.001)
                    throw new AssertionError("Controller binding/locator transform incorrect: "+s);
            }
            dev.matanyah.bedrockcompat.client.particle.NativeParticleQueryChecks.verify();
            stand.setPos(x,y,z);
            var stop=dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect.Kind.PARTICLE_STOP;
            NativeParticles.emit(stand,new dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect(stop,"fixture:bound","fixture_hand","",true,true),false);
            NativeParticles.emit(stand,new dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityEffect(stop,"fixture:unbound","fixture_hand","",false,true),false);
        });
        context.runOnClient(mc->{
            if(!NativeParticles.hasDefinition("fixture:fire"))throw new AssertionError("Particle definition was not imported");
            var atlas=(net.minecraft.client.renderer.texture.TextureAtlas)mc.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            var sprite=atlas.getSprite(net.minecraft.resources.Identifier.parse("bedrock_native:particle/fixture"));
            if(sprite==atlas.missingSprite())throw new AssertionError("Imported particle texture was not stitched");
            for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand){e.setRemainingFireTicks(100);e.setSharedFlagOnFire(true);}
            mc.particleEngine.createParticle(ParticleTypes.CRIT,0,100,0,0,.1,0);
            if(NativeParticles.particleCount()==0)throw new AssertionError("Vanilla particle replacement did not spawn");
        });
        context.waitTicks(5);
        context.runOnClient(mc->{
            boolean found=false;
            for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand) {
                var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                if(!NativeParticles.replacesFire(e)||state.displayFireAnimation)throw new AssertionError("Pack fire emitter did not replace Java entity flames");
                e.clearFire();e.setSharedFlagOnFire(false);found=true;
            }
            if(!found)throw new AssertionError("No test armor stand");
        });
        context.waitTicks(3);
        context.runOnClient(mc->{for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand&&NativeParticles.replacesFire(e))throw new AssertionError("Fire emitter survived extinguishing");});
        context.takeScreenshot("particle-reference-flame-smoke");
    }
}
