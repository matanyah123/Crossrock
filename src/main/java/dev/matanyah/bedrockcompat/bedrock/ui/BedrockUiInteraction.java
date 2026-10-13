package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;

/** Input policy shared by the compiler, native widget adapter and behavioral tests. */
public final class BedrockUiInteraction {
    public record Mapping(String source,String action,String kind) {}
    public static List<Mapping> mappings(Map<String,JsonElement> properties) {
        var out=new ArrayList<Mapping>();var seen=new HashSet<String>();
        if(!properties.containsKey("button_mappings"))throw new IllegalArgumentException("Button has no input mappings");
        for(var element:properties.get("button_mappings").getAsJsonArray()) {
            var p=element.getAsJsonObject().asMap();
            for(String key:p.keySet())if(!Set.of("from_button_id","to_button_id","mapping_type").contains(key))throw new IllegalArgumentException("Unsupported button mapping field: "+key);
            String source=BedrockUiLayout.string(p,"from_button_id",""),action=BedrockUiLayout.string(p,"to_button_id",""),kind=BedrockUiLayout.string(p,"mapping_type","");
            if(!(source.equals("button.menu_select")&&kind.equals("pressed")||source.equals("button.menu_ok")&&kind.equals("focused")))throw new IllegalArgumentException("Unsupported input mapping: "+source+"/"+kind);
            if(action.isEmpty()||!seen.add(source))throw new IllegalArgumentException("Missing/ambiguous button action");
            out.add(new Mapping(source,action,kind));
        }
        if(out.isEmpty())throw new IllegalArgumentException("Button has no input mappings");
        if(!BedrockUiLayout.bool(properties,"consume_event",true))throw new IllegalArgumentException("Non-consuming action buttons unsupported: prevents double dispatch");
        return List.copyOf(out);
    }
    public static boolean hit(BedrockUiLayout.Draw draw,double x,double y) {
        var b=draw.bounds();var c=draw.clip();
        var rendered=new BedrockUiLayout.Rect(Math.round(b.x()),Math.round(b.y()),Math.round(b.width()),Math.round(b.height()));
        var clip=new BedrockUiLayout.Rect(Math.floor(c.x()),Math.floor(c.y()),Math.ceil(c.x()+c.width())-Math.floor(c.x()),Math.ceil(c.y()+c.height())-Math.floor(c.y()));
        return draw.alpha()>0&&BedrockUiLayout.bool(draw.properties(),"enabled",true)&&contains(rendered,x,y)&&contains(clip,x,y);
    }
    private static boolean contains(BedrockUiLayout.Rect r,double x,double y){return x>=r.x()&&y>=r.y()&&x<r.x()+r.width()&&y<r.y()+r.height();}
    public static boolean overlaps(BedrockUiLayout.Rect a,BedrockUiLayout.Rect b){return a.intersect(b).width()>0&&a.intersect(b).height()>0;}
    /** No approximate matching, translated-label matching, or catch-all confirm action. */
    public static boolean supportedAction(String action,String root) {
        return switch(action) {
            case "button.menu_exit","button.menu_cancel" -> !root.equals("start.start_screen")&&!root.equals("hud.hud_screen");
            case "button.menu_continue","button.menu_settings" -> root.equals("pause.pause_screen");
            default -> false;
        };
    }
    public static String action(Map<String,JsonElement> p,boolean keyboard) {
        return mappings(p).stream().filter(m->m.source().equals(keyboard?"button.menu_ok":"button.menu_select")).map(Mapping::action).findFirst().orElse("");
    }
}
