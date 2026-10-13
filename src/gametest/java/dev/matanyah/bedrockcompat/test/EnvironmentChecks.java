package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.environment.NativeEnvironment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.function.Consumer;

/** Authored Bedrock schemas, tested through live client attribute and tint hooks, not parser acceptance. */
final class EnvironmentChecks {
    static void fixture(ClientGameTestContext context,Path pack)throws Exception {
        Files.createDirectories(pack.resolve("biomes"));Files.createDirectories(pack.resolve("fogs"));
        for(String id:java.util.List.of("plains","hell","the_end"))Files.writeString(pack.resolve("biomes/"+id+".client_biome.json"),"""
            {"format_version":"1.21.90","minecraft:client_biome":{"description":{"identifier":"%s"},"components":{
            "minecraft:sky_color":{"sky_color":"#80a0c0"},"minecraft:grass_appearance":{"color":"#123456"},
            "minecraft:foliage_appearance":{"color":"#234567"},"minecraft:dry_foliage_color":{"color":"#345678"},
            "minecraft:water_appearance":{"surface_color":"#456789"},"minecraft:fog_appearance":{"fog_identifier":"fixture:environment"},
            "minecraft:biome_music":{"volume_multiplier":0.25,"music_definition":""},
            "minecraft:ambient_sounds":{"loop":"fixture:ambient"},
            "minecraft:lighting_identifier":{"lighting_identifier":"fixture:light"}}}}
            """.formatted(id));
        Files.writeString(pack.resolve("fogs/environment.json"),"""
            {"format_version":"1.16.100","minecraft:fog_settings":{"description":{"identifier":"fixture:environment"},
            "distance":{"air":{"fog_start":4,"fog_end":64,"fog_color":"#506070","render_distance_type":"fixed"},
            "water":{"fog_start":1,"fog_end":18,"fog_color":"#102030","render_distance_type":"fixed"}}}}
            """);
        Files.writeString(pack.resolve("biomes/desert.client_biome.json"),"""
            {"format_version":"1.21.90","minecraft:client_biome":{"description":{"identifier":"desert"},
            "components":{"minecraft:sky_color":{"sky_color":"#204060"}}}}
            """);
        Files.createDirectories(pack.resolve("lighting"));
        Files.writeString(pack.resolve("lighting/test.json"),"""
            {"format_version":"1.26.0","minecraft:lighting_settings":{"description":{"identifier":"fixture:light"},
            "ambient":{"color":"#FFFFFF","illuminance":0.02},"directional_lights":{"orbital":{"sun":{"color":"#ff8000","illuminance":10000}}}}}
            """);
        Files.createDirectories(pack.resolve("sounds"));
        Files.writeString(pack.resolve("sounds.json"),"{\"individual_named_sounds\":{\"fixture:ambient\":{\"sound\":\"fixture.wind\"}}}");
        Files.writeString(pack.resolve("sounds/sound_definitions.json"),"{\"sound_definitions\":{\"fixture.wind\":{\"sounds\":[\"sounds/fixture-wind\"]}}}");
        // An existing Java OGG is copied into this GENERATED fixture, never into an installed pack.
        byte[] sound=context.computeOnClient(mc->{try(var stream=mc.getResourceManager().open(net.minecraft.resources.Identifier.withDefaultNamespace("sounds/ambient/cave/cave1.ogg"))){return stream.readAllBytes();}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}});
        Files.write(pack.resolve("sounds/fixture-wind.ogg"),sound);
    }
    static void disabled(ClientGameTestContext context){context.runOnClient(mc->{
        check(NativeEnvironment.snapshot().empty(),"removed pack left stale environment definitions");
        check(!NativeEnvironment.visuals(),"removed pack still overrides visuals");
        check(mc.level.getBiome(new BlockPos(0,100,0)).value().getGrassColor(0,0)!=0xff123456,"removed tint did not revert");
    });}
    static void verify(ClientGameTestContext context,Consumer<String> command){
        command.accept("fillbiome -8 92 -8 12 112 12 minecraft:plains");command.accept("time set 6000");command.accept("weather clear");
        context.waitTicks(8);
        context.runOnClient(mc->{
            var position=new BlockPos(0,100,0);var biome=mc.level.getBiome(position).value();
            check(biome.getGrassColor(0,0)==0xff123456,"grass hook");check(biome.getFoliageColor()==0xff234567,"foliage hook");
            check(biome.getDryFoliageColor()==0xff345678,"dry foliage hook");check(biome.getWaterColor()==0xff456789,"water tint must retain opaque Java alpha");
            var reader=mc.level.environmentAttributes();var p=new Vec3(0,100,0);
            check(reader.getValue(EnvironmentAttributes.FOG_START_DISTANCE,p)==4,"air fog start");
            check(reader.getValue(EnvironmentAttributes.FOG_END_DISTANCE,p)==64,"air fog end");
            check((reader.getValue(EnvironmentAttributes.FOG_COLOR,p)&0xffffff)==0x506070,"air fog color");
            check(reader.getValue(EnvironmentAttributes.WATER_FOG_END_DISTANCE,p)==18,"underwater fog distance");
            check((reader.getValue(EnvironmentAttributes.WATER_FOG_COLOR,p)&0xffffff)==0x102030,"underwater fog color");
            check((reader.getValue(EnvironmentAttributes.SKY_COLOR,p)&0xffffff)==0x80a0c0,"sky color: "+Integer.toHexString(reader.getValue(EnvironmentAttributes.SKY_COLOR,p)));
            check(reader.getValue(EnvironmentAttributes.BACKGROUND_MUSIC,p).equals(BackgroundMusic.EMPTY),"authored music mute");
            var sounds=reader.getValue(EnvironmentAttributes.AMBIENT_SOUNDS,p);
            check(sounds.loop().isPresent(),"imported ambient loop binding");
            check(sounds.loop().get().value().location().getNamespace().equals("bedrock_native"),"ambient uses imported sound event");
            // Independent builder checks the adapter before Java's intentional time/weather light modifiers.
            var builder=EnvironmentAttributeSystem.builder();NativeEnvironment.addLayers(builder,mc.level);var isolated=builder.build();
            check(isolated.getValue(EnvironmentAttributes.MUSIC_VOLUME,p)==0.25f,"music volume");
            verifyAmbientFallback(mc.level,p);
        });
        capture(context,"environment-plains-noon");
        // An enclosed camera must remain unlit despite the pack's default white ambient color.
        command.accept("fill -3 98 1 3 104 7 minecraft:stone");
        command.accept("fill -2 99 2 2 103 6 minecraft:air");context.waitTicks(20);
        context.runOnClient(mc->{
            var camera=BlockPos.containing(mc.gameRenderer.mainCamera().position());
            check(mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,camera)==0,"sealed room has no skylight");
            check(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,camera)==0,"sealed room has no block light");
            verifyAmbientFallback(mc.level,mc.gameRenderer.mainCamera().position());
        });
        capture(context,"environment-white-ambient-unlit-room");
        command.accept("setblock 0 99 3 minecraft:torch");context.waitTicks(20);
        capture(context,"environment-white-ambient-torch-room");
        command.accept("fill -3 98 1 3 104 7 minecraft:air");
        command.accept("fill -5 98 -5 5 98 5 minecraft:stone");
        command.accept("fill -2 99 2 2 103 6 minecraft:water");context.waitTicks(10);
        context.runOnClient(mc->check(mc.gameRenderer.mainCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.WATER,"underwater camera scenario"));
        capture(context,"environment-underwater");
        command.accept("fill -2 99 2 2 103 6 minecraft:air");
        command.accept("weather rain");command.accept("time set 18000");context.waitTicks(8);
        context.runOnClient(mc->verifyAmbientFallback(mc.level,new Vec3(0,100,0)));
        capture(context,"environment-rain-midnight");
        command.accept("weather clear");command.accept("time set 6000");
        command.accept("fillbiome 4 92 -8 24 112 12 minecraft:desert");context.waitTicks(8);
        context.runOnClient(mc->{
            var builder=EnvironmentAttributeSystem.builder();NativeEnvironment.addLayers(builder,mc.level);var isolated=builder.build();boolean boundary=false;
            for(int x=0;x<24;x+=4){
                int a=isolated.getValue(EnvironmentAttributes.SKY_COLOR,new Vec3(x,100,0));
                int b=isolated.getValue(EnvironmentAttributes.SKY_COLOR,new Vec3(x+4,100,0));
                int mid=isolated.getValue(EnvironmentAttributes.SKY_COLOR,new Vec3(x+2,100,0));
                check((mid&0xffffff)==dev.matanyah.bedrockcompat.bedrock.environment.BedrockEnvironment.mixColor(a,b,0.5),"spatial midpoint interpolation");
                boundary|=a!=b;
            }
            check(boundary,"two-biome transition fixture actually crossed a boundary");
        });
        command.accept("fillbiome -8 92 -8 24 112 12 minecraft:plains");
        for(String dimension:java.util.List.of("the_nether","the_end")){
            command.accept("execute as @a in minecraft:"+dimension+" run tp @s 0 100 0");
            context.waitFor(mc->mc.level!=null&&mc.level.dimension().identifier().getPath().equals(dimension));
            command.accept("execute in minecraft:"+dimension+" run fillbiome -8 92 -8 12 112 12 minecraft:"+(dimension.equals("the_end")?"the_end":"nether_wastes"));
            context.waitTicks(8);
            context.runOnClient(mc->check(mc.level.environmentAttributes().getValue(EnvironmentAttributes.WATER_FOG_END_DISTANCE,new Vec3(0,100,0))==18,"dimension-specific environment binding "+dimension));
            context.runOnClient(mc->verifyAmbientFallback(mc.level,new Vec3(0,100,0)));
            capture(context,"environment-"+dimension);
        }
        command.accept("execute as @a in minecraft:overworld run tp @s 0 99 4 180 0");
        context.waitFor(mc->mc.level!=null&&mc.level.dimension().identifier().getPath().equals("overworld"));
        context.waitTicks(8);
    }
    private static void verifyAmbientFallback(net.minecraft.world.level.Level level,Vec3 p){
        // Neither white ambient nor orange VV sunlight may replace Java's lightmap.
        for(var attribute:java.util.List.of(EnvironmentAttributes.AMBIENT_LIGHT_COLOR,EnvironmentAttributes.SKY_LIGHT_COLOR)){
            var vanilla=EnvironmentAttributeSystem.builder().build();
            var adapted=EnvironmentAttributeSystem.builder();NativeEnvironment.addLayers(adapted,level);
            check(adapted.build().getValue(attribute,p).equals(vanilla.getValue(attribute,p)),"default Java light color changed");
            // Also preserve existing dimension/mod-owned light layers, not a hardcoded white.
            for(int original:new int[]{0,0x102030,0xffffff}){
            var builder=EnvironmentAttributeSystem.builder();
            builder.addPositionalLayer(attribute,(value,pos,interpolator)->original);
            NativeEnvironment.addLayers(builder,level);
            check(builder.build().getValue(attribute,p)==original,
                "Bedrock chromaticity must not replace Java lightmap colors");
            }
        }
    }
    private static void capture(ClientGameTestContext context,String name){
        var options=net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name);
        String destination=System.getenv("BEDROCK_ENVIRONMENT_SCREENSHOTS");
        if(destination!=null&&!destination.isBlank())options.withDestinationDir(Path.of(destination));
        context.takeScreenshot(options);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("Environment: "+message);}
}
