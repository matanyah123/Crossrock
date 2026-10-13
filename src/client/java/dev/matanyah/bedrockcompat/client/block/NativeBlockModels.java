package dev.matanyah.bedrockcompat.client.block;

import com.google.gson.*;
import com.mojang.blaze3d.platform.Transparency;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.geometry.*;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.*;
import java.io.StringReader;
import java.util.*;

/** Reload-scoped, immutable meshes in Java's normal block/item baking and atlas pipeline. */
public final class NativeBlockModels {
    private NativeBlockModels() {}
    public static void register() {
        NativeBlockCulling.register();
        UnbakedModelDeserializer.register(Identifier.fromNamespaceAndPath("bedrock_compat","static_block_mesh"),(json,context)->{
            CuboidModel metadata=CuboidModel.fromStream(new StringReader(json.toString()));
            JsonArray quads=json.getAsJsonArray("quads").deepCopy();
            if(quads.size()>16384)throw new JsonParseException("Too many static block faces");
            UnbakedGeometry mesh=(slots,baker,state,name)->{
                QuadCollection.Builder out=new QuadCollection.Builder();
                for(var entry:quads) {
                    JsonObject q=entry.getAsJsonObject();String method=q.get("render_method").getAsString();
                    Material.Baked material=baker.materials().resolveSlot(slots,q.get("texture").getAsString(),name);
                    Transparency transparency=switch(method){case "blend"->Transparency.TRANSLUCENT;
                        case "alpha_test","alpha_test_single_sided"->Transparency.TRANSPARENT;default->Transparency.NONE;};
                    var info=BakedQuad.MaterialInfo.of(material,transparency,-1,q.get("shade").getAsBoolean(),0);
                    var vertices=q.getAsJsonArray("vertices");if(vertices.size()!=4)throw new JsonParseException("Expected four vertices");
                    Vector3fc[] positions=new Vector3fc[4];long[] uv=new long[4];
                    for(int i=0;i<4;i++) {
                        var v=vertices.get(i).getAsJsonArray();Vector3f p=new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
                        state.transformation().getMatrix().transformPosition(p);
                        positions[i]=baker.interner().vector(p);
                        uv[i]=UVPair.pack(material.sprite().getU(v.get(3).getAsFloat()),material.sprite().getV(v.get(4).getAsFloat()));
                    }
                    Vector3f normal=new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
                    if(normal.lengthSquared()<1e-12f)continue; // Authored zero scale intentionally hides geometry.
                    Direction direction=closest(normal);
                    BakedQuad front=new BakedQuad(positions[0],positions[1],positions[2],positions[3],uv[0],uv[1],uv[2],uv[3],direction,info);
                    NativeBlockCulling.tag(front,q,json.has("crossrock_culling_layer")?json.get("crossrock_culling_layer").getAsString():"");
                    out.addUnculledFace(front);
                    if(method.equals("alpha_test")||method.equals("double_sided")) {
                        BakedQuad back=new BakedQuad(positions[3],positions[2],positions[1],positions[0],uv[3],uv[2],uv[1],uv[0],direction.getOpposite(),info);
                        NativeBlockCulling.tag(back,q,json.has("crossrock_culling_layer")?json.get("crossrock_culling_layer").getAsString():"");out.addUnculledFace(back);
                    }
                }
                return out.build();
            };
            return new CuboidModel(mesh,metadata.guiLight(),metadata.ambientOcclusion(),metadata.transforms(),metadata.textureSlots(),metadata.parent());
        });
    }
    private static Direction closest(Vector3f normal) {
        Direction result=Direction.UP;float largest=-Float.MAX_VALUE;
        for(Direction d:Direction.values()){float dot=normal.x*d.getStepX()+normal.y*d.getStepY()+normal.z*d.getStepZ();if(dot>largest){largest=dot;result=d;}}
        return result;
    }
    /** Query the committed baked model rather than keeping a second mutable reload/pack cache. */
    public static boolean custom(BlockState state) {
        var mc=Minecraft.getInstance();if(mc==null||mc.getModelManager()==null)return false;
        var model=mc.getModelManager().getBlockStateModelSet().get(state);
        if(model==null)return false;
        Identifier sprite=model.particleMaterial().sprite().contents().name();
        return sprite.getNamespace().equals("bedrock_compat")&&sprite.getPath().startsWith("block/assets/");
    }
}
