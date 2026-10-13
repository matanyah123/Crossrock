package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.*;
import net.minecraft.util.RandomSource;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

final class BlockAssetExpansionChecks {
    static void fixture(Path pack)throws Exception {
        Files.createDirectories(pack.resolve("block_culling"));Files.createDirectories(pack.resolve("items"));
        JsonObject geometry=JsonParser.parseString("""
            {"format_version":"1.21.100","minecraft:geometry":[{"description":{"identifier":"geometry.expansion_item","texture_width":256,"texture_height":256},
             "bones":[{"name":"root","cubes":[{"origin":[-32,0,-8],"size":[64,32,16],"uv":[0,0]}]}],
             "item_display_transforms":{"gui":{"translation":[2,3,0],"rotation":[0,0,0],"scale":[1,1,1],"fit_to_frame":true},
             "fixed":{"translation":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}}}]}
            """).getAsJsonObject();
        Files.writeString(pack.resolve("models/blocks/expansion.geo.json"),geometry.toString());
        Files.writeString(pack.resolve("blocks/expansion.json"),"""
            {"format_version":"1.21.100","minecraft:block":{"description":{"identifier":"bedrock_compat_test:stateful_probe",
              "states":{"test:powered":[false,true],"minecraft:cardinal_direction":["north","south","east","west"]}},
             "components":{"minecraft:geometry":{"identifier":"minecraft:geometry.full_block","uv_lock":true,"culling":"test:expansion","culling_layer":"test:shared"},
              "minecraft:material_instances":{"*":{"texture":"static_sheet","render_method":"opaque"}},
              "minecraft:item_visual":{"geometry":"geometry.expansion_item","material_instances":{"*":{"texture":"static_sheet"}}}},
             "permutations":[
              {"condition":"q.block_state('test:powered')","components":{"minecraft:transformation":{"translation":[0.25,0,0],"scale":[0.5,1,0.5],"rotation":[0,90,0]}}},
              {"condition":"q.block_state('test:powered') && q.block_state('minecraft:cardinal_direction') == 'east'","components":{"minecraft:transformation":{"translation":[-0.25,0,0],"scale":[0.25,1,0.25]}}},
              {"condition":"q.block_state('test:powered') && q.block_state('minecraft:cardinal_direction') == 'west'","components":{"minecraft:geometry":{"identifier":"minecraft:geometry.full_block","bone_visibility":{"root":false}}}}
             ]}}
            """);
        JsonArray rules=new JsonArray();
        String[] directions={"north","south","east","west"},conditions={"same_block","same_block_permutation","same_culling_layer","default"};
        for(int i=0;i<4;i++){JsonObject rule=new JsonObject();rule.addProperty("direction",directions[i]);rule.addProperty("condition",conditions[i]);rule.addProperty("cull_against_full_and_opaque",i==3);
            JsonObject part=new JsonObject();part.addProperty("bone","root");part.addProperty("cube",0);part.addProperty("face",directions[i]);rule.add("geometry_part",part);rules.add(rule);}
        JsonObject culling=new JsonObject(),description=new JsonObject(),root=new JsonObject();description.addProperty("identifier","test:expansion");culling.add("description",description);culling.add("rules",rules);root.addProperty("format_version","1.21.80");root.add("minecraft:block_culling_rules",culling);
        Files.writeString(pack.resolve("block_culling/expansion.json"),root.toString());
        JsonObject custom=JsonParser.parseString(Files.readString(pack.resolve("blocks/custom.json"))).getAsJsonObject();
        custom.getAsJsonObject("minecraft:block").getAsJsonObject("components").add("minecraft:geometry",JsonParser.parseString("{\"identifier\":\"geometry.static_fixture\",\"culling_layer\":\"test:shared\"}"));Files.writeString(pack.resolve("blocks/custom.json"),custom.toString());
        var atlas=JsonParser.parseString(Files.readString(pack.resolve("textures/item_texture.json"))).getAsJsonObject();atlas.getAsJsonObject("texture_data").add("expansion_icon",JsonParser.parseString("{\"textures\":\"textures/geometry_fixture/sheet\"}"));Files.writeString(pack.resolve("textures/item_texture.json"),atlas.toString());
        Files.writeString(pack.resolve("items/legacy.json"),"""
            {"format_version":"1.10.0","minecraft:item":{"description":{"identifier":"bedrock_compat_test:icon_probe"},"components":{"minecraft:icon":"expansion_icon"}}}
            """);
        Files.writeString(pack.resolve("items/modern.json"),"""
            {"format_version":"1.21.60","minecraft:item":{"description":{"identifier":"minecraft:amethyst_shard"},"components":{"minecraft:icon":{"textures":{"default":"expansion_icon"}}}}}
            """);
    }
    private static BlockState state(boolean powered,Direction facing){return StaticBlockTestInitializer.STATEFUL.defaultBlockState().setValue(BlockStateProperties.POWERED,powered).setValue(BlockStateProperties.HORIZONTAL_FACING,facing);}
    private static List<BakedQuad> quads(Minecraft mc,BlockState state){List<BlockStateModelPart> parts=new ArrayList<>();mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0),parts);return parts.stream().flatMap(p->p.getQuads(null).stream()).toList();}
    static void verify(ClientGameTestContext context,Consumer<String> command) {
        context.runOnClient(mc->{
            var off=quads(mc,state(false,Direction.NORTH));var on=quads(mc,state(true,Direction.NORTH));var east=quads(mc,state(true,Direction.EAST));var hidden=quads(mc,state(true,Direction.WEST));
            if(off.size()!=6||on.size()!=6||!hidden.isEmpty())throw new AssertionError("state-driven geometry/visibility failed");
            near(minX(on),.5,"powered transformation");near(minX(east),.125,"later permutation must replace component");
            var item=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("bedrock_compat_test:stateful_probe")));
            var gui=item(mc,item,ItemDisplayContext.GUI).getModelBoundingBox();var fixed=item(mc,item,ItemDisplayContext.FIXED).getModelBoundingBox();
            near(gui.getXsize(),1,"GUI fit width");near(gui.getCenter().x,.125,"GUI authored X translation");near(gui.getCenter().y,.1875,"GUI authored Y translation");near(fixed.getXsize(),4,"non-GUI geometry remains unfitted");
            for(Item icon:List.of(StaticBlockTestInitializer.ICON,Items.AMETHYST_SHARD)) {
                var rendered=item(mc,new ItemStack(icon),ItemDisplayContext.GUI);var sprite=rendered.pickParticleMaterial(RandomSource.create(0)).sprite().contents().name();
                if(!sprite.getNamespace().equals("bedrock_compat")||!sprite.getPath().startsWith("item/assets/"))throw new AssertionError("non-block icon definition did not execute");
            }
        });
        command.accept("fill 7 98 7 9 100 9 minecraft:air");command.accept("setblock 8 99 8 bedrock_compat_test:stateful_probe[facing=north,powered=false]");
        command.accept("setblock 8 99 7 bedrock_compat_test:stateful_probe[facing=north,powered=true]");command.accept("setblock 8 99 9 bedrock_compat_test:stateful_probe[facing=north,powered=true]");
        command.accept("setblock 9 99 8 bedrock_compat_test:geometry_probe");command.accept("setblock 7 99 8 minecraft:stone");
        BlockPos pos=new BlockPos(8,99,8);context.waitFor(mc->mc.level.getBlockState(pos.east()).is(StaticBlockTestInitializer.BLOCK));
        context.runOnClient(mc->{if(emitted(mc,pos)!=3)throw new AssertionError("authored same-block/layer/full culling did not execute: "+emitted(mc,pos));});
        Object key=context.computeOnClient(mc->mc.getModelManager().getBlockStateModelSet().get(mc.level.getBlockState(pos)).createGeometryKey(mc.level,pos,mc.level.getBlockState(pos),RandomSource.create(0)));
        command.accept("setblock 8 99 9 bedrock_compat_test:stateful_probe[facing=north,powered=false]");context.waitFor(mc->!mc.level.getBlockState(pos.south()).getValue(BlockStateProperties.POWERED));
        context.runOnClient(mc->{if(emitted(mc,pos)!=2)throw new AssertionError("same permutation culling did not update with neighbor");
            var model=mc.getModelManager().getBlockStateModelSet().get(mc.level.getBlockState(pos));if(key.equals(model.createGeometryKey(mc.level,pos,mc.level.getBlockState(pos),RandomSource.create(0))))throw new AssertionError("neighbor-dependent geometry cache key stale");});
    }
    private static int emitted(Minecraft mc,BlockPos pos) {
        AtomicInteger count=new AtomicInteger();var emitter=Renderer.get().quadEmitter(q->count.incrementAndGet());BlockState state=mc.level.getBlockState(pos);
        mc.getModelManager().getBlockStateModelSet().get(state).emitQuads(emitter,mc.level,pos,state,RandomSource.create(0),face->false);return count.get();
    }
    private static net.minecraft.client.renderer.item.ItemStackRenderState item(Minecraft mc,ItemStack stack,ItemDisplayContext display){var item=new net.minecraft.client.renderer.item.ItemStackRenderState();mc.getItemModelResolver().updateForTopItem(item,stack,display,mc.level,mc.player,0);if(item.isEmpty())throw new AssertionError("missing expanded item model");return item;}
    private static double minX(List<BakedQuad> quads){return quads.stream().flatMap(q->java.util.stream.IntStream.range(0,4).mapToObj(q::position)).mapToDouble(org.joml.Vector3fc::x).min().orElseThrow();}
    private static void near(double value,double expected,String text){if(Math.abs(value-expected)>1e-4)throw new AssertionError(text+": "+value+" != "+expected);}
    static void disabled(ClientGameTestContext context){context.runOnClient(mc->{for(Item icon:List.of(StaticBlockTestInitializer.ICON,Items.AMETHYST_SHARD)){var rendered=item(mc,new ItemStack(icon),ItemDisplayContext.GUI);if(rendered.pickParticleMaterial(RandomSource.create(0)).sprite().contents().name().getNamespace().equals("bedrock_compat"))throw new AssertionError("removed item definition persists");}});}
}
