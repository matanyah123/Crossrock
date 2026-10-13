package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockWidgetTranslatorTest {
    @Test void combinedTitleIsPreservedAndJavaEditionOverlayIsCleared() throws Exception {
        var store=new InMemoryGeneratedResourceStore();var errors=new ArrayList<String>();
        byte[] title=png(256,44);
        BedrockWidgetTranslator.translate(Map.of("textures/ui/title.png",title),store,errors);
        assertTrue(errors.isEmpty(),errors.toString());
        var logo=ImageIO.read(new ByteArrayInputStream(data(store,"textures/gui/title/minecraft.png")));
        assertEquals(256,logo.getWidth());
        assertEquals(64,logo.getHeight());
        assertEquals(0xff884411,logo.getRGB(128,43));
        assertEquals(0,logo.getRGB(128,44));
        var edition=ImageIO.read(new ByteArrayInputStream(data(store,"textures/gui/title/edition.png")));
        assertEquals(0,edition.getRGB(64,8));
    }
    @Test void explicitEditionWinsAndNoTitleMeansNoTitleOverride() throws Exception {
        var store=new InMemoryGeneratedResourceStore();var errors=new ArrayList<String>();
        byte[] edition=png(128,16);
        BedrockWidgetTranslator.translate(Map.of("textures/ui/title.png",png(256,44),"textures/ui/edition.png",edition),store,errors);
        var converted=ImageIO.read(new ByteArrayInputStream(data(store,"textures/gui/title/edition.png")));
        assertEquals(0xff884411,converted.getRGB(64,13));
        assertEquals(0,converted.getRGB(64,14));
        var empty=new InMemoryGeneratedResourceStore();
        BedrockWidgetTranslator.translate(Map.of("textures/ui/checkbox_check.png",png(10,10)),empty,errors);
        assertNull(data(empty,"textures/gui/title/minecraft.png"));
        assertNull(data(empty,"textures/gui/title/edition.png"));
    }
    @Test void translatesAsymmetricBedrockNineSliceToJavaMetadata() throws Exception {
        var store=new InMemoryGeneratedResourceStore();var errors=new ArrayList<String>();
        byte[] png=png(18,18);
        BedrockWidgetTranslator.translate(Map.of("textures/ui/button_borderless_light.png",png,
            "textures/ui/button_borderless_light.json",bytes("""
            {"base_size":[9,9],"nineslice_size":[1,2,3,4]}
            """)),store,errors);
        assertTrue(errors.isEmpty(),errors.toString());
        assertArrayEquals(png,data(store,"textures/gui/sprites/widget/button.png"));
        var scaling=JsonParser.parseString(new String(data(store,"textures/gui/sprites/widget/button.png.mcmeta"),StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
        assertEquals("nine_slice",scaling.get("type").getAsString());
        assertEquals(9,scaling.get("width").getAsInt());
        assertEquals(3,scaling.getAsJsonObject("border").get("right").getAsInt());
        assertEquals(4,scaling.getAsJsonObject("border").get("bottom").getAsInt());
        assertFalse(scaling.has("nineslice_size"));
    }
    @Test void malformedWidgetDoesNotBreakOtherWidgets() throws Exception {
        var store=new InMemoryGeneratedResourceStore();var errors=new ArrayList<String>();
        BedrockWidgetTranslator.translate(Map.of("textures/ui/button_borderless_light.png",png(9,9),
            "textures/ui/button_borderless_light.json",bytes("{\"base_size\":[4,4],\"nineslice_size\":3}"),
            "textures/ui/checkbox_check.png",png(10,10),
            "textures/ui/slider_background.png",png(3,3),
            "textures/ui/book_arrowleft_default.png",png(16,16)),store,errors);
        assertEquals(1,errors.size());
        assertNull(data(store,"textures/gui/sprites/widget/button.png"));
        for(String name:List.of("checkbox_selected","slider","page_backward"))
            assertNotNull(data(store,"textures/gui/sprites/widget/"+name+".png"));
        assertNotNull(data(store,"textures/gui/sprites/recipe_book/page_backward.png"));
    }
    @Test void brightButtonsBecomeReadableButOtherWidgetsKeepTheirArtwork() throws Exception {
        var source=new BufferedImage(9,9,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<9;y++)for(int x=0;x<9;x++)source.setRGB(x,y,0xffcccccc);
        source.setRGB(0,0,0);
        var encoded=new ByteArrayOutputStream();ImageIO.write(source,"PNG",encoded);
        var store=new InMemoryGeneratedResourceStore();var errors=new ArrayList<String>();
        BedrockWidgetTranslator.translate(Map.of("textures/ui/button_borderless_light.png",encoded.toByteArray(),
            "textures/ui/checkbox_space.png",encoded.toByteArray()),store,errors);
        assertTrue(errors.isEmpty(),errors.toString());
        var corrected=ImageIO.read(new ByteArrayInputStream(data(store,"textures/gui/sprites/widget/button.png")));
        int channel=corrected.getRGB(4,4)&255;
        double c=channel/255.0, luminance=c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);
        assertTrue(1.05/(luminance+.05)>=4.5,"White label needs readable contrast");
        assertEquals(0,corrected.getRGB(0,0)>>>24);
        assertArrayEquals(encoded.toByteArray(),data(store,"textures/gui/sprites/widget/checkbox.png"));
    }
    private static byte[] data(InMemoryGeneratedResourceStore store,String path){return store.snapshot().resources().get(Identifier.withDefaultNamespace(path));}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static byte[] png(int w,int h)throws Exception {
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,0xff884411);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
}
