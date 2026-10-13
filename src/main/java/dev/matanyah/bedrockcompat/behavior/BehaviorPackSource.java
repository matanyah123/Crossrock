package dev.matanyah.bedrockcompat.behavior;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class BehaviorPackSource implements RepositorySource {
    private static final PackSource SOURCE=PackSource.create(PackSource.NO_DECORATION,false);
    private final Path datapacks;
    public BehaviorPackSource(Path datapacks){this.datapacks=datapacks;}
    @Override public void loadPacks(Consumer<Pack> output) {
        var catalog=PackCatalog.discover(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),datapacks);
        for(var p:catalog.packs())if(PackCatalog.behavior(p)) {
            var location=new PackLocationInfo(PackCatalog.datapackId(p),Component.literal(p.displayName()),SOURCE,Optional.empty());
            var supplier=new Pack.ResourcesSupplier(){
                @Override public PackResources openPrimary(PackLocationInfo l){return new BehaviorPackResources(l,p,List.of());}
                @Override public PackResources openFull(PackLocationInfo l,Pack.Metadata metadata){
                    try {List<BehaviorPackResources.Content> content=new ArrayList<>();
                        for(var dependency:catalog.closure(List.of(p))) {var files=PackCatalog.read(dependency);content.add(new BehaviorPackResources.Content(dependency,files,PackCatalog.hash(files)));}
                        return new BehaviorPackResources(l,p,content);
                    }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                }
            };
            var description=Component.literal(p.description()+"\nBehavior infrastructure; scripts/gameplay components deferred");
            output.accept(new Pack(location,supplier,new Pack.Metadata(description,PackCompatibility.COMPATIBLE,FeatureFlagSet.of(),List.of()),new PackSelectionConfig(false,Pack.Position.TOP,false)));
        }
    }
}
