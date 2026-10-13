package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class BlockItemAssetChecks {
    static void fixture(Path pack) throws Exception {
        Files.createDirectories(pack.resolve("textures/asset_fixture"));
        for (String name : new String[]{"world", "held", "icon"}) {
            var image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++) for(int x=0;x<16;x++) image.setRGB(x,y,name.equals("world")?0xffdd2244:0xff22cc88);
            ImageIO.write(image,"PNG",pack.resolve("textures/asset_fixture/"+name+".png").toFile());
        }
        JsonObject blocks = JsonParser.parseString(Files.readString(pack.resolve("blocks.json"))).getAsJsonObject();
        blocks.add("minecraft:iron_block",JsonParser.parseString("{\"textures\":{\"*\":\"fixture_world\"},\"carried_textures\":\"fixture_held\"}"));
        blocks.add("minecraft:gold_block",JsonParser.parseString("{\"textures\":\"fixture_missing\"}"));
        Files.writeString(pack.resolve("blocks.json"),blocks.toString());
        JsonObject terrain = JsonParser.parseString(Files.exists(pack.resolve("textures/terrain_texture.json")) ? Files.readString(pack.resolve("textures/terrain_texture.json")) : "{\"texture_data\":{}}").getAsJsonObject();
        for(String name:new String[]{"world","held"}) terrain.getAsJsonObject("texture_data").add("fixture_"+name,
            JsonParser.parseString("{\"textures\":\"textures/asset_fixture/"+name+"\"}"));
        Files.writeString(pack.resolve("textures/terrain_texture.json"),terrain.toString());
        JsonObject itemAtlas = JsonParser.parseString(Files.readString(pack.resolve("textures/item_texture.json"))).getAsJsonObject();
        JsonObject extra = JsonParser.parseString("""
            {"texture_data":{"diamond":{"textures":"textures/asset_fixture/icon"},
             "emerald":{"textures":"textures/asset_fixture/missing"}}}
            """).getAsJsonObject();
        extra.getAsJsonObject("texture_data").entrySet().forEach(e->itemAtlas.getAsJsonObject("texture_data").add(e.getKey(),e.getValue()));
        Files.writeString(pack.resolve("textures/item_texture.json"),itemAtlas.toString());
    }
    static void verify(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var models=mc.getModelManager().getBlockStateModelSet();
            var block=models.get(Blocks.IRON_BLOCK.defaultBlockState());
            if(block==models.missingModel() || !block.particleMaterial().sprite().contents().name().toString().equals("minecraft:block/crossrock_assets/textures/asset_fixture/world"))
                throw new AssertionError("blocks.json cube did not bake with authored world texture");
            for(ItemDisplayContext display:new ItemDisplayContext[]{ItemDisplayContext.GUI,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED}) {
                var item=new net.minecraft.client.renderer.item.ItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(item,new ItemStack(Items.IRON_BLOCK),display,mc.level,mc.player,0);
                var material=item.pickParticleMaterial(net.minecraft.util.RandomSource.create(1));
                if(item.isEmpty() || material==null || !material.sprite().contents().name().toString().equals("minecraft:block/crossrock_assets/textures/asset_fixture/held"))
                    throw new AssertionError("carried_textures did not bake in "+display);
            }
            var gold=models.get(Blocks.GOLD_BLOCK.defaultBlockState());
            if(!gold.particleMaterial().sprite().contents().name().equals(Identifier.withDefaultNamespace("block/gold_block")))
                throw new AssertionError("missing alias replaced vanilla gold model");
            try(var in=mc.getResourceManager().open(Identifier.withDefaultNamespace("textures/item/diamond.png"))) {
                if(ImageIO.read(in).getRGB(0,0)!=0xff22cc88) throw new AssertionError("item atlas alias was not consumed");
            } catch(java.io.IOException ex) {throw new java.io.UncheckedIOException(ex);}
            var diamond=new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(diamond,new ItemStack(Items.DIAMOND),ItemDisplayContext.GUI,mc.level,mc.player,0);
            if(diamond.isEmpty() || !diamond.pickParticleMaterial(net.minecraft.util.RandomSource.create(1)).sprite().contents().name().equals(Identifier.withDefaultNamespace("item/diamond")))
                throw new AssertionError("translated item icon failed to bake");
        });
    }
    static void disabled(ClientGameTestContext context) {
        context.runOnClient(mc->{
            var model=mc.getModelManager().getBlockStateModelSet().get(Blocks.IRON_BLOCK.defaultBlockState());
            if(!model.particleMaterial().sprite().contents().name().equals(Identifier.withDefaultNamespace("block/iron_block")))
                throw new AssertionError("pack removal retained generated cube texture");
            var item=new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(item,new ItemStack(Items.IRON_BLOCK),ItemDisplayContext.GUI,mc.level,mc.player,0);
            if(!item.pickParticleMaterial(net.minecraft.util.RandomSource.create(1)).sprite().contents().name().equals(Identifier.withDefaultNamespace("block/iron_block")))
                throw new AssertionError("pack removal retained carried texture");
        });
    }

}
