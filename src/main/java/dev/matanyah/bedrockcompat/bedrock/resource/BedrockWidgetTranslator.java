package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generic Bedrock art mappings only: Java retains widget layout, input and accessibility. */
final class BedrockWidgetTranslator {
    private record Widget(String source, String target, int baseWidth, int baseHeight, int border) {}
    private static final List<Widget> WIDGETS = List.of(
        new Widget("button_borderless_light", "widget/button", 4, 4, 1),
        new Widget("button_borderless_lighthover", "widget/button_highlighted", 4, 4, 1),
        new Widget("button_borderless_lightpressednohover", "widget/button_disabled", 4, 4, 1),
        new Widget("checkbox_space", "widget/checkbox", 0, 0, 0),
        new Widget("checkbox_spacehover", "widget/checkbox_highlighted", 0, 0, 0),
        new Widget("checkbox_check", "widget/checkbox_selected", 0, 0, 0),
        new Widget("checkbox_checkhover", "widget/checkbox_selected_highlighted", 0, 0, 0),
        new Widget("slider_background", "widget/slider", 3, 3, 1),
        new Widget("slider_background_hover", "widget/slider_highlighted", 3, 3, 1),
        new Widget("slider_button_default", "widget/slider_handle", 6, 6, 2),
        new Widget("slider_button_hover", "widget/slider_handle_highlighted", 6, 6, 2),
        new Widget("book_arrowleft_default", "widget/page_backward", 0, 0, 0),
        new Widget("book_arrowleft_hover", "widget/page_backward_highlighted", 0, 0, 0),
        new Widget("book_arrowright_default", "widget/page_forward", 0, 0, 0),
        new Widget("book_arrowright_hover", "widget/page_forward_highlighted", 0, 0, 0),
        new Widget("close_button_default", "widget/cross_button", 0, 0, 0),
        new Widget("close_button_hover", "widget/cross_button_highlighted", 0, 0, 0),
        new Widget("recipe_book_light_button", "recipe_book/button", 0, 0, 0),
        new Widget("recipe_book_button_borderless_lighthover", "recipe_book/button_highlighted", 0, 0, 0),
        new Widget("book_arrowleft_default", "recipe_book/page_backward", 0, 0, 0),
        new Widget("book_arrowleft_hover", "recipe_book/page_backward_highlighted", 0, 0, 0),
        new Widget("book_arrowright_default", "recipe_book/page_forward", 0, 0, 0),
        new Widget("book_arrowright_hover", "recipe_book/page_forward_highlighted", 0, 0, 0),
        new Widget("craft_toggle_off", "recipe_book/filter_disabled", 0, 0, 0),
        new Widget("craft_toggle_off_hover", "recipe_book/filter_disabled_highlighted", 0, 0, 0),
        new Widget("craft_toggle_on", "recipe_book/filter_enabled", 0, 0, 0),
        new Widget("craft_toggle_on_hover", "recipe_book/filter_enabled_highlighted", 0, 0, 0),
        new Widget("furnace_toggle_off", "recipe_book/furnace_filter_disabled", 0, 0, 0),
        new Widget("furnace_toggle_off_hover", "recipe_book/furnace_filter_disabled_highlighted", 0, 0, 0),
        new Widget("furnace_toggle_on", "recipe_book/furnace_filter_enabled", 0, 0, 0),
        new Widget("furnace_toggle_on_hover", "recipe_book/furnace_filter_enabled_highlighted", 0, 0, 0)
    );
    private BedrockWidgetTranslator() {}

