package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Immutable controller program; each entity owns a separate Runtime and clocks. */
public final class BedrockAnimationController {
    public record Animation(String alias, Molang.Expression weight) {}
    private record Transition(String destination, Molang.Expression condition) {}
    private record Effect(BedrockEntityEffect action,Molang.Expression script) {}
    private record Curve(NavigableMap<Double,Double> points) {
        double sample(double input) {
            if(!Double.isFinite(input))throw new IllegalArgumentException("Non-finite controller curve input");
            var a=points.floorEntry(input);var b=points.ceilingEntry(input);
            if(a==null)return points.firstEntry().getValue();if(b==null)return points.lastEntry().getValue();
            if(a.getKey().equals(b.getKey()))return a.getValue();
            return a.getValue()+(b.getValue()-a.getValue())*(input-a.getKey())/(b.getKey()-a.getKey());
        }
    }
    private record Variable(String name,Molang.Expression input,Curve curve) {}
    private record State(List<Animation> animations, List<Transition> transitions,
                         List<Molang.Expression> entry, List<Molang.Expression> exit, double blendSeconds,Curve blendCurve,
                         List<Variable> variables,List<Effect> effects) {}
    public record Sample(Map<String,BedrockAnimation.Pose> poses, boolean finished,Set<String> replacedBones) {
        public Sample(Map<String,BedrockAnimation.Pose> poses,boolean finished){this(poses,finished,Set.of());}
    }
    @FunctionalInterface public interface Sampler {
        Sample sample(String alias, double elapsedSeconds, Molang.Context context);
    }
    @FunctionalInterface public interface ContextualSampler {
        Sample sample(String alias,double elapsedSeconds,Molang.Context context,Map<String,BedrockAnimation.Pose> preceding,double weight);
    }
    @FunctionalInterface public interface HierarchicalSampler {
        /** Preceding poses are controller-local deltas; replacement bones reset inherited outer transforms. */
        Sample sample(String alias,double elapsedSeconds,Molang.Context context,Sample preceding,double weight);
    }
    private final Map<String,State> states;
    private final String initial;

    public BedrockAnimationController(JsonObject source) {
        initial = source.has("initial_state") ? source.get("initial_state").getAsString() : "default";
        Map<String,State> result = new LinkedHashMap<>();
        for (var entry : source.getAsJsonObject("states").entrySet()) {
            JsonObject state = entry.getValue().getAsJsonObject();
            List<Transition> transitions = new ArrayList<>();
            if (state.has("transitions")) for (JsonElement element : state.getAsJsonArray("transitions"))
                for (var transition : element.getAsJsonObject().entrySet())
                    transitions.add(new Transition(transition.getKey(),Molang.compile(transition.getValue().getAsString())));
            Curve blendCurve=state.has("blend_transition")&&state.get("blend_transition").isJsonObject()
                ?curve(state.getAsJsonObject("blend_transition")):null;
            double blend = blendCurve!=null?blendCurve.points.lastKey():state.has("blend_transition") ? state.get("blend_transition").getAsDouble() : 0;
            if (!Double.isFinite(blend) || blend < 0) throw new IllegalArgumentException("Invalid state blend duration");
            List<Variable> variables=new ArrayList<>();
            if(state.has("variables"))for(var variable:state.getAsJsonObject("variables").entrySet()) {
                var value=variable.getValue();
                String input=value.isJsonObject()?value.getAsJsonObject().get("input").getAsString():value.getAsString();
                Curve remap=value.isJsonObject()&&value.getAsJsonObject().has("remap_curve")?curve(value.getAsJsonObject().getAsJsonObject("remap_curve")):null;
                String name=variable.getKey().toLowerCase(Locale.ROOT).replaceFirst("^(?:variable|v)\\.","");
                variables.add(new Variable(name,Molang.compile(input),remap));
            }
            result.put(entry.getKey(),new State(animations(state.getAsJsonArray("animations")),List.copyOf(transitions),
                scripts(state.getAsJsonArray("on_entry")),scripts(state.getAsJsonArray("on_exit")),blend,blendCurve,List.copyOf(variables),effects(state)));
        }
        if (!result.containsKey(initial)) throw new IllegalArgumentException("Missing initial controller state: "+initial);
        for (State state : result.values()) for (Transition transition : state.transitions)
            if (!result.containsKey(transition.destination)) throw new IllegalArgumentException("Missing destination state: "+transition.destination);
        states = Map.copyOf(result);
    }

    public Runtime createRuntime() { return new Runtime(); }
    public final class Runtime {
        private String current = initial;
        private boolean entered;
        private double enteredAt, lastTime = Double.NaN, blendAt, blendDuration;
        private Map<String,BedrockAnimation.Pose> outgoing = Map.of();
        private Curve outgoingCurve;
        private Map<String,Object> currentVariables=Map.of();
        private Sample last = new Sample(Map.of(),false);
        private long stateEpoch;
        public String state() { return current; }
        public long stateEpoch() { return stateEpoch; }

