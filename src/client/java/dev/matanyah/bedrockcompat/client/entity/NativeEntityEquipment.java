package dev.matanyah.bedrockcompat.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryMesh;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometry;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockRenderController;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.*;

/** Java equipment assets attached to native animated bones, without enabling displaced Java layers. */
public final class NativeEntityEquipment {
    public record Piece(Matrix4fc transform, Model<Void> model, ItemStack stack,
                        ResourceKey<EquipmentAsset> asset, EquipmentClientInfo.LayerType layer) {}
    private static final Map<EquipmentSlot,Map<String,Model<Void>>> MODELS = models();
    private NativeEntityEquipment() {}

    public static List<Piece> extract(HumanoidRenderState state,Map<String,Matrix4f> anchors,
                                     List<BedrockRenderController.Visibility> visibility) {
        return extract(state,anchors,visibility,Set.of());
    }
    public static List<Piece> extract(HumanoidRenderState state,Map<String,Matrix4f> anchors,
                                     List<BedrockRenderController.Visibility> visibility,Set<EquipmentSlot> nativeSlots) {
        var pieces=new ArrayList<Piece>();
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            if(nativeSlots.contains(slot))continue;
            ItemStack stack=switch(slot) {
                case HEAD->state.headEquipment;case CHEST->state.chestEquipment;
                case LEGS->state.legsEquipment;case FEET->state.feetEquipment;default->ItemStack.EMPTY;
            };
            if(stack==null||!HumanoidArmorLayer.shouldRender(stack,slot))continue;
            var asset=stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow();
            for(var entry:MODELS.get(slot).entrySet()) {
                String bone=boneName(anchors,entry.getKey());
                if(bone==null) {
                    NativeEntityResources.runtimeError("Equipment "+slot+": missing native bone "+entry.getKey());
                    continue;
                }
                boolean visible=true;
                for(var rule:visibility)if(BedrockGeometryMesh.matches(rule.bonePattern(),bone))visible=rule.visible();
                if(!visible)continue;
                var matrix=dev.matanyah.bedrockcompat.bedrock.entity.BedrockModelSpace.rendererTransform()
                    .mul(anchors.get(bone)).scale(16,-16,16);
                pieces.add(new Piece(matrix,entry.getValue(),stack.copy(),asset,
                    slot==EquipmentSlot.LEGS?EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS:EquipmentClientInfo.LayerType.HUMANOID));
            }
        }
        return List.copyOf(pieces);
    }

    /** Accept spelling variants of semantic bone names, never pack-specific/obfuscated guesses. */
    public static String boneName(Map<String,?> anchors,String semantic) {
        String wanted=semantic.replace("_","").toLowerCase(Locale.ROOT);
        if(anchors.containsKey(semantic))return semantic;
        for(String name:anchors.keySet())if(name.replace("_","").toLowerCase(Locale.ROOT).equals(wanted))return name;
        return null;
    }

    public static Matrix4f handAnchor(Map<String,Matrix4f> anchors,boolean right,BedrockGeometry geometry,
                                      List<BedrockRenderController.Visibility> visibility) {
        String item=boneName(anchors,right?"rightitem":"leftitem");
        String arm=boneName(anchors,right?"rightarm":"leftarm");
        String selected=item==null?arm:item;
        if(selected==null)return null;
        String owner=selected;
        for(var bone:geometry.bones())if(bone.locators().containsKey(selected)){owner=bone.name();break;}
        boolean visible=true;
        for(var rule:visibility)if(BedrockGeometryMesh.matches(rule.bonePattern(),owner))visible=rule.visible();
        if(!visible)return null;
        return item!=null?new Matrix4f(anchors.get(item)):new Matrix4f(anchors.get(arm)).translate(0,-10,0);
    }

    public static void submit(List<Piece> pieces,EquipmentLayerRenderer renderer,PoseStack poses,
                              SubmitNodeCollector collector,int light,int outline) {
        if(renderer==null)return;
        for(var piece:pieces) {
            poses.pushPose();
            try {
                poses.mulPose(piece.transform);
                renderer.renderLayers(piece.layer,piece.asset,piece.model,null,piece.stack,poses,collector,light,outline);
            } finally {poses.popPose();}
        }
    }

    private static Map<EquipmentSlot,Map<String,Model<Void>>> models() {
        var meshes=HumanoidModel.createArmorMeshSet(new CubeDeformation(.5f),new CubeDeformation(1));
        Map<EquipmentSlot,Map<String,Model<Void>>> result=new EnumMap<>(EquipmentSlot.class);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            var root=LayerDefinition.create(meshes.get(slot),64,32).bakeRoot();
            List<String> names=switch(slot) {
                case HEAD->List.of("head");case CHEST->List.of("body","right_arm","left_arm");
                case LEGS->List.of("body","right_leg","left_leg");case FEET->List.of("right_leg","left_leg");
                default->List.of();
            };
            Map<String,Model<Void>> parts=new LinkedHashMap<>();
            for(String name:names) {
                var part=root.getChild(name);
                part.loadPose(PartPose.ZERO);
                part.setInitialPose(PartPose.ZERO);
                parts.put(name,new Model<Void>(part,RenderTypes::armorCutoutNoCull) {
                    @Override public void setupAnim(Void ignored) { /* Native anchor owns the complete transform. */ }
                });
            }
            result.put(slot,Collections.unmodifiableMap(parts));
        }
        return Collections.unmodifiableMap(result);
    }
}
