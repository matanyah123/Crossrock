package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;

/** GUI-unit destinations with pixel UV source regions. No texture loading or per-frame IO. */
public final class BedrockUiNineSlice {
    public record Slice(int x,int y,int width,int height,int u,int v,int sourceWidth,int sourceHeight){}
    public static List<Slice> regions(Map<String,JsonElement> p,int x,int y,int width,int height,int u,int v,int sourceWidth,int sourceHeight){
        if(width<0||height<0||sourceWidth<=0||sourceHeight<=0)throw new IllegalArgumentException("Invalid image size");
        int uniform=p.containsKey("nineslice_size")?pixel(p.get("nineslice_size")):0;
        int l=edge(p,"nine_slice_left",uniform),r=edge(p,"nine_slice_right",uniform),t=edge(p,"nine_slice_top",uniform),b=edge(p,"nine_slice_buttom",uniform);
        if((long)l+r>=sourceWidth&&(l+r!=0)||(long)t+b>=sourceHeight&&(t+b!=0))throw new IllegalArgumentException("Nine-slice borders leave no source center");
        int dl=Math.min(l,width),dr=Math.min(r,Math.max(0,width-dl)),dt=Math.min(t,height),db=Math.min(b,Math.max(0,height-dt));
        // Undersized images collapse the center; deterministic leading-edge priority avoids overlap.
        int[] dx={x,x+dl,x+width-dr,x+width},dy={y,y+dt,y+height-db,y+height};
        int[] sx={u,u+l,u+sourceWidth-r,u+sourceWidth},sy={v,v+t,v+sourceHeight-b,v+sourceHeight};
        var out=new ArrayList<Slice>(9);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(dx[col+1]>dx[col]&&dy[row+1]>dy[row]&&sx[col+1]>sx[col]&&sy[row+1]>sy[row])out.add(new Slice(dx[col],dy[row],dx[col+1]-dx[col],dy[row+1]-dy[row],sx[col],sy[row],sx[col+1]-sx[col],sy[row+1]-sy[row]));
        return List.copyOf(out);
    }
    private static int edge(Map<String,JsonElement> p,String key,int fallback){return p.containsKey(key)?pixel(p.get(key)):fallback;}
    private static int pixel(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Nine-slice border must be an integer");double d=e.getAsDouble();if(!Double.isFinite(d)||d<0||d>16384||d!=(int)d)throw new IllegalArgumentException("Invalid nine-slice border");return (int)d;}
    private BedrockUiNineSlice(){}
}