        /** nowSeconds is an entity playback clock, never a rendered-frame counter. */
        public Sample sample(double nowSeconds, Molang.Context context, Sampler sampler) {
            return sample(nowSeconds,context,sampler,effect->{});
        }
        public Sample sample(double nowSeconds, Molang.Context context, Sampler sampler,Consumer<BedrockEntityEffect> effects) {
            return sampleContextual(nowSeconds,context,(a,t,c,p,w)->sampler.sample(a,t,c),effects);
        }
        public Sample sampleContextual(double nowSeconds,Molang.Context context,ContextualSampler sampler,Consumer<BedrockEntityEffect> effects) {
            return sampleHierarchical(nowSeconds,context,(a,t,c,p,w)->sampler.sample(a,t,c,p.poses,w),effects);
        }
        public Sample sampleHierarchical(double nowSeconds,Molang.Context context,HierarchicalSampler sampler,Consumer<BedrockEntityEffect> effects) {
            if (!Double.isFinite(nowSeconds) || nowSeconds < 0) throw new IllegalArgumentException("Invalid controller time");
            // Render passes and partial ticks can revisit an older observation.
            // Reuse the sample without replaying transitions or poisoning the instance.
            if (!Double.isNaN(lastTime) && nowSeconds <= lastTime) return last;
            if (!entered) {
                entered = true; enteredAt = nowSeconds;
                run(states.get(current).entry,context,0);
                emit(states.get(current),context,effects);
            }
            State state = states.get(current);
            double elapsed = nowSeconds-enteredAt;
            remap(state,elapsed,context);
            // Unconditional/input-driven transitions select the frame's state
            // before running its clips. Ping-pong physics controllers must not
            // integrate both the outgoing and incoming states in one frame.
            String next=transition(state,elapsed,last.finished,context);
            Sample sampled=last;
            Map<String,BedrockAnimation.Pose> displayed=last.poses;
            if(next==null) {
                sampled=evaluate(state,elapsed,context,sampler);
                displayed=blend(sampled.poses,nowSeconds);
                // Completion may become true only after advancing a clip.
                next=transition(state,elapsed,sampled.finished,context);
            }
            // Bedrock evaluates ordered transitions once per frame, not an unbounded transition loop.
            if (next != null) {
                for(var effect:state.effects)if(effect.action.kind()==BedrockEntityEffect.Kind.PARTICLE) {
                    var action=effect.action;
                    effects.accept(new BedrockEntityEffect(BedrockEntityEffect.Kind.PARTICLE_STOP,
                        action.effect(),action.locator(),action.preEffectScript(),action.bindToActor(),true));
                }
                context.withVariables(currentVariables,()->{run(state.exit,context,elapsed);return null;});
                current = next; enteredAt = nowSeconds;stateEpoch++;
                outgoing = displayed; blendAt = nowSeconds; blendDuration = state.blendSeconds;outgoingCurve=state.blendCurve;
                State destination = states.get(next);
                run(destination.entry,context,0);
                emit(destination,context,effects);
                sampled = evaluate(destination,0,context,sampler);
                displayed = blend(sampled.poses,nowSeconds);
            }
            lastTime = nowSeconds;
            last = new Sample(displayed,sampled.finished,sampled.replacedBones);
            return last;
        }
        private String transition(State state,double elapsed,boolean finished,Molang.Context context) {
            var facts=Map.<String,Object>of("state_time",elapsed,"any_animation_finished",anyFinished?1d:0d,
                "all_animations_finished",finished?1d:0d);
            return context.withVariables(currentVariables,()->context.withQueries(facts,()-> {
                for (Transition transition : state.transitions)
                    if (Molang.truth(transition.condition.evaluate(context))) return transition.destination;
                return null;
            }));
        }
        private boolean anyFinished;
        private void remap(State state,double elapsed,Molang.Context context) {
            context.withQueries(Map.of("state_time",elapsed),()-> {
                Map<String,Object> remapped=new LinkedHashMap<>();
                for(var variable:state.variables) {
                    double input=Molang.number(variable.input.evaluate(context));
                    remapped.put(variable.name,variable.curve==null?input:variable.curve.sample(input));
                }
                currentVariables=Map.copyOf(remapped);return null;
            });
        }
        private Sample evaluate(State state,double elapsed,Molang.Context context,HierarchicalSampler sampler) {
            return context.withQueries(Map.of("state_time",elapsed),()-> {
                Map<String,Object> remapped=new LinkedHashMap<>();
                for(var variable:state.variables) {
                    double input=Molang.number(variable.input.evaluate(context));
                    remapped.put(variable.name,variable.curve==null?input:variable.curve.sample(input));
                }
                currentVariables=Map.copyOf(remapped);
                return context.withVariables(remapped,()-> {
                Map<String,BedrockAnimation.Pose> result = new LinkedHashMap<>();
                Set<String> replaced=new HashSet<>();
                boolean all=true; anyFinished=false;
                for (Animation animation : state.animations) {
                    double weight = Molang.number(animation.weight.evaluate(context));
                    if (!Double.isFinite(weight)) throw new IllegalArgumentException("Non-finite animation weight");
                    // Disabled branches must not run physics scripts or mutate shared temporaries.
                    if(weight==0)continue;
                    // Carry replacement metadata with the accumulated deltas. A nested
                    // override also resets the inherited outer pose seen by later clips.
                    Sample clip = sampler.sample(animation.alias,elapsed,context,
                        new Sample(Map.copyOf(result),all,Set.copyOf(replaced)),weight);
                    all &= clip.finished; anyFinished |= clip.finished;
                    clip.replacedBones.forEach(result::remove);replaced.addAll(clip.replacedBones);
                    accumulate(result,clip.poses,weight);
                }
                return new Sample(Map.copyOf(result),all,Set.copyOf(replaced));
                });
            });
        }
        private Map<String,BedrockAnimation.Pose> blend(Map<String,BedrockAnimation.Pose> pose,double now) {
            double fraction = blendDuration == 0 ? 1 : Math.min(1,(now-blendAt)/blendDuration);
            if (fraction >= 1) { outgoing=Map.of(); return pose; }
            Map<String,BedrockAnimation.Pose> result = new LinkedHashMap<>();
            double outgoingWeight=outgoingCurve==null?1-fraction:Math.clamp(outgoingCurve.sample(now-blendAt),0,1);
            accumulate(result,outgoing,outgoingWeight); accumulate(result,pose,1-outgoingWeight);
            return Map.copyOf(result);
        }
    }

