package dev.matanyah.bedrockcompat.client.behavior;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.network.chat.Component;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackResources;
import java.util.*;
import java.util.function.Consumer;
public final class WorldResourcePacks {
    public static void load(Consumer<Pack> output){
        for(var e:BehaviorClientSession.resources().entrySet()) {
            var resource=e.getValue();var location=new PackLocationInfo(e.getKey(),Component.literal(resource.pack().displayName()),PackSource.SERVER,Optional.empty());
            var supplier=new Pack.ResourcesSupplier(){
                @Override public PackResources openPrimary(PackLocationInfo l){return new VirtualCompatibilityPackResources(l,resource.snapshot(),resource.pack().icon().orElse(null));}
                @Override public PackResources openFull(PackLocationInfo l,Pack.Metadata m){return openPrimary(l);}
            };
            output.accept(new Pack(location,supplier,new Pack.Metadata(Component.literal(resource.pack().description()),PackCompatibility.COMPATIBLE,FeatureFlagSet.of(),List.of()),new PackSelectionConfig(true,Pack.Position.TOP,true)));
        }
    }
}
