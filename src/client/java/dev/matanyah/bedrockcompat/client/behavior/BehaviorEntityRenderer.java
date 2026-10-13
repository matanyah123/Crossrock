package dev.matanyah.bedrockcompat.client.behavior;
import dev.matanyah.bedrockcompat.behavior.BehaviorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
/** Native extraction/submission mixins replace this diagnostic host mesh when a pack supplies a model. */
public final class BehaviorEntityRenderer extends HumanoidMobRenderer<BehaviorEntity,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    public BehaviorEntityRenderer(EntityRendererProvider.Context context){super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),.5f);}
    @Override public HumanoidRenderState createRenderState(){return new HumanoidRenderState();}
    @Override public Identifier getTextureLocation(HumanoidRenderState state){return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");}
}