    private static Curve curve(JsonObject json) {
        if(json.size()==0||json.size()>4096)throw new IllegalArgumentException("Invalid controller curve size");
        NavigableMap<Double,Double> points=new TreeMap<>();
        json.entrySet().forEach(entry-> {
            double x=Double.parseDouble(entry.getKey()),y=entry.getValue().getAsDouble();
            if(!Double.isFinite(x)||!Double.isFinite(y)||points.put(x,y)!=null)throw new IllegalArgumentException("Invalid controller curve point");
        });
        return new Curve(Collections.unmodifiableNavigableMap(points));
    }

    private static List<Effect> effects(JsonObject state) {
        List<Effect> result=new ArrayList<>();
        for(var kind:List.of(BedrockEntityEffect.Kind.SOUND,BedrockEntityEffect.Kind.PARTICLE)) {
            String key=kind==BedrockEntityEffect.Kind.SOUND?"sound_effects":"particle_effects";
            if(!state.has(key))continue;
            for(var value:state.getAsJsonArray(key)) {
                JsonObject effect=value.getAsJsonObject();
                result.add(new Effect(new BedrockEntityEffect(kind,effect.get("effect").getAsString(),
                    effect.has("locator")?effect.get("locator").getAsString():"",
                    effect.has("pre_effect_script")?effect.get("pre_effect_script").getAsString():"",
                    !effect.has("bind_to_actor")||effect.get("bind_to_actor").getAsBoolean(),true),
                    Molang.compile(effect.has("pre_effect_script")?effect.get("pre_effect_script").getAsString():"0")));
            }
        }
        return List.copyOf(result);
    }
    private static void emit(State state,Molang.Context context,Consumer<BedrockEntityEffect> sink) {
        for(Effect effect:state.effects){if(effect.action.kind()==BedrockEntityEffect.Kind.SOUND)effect.script.evaluate(context);sink.accept(effect.action);}
    }

    public static void accumulate(Map<String,BedrockAnimation.Pose> target,Map<String,BedrockAnimation.Pose> source,double weight) {
        if(weight==0)return;
        source.forEach((bone,pose)-> {
            var existing=target.getOrDefault(bone,IDENTITY);
            target.put(bone,new BedrockAnimation.Pose(add(existing.position(),pose.position(),weight,0),
                add(existing.rotation(),pose.rotation(),weight,0),add(existing.scale(),pose.scale(),weight,1),
                existing.entityRelativeRotation()||(weight!=0&&pose.entityRelativeRotation())));
        });
    }
    private static final BedrockAnimation.Pose IDENTITY = new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
        new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1));
    private static BedrockAnimation.Vector add(BedrockAnimation.Vector a,BedrockAnimation.Vector b,double w,double base) {
        return new BedrockAnimation.Vector(a.x()+(b.x()-base)*w,a.y()+(b.y()-base)*w,a.z()+(b.z()-base)*w);
    }
    private static void run(List<Molang.Expression> scripts,Molang.Context context,double time) {
        context.withQueries(Map.of("state_time",time),()-> { scripts.forEach(e->e.evaluate(context)); return null; });
    }
    public static List<Animation> animations(JsonArray array) {
        if (array==null) return List.of();
        List<Animation> result=new ArrayList<>();
        for(JsonElement e:array) {
            if(e.isJsonPrimitive())result.add(new Animation(e.getAsString(),Molang.compile("1")));
            else for(var entry:e.getAsJsonObject().entrySet())result.add(new Animation(entry.getKey(),Molang.compile(entry.getValue().getAsString())));
        }
        return List.copyOf(result);
    }
    private static List<Molang.Expression> scripts(JsonArray array) {
        if (array==null)return List.of();
        List<Molang.Expression> result=new ArrayList<>();
        for(JsonElement e:array)result.add(Molang.compile(e.getAsString()));
        return List.copyOf(result);
    }
}
