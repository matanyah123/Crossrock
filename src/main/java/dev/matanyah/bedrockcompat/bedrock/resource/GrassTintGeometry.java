package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

/** Grass blocks use solid terrain draws. Never rely on transparent tinted dirt quads. */
final class GrassTintGeometry {
    static void clipOverlay(JsonObject model,byte[] png) {
        try {
            var image=ImageIO.read(new ByteArrayInputStream(png));
            if(image==null||image.getWidth()>256||image.getHeight()!=image.getWidth())return;
            int size=image.getWidth();
            boolean[][] mask=new boolean[size][size];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
                int alpha=image.getRGB(x,y)>>>24;
                mask[y][x]=alpha>1;
            }
            var elements=model.getAsJsonArray("elements");
            // Both layers are solid terrain. Replace BOTH full side faces, not just the tint:
            // coplanar base geometry under a tinted patch causes depth fighting (not transparency).
            for(var element:elements) {
                var faces=element.getAsJsonObject().getAsJsonObject("faces");
                for(String face:List.of("north","south","east","west")) {
                    var definition=faces.getAsJsonObject(face);
                    if(definition!=null&&Set.of("#side","#base").contains(definition.get("texture").getAsString()))faces.remove(face);
                }
            }
            // Java rejects elements with zero faces (the old side-only base is now empty).
            for(int i=elements.size()-1;i>=0;i--)if(elements.get(i).getAsJsonObject().getAsJsonObject("faces").isEmpty())elements.remove(i);
            appendRegions(elements,mask,size,true);
            appendRegions(elements,mask,size,false);
        }catch(IOException ignored) { /* Invalid textures are reported by the resource loader. */ }
    }
    private static void appendRegions(JsonArray elements,boolean[][] coverage,int size,boolean tinted) {
            boolean[][] mask=new boolean[size][size];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)mask[y][x]=coverage[y][x]==tinted;
            // Greedy rectangles preserve exact pixel boundaries without one quad per texel.
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(mask[y][x]) {
                int right=x+1;while(right<size&&mask[y][right])right++;
                int bottom=y+1;
                outer:while(bottom<size) {
                    for(int column=x;column<right;column++)if(!mask[bottom][column])break outer;
                    bottom++;
                }
                for(int row=y;row<bottom;row++)Arrays.fill(mask[row],x,right,false);
                double u0=16.0*x/size,u1=16.0*right/size,v0=16.0*y/size,v1=16.0*bottom/size;
                for(String face:List.of("north","south","east","west")) {
                    double[] from=switch(face){
                        case "north"->new double[]{16-u1,16-v1,0};
                        case "south"->new double[]{u0,16-v1,16};
                        case "east"->new double[]{16,16-v1,16-u1};
                        default->new double[]{0,16-v1,u0};
                    };
                    double[] to=switch(face){
                        case "north"->new double[]{16-u0,16-v0,0};
                        case "south"->new double[]{u1,16-v0,16};
                        case "east"->new double[]{16,16-v0,16-u0};
                        default->new double[]{0,16-v0,u1};
                    };
                    var element=new JsonObject();element.add("from",array(from));element.add("to",array(to));
                    var definition=new JsonObject();definition.addProperty("texture",tinted?"#side":"#base");
                    if(tinted)definition.addProperty("tintindex",0);
                    definition.addProperty("cullface",face);
                    definition.add("uv",array(new double[]{u0,v0,u1,v1}));
                    var side=new JsonObject();side.add(face,definition);element.add("faces",side);elements.add(element);
                }
            }
    }
    private static JsonArray array(double[] values){var result=new JsonArray();for(double value:values)result.add(value);return result;}
}
