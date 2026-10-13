package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.client.block.NativeBlockModels;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

final class StaticBlockGeometryChecks {
    static void fixture(Path pack)throws Exception {
        JsonObject manifest=JsonParser.parseString(Files.readString(pack.resolve("manifest.json"))).getAsJsonObject();
        manifest.getAsJsonArray("modules").add(JsonParser.parseString("{\"type\":\"data\",\"uuid\":\"af8123d8-5fc0-4df1-a715-04065feebd01\",\"version\":[1,0,0]}"));
        Files.writeString(pack.resolve("manifest.json"),manifest.toString());
        Files.createDirectories(pack.resolve("models/blocks"));Files.createDirectories(pack.resolve("blocks"));
        Files.createDirectories(pack.resolve("textures/geometry_fixture"));
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0x807733dd);
        ImageIO.write(image,"PNG",pack.resolve("textures/geometry_fixture/sheet.png").toFile());
        JsonObject atlas=JsonParser.parseString(Files.readString(pack.resolve("textures/terrain_texture.json"))).getAsJsonObject();
        atlas.getAsJsonObject("texture_data").add("static_sheet",JsonParser.parseString("{\"textures\":\"textures/geometry_fixture/sheet\"}"));
        Files.writeString(pack.resolve("textures/terrain_texture.json"),atlas.toString());
        Files.writeString(pack.resolve("models/blocks/static.geo.json"),"""
            {"format_version":"1.21.30","minecraft:geometry":[
              {"description":{"identifier":"geometry.static_fixture","texture_width":64,"texture_height":64},
               "bones":[{"name":"root","cubes":[{"origin":[-4,0,-4],"size":[8,8,8],"uv":[0,0]}]},
                 {"name":"tilted","parent":"root","pivot":[0,10,0],"rotation":[0,0,30],
                  "cubes":[{"origin":[-2,8,-2],"size":[4,4,4],"uv":{"north":{"uv":[32,0],"uv_size":[4,4],"material_instance":"glass"},
                    "south":{"uv":[32,4],"uv_size":[-4,4],"material_instance":"leaf","uv_rotation":90}}}]}]},
              {"description":{"identifier":"geometry.static_item","texture_width":64,"texture_height":64},
               "bones":[{"name":"root","cubes":[{"origin":[-2,6,-2],"size":[4,4,4],"uv":[0,0]}]}],
               "item_display_transforms":{
                 "gui":{"translation":[16,0,0],"rotation":[0,0,0],"scale":[1,1,1],"fit_to_frame":false},
                 "fixed":{"translation":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 "firstperson_righthand":{"translation":[8,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 "firstperson_lefthand":{"translation":[-8,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 "thirdperson_righthand":{"translation":[0,8,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 "thirdperson_lefthand":{"translation":[0,-8,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 "ground":{"translation":[0,0,0],"rotation":[0,0,0],"scale":[2,2,2]},
                 "head":{"translation":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}}}
            ]}
            """);
        Files.writeString(pack.resolve("blocks/static.json"),"""
            {"format_version":"1.21.60","minecraft:block":{"description":{"identifier":"minecraft:diamond_block"},
             "components":{"minecraft:geometry":"geometry.static_fixture",
              "minecraft:material_instances":{"*":"base","base":{"texture":"static_sheet","render_method":"opaque","ambient_occlusion":false},
                "glass":{"texture":"static_sheet","render_method":"blend","ambient_occlusion":false,"face_dimming":false},
                "leaf":{"texture":"static_sheet","render_method":"alpha_test","ambient_occlusion":false}},
              "minecraft:item_visual":{"geometry":"geometry.static_item","material_instances":{"*":{"texture":"static_sheet","render_method":"alpha_test_single_sided"}}}}}}
            """);
        String binding=Files.readString(pack.resolve("blocks/static.json"));
        Files.writeString(pack.resolve("blocks/custom.json"),binding.replace("minecraft:diamond_block","bedrock_compat_test:geometry_probe"));
    }
    static void verify(ClientGameTestContext context,java.util.function.Consumer<String> command) {
        context.runOnClient(mc->{
            var state=Blocks.DIAMOND_BLOCK.defaultBlockState();var model=mc.getModelManager().getBlockStateModelSet().get(state);
            if(!NativeBlockModels.custom(state))throw new AssertionError("static geometry not active");
            if(!NativeBlockModels.custom(StaticBlockTestInitializer.BLOCK.defaultBlockState()))throw new AssertionError("external custom block identity not bound");
            var customItem=new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(customItem,new ItemStack(StaticBlockTestInitializer.ITEM),ItemDisplayContext.FIXED,mc.level,mc.player,0);
            if(customItem.isEmpty())throw new AssertionError("external custom block item did not bake");
            near(customItem.getModelBoundingBox().getXsize(),.25,"external custom block item geometry");
            List<BlockStateModelPart> parts=new ArrayList<>();model.collectParts(RandomSource.create(1),parts);
            List<BakedQuad> quads=parts.stream().flatMap(p->p.getQuads(null).stream()).toList();
            if(quads.size()!=9)throw new AssertionError("Expected 6 opaque + 1 blended + 2 double-sided cutout faces, got "+quads.size());
            if(quads.stream().noneMatch(q->q.materialInfo().layer().translucent()))throw new AssertionError("blend face lost translucent layer");
            if(quads.stream().filter(q->!q.materialInfo().shade()).count()!=1)throw new AssertionError("face_dimming did not execute");
            if(parts.stream().anyMatch(BlockStateModelPart::useAmbientOcclusion))throw new AssertionError("AO false did not execute");
            if(!Block.shouldRenderFace(Blocks.STONE.defaultBlockState(),state,Direction.NORTH))throw new AssertionError("vanilla neighbour hidden by hollow custom shape");
            var gui=bounds(mc,ItemDisplayContext.GUI);var fixed=bounds(mc,ItemDisplayContext.FIXED);
            near(gui.minX-fixed.minX,1,"GUI translation");near(fixed.getXsize(),.25,"separate item_visual geometry");
            var right=bounds(mc,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);var left=bounds(mc,ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            near(right.minX-left.minX,1,"independent authored hand translations");
            var thirdRight=bounds(mc,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);var thirdLeft=bounds(mc,ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            near(thirdRight.minY-thirdLeft.minY,1,"third-person contexts");
            near(bounds(mc,ItemDisplayContext.GROUND).getXsize(),.5,"ground scale");near(bounds(mc,ItemDisplayContext.HEAD).getXsize(),.25,"head context");
        });
        command.accept("setblock 2 99 0 minecraft:diamond_block");command.accept("setblock 2 99 -1 minecraft:stone");
        command.accept("item replace entity @a weapon.mainhand with minecraft:diamond_block");
        context.waitTicks(5);context.takeScreenshot("static-block-geometry-materials");
    }
    static void verifyReload(ClientGameTestContext context,Path pack,Runnable reload)throws Exception {
        Path file=pack.resolve("models/blocks/static.geo.json");String original=Files.readString(file);
        try {
            Files.writeString(file,original.replace("[-4,0,-4]","[-6,0,-4]"));
            reload.run();
            context.runOnClient(mc->{
                var model=mc.getModelManager().getBlockStateModelSet().get(Blocks.DIAMOND_BLOCK.defaultBlockState());
                List<BlockStateModelPart> parts=new ArrayList<>();model.collectParts(RandomSource.create(1),parts);
                double min=parts.stream().flatMap(p->p.getQuads(null).stream()).flatMap(q->java.util.stream.IntStream.range(0,4).mapToObj(q::position)).mapToDouble(org.joml.Vector3fc::x).min().orElseThrow();
                near(min,.125,"geometry file reload");
            });
        } finally {Files.writeString(file,original);reload.run();}
    }
    private static net.minecraft.world.phys.AABB bounds(net.minecraft.client.Minecraft mc,ItemDisplayContext display) {
        var item=new net.minecraft.client.renderer.item.ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(item,new ItemStack(Items.DIAMOND_BLOCK),display,mc.level,mc.player,0);
        if(item.isEmpty())throw new AssertionError("missing static item model "+display);
        return item.getModelBoundingBox();
    }
    private static void near(double value,double expected,String label){if(Math.abs(value-expected)>1e-4)throw new AssertionError(label+": "+value+" != "+expected);}
    static void disabled(ClientGameTestContext context) {
        context.runOnClient(mc->{
            if(NativeBlockModels.custom(StaticBlockTestInitializer.BLOCK.defaultBlockState()))throw new AssertionError("removed external custom block geometry remains active");
            if(NativeBlockModels.custom(Blocks.DIAMOND_BLOCK.defaultBlockState()))throw new AssertionError("removed custom geometry remains active");
            if(Block.shouldRenderFace(Blocks.STONE.defaultBlockState(),Blocks.DIAMOND_BLOCK.defaultBlockState(),Direction.NORTH))throw new AssertionError("removed custom geometry retained neighbour culling bypass");
            var item=new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(item,new ItemStack(Items.DIAMOND_BLOCK),ItemDisplayContext.GUI,mc.level,mc.player,0);
            if(!item.pickParticleMaterial(RandomSource.create(1)).sprite().contents().name().toString().equals("minecraft:block/diamond_block"))
                throw new AssertionError("removed display model retained item material");
        });
    }
}
