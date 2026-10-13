package dev.matanyah.bedrockcompat.bedrock.ui;

import java.util.*;

/** Atomic presentation plan. Never creates slots or changes their identities/contents. */
public final class BedrockUiSlots {
    public record Slot(int index,int x,int y) {}
    public static Map<Integer,Slot> plan(List<BedrockUiLayout.Draw> draws,List<Slot> nativeSlots,BedrockUiLayout.Rect panel) {
        var result=new LinkedHashMap<Integer,Slot>();var indices=new HashSet<Integer>();
        for(var slot:nativeSlots)if(!indices.add(slot.index()))throw new IllegalArgumentException("Duplicate native slot identity");
        for(var draw:draws)if(draw.type().equals("custom")) {
            var b=draw.bounds();
            if(!draw.properties().containsKey("__slot"))throw new IllegalArgumentException("Item renderer has no authoritative slot");
            int index=draw.properties().get("__slot").getAsInt();
            if(!indices.contains(index)||result.containsKey(index))throw new IllegalArgumentException("Unknown/duplicate imported slot");
            if(b.width()!=16||b.height()!=16||draw.alpha()!=1||!BedrockUiLayout.bool(draw.properties(),"enabled",true)||!b.intersect(panel).equals(b)||!b.intersect(draw.clip()).equals(b))throw new IllegalArgumentException("Slot must be fully visible, enabled, 16 GUI pixels and inside native panel");
            // Match NativeJsonUi's GUI draw rounding, including odd viewport dimensions.
            double x=Math.round(b.x())-panel.x(),y=Math.round(b.y())-panel.y();
            var rounded=new BedrockUiLayout.Rect(panel.x()+x,panel.y()+y,16,16);
            if(!rounded.intersect(panel).equals(rounded)||!rounded.intersect(draw.clip()).equals(rounded))throw new IllegalArgumentException("Rounded slot outside panel/clip");
            result.put(index,new Slot(index,(int)x,(int)y));
        }
        if(result.isEmpty())return Map.of();
        var occupied=new ArrayList<BedrockUiLayout.Rect>();
        for(var original:nativeSlots) {
            var next=result.getOrDefault(original.index(),original);var rect=new BedrockUiLayout.Rect(next.x(),next.y(),16,16);
            if(occupied.stream().anyMatch(r->BedrockUiInteraction.overlaps(r,rect)))throw new IllegalArgumentException("Imported slots overlap native slots");
            occupied.add(rect);
        }
        return Map.copyOf(result);
    }
}
