package dev.matanyah.bedrockcompat.client.block;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.lang.ref.*;
import java.util.*;
import java.util.function.Predicate;

/** Authored neighbour culling through Fabric's world-aware baked-model emission API. */
public final class NativeBlockCulling {
    private record Rule(Direction direction,String condition,boolean full) {}
    private record Tag(List<Rule> rules,String layer) {}
    private static final ReferenceQueue<BakedQuad> QUEUE=new ReferenceQueue<>();
    private static final Map<Key,Tag> TAGS=new HashMap<>();
    private static final class Key extends WeakReference<BakedQuad> {
        private final int hash;
        Key(BakedQuad q){super(q,QUEUE);hash=System.identityHashCode(q);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
    }
    private NativeBlockCulling() {}
    private static synchronized Tag tag(BakedQuad q) {
        Reference<? extends BakedQuad> old;while((old=QUEUE.poll())!=null)TAGS.remove(old);
        return TAGS.get(new Key(q));
    }
    static synchronized void tag(BakedQuad q,JsonObject source,String layer) {
        List<Rule> rules=new ArrayList<>();
        if(source.has("culling"))for(var e:source.getAsJsonArray("culling")) {
            var r=e.getAsJsonObject();rules.add(new Rule(Direction.byName(r.get("direction").getAsString()),r.get("condition").getAsString(),r.get("full").getAsBoolean()));
        }
        TAGS.put(new Key(q),new Tag(List.copyOf(rules),layer));
    }
    public static void register() {
        ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
            List<BlockStateModelPart> parts=new ArrayList<>();model.collectParts(RandomSource.create(0),parts);
            boolean authored=parts.stream().flatMap(p->p.getQuads(null).stream()).anyMatch(q->{Tag t=tag(q);return t!=null&&!t.rules.isEmpty();});
            return authored?new CulledModel(model):model;
        }));
    }
    public static boolean visible(BakedQuad q,BlockAndTintGetter world,BlockPos pos,BlockState state) {
        Tag tag=tag(q);if(tag==null)return true;
        for(var rule:tag.rules) {
            BlockState neighbor=world.getBlockState(pos.relative(rule.direction));
            boolean full=rule.full&&!NativeBlockModels.custom(neighbor)&&neighbor.isSolidRender()
                &&Block.isShapeFullBlock(neighbor.getFaceOcclusionShape(rule.direction.getOpposite()));
            boolean matches=switch(rule.condition) {
                case "same_block" -> neighbor.is(state.getBlock());
                case "same_block_permutation" -> neighbor==state;
                case "same_culling_layer" -> !tag.layer.isEmpty()&&!tag.layer.equals("minecraft:culling_layer.undefined")&&tag.layer.equals(layer(neighbor));
                default -> false;
            };
            if(full||matches)return false;
        }
        return true;
    }
    private static String layer(BlockState state) {
        var model=Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts=new ArrayList<>();model.collectParts(RandomSource.create(0),parts);
        for(var part:parts)for(var q:part.getQuads(null)){Tag t=tag(q);if(t!=null)return t.layer;}
        return "";
    }
    private record KeyForWorld(Object model,BlockState state,List<BlockState> neighbors) {}
    private record CulledModel(BlockStateModel delegate) implements BlockStateModel {
        @Override public void collectParts(RandomSource random,List<BlockStateModelPart> parts){delegate.collectParts(random,parts);}
        @Override public Material.Baked particleMaterial(){return delegate.particleMaterial();}
        @Override public int materialFlags(){return delegate.materialFlags();}
        @Override public Object createGeometryKey(BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random) {
            List<BlockState> neighbors=new ArrayList<>();for(var d:Direction.values())neighbors.add(world.getBlockState(pos.relative(d)));
            return new KeyForWorld(this,state,List.copyOf(neighbors));
        }
        @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest) {
            List<BlockStateModelPart> parts=new ArrayList<>();delegate.collectParts(random,parts);
            for(var part:parts) {
                for(var q:part.getQuads(null))if(visible(q,world,pos,state))emitter.fromBakedQuad(q).ambientOcclusion(TriState.of(part.useAmbientOcclusion())).emit();
                for(var d:Direction.values())if(!cullTest.test(d))for(var q:part.getQuads(d))if(visible(q,world,pos,state))emitter.fromBakedQuad(q).cullFace(d).ambientOcclusion(TriState.of(part.useAmbientOcclusion())).emit();
            }
        }
    }
}
