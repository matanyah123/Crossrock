package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAttachableTest {
    @Test void zeroPivotPropsStayAtTheHandWithNoGlobalOriginOffset() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"item","pivot":[0,0,0],"binding":"'hand'",
              "cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        var binding=BedrockAttachableBindings.resolve(geometry,new Molang.Context((n,a)->0d),
            Map.of("hand",new Matrix4f().translate(-6,15,1)),List.of());
        for(var quad:BedrockGeometryMesh.bake(geometry,Map.of(),List.of(),binding))for(var vertex:quad.vertices()) {
            assertTrue(vertex.y()>=15&&vertex.y()<=16);
        }
    }
    @Test void explicitBindingsUseAuthoredPivotForCubesAndTextureMeshes() {
        var holder=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[
             {"name":"arm","pivot":[-5,23,0],"rotation":[0,0,30]},
             {"name":"hand","parent":"arm","pivot":[-6,16,1]}]}
            """).getAsJsonObject());
        var item=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[
             {"name":"item","pivot":[0,11.75,0],"binding":"'hand'",
              "cubes":[{"origin":[0,11.75,0],"size":[2,2,2],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        var context=new Molang.Context((name,args)->0d);
        var anchors=BedrockGeometryMesh.anchors(holder,Map.of());
        var bindings=BedrockAttachableBindings.resolve(item,context,anchors,List.of());
        var actual=BedrockGeometryMesh.bake(item,Map.of(),List.of(),bindings);
        var textureBindings=BedrockAttachableBindings.textureBindings(item,bindings);
        var textureOrigin=new org.joml.Vector3f(0,11.75f,0);
        textureBindings.get("item").translate(0,-11.75f,0).transformPosition(textureOrigin);
        var expectedOrigin=anchors.get("hand").transformPosition(new org.joml.Vector3f());
        assertEquals(expectedOrigin.y,textureOrigin.y,1e-5);
        var deformation=new Matrix4f(anchors.get("hand")).translate(0,-11.75f,0);
        var expected=BedrockGeometryMesh.bake(item,Map.of(),List.of());
        for(int q=0;q<actual.size();q++)for(int v=0;v<4;v++) {
            var vertex=expected.get(q).vertices().get(v);
            var point=deformation.transformPosition(new org.joml.Vector3f(vertex.x(),vertex.y(),vertex.z()));
            var transformed=actual.get(q).vertices().get(v);
            assertEquals(point.x,transformed.x(),1e-5);assertEquals(point.y,transformed.y(),1e-5);
            assertEquals(point.z,transformed.z(),1e-5);
        }
    }
    @Test void unmatchedBonesUseTheirOwnHierarchyAndCopiedBonesCanCoexistWithBindings() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[
             {"name":"root"},{"name":"body","parent":"root"},
             {"name":"prop","parent":"root","binding":"'hand'"}]}
            """).getAsJsonObject());
        var bindings=BedrockAttachableBindings.resolve(geometry,(name,args)->0d,Map.of(),
            Map.of("body",new Matrix4f().translate(1,2,3),"hand",new Matrix4f().translate(4,5,6)),List.of());
        assertEquals(Set.of("body","prop"),bindings.keySet());
        var unbound=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[{"name":"root"}]}
            """).getAsJsonObject());
        assertTrue(BedrockAttachableBindings.resolve(unbound,(name,args)->0d,Map.of(),Map.of(),List.of()).isEmpty());
    }
    @Test void editionItemAliasesAndArmorTagsAreAvailableWithoutPackSpecificRules() {
        var definition=BedrockAttachable.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:glow_frame"}}
            """).getAsJsonObject());
        assertTrue(definition.matches("minecraft:glow_item_frame",(name,args)->0d,Map.of()));
        var equipment=new HashMap<String,EntityFrameQueries.Item>();
        equipment.put("slot.weapon.mainhand",new EntityFrameQueries.Item("minecraft:glow_item_frame",Set.of()));
        equipment.put("slot.weapon.offhand",new EntityFrameQueries.Item("minecraft:golden_helmet",Set.of("example:tag")));
        var queries=new EntityFrameQueries(new EntityQueryContext("","",Set.of(),Map.of()),equipment,0,0,"fancy");
        assertEquals("glow_frame",queries.get("get_equipped_item_name",List.of()));
        assertEquals(1d,queries.get("is_item_name_any",List.of("slot.weapon.mainhand","minecraft:glow_frame")));
        assertEquals(1d,queries.get("equipped_item_all_tags",List.of("slot.weapon.offhand","minecraft:is_armor","minecraft:gold_tier","example:tag")));
        assertEquals(1d,queries.get("equipped_item_all_tags",List.of("slot.weapon.offhand","minecraft:golden_tier")));
        assertFalse(BedrockItemIds.tags("example:diamond_helmet",Set.of()).contains("minecraft:is_armor"));
    }
    @Test void setupUsesHolderScopeAndOwnerAccessIsScopedAndDoesNotRepeatOnSameFrame() {
        var holder=new BedrockEntityInstance(BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"example:holder","scripts":{"pre_animation":["v.order = 1;"]}}}
            """).getAsJsonObject()));
        var child=new BedrockEntityInstance(BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"example:child","scripts":{"pre_animation":["v.result = c.owning_entity->v.shared;"]}}}
            """).getAsJsonObject()));
        Molang.Queries queries=(name,args)->0d;
        java.util.function.Consumer<Molang.Context> setup=context->{
            assertEquals(1d,context.variable("order"));
            context.withAdditionalBindings(Map.of("context.item_slot","main_hand"),()->
                Molang.compile("c.item_slot == 'main_hand' ? {v.shared = (v.shared ?? 0) + 1;};").evaluate(context));
        };
        holder.evaluateFrame(queries,Map.of(),Map.of(),Map.of(),OptionalInt.of(4),0,Map.of(),Map.of(),Map.of(),setup);
        holder.evaluateFrame(queries,Map.of(),Map.of(),Map.of(),OptionalInt.of(4),0,Map.of(),Map.of(),Map.of(),setup);
        holder.withContext(queries,owner->child.withOwner(owner,()->child.evaluateFrame(queries,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),0)));
        child.withContext(queries,context->{assertEquals(1d,context.variable("result"));return null;});
        child.evaluateFrame(queries,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),1);
        child.withContext(queries,context->{assertEquals(0d,context.variable("result"));return null;});
        holder.withContext(queries,context->{assertEquals(4d,context.variable("armor_stand.pose_index"));return null;});
    }
    @Test void copiedBonesFollowWorldAnchorsWithoutDoublingRestRotationOrDrawingHolderCubes() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[
             {"name":"body","pivot":[0,20,0],"rotation":[0,0,90]},
             {"name":"rightItem","parent":"body","pivot":[0,20,0]},
             {"name":"prop","parent":"rightItem","cubes":[{"origin":[0,20,0],"size":[1,1,1],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        var holder=Map.of("body",new Matrix4f().translate(5,10,0).rotateZ(-1.5707964f),
            "rightItem",new Matrix4f().translate(8,14,0));
        var bindings=BedrockAttachableBindings.resolve(geometry,(name,args)->0d,Map.of(),holder,List.of());
        var mesh=BedrockGeometryMesh.bake(geometry,Map.of(),List.of(),bindings);
        assertEquals(6,mesh.size());
        for(var quad:mesh)for(var vertex:quad.vertices()) {
            assertEquals("prop",quad.bone());
            assertTrue(vertex.x()>=8&&vertex.x()<=9);
            assertTrue(vertex.y()>=14&&vertex.y()<=15);
        }
    }
    @Test void itemOverridesIdentifierAndOwnerConditionIsNotPackSpecific() {
        var definition=BedrockAttachable.parse(JsonParser.parseString("""
            {"description":{"identifier":"example:prop","item":{"minecraft:stick":"q.is_owner_identifier_any('minecraft:armor_stand') && c.item_slot == 'main_hand'"}}}
            """).getAsJsonObject());
        var queries=new EntityQueryContext("","",Set.of(),Map.of("owner_identifier","minecraft:armor_stand"));
        assertTrue(definition.matches("minecraft:stick",queries,Map.of("context.item_slot","main_hand")));
        assertFalse(definition.matches("minecraft:stick",queries,Map.of("context.item_slot","off_hand")));
        assertFalse(definition.matches("example:prop",queries,Map.of("context.item_slot","main_hand")));
    }
    @Test void boundRootAndChildrenFollowHolderAnchorWithoutDoublePivotAndHiddenBranchesAreFinite() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[
             {"name":"prop","pivot":[0,20,0],"binding":"q.item_slot_to_bone_name(c.item_slot)"},
             {"name":"tip","parent":"prop","cubes":[{"origin":[0,20,0],"size":[1,1,1],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        Molang.Queries queries=(name,args)->{assertEquals("item_slot_to_bone_name",name);assertEquals(List.of("main_hand"),args);return "rightItem";};
        var anchors=Map.of("rightItem",new Matrix4f().translate(-6,15,1));
        var values=Map.<String,Object>of("context.item_slot","main_hand");
        var bindings=BedrockAttachableBindings.resolve(geometry,queries,values,anchors,List.of());
        var mesh=BedrockGeometryMesh.bake(geometry,Map.of(),List.of(),bindings);
        assertEquals(6,mesh.size());
        for(var quad:mesh)for(var vertex:quad.vertices()) {
            assertTrue(vertex.x()>=-6&&vertex.x()<=-5);
            assertTrue(vertex.y()>=15&&vertex.y()<=16);
            assertTrue(vertex.z()>=1&&vertex.z()<=2);
        }
        assertEquals(15,anchors.get("rightItem").m31(),"Binding resolution must not mutate the holder anchor");
        var variableGeometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"texture_width":16,"texture_height":16,"bones":[{"name":"prop","binding":"v.selected_bone"}]}
            """).getAsJsonObject());
        var runtimeContext=new Molang.Context(queries);runtimeContext.setVariable("selected_bone","rightItem");
        assertTrue(BedrockAttachableBindings.resolve(variableGeometry,runtimeContext,anchors,List.of()).containsKey("prop"));
        var hidden=BedrockAttachableBindings.resolve(geometry,queries,values,anchors,
            List.of(new BedrockRenderController.Visibility("rightItem",false)));
        assertEquals(6,BedrockGeometryMesh.bake(geometry,Map.of(),List.of(),hidden).size(),
            "Hiding holder mesh must not hide independent equipment");
        var collapsed=BedrockAttachableBindings.resolve(geometry,queries,values,
            Map.of("rightItem",new Matrix4f().scale(0)),List.of());
        assertTrue(BedrockGeometryMesh.bake(geometry,Map.of(),List.of(),collapsed).isEmpty());
        assertThrows(IllegalArgumentException.class,()->BedrockAttachableBindings.resolve(geometry,queries,values,Map.of(),List.of()));
    }
}