    static void translate(Map<String,byte[]> content, GeneratedResourceSink sink, List<String> errors) {
        title(content,sink,errors);
        for(Widget widget:WIDGETS) {
            String path="textures/ui/"+widget.source()+".png";
            byte[] png=content.get(path);
            if(png==null)continue;
            try {
                BufferedImage image=decode(png);
                JsonObject metadata=scaling(content,widget,image);
                String target="textures/gui/sprites/"+widget.target()+".png";
                if(widget.target().startsWith("widget/button")) {
                    image=readableButton(image,widget.target().endsWith("_disabled")?.04:.16);
                    png=encode(image);
                }
                sink.put(Identifier.withDefaultNamespace(target),png);
                sink.put(Identifier.withDefaultNamespace(target+".mcmeta"),metadata.toString().getBytes(StandardCharsets.UTF_8));
            }catch(IOException|RuntimeException ex){errors.add("Widget "+widget.source()+": "+ex.getMessage());}
        }
    }
    private static void title(Map<String,byte[]> content,GeneratedResourceSink sink,List<String> errors) {
        byte[] png=content.get("textures/ui/title.png");
        if(png!=null)try {
            BufferedImage image=titleCanvas(decode(png),256,64,44);
            byte[] javaTitle=encode(image);
            sink.put(Identifier.withDefaultNamespace("textures/gui/title/minecraft.png"),javaTitle);
            sink.put(Identifier.withDefaultNamespace("textures/gui/title/minceraft.png"),javaTitle);
            // Bedrock packs own their whole title composition. Never place Java's extra subtitle
            // on top of integrated pack branding; no pack names or artwork coordinates are assumed.
            if(!content.containsKey("textures/ui/edition.png")) {
                var out=new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(128,16,BufferedImage.TYPE_INT_ARGB),"PNG",out);
                sink.put(Identifier.withDefaultNamespace("textures/gui/title/edition.png"),out.toByteArray());
            }
        }catch(IOException|RuntimeException ex){errors.add("Title graphic: "+ex.getMessage());}
        byte[] edition=content.get("textures/ui/edition.png");
        if(edition!=null)try{
            sink.put(Identifier.withDefaultNamespace("textures/gui/title/edition.png"),encode(titleCanvas(decode(edition),128,16,14)));
        }catch(IOException|RuntimeException ex){errors.add("Edition graphic: "+ex.getMessage());}
    }
    /** LogoRenderer samples a 256x44 window from a 256x64 texture, not the whole PNG. */
    private static BufferedImage titleCanvas(BufferedImage image,int width,int height,int visibleHeight) {
        int scale=Math.max(1,Math.min(image.getWidth()/width,image.getHeight()/visibleHeight));
        int canvasWidth=width*scale,windowHeight=visibleHeight*scale;
        double ratio=Math.min((double)canvasWidth/image.getWidth(),(double)windowHeight/image.getHeight());
        int w=Math.max(1,(int)Math.round(image.getWidth()*ratio)),h=Math.max(1,(int)Math.round(image.getHeight()*ratio));
        BufferedImage canvas=new BufferedImage(canvasWidth,height*scale,BufferedImage.TYPE_INT_ARGB);
        int left=(canvasWidth-w)/2;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            canvas.setRGB(left+x,y,image.getRGB(x*image.getWidth()/w,y*image.getHeight()/h));
        return canvas;
    }
    /** Java controls retain white/gray labels; Bedrock's light artwork expects dark labels.
     * Limit luminance only when needed, preserving transparency, hue, and existing dark themes. */
    private static BufferedImage readableButton(BufferedImage image,double ceiling) {
        double maximum=0;
        int x0=image.getWidth()/3,y0=image.getHeight()/3;
        for(int y=y0;y<image.getHeight()-y0;y++)for(int x=x0;x<image.getWidth()-x0;x++) {
            int pixel=image.getRGB(x,y);
            if((pixel>>>24)<128)continue;
            maximum=Math.max(maximum,.2126*linear(pixel>>16&255)+.7152*linear(pixel>>8&255)+.0722*linear(pixel&255));
        }
        if(maximum<=ceiling)return image;
        double factor=ceiling/maximum;
        BufferedImage out=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int pixel=image.getRGB(x,y);
            out.setRGB(x,y,(pixel&0xff000000)|srgb(linear(pixel>>16&255)*factor)<<16
                |srgb(linear(pixel>>8&255)*factor)<<8|srgb(linear(pixel&255)*factor));
        }
        return out;
    }
    private static double linear(int component){double c=component/255.0;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
    private static int srgb(double component){return (int)Math.round(255*(component<=.0031308?component*12.92:1.055*Math.pow(component,1/2.4)-.055));}
    private static byte[] encode(BufferedImage image)throws IOException {
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
    private static JsonObject scaling(Map<String,byte[]> content,Widget widget,BufferedImage image) {
        JsonObject root=new JsonObject(),gui=new JsonObject(),scaling=new JsonObject();
        root.add("gui",gui);gui.add("scaling",scaling);
        byte[] bytes=content.get("textures/ui/"+widget.source()+".json");
        JsonObject descriptor=bytes==null?null:JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        boolean sliced=descriptor!=null?descriptor.has("nineslice_size"):widget.baseWidth()>0;
        if(!sliced){scaling.addProperty("type","stretch");return root;}
        int w=widget.baseWidth()>0?widget.baseWidth():image.getWidth();
        int h=widget.baseHeight()>0?widget.baseHeight():image.getHeight();
        if(descriptor!=null&&descriptor.has("base_size")) {
            var base=descriptor.getAsJsonArray("base_size");
            if(base.size()!=2)throw new IllegalArgumentException("Expected two base_size values");
            w=base.get(0).getAsInt();h=base.get(1).getAsInt();
        }
        int[] edges={widget.border(),widget.border(),widget.border(),widget.border()};
        if(descriptor!=null&&descriptor.has("nineslice_size")) {
            var border=descriptor.get("nineslice_size");
            if(border.isJsonArray()&&border.getAsJsonArray().size()!=4)
                throw new IllegalArgumentException("Expected four nine-slice borders");
            for(int i=0;i<4;i++)edges[i]=border.isJsonArray()?border.getAsJsonArray().get(i).getAsInt():border.getAsInt();
        }
        if(w<=0||h<=0||Arrays.stream(edges).anyMatch(edge->edge<0)||edges[0]+edges[2]>=w||edges[1]+edges[3]>=h)
            throw new IllegalArgumentException("Invalid widget nine-slice dimensions");
        scaling.addProperty("type","nine_slice");scaling.addProperty("width",w);scaling.addProperty("height",h);
        JsonObject border=new JsonObject();
        for(int i=0;i<4;i++)border.addProperty(new String[]{"left","top","right","bottom"}[i],edges[i]);
        scaling.add("border",border);
        return root;
    }
    private static BufferedImage decode(byte[] bytes)throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
        if(image==null)throw new IOException("Invalid PNG");
        return image;
    }
}
