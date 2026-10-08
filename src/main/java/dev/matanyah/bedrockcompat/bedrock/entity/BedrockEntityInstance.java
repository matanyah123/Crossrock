package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** One entity and one reload generation: variables can never leak between entities or reloads. */
public final class BedrockEntityInstance {
    private final BedrockClientEntity definition;
    private final Molang.Context context;
    private final java.util.function.Consumer<String> diagnostics;
    private Molang.Queries queries;
    private boolean initialized;
    private double lastTime=Double.NaN;
    private Frame lastFrame;
    private final List<BedrockEntityEffect> pendingEffects=new ArrayList<>();
    private Map<String,BedrockAnimation.Pose> basePose=Map.of(),previousPose=Map.of();
    private Map<String,BedrockGeometry.Bone> bones=Map.of();
    private Map<String,org.joml.Matrix4f> previousAnchors=Map.of();
    private final Map<String,AnimationNode> animationStates = new HashMap<>();
    public record Frame(List<BedrockRenderController.Selection> selections, Map<String,BedrockAnimation.Pose> poses,
                        List<BedrockEntityEffect> effects,BedrockAnimation.Vector scale,double heldItemScale) {}

    public BedrockEntityInstance(BedrockClientEntity definition) {
        this(definition,0);
    }
    public BedrockEntityInstance(BedrockClientEntity definition,long seed) {
        this(definition,seed,message->{throw new IllegalArgumentException(message);});
    }
    public BedrockEntityInstance(BedrockClientEntity definition,long seed,java.util.function.Consumer<String> diagnostics) {
        this.definition = definition;
        this.diagnostics=diagnostics;
        this.context = new Molang.Context((name,args) -> boneQuery(name,args),seed);
    }

