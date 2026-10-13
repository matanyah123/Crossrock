package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import org.joml.*;
import java.util.*;
import java.util.Random;
import java.lang.Math;

/** Resource-driven emitter simulation. The client supplies world, particle and audio sinks. */
public abstract class BedrockParticleEmitter {
    public final BedrockParticleEffect definition;
    public final Molang.Context context;
    public final Random random;
    public double age,lifetime,cycle;
    public int count;
    public boolean finished;
    private double credit,previousAge=-1,distance;
    private int steadyLimit;
    private boolean started,instantSpawned;
    private Vector3f lastPosition;
    protected BedrockParticleEmitter(BedrockParticleEffect definition,Molang.Queries queries,
            Map<String,Object> variables,String preEffectScript,long seed) {
        this.definition=definition;random=new Random(seed);
        context=new Molang.Context(queries,random.nextLong());variables.forEach(context::setVariable);
        context.setVariable("emitter_age",0d);
        for(int i=1;i<=4;i++)context.setVariable("emitter_random_"+i,random.nextDouble());
        if(!preEffectScript.isEmpty())Molang.compile(preEffectScript).evaluate(context);
        definition.script(definition.component("emitter_initialization"),"creation_expression",context);
        beginLoop();
    }
    private void beginLoop() {
        age=0;previousAge=-1;instantSpawned=false;credit=0;
        if(started)for(int i=1;i<=4;i++)context.setVariable("emitter_random_"+i,random.nextDouble());
        context.setVariable("emitter_age",0d);
        var life=definition.has("emitter_lifetime_once")?definition.component("emitter_lifetime_once"):definition.component("emitter_lifetime_looping");
        lifetime=Math.max(0,definition.value(life,"active_time",context,10));
        cycle=lifetime+Math.max(0,definition.value(life,"sleep_time",context,0));
        context.setVariable("emitter_lifetime",lifetime);
        steadyLimit=Math.max(0,(int)definition.value(definition.component("emitter_rate_steady"),"max_particles",context,50));
    }
    public abstract Matrix4f transform();
    protected abstract void spawnParticle(Vector3f velocity,boolean inheritVelocity);
    protected abstract void child(String effect,String type,String preExpression,Matrix4f at,Vector3f velocity);
    protected abstract void sound(String name,Vector3f at);
    protected abstract void log(String message,Vector3f at);
    public void update(double dt) {
        if(finished)return;
        if(!Double.isFinite(dt)||dt<0)throw new IllegalArgumentException("Invalid particle delta time");
        context.beginFrame();context.setVariable("emitter_age",age);definition.curves(context);
        definition.script(definition.component("emitter_initialization"),"per_update_expression",context);
        Matrix4f frame=new Matrix4f(transform());
        var events=definition.component("emitter_lifetime_events");
        if(!started){started=true;event(events.get("creation_event"),frame,new Vector3f());}
        if(definition.value(definition.component("emitter_lifetime_expression"),"expiration_expression",context,0)!=0){expire();return;}
        timeline(events,previousAge,age,frame,new Vector3f());
        Vector3f position=frame.getTranslation(new Vector3f());
        double previousDistance=distance;
        if(lastPosition!=null)distance+=lastPosition.distance(position);
        lastPosition=position;
        thresholds(events.getAsJsonObject("travel_distance_events"),previousDistance,distance,frame,new Vector3f());
        if(events.has("looping_travel_distance_events"))for(var e:events.getAsJsonArray("looping_travel_distance_events")) {
            var entry=e.getAsJsonObject();double interval=definition.value(entry,"distance",context,0);
            if(interval<=0)continue;
            int crossed=(int)Math.min(256,Math.floor(distance/interval)-Math.floor(previousDistance/interval));
            for(int i=0;i<crossed;i++)event(entry.get("effects"),frame,new Vector3f());
        }
        boolean timed=definition.has("emitter_lifetime_once")||definition.has("emitter_lifetime_looping");
        boolean initialBurst=age==0&&!instantSpawned&&definition.has("emitter_rate_instant");
        boolean active=(!timed||age<lifetime||initialBurst)
            &&definition.value(definition.component("emitter_lifetime_expression"),"activation_expression",context,1)!=0;
        int spawn=0;
        if(active&&!instantSpawned&&definition.has("emitter_rate_instant")) {
            spawn=Math.max(0,(int)definition.value(definition.component("emitter_rate_instant"),"num_particles",context,10));instantSpawned=true;
        }
        if(active&&definition.has("emitter_rate_steady")) {
            credit+=Math.max(0,definition.value(definition.component("emitter_rate_steady"),"spawn_rate",context,1))*dt;
            int requested=(int)Math.min(256,credit);credit-=Math.floor(credit);
            spawn+=Math.min(requested,Math.max(0,steadyLimit-count-spawn));
        }
        for(int i=0;i<Math.min(256,spawn);i++)spawnParticle(new Vector3f(),false);
        previousAge=age;age+=dt;
        // Include the final endpoint before retiring/resetting the loop.
        if(timed&&age>=lifetime&&previousAge<lifetime)timeline(events,previousAge,lifetime,frame,new Vector3f());
        if(definition.has("emitter_lifetime_looping")&&age>=Math.max(cycle,dt))beginLoop();
        else if(definition.has("emitter_lifetime_once")&&age>=lifetime)expire();
        else if(!timed&&!definition.has("emitter_lifetime_expression")&&!definition.has("emitter_rate_steady")
                &&!definition.has("emitter_rate_manual")&&instantSpawned&&count==0)expire();
    }
    public boolean canEmitManually() {
        return !finished&&count<Math.max(0,(int)definition.value(definition.component("emitter_rate_manual"),"max_particles",context,50));
    }
    public void expire() {
        if(finished)return;finished=true;
        event(definition.component("emitter_lifetime_events").get("expiration_event"),new Matrix4f(transform()),new Vector3f());
    }
    public void timeline(JsonObject events,double previous,double now,Matrix4f at,Vector3f velocity) {
        thresholds(events.getAsJsonObject("timeline"),previous,now,at,velocity);
    }
    private void thresholds(JsonObject entries,double previous,double now,Matrix4f at,Vector3f velocity) {
        if(entries==null)return;
        // JSON object order is not chronological order.
        var ordered=new TreeMap<Double,JsonElement>();
        entries.entrySet().forEach(e->ordered.put(Double.parseDouble(e.getKey()),e.getValue()));
        ordered.forEach((time,event)->{if(time>previous+1e-9&&time<=now+1e-9)event(event,at,velocity);});
    }
    public void event(JsonElement names,Matrix4f at,Vector3f velocity){event(names,at,velocity,0);}
    private void event(JsonElement names,Matrix4f at,Vector3f velocity,int depth) {
        if(names==null||depth>8)return;
        if(names.isJsonArray()){names.getAsJsonArray().forEach(n->event(n,at,velocity,depth+1));return;}
        JsonObject action=names.isJsonObject()?names.getAsJsonObject():definition.events().getAsJsonObject(names.getAsString());
        if(action==null)return;
        // Event expressions run on the firing emitter, even for particle lifetime/collision events.
        definition.script(action,"expression",context);
        if(action.has("sequence"))event(action.get("sequence"),at,velocity,depth+1);
        if(action.has("randomize")) {
            var choices=action.getAsJsonArray("randomize");double sum=0;
            for(var choice:choices)sum+=Math.max(0,definition.value(choice.getAsJsonObject(),"weight",context,1));
            double selected=random.nextDouble()*sum;
            for(var choice:choices) {
                selected-=Math.max(0,definition.value(choice.getAsJsonObject(),"weight",context,1));
                if(selected<0){event(choice,at,velocity,depth+1);break;}
            }
        }
        if(action.has("particle_effect")) {
            var effect=action.getAsJsonObject("particle_effect");
            String type=effect.has("type")?effect.get("type").getAsString():"emitter";
            child(effect.get("effect").getAsString(),type,
                effect.has("pre_effect_expression")?effect.get("pre_effect_expression").getAsString():"",
                new Matrix4f(at),type.equals("particle_with_velocity")?new Vector3f(velocity):new Vector3f());
        }
        if(action.has("sound_effect"))sound(action.getAsJsonObject("sound_effect").get("event_name").getAsString(),at.getTranslation(new Vector3f()));
        if(action.has("log"))log(action.get("log").getAsString(),at.getTranslation(new Vector3f()));
    }
    /** Samples emitter-local offsets; the entity AABB is supplied in world axes separately by the client. */
    public Vector3f shape(Molang.Context particle,Vector3f direction,Vector3f entityHalf) {
        String name=List.of("point","box","sphere","disc","custom","entity_aabb").stream().filter(n->definition.has("emitter_shape_"+n)).findFirst().orElse("point");
        var shape=definition.component("emitter_shape_"+name);var offset=definition.vector(shape.get("offset"),particle,0,0,0);
        var point=new Vector3f();boolean surface=definition.value(shape,"surface_only",particle,0)!=0;
        if(name.equals("box")||name.equals("entity_aabb")) {
            var h=definition.vector(shape.get("half_dimensions"),particle,.5,.5,.5);
            if(name.equals("entity_aabb"))h=entityHalf==null?new double[3]:new double[]{entityHalf.x,entityHalf.y,entityHalf.z};
            for(int i=0;i<3;i++)h[i]=Math.abs(h[i]);
            point.set((float)((random.nextDouble()*2-1)*h[0]),(float)((random.nextDouble()*2-1)*h[1]),(float)((random.nextDouble()*2-1)*h[2]));
            if(surface) {
                double[] weights={h[1]*h[2],h[0]*h[2],h[0]*h[1]};
                double selected=random.nextDouble()*(weights[0]+weights[1]+weights[2]);int axis=0;
                while(axis<2&&selected>=weights[axis])selected-=weights[axis++];
                point.setComponent(axis,(float)(h[axis]*(random.nextBoolean()?1:-1)));
            }
            if(name.equals("entity_aabb")&&entityHalf!=null)offset[1]+=entityHalf.y;
        } else if(name.equals("sphere")||name.equals("disc")) {
            double radius=Math.max(0,definition.value(shape,"radius",particle,1)),theta=random.nextDouble()*Math.PI*2;
            if(name.equals("sphere")) {
                double y=random.nextDouble()*2-1,r=Math.sqrt(1-y*y);
                point.set((float)(r*Math.cos(theta)),(float)y,(float)(r*Math.sin(theta))).mul((float)(radius*(surface?1:Math.cbrt(random.nextDouble()))));
            } else {
                point.set((float)Math.cos(theta),0,(float)Math.sin(theta)).mul((float)(radius*(surface?1:Math.sqrt(random.nextDouble()))));
                var plane=shape.get("plane_normal");double[] normal;
                if(plane!=null&&plane.isJsonPrimitive()&&Set.of("x","y","z").contains(plane.getAsString()))
                    normal=switch(plane.getAsString()){case "x"->new double[]{1,0,0};case "z"->new double[]{0,0,1};default->new double[]{0,1,0};};
                else normal=definition.vector(plane,particle,0,1,0);
                var n=new Vector3f((float)normal[0],(float)normal[1],(float)normal[2]);
                if(n.lengthSquared()>1e-12)point.rotate(new Quaternionf().rotationTo(new Vector3f(0,1,0),n.normalize()));
            }
        }
        var authored=shape.get("direction");
        if(authored!=null&&authored.isJsonArray()) {
            var d=definition.vector(authored,particle,0,0,0);direction.set((float)d[0],(float)d[1],(float)d[2]);
        } else if(name.equals("custom")||name.equals("point"))direction.zero();
        else {direction.set(point);if(authored!=null&&authored.getAsString().equals("inwards"))direction.negate();}
        if(direction.lengthSquared()>1e-12)direction.normalize();
        return point.add((float)offset[0],(float)offset[1],(float)offset[2]);
    }
}
