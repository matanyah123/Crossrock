package dev.matanyah.bedrockcompat.client.particle;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.client.entity.NativeEntityResources;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.lang.ref.WeakReference;
import java.util.*;

/** Client-ticked emitters, shared by animation events and vanilla particle replacements. */
public final class NativeParticles {
    static final int MAX_PARTICLES=8192,MAX_EMITTERS=512,MAX_SPAWN_PER_TICK=256;
    static Map<String,BedrockParticleEffect> definitions=Map.of();
    private static final Map<Entity,Owner> owners=new WeakHashMap<>();
    private static final List<Emitter> emitters=new ArrayList<>(),pending=new ArrayList<>();
    private static Object level;
    private static final Map<Identifier,Boolean> vanillaPriority=new HashMap<>();
    static int generation;
    static int alive;
    private static final Map<String,Integer> aliveByEffect=new HashMap<>();
    private static int spawnDepth;
    private NativeParticles() {}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(mc->tick());}
    public static void reload(Map<String,String> resources,List<String> errors) {
        clear();var next=new HashMap<String,BedrockParticleEffect>();
        resources.forEach((id,json)-> {
            if(!id.startsWith("particle."))return;
            try {var effect=new BedrockParticleEffect(JsonParser.parseString(json).getAsJsonObject());
                var texture=Identifier.fromNamespaceAndPath("bedrock_native",effect.texture+".png");
                if(Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty())throw new IllegalArgumentException("Missing particle texture: "+texture);
                next.put(effect.id,effect);}
            catch(RuntimeException failure){errors.add(id+": "+failure.getMessage());}
        });definitions=Map.copyOf(next);
    }
    private static void clear(){owners.clear();emitters.clear();pending.clear();vanillaPriority.clear();aliveByEffect.clear();alive=0;generation++;}
    static void released(String id){alive=Math.max(0,alive-1);aliveByEffect.computeIfPresent(id,(key,value)->Math.max(0,value-1));}
    private static Molang.Queries particleQueries(BedrockParticleEffect definition,Owner owner,String locator) {
        return new ParticleQueryContext((q,a)->owner.queries.get(q,a),()-> {
            var frames=owner.frameSource==null?owner.frames:owner.frameSource.frames;
            var p=frames.getOrDefault(locator,frames.getOrDefault("",new Matrix4f())).getTranslation(new Vector3f());
            return new double[]{p.x,p.y,p.z};
        },()-> {
            var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
            return new double[]{camera.x,camera.y,camera.z};
        },()->(int)concat().stream().filter(e->!e.finished&&e.definition.id.equals(definition.id)).count(),
            ()->aliveByEffect.getOrDefault(definition.id,0));
    }
    public static int emitterCount(){return emitters.size()+pending.size();}
    public static int particleCount(){return alive;}
    public static boolean hasDefinition(String id){return definitions.containsKey(id);}
    /** Starts a standalone Bedrock effect without fabricating an entity or changing vanilla particles. */
    public static boolean emitAt(String id,Matrix4f world,boolean manual) {
        var definition=definitions.get(id);if(definition==null||Minecraft.getInstance().level==null||concat().size()>=MAX_EMITTERS)return false;
        var owner=new Owner(null);owner.frames.put("",new Matrix4f(world));
        var emitter=new Emitter(definition,owner,"",false);emitter.manualEvent=manual;
        if(manual&&emitter.canEmitManually())emitter.spawn(new Vector3f(),false);
        pending.add(emitter);return true;
    }
    public record Snapshot(String effect,Vector3f position,boolean bound,int particles,Map<String,Object> variables) {}
    public static List<Snapshot> snapshots(){return concat().stream().map(e->new Snapshot(e.definition.id,
        e.transform().getTranslation(new Vector3f()),e.owner.attached,e.count,e.context.variables())).toList();}
    /** Shared model-to-world locator transform for entity particle and positional sound events. */
    public static Optional<Vector3f> locatorPosition(Entity entity,String locator) {
        var owner=owners.get(entity);
        var frame=owner==null?null:owner.frames.get(locator);
        return frame==null?Optional.empty():Optional.of(frame.getTranslation(new Vector3f()));
    }
    public static void observe(Entity entity,Molang.Queries queries,Map<String,Object> variables,Map<String,Matrix4f> anchors,double yaw,BedrockAnimation.Vector scale,double originY) {
        var owner=owners.computeIfAbsent(entity,Owner::new);owner.queries=queries;owner.variables=variables;
        // Geometry/variant switches must not retain locators from the previous skeleton.
        owner.frames.clear();
        var world=new Matrix4f().translation((float)entity.getX(),(float)entity.getY(),(float)entity.getZ())
            .rotateY((float)Math.toRadians(-yaw)).translate(0,(float)originY,0)
            .scale((float)scale.x(),(float)scale.y(),(float)-scale.z());
        owner.frames.put("",world);
        anchors.forEach((name,matrix)->owner.frames.put(name,new Matrix4f(world).scale(1/16f).mul(matrix).scale(16)));
    }
    public static void attachable(Entity entity,String slot,Map<String,Matrix4f> anchors,List<BedrockEntityEffect> effects) {
        var owner=owners.get(entity);if(owner==null||!owner.frames.containsKey(""))return;
        String prefix="attachable/"+slot+"/";
        anchors.forEach((name,matrix)->owner.frames.put(prefix+name,new Matrix4f(owner.frames.get("")).scale(1/16f).mul(matrix).scale(16)));
        for(var effect:effects)if(effect.kind()!=BedrockEntityEffect.Kind.SOUND)
            emit(entity,new BedrockEntityEffect(effect.kind(),effect.effect(),effect.locator().isEmpty()?"":prefix+effect.locator(),effect.preEffectScript(),effect.bindToActor(),effect.managed()),false);
    }
    public static boolean emit(Entity entity,BedrockEntityEffect event,boolean fire) {
        var definition=definitions.get(event.effect());
        if(definition==null){NativeEntityResources.runtimeError("Missing particle definition: "+event.effect());return false;}
        var owner=owners.computeIfAbsent(entity,Owner::new);
        if(event.kind()==BedrockEntityEffect.Kind.PARTICLE_STOP) {
            for(var emitter:concat())if(emitter.controllerOwner==owner&&emitter.definition==definition&&emitter.managed
                    &&emitter.bindToActor==event.bindToActor()&&emitter.preEffectScript.equals(event.preEffectScript())
                    &&emitter.eventLocator.equals(event.locator()))emitter.expire();
            return true;
        }
        if(!event.locator().isEmpty()&&!owner.frames.containsKey(event.locator())) {
            NativeEntityResources.runtimeError("Particle "+event.effect()+": missing locator "+event.locator());return false;
        }
        // Repeated controller/loop events retain one attached emitter, not one per rendered frame.
        for(var emitter:concat())if(event.managed()&&emitter.managed&&emitter.controllerOwner==owner&&!emitter.finished&&emitter.definition==definition
                &&emitter.eventLocator.equals(event.locator())&&emitter.bindToActor==event.bindToActor()
                &&emitter.preEffectScript.equals(event.preEffectScript()))return true;
        if(emitters.size()+pending.size()>=MAX_EMITTERS)return false;
        try {
            Owner simulationOwner=owner;
            if(!event.bindToActor()) {
                simulationOwner=new Owner(null);simulationOwner.queries=owner.queries;
                simulationOwner.frames.put("",new Matrix4f(owner.frames.getOrDefault(event.locator(),owner.frames.getOrDefault("",new Matrix4f()))));
            }
            var emitter=new Emitter(definition,simulationOwner,event.bindToActor()?event.locator():"",fire&&event.bindToActor(),owner.variables,event.preEffectScript());
            emitter.controllerOwner=owner;emitter.eventLocator=event.locator();emitter.managed=event.managed();emitter.bindToActor=event.bindToActor();
            pending.add(emitter);return true;
        }
        catch(RuntimeException failure){NativeEntityResources.runtimeError("Particle "+definition.id+": "+failure.getMessage());return false;}
    }
    private static List<Emitter> concat(){var out=new ArrayList<>(emitters);out.addAll(pending);return out;}
    public static boolean replacesFire(Entity entity) {
        for(var emitter:concat())if(emitter.fire&&!emitter.finished&&emitter.owner.entity.get()==entity)return true;
        return false;
    }
    public static boolean replace(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz) {
        var mc=Minecraft.getInstance();if(mc.level==null||definitions.isEmpty())return false;
        var javaId=BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        String id=BedrockParticleIds.bedrock(javaId.toString());var definition=definitions.get(id);
        if(definition==null)return false;
        // A higher Java resource pack still owns its particle declaration/texture.
        boolean higherPriority=vanillaPriority.computeIfAbsent(javaId,key->{
        var resource=mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath(key.getNamespace(),"particles/"+key.getPath()+".json"));
        if(resource.isPresent()) {
            var packs=mc.getResourceManager().listPacks().map(p->p.packId()).toList();
            int nativeRank=packs.indexOf(dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider.PACK_ID);
            if(packs.indexOf(resource.get().sourcePackId())>nativeRank)return true;
        }
        return false;});
        if(higherPriority)return false;
        var owner=new Owner(null);owner.frames.put("",new Matrix4f().translation((float)x,(float)y,(float)z));
        try {var emitter=new Emitter(definition,owner,"",false);
            emitter.spawn(new Vector3f((float)dx,(float)dy,(float)dz),true);return true;}
        catch(RuntimeException failure){NativeEntityResources.runtimeError("Particle "+definition.id+": "+failure.getMessage());return false;}
    }
    private static void tick() {
        var mc=Minecraft.getInstance();if(level!=mc.level){clear();level=mc.level;}if(mc.level==null)return;
        emitters.addAll(pending);pending.clear();
        for(var emitter:emitters)try {emitter.tick();}catch(RuntimeException failure) {
            emitter.finished=true;NativeEntityResources.runtimeError("Particle "+emitter.definition.id+": "+failure.getMessage());
        }
        emitters.removeIf(e->e.finished);
    }
    static final class Owner {
        final WeakReference<Entity> entity;
        final Map<String,Matrix4f> frames=new HashMap<>();
        Map<String,Object> variables=Map.of();
        final boolean attached;
        Owner frameSource;
        Molang.Queries queries=(name,args)->{throw new IllegalArgumentException("Unsupported Bedrock query in standalone particle context: "+name);};
        Owner(Entity entity){this.entity=new WeakReference<>(entity);attached=entity!=null;}
    }
    static final class Emitter extends BedrockParticleEmitter {
        final Owner owner;final String locator;final boolean fire;
        Owner controllerOwner;
        String preEffectScript="",eventLocator="";
        boolean managed,bindToActor=true,manualEvent;
        Molang.Queries queries;
        Emitter(BedrockParticleEffect definition,Owner owner,String locator,boolean fire) {
            this(definition,owner,locator,fire,owner.variables,"");
        }
        Emitter(BedrockParticleEffect definition,Owner owner,String locator,boolean fire,Map<String,Object> variables,String script) {
            super(definition,particleQueries(definition,owner,locator),variables,script,new Random().nextLong());
            this.owner=owner;this.locator=locator;this.fire=fire;preEffectScript=script;
            queries=particleQueries(definition,owner,locator);
        }
        @Override public Matrix4f transform(){var frames=owner.frameSource==null?owner.frames:owner.frameSource.frames;return frames.getOrDefault(locator,frames.getOrDefault("",new Matrix4f()));}
        void tick() {
            if(manualEvent&&count==0){expire();return;}
            var entity=owner.entity.get();
            if(owner.attached&&entity==null||entity!=null&&(entity.isRemoved()||entity.level()!=Minecraft.getInstance().level||fire&&!entity.isOnFire())){expire();return;}
            update(.05);
        }
        @Override protected void spawnParticle(Vector3f velocity,boolean inheritVelocity){spawn(velocity,false,inheritVelocity);}
        void spawn(Vector3f velocity,boolean vanilla){spawn(velocity,vanilla,false);}
        void spawn(Vector3f velocity,boolean vanilla,boolean inheritVelocity) {
            if(alive>=MAX_PARTICLES||spawnDepth>=16)return;
            var mc=Minecraft.getInstance();if(mc.level==null)return;
            var atlas=(TextureAtlas)mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            var sprite=atlas.getSprite(Identifier.fromNamespaceAndPath("bedrock_native",definition.texture.replaceFirst("^textures/","")));
            if(sprite==atlas.missingSprite())throw new IllegalArgumentException("Particle texture was not stitched: "+definition.texture);
            count++;alive++;aliveByEffect.merge(definition.id,1,Integer::sum);spawnDepth++;
            try {mc.particleEngine.add(new NativeParticle(mc.level,this,sprite,velocity,vanilla,inheritVelocity));}
            catch(RuntimeException failure){count--;released(definition.id);throw failure;}
            finally {spawnDepth--;}
        }
        Vector3f shape(Molang.Context particle,Vector3f direction) {
            var e=owner.entity.get();
            return super.shape(particle,direction,e==null?null:new Vector3f(e.getBbWidth()/2,e.getBbHeight()/2,e.getBbWidth()/2));
        }
        @Override protected void child(String id,String type,String script,Matrix4f at,Vector3f velocity) {
            var effect=definitions.get(id);
            if(effect==null){NativeEntityResources.runtimeError("Missing particle definition: "+id);return;}
            if(pending.size()+emitters.size()>=MAX_EMITTERS)return;
            if(!Set.of("emitter","emitter_bound","particle","particle_with_velocity").contains(type)) {
                NativeEntityResources.runtimeError("Unsupported particle event type: "+type);return;
            }
            boolean bound=type.equals("emitter_bound")&&owner.attached;
            boolean manual=type.equals("particle")||type.equals("particle_with_velocity");
            // Manual events share an emitter (and its cap) while particles from it are alive.
            if(manual)for(var existing:concat())if(existing.controllerOwner==owner&&existing.definition==effect
                    &&existing.preEffectScript.equals(script)&&existing.count>0&&!existing.finished) {
                existing.owner.frames.put("",new Matrix4f(at));
                if(existing.canEmitManually())existing.spawn(velocity,false,type.equals("particle_with_velocity"));
                return;
            }
            var childOwner=new Owner(bound?owner.entity.get():null);
            childOwner.frames.put("",new Matrix4f(at));childOwner.queries=owner.queries;
            if(bound)childOwner.frameSource=owner.frameSource==null?owner:owner.frameSource;
            // Fresh emitter context: no spawning-emitter variables or temporaries are copied.
            var next=new Emitter(effect,childOwner,bound?locator:"",false,Map.of(),script);
            next.controllerOwner=owner;next.manualEvent=manual;
            if(manual) {
                if(next.canEmitManually())next.spawn(velocity,false,type.equals("particle_with_velocity"));
                if(next.count>0)pending.add(next);
            } else pending.add(next);
        }
        @Override protected void sound(String name,Vector3f at) {
            var mc=Minecraft.getInstance();var manager=mc.getSoundManager();
            var id=NativeSoundEvents.id(name);
            if(manager.getSoundEvent(id)==null) {
                // Level sound names without an imported override can still use a Java event.
                id=Identifier.tryParse(name);
                if(id==null||manager.getSoundEvent(id)==null)for(String mapped:dev.matanyah.bedrockcompat.bedrock.resource.VanillaSoundTranslator.particleEventFallbacks(name)) {
                    var candidate=Identifier.tryParse(mapped);
                    if(candidate!=null&&manager.getSoundEvent(candidate)!=null){id=candidate;break;}
                }
                if(id==null||manager.getSoundEvent(id)==null){NativeEntityResources.runtimeError("Particle "+definition.id+": missing sound event "+name);return;}
            }
            manager.play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.NEUTRAL,
                1,1,net.minecraft.util.RandomSource.create(random.nextLong()),at.x,at.y,at.z));
        }
        @Override protected void log(String message,Vector3f at) {
            org.slf4j.LoggerFactory.getLogger("Crossrock/particles").info("Particle {} at ({}, {}, {}): {}",definition.id,at.x,at.y,at.z,message);
        }
    }
}
