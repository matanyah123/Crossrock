package dev.matanyah.bedrockcompat.client.ui.json;

import dev.matanyah.bedrockcompat.bedrock.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Uses Java's widget focus/input dispatch; never installs a second screen input listener. */
public final class NativeJsonUiButton extends AbstractWidget {
    private BedrockUiLayout.Draw draw;
    private final Consumer<String> execute;
    private boolean pressed;
    NativeJsonUiButton(BedrockUiLayout.Draw draw,Consumer<String> execute){super(0,0,0,0,Component.empty());this.execute=execute;update(draw);}
    void update(BedrockUiLayout.Draw draw){this.draw=draw;var b=draw.bounds();setRectangle((int)Math.round(b.width()),(int)Math.round(b.height()),(int)Math.round(b.x()),(int)Math.round(b.y()));setMessage(Component.translatable(BedrockUiLayout.string(draw.properties(),"text","")));active=BedrockUiLayout.bool(draw.properties(),"enabled",true)&&draw.alpha()>0;visible=draw.alpha()>0;if(!active){pressed=false;setFocused(false);}}
    public String path(){return draw.properties().get("__path").getAsString();}
    String state(){return pressed?"pressed":isHoveredOrFocused()?"hover":"default";}
    @Override public boolean isMouseOver(double x,double y){return visible&&BedrockUiInteraction.hit(draw,x,y);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.button()!=0||!isMouseOver(event.x(),event.y()))return false;
        String action=BedrockUiInteraction.action(draw.properties(),false);if(action.isEmpty())return false;
        pressed=true;execute.accept(action);return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean captured=pressed&&event.button()==0;if(event.button()==0)pressed=false;return captured;}
    @Override public boolean keyPressed(KeyEvent event){
        if(!active||!visible||!isFocused()||!BedrockUiLayout.bool(draw.properties(),"focus_enabled",true)||event.key()!=257&&event.key()!=32&&event.key()!=335)return false;
        String action=BedrockUiInteraction.action(draw.properties(),true);if(action.isEmpty())return false;execute.accept(action);return true;
    }
    @Override public void setFocused(boolean value){super.setFocused(value&&BedrockUiLayout.bool(draw.properties(),"focus_enabled",true));}
    @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event){return BedrockUiLayout.bool(draw.properties(),"focus_enabled",true)?super.nextFocusPath(event):null;}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        isHovered=isMouseOver(x,y);
        // Imported state children supply authored visuals. Otherwise use an explicit Java-style fallback.
        if(draw.properties().containsKey("default_control"))return;
        var c=draw.clip();g.enableScissor((int)Math.floor(c.x()),(int)Math.floor(c.y()),(int)Math.ceil(c.x()+c.width()),(int)Math.ceil(c.y()+c.height()));
        try{int opacity=(int)Math.round(draw.alpha()*255)<<24;g.fill(getX(),getY(),getRight(),getBottom(),opacity|(!active?0x333333:isHoveredOrFocused()?0x999999:0x666666));g.text(Minecraft.getInstance().font,getMessage(),getX()+4,getY()+(height-9)/2,opacity|(active?0xffffff:0xaaaaaa));}finally{g.disableScissor();}
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
}
