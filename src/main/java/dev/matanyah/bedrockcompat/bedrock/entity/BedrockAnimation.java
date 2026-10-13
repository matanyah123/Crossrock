package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Samples native bone channels in seconds; no conversion to Java animation files. */
public final class BedrockAnimation {
    public record Vector(double x,double y,double z) {
        static Vector lerp(Vector a,Vector b,double t){return new Vector(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
    }
    public record Pose(Vector position,Vector rotation,Vector scale,boolean entityRelativeRotation) {
        public Pose(Vector position,Vector rotation,Vector scale){this(position,rotation,scale,false);}
    }
    private record Value(Molang.Expression x,Molang.Expression y,Molang.Expression z) {
        Vector sample(Molang.Context ctx,Vector current){return new Vector(ctx.withThis(current.x,()->Molang.number(x.evaluate(ctx))),
            ctx.withThis(current.y,()->Molang.number(y.evaluate(ctx))),ctx.withThis(current.z,()->Molang.number(z.evaluate(ctx))));}
    }
    private record Key(double time,Value pre,Value post,boolean smooth) {}
    private record Channel(List<Key> keys) {
        Vector sample(double time,Molang.Context ctx,Vector current) {
            if(time<keys.getFirst().time)return sample(keys.getFirst().pre,ctx,current,0);
            for(int i=1;i<keys.size();i++) {
                Key a=keys.get(i-1),b=keys.get(i);
                if(time<b.time) {
                    double t=(time-a.time)/(b.time-a.time);
                    if (!a.smooth) return Vector.lerp(sample(a.post,ctx,current,t),sample(b.pre,ctx,current,t),t);
                    Vector p0=sample(keys.get(Math.max(0,i-2)).post,ctx,current,t),p1=sample(a.post,ctx,current,t),p2=sample(b.pre,ctx,current,t),p3=sample(keys.get(Math.min(keys.size()-1,i+1)).pre,ctx,current,t);
                    return new Vector(spline(p0.x,p1.x,p2.x,p3.x,t),spline(p0.y,p1.y,p2.y,p3.y,t),spline(p0.z,p1.z,p2.z,p3.z,t));
                }
                if(time==b.time)return sample(b.post,ctx,current,i==keys.size()-1?1:0);
            }
            return sample(keys.getLast().post,ctx,current,keys.size()==1?0:1);
        }
        private static Vector sample(Value value,Molang.Context ctx,Vector current,double ratio) {
            return ctx.withQueries(Map.of("key_frame_lerp_time",ratio),()->value.sample(ctx,current));
        }
    }
    private record Bone(Channel position,Channel rotation,Channel scale,boolean entityRelativeRotation) {}
    private final Map<String,Bone> bones;
    private final double length;
    private final boolean loop,hold;
    private final Molang.Expression timeUpdate,startDelay,loopDelay,blendWeight,loopExpression;
    private final List<Action> actions;
    private final boolean override;
    private record Action(double time,List<Molang.Expression> scripts,List<BedrockEntityEffect> effects) {}
    public double length() { return length; }
    public boolean finished(double seconds) { return !loop && length > 0 && seconds >= length; }
    private static double spline(double a,double b,double c,double d,double t) {
        return .5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
    }
    public BedrockAnimation(JsonObject source) {
        String mode=source.has("loop")?source.get("loop").getAsString():"false";
        loop=mode.equals("true");hold=mode.equals("hold_on_last_frame");
        loopExpression=Molang.compile(hold?"0":mode);
        timeUpdate=source.has("anim_time_update")?Molang.compile(source.get("anim_time_update").getAsString()):null;
        startDelay=expression(source.has("start_delay")?source.get("start_delay"):new JsonPrimitive(0));
        loopDelay=expression(source.has("loop_delay")?source.get("loop_delay"):new JsonPrimitive(0));
        blendWeight=expression(source.has("blend_weight")?source.get("blend_weight"):new JsonPrimitive(1));
        override=source.has("override_previous_animation")&&source.get("override_previous_animation").getAsBoolean();
        actions=actions(source);
        Map<String,Bone> compiled=new LinkedHashMap<>();double max=0;
        if(source.has("bones"))for(var entry:source.getAsJsonObject("bones").entrySet()) {
            JsonObject bone=entry.getValue().getAsJsonObject();
            boolean global=bone.has("relative_to")&&bone.getAsJsonObject("relative_to").has("rotation")
                &&bone.getAsJsonObject("relative_to").get("rotation").getAsString().equals("entity");
            Bone value=new Bone(channel(bone.get("position"),0),channel(bone.get("rotation"),0),channel(bone.get("scale"),1),global);
            compiled.put(entry.getKey(),value);
            max=Math.max(max,Math.max(value.position.keys.getLast().time,Math.max(value.rotation.keys.getLast().time,value.scale.keys.getLast().time)));
        }
        length=source.has("animation_length")?source.get("animation_length").getAsDouble():max;
        if(!Double.isFinite(length)||length<0)throw new IllegalArgumentException("Invalid animation length");
        // Expressions can publish variables consumed by later authored bones.
        bones=Collections.unmodifiableMap(compiled);
    }
    public Runtime createRuntime(){return new Runtime();}
    public boolean overridesPrevious(){return override;}

    /** Playback state belongs to one occurrence of a clip, not to its shared resource definition. */
    public final class Runtime {
        private double lastInput=Double.NaN,time,updateClock,startRemaining,loopRemaining;
        private boolean first=true,initialEvent=true,completed;
        private BedrockAnimationController.Sample last=new BedrockAnimationController.Sample(Map.of(),false);
        public double animationTime(){return time;}
        public BedrockAnimationController.Sample sample(double input,Molang.Context context,Map<String,Pose> current,
                java.util.function.Consumer<BedrockEntityEffect> effects) {
            return sample(input,context,current,effects,Double.NaN);
        }
        public BedrockAnimationController.Sample sample(double input,Molang.Context context,Map<String,Pose> current,
                java.util.function.Consumer<BedrockEntityEffect> effects,double frameDelta) {
            if(!Double.isFinite(input)||input<0)throw new IllegalArgumentException("Invalid animation playback clock");
            if(!Double.isNaN(lastInput)&&input<=lastInput)return last;
            double delta=Double.isNaN(lastInput)?input:input-lastInput;
            if(first){startRemaining=delay(startDelay,context);first=false;}
            lastInput=input;
            if(startRemaining>0) {
                double consume=Math.min(delta,startRemaining);delta-=consume;startRemaining-=consume;
                if(startRemaining>0){last=new BedrockAnimationController.Sample(Map.of(),false);return last;}
            }
            // A controller can enter a fresh clip at local time zero during an
            // existing rendered frame. Physics consumes frame time, not clip age.
            final double step=Double.isNaN(frameDelta)?delta:frameDelta;
            // The driver clock must not wrap with the sampled clip phase. Absolute
            // drivers (life_time, distance_moved) otherwise replay every old loop on
            // every frame, eventually exhausting the loop budget and dropping models.
            double driver=timeUpdate==null?updateClock+delta:context.withQueries(Map.of("anim_time",updateClock,"delta_time",step),()->finite(timeUpdate,context));
            driver=Math.max(0,driver);
            double next=Math.max(0,time+driver-updateClock);
            updateClock=driver;
            boolean looping=context.withQueries(Map.of("anim_time",time),()->Molang.truth(loopExpression.evaluate(context)));
            if(initialEvent){dispatch(-1,0,context,effects);initialEvent=false;}
            if(next<time)time=next;
            else {
                double advance=next-time;
                for(int iterations=0;;iterations++) {
                    // A newly observed absolute clock may already be hours old. Do
                    // not replay an unbounded backlog of effects from before observation.
                    if(iterations>256&&length>0)advance%=length;
                    if(loopRemaining>0) {
                        double consume=Math.min(advance,loopRemaining);loopRemaining-=consume;advance-=consume;
                        if(loopRemaining>0)break;
                        dispatch(-1,0,context,effects);
                    }
                    double destination=time+advance;
                    if(!looping||length==0||destination<length) {
                        dispatch(time,length>0?Math.min(destination,length):destination,context,effects);
                        time=destination;completed|=length>0&&time>=length;break;
                    }
                    dispatch(time,length,context,effects);completed=true;
                    advance=destination-length;time=0;loopRemaining=delay(loopDelay,context);
                    if(loopRemaining==0)dispatch(-1,0,context,effects);
                    if(advance==0)break;
                }
            }
            final double at=loopRemaining>0?length:length>0?Math.min(time,length):time;
            Map<String,Pose> sampled=!looping&&!hold&&length>0&&time>length?Map.of()
                :context.withQueries(Map.of("anim_time",at,"delta_time",step),()->sampleChannels(at,context,current));
            double weight=context.withQueries(Map.of("anim_time",at),()->finite(blendWeight,context));
            Map<String,Pose> weighted=new LinkedHashMap<>();BedrockAnimationController.accumulate(weighted,sampled,weight);
            last=new BedrockAnimationController.Sample(Map.copyOf(weighted),completed,override?Set.copyOf(weighted.keySet()):Set.of());return last;
        }
        private void dispatch(double after,double through,Molang.Context context,java.util.function.Consumer<BedrockEntityEffect> effects) {
            for(var action:actions)if(action.time>after&&action.time<=through)
                context.withQueries(Map.of("anim_time",action.time),()-> {
                    action.scripts.forEach(script->script.evaluate(context));action.effects.forEach(effects);return null;
                });
        }
    }
    private static double finite(Molang.Expression expression,Molang.Context context) {
        double value=Molang.number(expression.evaluate(context));
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite animation expression");return value;
    }
    private static double delay(Molang.Expression expression,Molang.Context context){return Math.max(0,finite(expression,context));}
    private static List<Action> actions(JsonObject source) {
        Map<Double,List<Molang.Expression>> scripts=new TreeMap<>();Map<Double,List<BedrockEntityEffect>> effects=new TreeMap<>();
        if(source.has("timeline"))for(var entry:source.getAsJsonObject("timeline").entrySet()) {
            double time=eventTime(entry.getKey());List<Molang.Expression> values=scripts.computeIfAbsent(time,key->new ArrayList<>());
            JsonElement value=entry.getValue();
            if(value.isJsonArray())value.getAsJsonArray().forEach(v->values.add(expression(v)));else values.add(expression(value));
        }
        for(var kind:List.of(BedrockEntityEffect.Kind.SOUND,BedrockEntityEffect.Kind.PARTICLE)) {
            String key=kind==BedrockEntityEffect.Kind.SOUND?"sound_effects":"particle_effects";
            if(!source.has(key))continue;
            for(var entry:source.getAsJsonObject(key).entrySet()) {
                double time=eventTime(entry.getKey());JsonElement value=entry.getValue();
                JsonArray values=value.isJsonArray()?value.getAsJsonArray():new JsonArray();
                if(!value.isJsonArray())values.add(value);
                for(var element:values) {
                    JsonObject effect=element.getAsJsonObject();
                    if(kind==BedrockEntityEffect.Kind.SOUND&&effect.has("pre_effect_script"))scripts.computeIfAbsent(time,t->new ArrayList<>()).add(expression(effect.get("pre_effect_script")));
                    effects.computeIfAbsent(time,t->new ArrayList<>()).add(new BedrockEntityEffect(kind,effect.get("effect").getAsString(),
                        effect.has("locator")?effect.get("locator").getAsString():"",
                        effect.has("pre_effect_script")?effect.get("pre_effect_script").getAsString():""));
                }
            }
        }
        Set<Double> times=new TreeSet<>(scripts.keySet());times.addAll(effects.keySet());
        return times.stream().map(t->new Action(t,List.copyOf(scripts.getOrDefault(t,List.of())),List.copyOf(effects.getOrDefault(t,List.of())))).toList();
    }
    private static double eventTime(String text){double time=Double.parseDouble(text);if(!Double.isFinite(time)||time<0)throw new IllegalArgumentException("Invalid timeline time");return time;}
    public Map<String,Pose> sample(double elapsedSeconds,Molang.Context context) {
        return sample(elapsedSeconds,context,Map.of());
    }
    public Map<String,Pose> sample(double elapsedSeconds,Molang.Context context,Map<String,Pose> current) {
        if(!Double.isFinite(elapsedSeconds))throw new IllegalArgumentException("Invalid animation time");
        boolean looping=Molang.truth(loopExpression.evaluate(context));
        if(!looping&&!hold&&length>0&&elapsedSeconds>length)return Map.of();
        double time=Math.max(0,elapsedSeconds);
        if(looping&&length>0)time%=length;else if(length>0)time=Math.min(time,length);
        final double sampleTime=time;
        return context.withQueries(Map.of("anim_time",time),()->sampleChannels(sampleTime,context,current));
    }
    private Map<String,Pose> sampleChannels(double time,Molang.Context context,Map<String,Pose> current) {
        Map<String,Pose> result=new LinkedHashMap<>();
        for(var entry:bones.entrySet()) {
            Pose previous=current.getOrDefault(entry.getKey(),new Pose(new Vector(0,0,0),new Vector(0,0,0),new Vector(1,1,1)));
            Bone b=entry.getValue();result.put(entry.getKey(),new Pose(b.position.sample(time,context,previous.position),
                b.rotation.sample(time,context,previous.rotation),b.scale.sample(time,context,previous.scale),b.entityRelativeRotation));
        }
        return Map.copyOf(result);
    }
    private static Channel channel(JsonElement source,double fallback) {
        if(source==null)source=new JsonPrimitive(fallback);
        List<Key> keys=new ArrayList<>();
        if(source.isJsonObject()) for(var entry:source.getAsJsonObject().entrySet()) {
            double time=Double.parseDouble(entry.getKey());
            if(time<0||!Double.isFinite(time))throw new IllegalArgumentException("Invalid animation key time");
            JsonElement v=entry.getValue();Value pre,post;boolean smooth=false;
            if(v.isJsonObject()) {
                JsonObject key=v.getAsJsonObject();
                String mode=key.has("lerp_mode")?key.get("lerp_mode").getAsString():"linear";
                if(!Set.of("linear","catmullrom").contains(mode)) throw new IllegalArgumentException("Unsupported interpolation: "+mode);
                smooth=mode.equals("catmullrom");
                pre=value(key.has("pre")?key.get("pre"):key.get("post"));
                post=value(key.has("post")?key.get("post"):key.get("pre"));
            } else pre=post=value(v);
            keys.add(new Key(time,pre,post,smooth));
        } else {Value v=value(source);keys.add(new Key(0,v,v,false));}
        if(keys.isEmpty())throw new IllegalArgumentException("Empty animation channel");
        keys.sort(Comparator.comparingDouble(Key::time));
        for(int i=1;i<keys.size();i++)if(keys.get(i-1).time==keys.get(i).time)throw new IllegalArgumentException("Duplicate animation key time");
        return new Channel(List.copyOf(keys));
    }
    private static Value value(JsonElement v) {
        if(v==null)throw new IllegalArgumentException("Missing keyframe value");
        if(v.isJsonArray()) {
            JsonArray a=v.getAsJsonArray();
            if(a.size()==1) {
                Molang.Expression scalar=expression(a.get(0));return new Value(scalar,scalar,scalar);
            }
            if(a.size()!=3)throw new IllegalArgumentException("Expected one or three animation components");
            return new Value(expression(a.get(0)),expression(a.get(1)),expression(a.get(2)));
        }
        Molang.Expression expression=expression(v);return new Value(expression,expression,expression);
    }
    private static Molang.Expression expression(JsonElement v){return Molang.compile(v.getAsString());}
}
