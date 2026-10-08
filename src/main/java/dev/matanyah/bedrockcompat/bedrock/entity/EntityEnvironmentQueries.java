package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Extraction-time environment adapter. The renderer never retains world objects in submitted meshes. */
public record EntityEnvironmentQueries(Molang.Queries fallback, BlockLookup blocks,
                                       double x,double y,double z) implements Molang.Queries {
    @FunctionalInterface public interface BlockLookup {
        boolean hasTag(int x,int y,int z,String tag);
    }
    @Override public Object get(String query,List<Object> args) {
        if(!query.equals("relative_block_has_any_tag")&&!query.equals("relative_block_has_all_tags"))return fallback.get(query,args);
        if(args.size()<4)throw new IllegalArgumentException(query+" requires three offsets and one or more block tags");
        double dx=offset(args.get(0)),dy=offset(args.get(1)),dz=offset(args.get(2));
        // Add before flooring: flooring offsets separately is wrong near block boundaries.
        int bx=(int)Math.floor(x+dx),by=(int)Math.floor(y+dy),bz=(int)Math.floor(z+dz);
        var tags=args.subList(3,args.size());
        boolean all=query.equals("relative_block_has_all_tags");
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