    public List<BedrockRenderController.Selection> evaluate(Molang.Queries frameQueries,
            Map<String,BedrockRenderController> controllers, OptionalInt armorStandPose) {
        return evaluateFrame(frameQueries,controllers,Map.of(),Map.of(),armorStandPose,
            Double.isNaN(lastTime)?0:lastTime+.05).selections();
    }
    public Frame evaluateFrame(Molang.Queries frameQueries, Map<String,BedrockRenderController> controllers,
            Map<String,BedrockAnimation> clips,Map<String,BedrockAnimationController> animationControllers,
            OptionalInt armorStandPose,double elapsedSeconds) {
        return evaluateFrame(frameQueries,controllers,clips,animationControllers,armorStandPose,elapsedSeconds,Map.of());
    }
    public Frame evaluateFrame(Molang.Queries frameQueries, Map<String,BedrockRenderController> controllers,
            Map<String,BedrockAnimation> clips,Map<String,BedrockAnimationController> animationControllers,
            OptionalInt armorStandPose,double elapsedSeconds,Map<String,BedrockGeometry> geometries) {
        return evaluateFrame(frameQueries,controllers,clips,animationControllers,armorStandPose,elapsedSeconds,geometries,Map.of(),Map.of());
    }
    /** Engine bindings are frame inputs, not pack variables: refresh after initialize on every view. */
    public Frame evaluateFrame(Molang.Queries frameQueries, Map<String,BedrockRenderController> controllers,
            Map<String,BedrockAnimation> clips,Map<String,BedrockAnimationController> animationControllers,
            OptionalInt armorStandPose,double elapsedSeconds,Map<String,BedrockGeometry> geometries,
            Map<String,Object> engineVariables,Map<String,Object> runtimeAliases) {
        // Input providers are transient overlays. Removing one must not leave its last
        // morph/emote value in the entity's persistent pack-script variable dictionary.
        return context.withVariables(engineVariables,()->evaluateFrameBound(frameQueries,controllers,clips,
            animationControllers,armorStandPose,elapsedSeconds,geometries,engineVariables,runtimeAliases));
    }
    private Frame evaluateFrameBound(Molang.Queries frameQueries,Map<String,BedrockRenderController> controllers,
            Map<String,BedrockAnimation> clips,Map<String,BedrockAnimationController> animationControllers,
            OptionalInt armorStandPose,double elapsedSeconds,Map<String,BedrockGeometry> geometries,
            Map<String,Object> engineVariables,Map<String,Object> runtimeAliases) {
        // Minecraft may extract the same entity again with an older partial tick (outline/UI/test passes).
        // Never rewind clocks, repeat pre-animation side effects, or dispatch an entry effect twice.
        if(lastFrame!=null&&elapsedSeconds<=lastTime)return new Frame(lastFrame.selections,lastFrame.poses,List.of(),lastFrame.scale,lastFrame.heldItemScale);
        pendingEffects.clear();
        Map<String,Object> aliases=new LinkedHashMap<>(definition.aliases());aliases.putAll(runtimeAliases);
        Object defaultGeometry=aliases.get("geometry."+definition.queryableGeometry());
        var geometry=defaultGeometry instanceof String identifier?geometries.get(identifier):null;
        if(geometry!=null) {
            Map<String,BedrockAnimation.Pose> defaults=new HashMap<>();Map<String,BedrockGeometry.Bone> byName=new HashMap<>();
            for(var bone:geometry.bones()) {
                var rotation=bone.rotation();byName.put(bone.name(),bone);
                defaults.put(bone.name(),new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
                    new BedrockAnimation.Vector(rotation.x(),rotation.y(),rotation.z()),new BedrockAnimation.Vector(1,1,1)));
            }
            basePose=Map.copyOf(defaults);bones=Map.copyOf(byName);
            if(previousAnchors.isEmpty())previousAnchors=BedrockGeometryMesh.anchors(geometry,Map.of());
        }
        queries = Objects.requireNonNull(frameQueries);
        context.beginFrame();
        context.bind(aliases);
        engineVariables.forEach(context::setVariable);
        if (!initialized) {
            for (Molang.Expression expression : definition.initialize()) expression.evaluate(context);
            initialized = true;
        }
        engineVariables.forEach(context::setVariable);
        // Pack initialize scripts may set a default; the synchronized pose is authoritative thereafter.
        if (armorStandPose.isPresent()) context.setVariable("armor_stand.pose_index", (double)armorStandPose.getAsInt());
        for (Molang.Expression expression : definition.preAnimation()) expression.evaluate(context);
        Map<String,BedrockAnimation.Pose> poses = new LinkedHashMap<>();
        for (var animation : definition.animate()) {
            double weight = Molang.number(animation.weight().evaluate(context));
            if (!Double.isFinite(weight)) throw new IllegalArgumentException("Non-finite animation blend weight");
            if(weight==0&&!clips.containsKey(animationId(animation.alias()))&&!animationControllers.containsKey(animationId(animation.alias())))continue;
            String id = animationId(animation.alias());
            if(!clips.containsKey(id)&&!animationControllers.containsKey(id)) {
                diagnostics.accept("Missing animation definition: "+id+" (other animations remain active)");continue;
            }
            var node = animationStates.computeIfAbsent(animation.alias(),key->new AnimationNode(id,clips,animationControllers));
            var sampled = node.sample(elapsedSeconds,clips,animationControllers,0,compose(basePose,poses),weight!=0);
            if(weight==0)continue;
            sampled.replacedBones().forEach(poses::remove);
            BedrockAnimationController.accumulate(poses,sampled.poses(),weight);
        }
        List<BedrockRenderController.Selection> selections = new ArrayList<>();
        for (BedrockClientEntity.Controller entry : definition.controllers()) {
            context.bind(aliases);
            if (!Molang.truth(entry.condition().evaluate(context))) continue;
            BedrockRenderController controller = controllers.get(entry.identifier());
            if (controller == null) {diagnostics.accept("Missing render controller: " + entry.identifier()+" (other controllers remain active)");continue;}
            selections.add(controller.select(context, aliases));
        }
        lastTime=elapsedSeconds;
        var scale=new BedrockAnimation.Vector(Molang.number(definition.scaleX().evaluate(context)),
            Molang.number(definition.scaleY().evaluate(context)),Molang.number(definition.scaleZ().evaluate(context)));
        if(!Double.isFinite(scale.x())||!Double.isFinite(scale.y())||!Double.isFinite(scale.z()))throw new IllegalArgumentException("Non-finite entity scale");
        double heldItemScale=Molang.number(definition.heldItemScale().evaluate(context));
        if(!Double.isFinite(heldItemScale))throw new IllegalArgumentException("Non-finite held-item scale");
        lastFrame=new Frame(List.copyOf(selections),Map.copyOf(poses),List.copyOf(pendingEffects),scale,heldItemScale);
        previousPose=compose(basePose,poses);
        if(geometry!=null)previousAnchors=BedrockGeometryMesh.anchors(geometry,poses);
        return lastFrame;
    }
    private Object boneQuery(String name,List<Object> args) {
        // Clips/timeline actions provide their own scoped clock through Context.withQueries.
        // Outside that scope Bedrock specifies zero, not entity age or a previous clip's time.
        if(name.equals("anim_time"))return 0d;
        if(name.equals("get_root_locator_offset")) {
            if(args.size()!=2)throw new IllegalArgumentException("get_root_locator_offset requires locator and axis");
            int axis=(int)Molang.number(args.get(1));
            if(axis<0||axis>2)throw new IllegalArgumentException("Locator axis must be 0, 1 or 2");
            var anchor=previousAnchors.get(args.getFirst().toString());
            if(anchor==null)return 0d;
            return (double)(axis==0?anchor.m30():axis==1?anchor.m31():anchor.m32());
        }
        if((name.equals("bone_orientation_trs")||name.equals("bone_origin")||name.equals("bone_rotation"))&&args.size()==1) {
            String bone=args.getFirst().toString();
            if(name.equals("bone_origin")||name.equals("bone_rotation")) {
                var value=bones.get(bone);var p=value==null?BedrockGeometry.Vec.ZERO:
                    name.equals("bone_origin")?value.pivot():value.rotation();
                return Map.of("x",p.x(),"y",p.y(),"z",p.z());
            }
            // TRS is the bone orientation in entity/model space, not just this
            // bone's animation delta. Its translation includes the inherited pivot.
            var matrix=previousAnchors.getOrDefault(bone,new org.joml.Matrix4f());
            var t=matrix.getTranslation(new org.joml.Vector3f());var s=matrix.getScale(new org.joml.Vector3f());
            var r=matrix.getUnnormalizedRotation(new org.joml.Quaternionf()).getEulerAnglesZYX(new org.joml.Vector3f());
            return Map.of("t",vector(new BedrockAnimation.Vector(t.x,t.y,t.z)),
                "r",vector(new BedrockAnimation.Vector(-Math.toDegrees(r.x),Math.toDegrees(r.y),-Math.toDegrees(r.z))),
                "s",vector(new BedrockAnimation.Vector(s.x,s.y,s.z)));
        }
        return queries.get(name,args);
    }
    private static Map<String,Double> vector(BedrockAnimation.Vector v){return Map.of("x",v.x(),"y",v.y(),"z",v.z());}
    private static Map<String,BedrockAnimation.Pose> compose(Map<String,BedrockAnimation.Pose> base,Map<String,BedrockAnimation.Pose> added) {
        Map<String,BedrockAnimation.Pose> result=new HashMap<>(base);BedrockAnimationController.accumulate(result,added,1);return Map.copyOf(result);
    }
    private String animationId(String alias) {
        String id=definition.animationAliases().get(alias);
        if(id==null)throw new IllegalArgumentException("Missing entity animation alias: "+alias);
        return id;
    }
    private final class AnimationNode {
        private final String id;
        private final BedrockAnimationController.Runtime controller;
        private final BedrockAnimation.Runtime clipRuntime;
        private final Map<String,AnimationNode> children=new HashMap<>();
        private long childEpoch=-1;
        AnimationNode(String id,Map<String,BedrockAnimation> clips,Map<String,BedrockAnimationController> controllers) {
            this.id=id;
            controller=controllers.containsKey(id)?controllers.get(id).createRuntime():null;
            clipRuntime=clips.containsKey(id)?clips.get(id).createRuntime():null;
            if(controller==null&&!clips.containsKey(id))throw new IllegalArgumentException("Missing animation definition: "+id);
        }
        BedrockAnimationController.Sample sample(double time,Map<String,BedrockAnimation> clips,
                Map<String,BedrockAnimationController> controllers,int depth,Map<String,BedrockAnimation.Pose> currentPose,boolean effectsEnabled) {
            if(depth>=64)throw new IllegalArgumentException("Cyclic or excessively nested animation controllers: "+id);
            if(controller==null) {
                BedrockAnimation clip=clips.get(id);
                return clipRuntime.sample(time,context,clip.overridesPrevious()?basePose:currentPose,
                    effect->{if(effectsEnabled)effect(effect);});
            }
            return controller.sampleContextual(time,context,(alias,elapsed,ctx,preceding,weight)-> {
                // Every state entry owns fresh child clocks, including re-entry into the same state.
                if(childEpoch!=controller.stateEpoch()){children.clear();childEpoch=controller.stateEpoch();}
                String childId=animationId(alias);
                if(!clips.containsKey(childId)&&!controllers.containsKey(childId)) {
                    diagnostics.accept("Missing animation definition: "+childId+" (other animations remain active)");
                    return new BedrockAnimationController.Sample(Map.of(),false);
                }
                var child=children.computeIfAbsent(alias,key->new AnimationNode(animationId(alias),clips,controllers));
                return child.sample(elapsed,clips,controllers,depth+1,compose(currentPose,preceding),effectsEnabled&&weight!=0);
            },effect->{if(effectsEnabled)effect(effect);});
        }
    }
    private void effect(BedrockEntityEffect effect) {
        String prefix=effect.kind()==BedrockEntityEffect.Kind.SOUND?"sound.":"particle.";
        Object resolved=definition.aliases().get(prefix+effect.effect().toLowerCase(Locale.ROOT));
        if(!(resolved instanceof String name)){diagnostics.accept("Missing entity effect alias: "+prefix+effect.effect());return;}
        pendingEffects.add(new BedrockEntityEffect(effect.kind(),name,effect.locator()));
    }
}
