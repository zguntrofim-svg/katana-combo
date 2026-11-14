package dev.animerpg.combo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
public final class EffectScreen extends Screen {
    private EffectStyle draft=EffectClient.selected();
    public EffectScreen(){super(Text.literal("KATANA / SLASH STUDIO"));}
    @Override protected void init(){
        int columns=width>=520?3:2,rows=6/columns;
        int panel=Math.min(900,width-28),left=(width-panel)/2,gap=8;
        int cardWidth=(panel-gap*(columns-1))/columns;
        int cardHeight=Math.max(32,(height-103-gap*(rows-1))/rows);
        for(var style:EffectStyle.values()){
            int index=style.ordinal(),x=left+(index%columns)*(cardWidth+gap),y=49+(index/columns)*(cardHeight+gap);
            addDrawableChild(new PressableWidget(x,y,cardWidth,cardHeight,Text.literal(style.title)){
                public void onPress(){draft=style;}
                @Override protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
                @Override protected void renderWidget(DrawContext draw,int mouseX,int mouseY,float delta){
                    int border=draft==style?0xff000000|style.edge:(isHovered()||isFocused()?0xffa780cd:0xff493454);
                    draw.fill(getX()-1,getY()-1,getX()+getWidth()+1,getY()+getHeight()+1,border);
                    draw.fillGradient(getX(),getY(),getX()+getWidth(),getY()+getHeight(),draft==style?0xf02a183e:0xf0181522,0xf00c0b14);
                    draw.fill(getX(),getY(),getX()+3,getY()+getHeight(),0xff000000|style.color);
                    draw.drawTextWithShadow(textRenderer,String.format("%02d",index+1),getX()+9,getY()+7,0xff000000|style.edge);
                    String name=textRenderer.trimToWidth(style.title,getWidth()-44);
                    draw.drawTextWithShadow(textRenderer,name,getX()+30,getY()+7,0xfff1e8ff);
                    preview(draw,style,getX()+8,getY()+21,getWidth()-16,Math.max(9,getHeight()-(getHeight()>70?43:25)));
                    if(getHeight()>70)draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(style.description,getWidth()-18),getX()+9,getY()+getHeight()-16,0xffb8abc8);
                }
            });
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Применить"),button->{EffectClient.select(draft);close();}).dimensions(width/2-104,height-27,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),button->close()).dimensions(width/2+4,height-27,100,20).build());
    }
    @Override public void render(DrawContext draw,int x,int y,float delta){
        draw.fillGradient(0,0,width,height,0xf20a0812,0xf21a0c26);
        draw.fill(14,13,width-14,14,0xff75419b);
        draw.drawCenteredTextWithShadow(textRenderer,"KATANA / SLASH STUDIO",width/2,21,0xffecd8ff);
        draw.drawCenteredTextWithShadow(textRenderer,"Выбери след своего клинка",width/2,35,0xffaa92bc);
        super.render(draw,x,y,delta);
        draw.drawCenteredTextWithShadow(textRenderer,"F12 + F11  ·  "+draft.title,width/2,height-41,0xffc4afd4);
    }
    private static void preview(DrawContext draw,EffectStyle style,int x,int y,int w,int h){
        double time=System.nanoTime()/1e9;
        int layers=style==EffectStyle.PHANTOM?3:style==EffectStyle.VOID?2:1;
        for(int layer=layers-1;layer>=0;layer--){
            int px=0,py=0;
            for(int i=0;i<=72;i++){
                float t=i/72f;
                int xx=x+(int)(w*(.06+.88*t));
                double curve=Math.sin(t*Math.PI)*.30;
                if(style==EffectStyle.VOID)curve+=Math.sin(t*16+time*.8)*.045;
                int yy=y+(int)(h*(.72-curve))-(int)(layer*h*.11);
                int thickness=Math.max(1,(int)(h*.12*Math.sin(Math.PI*t)*(1-t*.5)));
                float fade=(float)Math.sin(t*Math.PI);int alpha=(int)(fade*(layer==0?185:65));
                int color=(alpha<<24)|style.color;
                draw.fill(xx,yy-thickness,xx+Math.max(2,w/65),yy+thickness+1,color);
                if(layer==0){
                    draw.fill(xx,yy-thickness,xx+Math.max(2,w/65),yy-thickness+1,((int)(fade*245)<<24)|style.edge);
                    if(style==EffectStyle.CRIMSON)draw.fill(xx,y+h-(yy-y)-1,xx+Math.max(2,w/65),y+h-(yy-y)+1,((int)(fade*220)<<24)|style.color);
                    if(style==EffectStyle.STORM && i%3==0 && i>8 && i<65)line(draw,xx,yy,xx+5,yy-(i%2==0?7:3),0xbbaedfff);
                }
                px=xx;py=yy;
            }
        }
        if(style==EffectStyle.MOON || style==EffectStyle.VOID){
            for(int i=0;i<4;i++){
                int xx=x+w*(i+1)/5,yy=y+(int)(h*(.25+.12*Math.sin(time+i)));
                draw.fill(xx-1,yy-2,xx+1,yy+2,0x99ffffff&0xff000000|style.edge);
            }
        }
    }
    private static void line(DrawContext draw,int x,int y,int xx,int yy,int color){
        int steps=Math.max(Math.abs(xx-x),Math.abs(yy-y));
        for(int i=0;i<=steps;i++){int px=x+(xx-x)*i/Math.max(1,steps),py=y+(yy-y)*i/Math.max(1,steps);draw.fill(px,py,px+1,py+1,color);}
    }
}
