package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Extraction-time environment adapter. The renderer never retains world objects in submitted meshes. */
public record EntityEnvironmentQueries(Molang.Queries fallback, BlockLookup blocks,
                                       double x,double y,double z) implements Molang.Queries {
    @FunctionalInterface public interface BlockLookup {
        boolean hasTag(int x,int y,int z,String tag);
    }
    @Override public Object get(String query,List<Object> args) {
        boolean relative=query.startsWith("relative_");
        boolean neighbor=query.startsWith("block_neighbor_");
        if(!Set.of("relative_block_has_any_tag","relative_block_has_all_tags","block_has_any_tag","block_has_all_tags","block_neighbor_has_any_tag","block_neighbor_has_all_tags").contains(query))return fallback.get(query,args);
        if(args.size()<4)throw new IllegalArgumentException(query+" requires three offsets and one or more block tags");
        double dx=offset(relative||neighbor?Molang.number(args.get(0)):Molang.number(args.get(0))-x),
            dy=offset(relative||neighbor?Molang.number(args.get(1)):Molang.number(args.get(1))-y),
            dz=offset(relative||neighbor?Molang.number(args.get(2)):Molang.number(args.get(2))-z);
        // Add before flooring: flooring offsets separately is wrong near block boundaries.
        int bx=(int)Math.floor((neighbor?Math.floor(x):x)+dx),by=(int)Math.floor((neighbor?Math.floor(y):y)+dy),bz=(int)Math.floor((neighbor?Math.floor(z):z)+dz);
        var tags=args.subList(3,args.size());
        boolean all=query.endsWith("has_all_tags");
        boolean result=all?tags.stream().allMatch(tag->blocks.hasTag(bx,by,bz,tag.toString())):
            tags.stream().anyMatch(tag->blocks.hasTag(bx,by,bz,tag.toString()));
        return result?1d:0d;
    }
    private static double offset(Object value) {
        double offset=Molang.number(value);
        if(!Double.isFinite(offset)||Math.abs(offset)>128)
            throw new IllegalArgumentException("Unsupported Bedrock relative block offset outside client observation range");
        return offset;
    }
}
