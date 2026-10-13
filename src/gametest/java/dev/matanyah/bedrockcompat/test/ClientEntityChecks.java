package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.entity.*;
import dev.matanyah.bedrockcompat.client.particle.NativeParticles;
import dev.matanyah.bedrockcompat.bedrock.entity.Molang;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Independent generated client-entity fixture, not an edit to an installed pack. */
final class ClientEntityChecks {
    static void fixture(Path pack)throws Exception {
        for(boolean controls:List.of(false,true)) {
            String species=controls?"husk":"zombie";
            Files.writeString(pack.resolve("entity/client_"+species+".json"),"""
                {"format_version":"1.8.0","minecraft:client_entity":{"description":{
                "identifier":"minecraft:SPECIES","enable_attachables":true,"hide_armor":CONTROLS,
                "held_item_ignores_lighting":CONTROLS,
                "geometry":{"default":"geometry.test_stand"},"materials":{"default":"entity_alphatest"},
                "textures":{"default":"textures/entity/armor_stand"},
                "locators":{"legacy_lead":{"head":[0,32,-8]}},
                "animation_controllers":[{"legacy":"controller.animation.client_fixture"}],
                "render_controllers":["controller.render.client_fixture"],
                "scripts":{"initialize":["v.public_probe=7;v.secret_probe=9;"],
                  "variables":{"variable.public_probe":"public"},"scaleX":1,"scaleY":1,"scaleZ":1},
                "spawn_egg":{"texture":"client_fixture","texture_index":1}}}}
                """.replace("SPECIES",species).replace("CONTROLS",Boolean.toString(controls)));
        }
        Files.writeString(pack.resolve("animation_controllers/client_fixture.json"),"""
            {"animation_controllers":{"controller.animation.client_fixture":{"states":{"default":{"on_entry":["v.public_probe=v.public_probe+1;"]}}}}}
            """);
        Files.writeString(pack.resolve("render_controllers/client_fixture.json"),"""
            {"render_controllers":{"controller.render.client_fixture":{"geometry":"Geometry.default","textures":["Texture.default"],"materials":[{"*":"Material.default"}]}}}
            """);
        Files.writeString(pack.resolve("textures/item_texture.json"),"""
            {"texture_data":{"client_fixture":{"textures":["textures/entity/emissive_fixture","textures/entity/armor_stand"]}}}
            """);
    }
    static void verify(ClientGameTestContext context,Consumer<String> command) {
        var observed=new ArrayList<net.minecraft.world.entity.Entity>();
        for(String species:List.of("zombie","husk")) {
            command.accept("summon minecraft:"+species+" 2 99 1 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            command.accept("item replace entity @e[type=minecraft:"+species+"] armor.chest with minecraft:diamond_chestplate");
            command.accept("item replace entity @e[type=minecraft:"+species+"] weapon.mainhand with minecraft:stick");
        }
        context.waitTicks(10);
        context.runOnClient(mc->{
            int found=0;
            for(var actor:mc.level.entitiesForRendering()) {
                String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).toString();
                if(!Set.of("minecraft:zombie","minecraft:husk").contains(id))continue;
                boolean controls=id.endsWith("husk");found++;observed.add(actor);
                var state=mc.getEntityRenderDispatcher().getRenderer(actor).createRenderState(actor,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                if(frame==null||frame.draws().isEmpty())throw new AssertionError("Client fixture fell back: "+NativeEntityResources.errors());
                if(frame.attachments().isEmpty()||frame.attachments().stream().anyMatch(a->a.fullBright()!=controls))
                    throw new AssertionError("Held-item light control not applied: "+id);
                if(frame.equipment().isEmpty()!=controls)throw new AssertionError("hide_armor did not override attachables/Java fallback: "+id);
                var publicContext=NativeEntityRenderer.publicContext(actor).orElseThrow();
                if(Molang.number(publicContext.variable("public_probe"))!=8||Molang.number(publicContext.variable("secret_probe"))!=0)
                    throw new AssertionError("Legacy controller/public visibility did not execute");
                var lead=NativeParticles.locatorPosition(actor,"legacy_lead").orElseThrow();
                if(Math.abs(lead.y-(actor.getY()+2))>.001)throw new AssertionError("Legacy model-space locator offset changed: "+lead);
                NativeParticles.observe(actor,(q,a)->0d,Map.of(),Map.of(),0,new dev.matanyah.bedrockcompat.bedrock.entity.BedrockAnimation.Vector(1,1,1),0);
                if(NativeParticles.locatorPosition(actor,"legacy_lead").isPresent())throw new AssertionError("Old geometry locator survived refresh");
                var egg=new net.minecraft.client.renderer.item.ItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(egg,new net.minecraft.world.item.ItemStack(controls?net.minecraft.world.item.Items.HUSK_SPAWN_EGG:net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG),
                    net.minecraft.world.item.ItemDisplayContext.GUI,mc.level,mc.player,0);
                if(egg.isEmpty())throw new AssertionError("Authored egg did not load into Java item renderer");
            }
            if(found!=2)throw new AssertionError("Missing client entity fixtures: "+found);
        });
        context.takeScreenshot("client-entity-equipment-controls");
        command.accept("kill @e[type=minecraft:zombie]");command.accept("kill @e[type=minecraft:husk]");
        context.waitTicks(3);
        context.runOnClient(mc->{
            for(var actor:observed)if(actor.isRemoved()&&NativeEntityRenderer.publicContext(actor).isPresent())
                throw new AssertionError("Removed actor exposed stale public state");
        });
    }
}
