package dev.matanyah.bedrockcompat.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;

final class PresentationResourceChecks {
    static void fixture(Path pack) throws Exception {
        Files.createDirectories(pack.resolve("textures/ui"));
        Files.createDirectories(pack.resolve("textures/entity/bed"));
        Files.createDirectories(pack.resolve("textures/blocks"));
        Files.writeString(pack.resolve("splashes.json"),"{\"splashes\":[\"Crossrock presentation fixture!\"]}");
        Files.writeString(pack.resolve("blocks.json"),"{\"normal_stone_double_slab\":{\"textures\":\"crossrock_double\"}}");
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff000000|x*4<<16|y*4<<8);
        ImageIO.write(image,"PNG",pack.resolve("textures/entity/bed/red.png").toFile());
        ImageIO.write(image,"PNG",pack.resolve("textures/blocks/crossrock_double.png").toFile());
        ImageIO.write(image,"PNG",pack.resolve("textures/ui/panorama_0.png").toFile());
        ImageIO.write(image,"PNG",pack.resolve("textures/ui/heart.png").toFile());
        ImageIO.write(image,"PNG",pack.resolve("textures/ui/title.png").toFile());
        for(String name:new String[]{"button_borderless_light","button_borderless_lighthover",
                "button_borderless_lightpressednohover","slider_background","slider_button_default",
                "checkbox_check","book_arrowleft_default","close_button_default"}) {
            ImageIO.write(image,"PNG",pack.resolve("textures/ui/"+name+".png").toFile());
        }
        Files.writeString(pack.resolve("entity/bed.json"),"""
            {"minecraft:client_entity":{"description":{"identifier":"minecraft:bed",
             "geometry":{"default":"geometry.fixture_bed"},"textures":{"red":"textures/entity/bed/red"}}}}
            """);
        Files.writeString(pack.resolve("models/entity/bed.json"),"""
            {"minecraft:geometry":[{"description":{"identifier":"geometry.fixture_bed","texture_width":64,"texture_height":64},
            "bones":[{"name":"bed","cubes":[{"origin":[0,0,0],"size":[16,32,6],"uv":[0,0]}]}]}]}
            """);
    }
    static void verify(ClientGameTestContext context) {
        context.runOnClient(mc->{
            for(String path:new String[]{"texts/splashes.txt","textures/gui/title/background/panorama_0.png",
                    "textures/gui/sprites/hud/heart/full.png","models/block/red_bed_foot.json",
                    "models/block/red_bed_head.json","blockstates/stone_slab.json",
                    "textures/gui/title/minecraft.png","textures/gui/title/edition.png",
                    "textures/gui/sprites/widget/button.png","textures/gui/sprites/widget/slider_handle.png",
                    "textures/gui/sprites/widget/checkbox_selected.png"}) {
                if(mc.getResourceManager().getResource(Identifier.withDefaultNamespace(path)).isEmpty())
                    throw new AssertionError("Missing translated presentation resource: "+path);
            }
            var models=mc.getModelManager().getBlockStateModelSet();
            for(var state:new net.minecraft.world.level.block.state.BlockState[]{
                    Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART,BedPart.HEAD),
                    Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART,BedPart.FOOT),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.DOUBLE),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.BOTTOM),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP)}) {
                if(models.get(state)==models.missingModel())throw new AssertionError("Translated model failed to bake: "+state);
            }
        });
    }
}
