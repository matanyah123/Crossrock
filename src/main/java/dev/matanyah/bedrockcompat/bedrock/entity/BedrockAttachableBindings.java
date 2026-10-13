package dev.matanyah.bedrockcompat.bedrock.entity;

import org.joml.Matrix4f;
import java.util.*;

/** Resolve authored binding expressions without renaming, flattening or copying the holder mesh. */
public final class BedrockAttachableBindings {
    private BedrockAttachableBindings() {}
    /** Voxelized texture meshes are already expressed in the authored bone frame. */
    public static Map<String,Matrix4f> textureBindings(BedrockGeometry geometry,Map<String,Matrix4f> bindings) {
        Map<String,Matrix4f> result=new LinkedHashMap<>();
        for(var bone:geometry.bones())if(bindings.containsKey(bone.name())) {
            var matrix=new Matrix4f(bindings.get(bone.name()));
            result.put(bone.name(),matrix);
        }
        return Map.copyOf(result);
    }
    public static Map<String,Matrix4f> resolve(BedrockGeometry geometry,Molang.Queries queries,
            Map<String,Object> contextValues,Map<String,Matrix4f> holderAnchors,
            List<BedrockRenderController.Visibility> holderVisibility) {
        var context=new Molang.Context(queries);context.bind(contextValues);
        return resolve(geometry,context,holderAnchors,holderVisibility);
    }
    /** Binding expressions may read variables computed by this attachable's pre-animation. */
    public static Map<String,Matrix4f> resolve(BedrockGeometry geometry,Molang.Context context,
            Map<String,Matrix4f> holderAnchors,List<BedrockRenderController.Visibility> holderVisibility) {
        Map<String,Matrix4f> result=new LinkedHashMap<>();
        for(var bone:geometry.bones())if(bone.binding()!=null||holderAnchors.containsKey(bone.name())) {
            String name=bone.binding()!=null?String.valueOf(bone.binding().evaluate(context)):bone.name();
            var anchor=holderAnchors.get(name);
            if(anchor==null)throw new IllegalArgumentException("Missing attachable binding bone: "+name);
            // Holder part_visibility hides its mesh, not independently rendered
            // equipment. Prop modes deliberately hide the stand while keeping items.
            // Animated zero-scale anchors still collapse attached geometry naturally.
            var binding=new Matrix4f(anchor);
            // A binding replaces the bone's parent frame at its authored pivot.
            // There is no universal Y=24 item origin: cubes and texture meshes
            // both follow the same pivot, including pack-authored animation.
            if(bone.binding()==null) {
                // The copied holder anchor already includes the rest rotation. Cancel just
                // that rest rotation; attachable-authored animation is still applied by bake.
                var r=bone.rotation();
                binding.mul(new Matrix4f().rotateZYX((float)Math.toRadians(-r.z()),
                    (float)Math.toRadians(r.y()),(float)Math.toRadians(-r.x())).invert());
            }
            result.put(bone.name(),binding);
        }
        return Map.copyOf(result);
    }
}
