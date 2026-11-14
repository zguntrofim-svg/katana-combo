package dev.animerpg.combo;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
public final class EffectClient {
    private static final Map<UUID,EffectStyle> STYLES=new HashMap<>();
    private static EffectStyle selected=EffectStyle.AZURE;
    private static boolean pendingF11,chord;
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir().resolve("katana-combo-effects.properties");
    public static void init(){
        if(Files.isRegularFile(CONFIG))try(var reader=Files.newBufferedReader(CONFIG,StandardCharsets.UTF_8)){
            Properties p=new Properties();p.load(reader);selected=EffectStyle.valueOf(p.getProperty("style","AZURE"));
        }catch(Exception e){LoggerFactory.getLogger("katana_combo").warn("Cannot read trail preference; using Azure Flash",e);}
        ClientPlayNetworking.registerGlobalReceiver(EffectNetwork.State.ID,(state,context)->STYLES.put(state.player(),EffectStyle.get(state.style())));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->send());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{STYLES.clear();pendingF11=false;chord=false;});
    }
    public static EffectStyle selected(){return selected;}
    public static EffectStyle forPlayer(UUID id){
        var client=MinecraftClient.getInstance();
        return STYLES.getOrDefault(id,client.player!=null && client.player.getUuid().equals(id)?selected:EffectStyle.AZURE);
    }
    public static void select(EffectStyle style){
        selected=style;var client=MinecraftClient.getInstance();if(client.player!=null)STYLES.put(client.player.getUuid(),style);
        try{
            Files.createDirectories(CONFIG.getParent());Path temp=Files.createTempFile(CONFIG.getParent(),"katana-effects-",".tmp");
            try{Files.writeString(temp,"style="+style.name()+"\n",StandardCharsets.UTF_8);Files.move(temp,CONFIG,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
        }catch(java.io.IOException e){LoggerFactory.getLogger("katana_combo").warn("Could not save trail preference",e);}
        send();
    }
    private static void send(){if(ClientPlayNetworking.canSend(EffectNetwork.Choice.ID))ClientPlayNetworking.send(new EffectNetwork.Choice(selected.ordinal()));}
    public static boolean key(long window,int key,int scan,int action){
        var client=MinecraftClient.getInstance();
        if(client.getWindow()==null || window!=client.getWindow().getHandle())return false;
        if(key!=GLFW.GLFW_KEY_F11 && key!=GLFW.GLFW_KEY_F12)return false;
        if(action==GLFW.GLFW_RELEASE){
            if(key==GLFW.GLFW_KEY_F11 && pendingF11){pendingF11=false;if(!chord){client.getWindow().toggleFullscreen();client.options.getFullscreen().setValue(client.getWindow().isFullscreen());}return true;}
            if(!InputUtil.isKeyPressed(window,GLFW.GLFW_KEY_F11) && !InputUtil.isKeyPressed(window,GLFW.GLFW_KEY_F12))chord=false;
            return chord;
        }
        boolean canOpen=client.player!=null && (client.currentScreen==null || client.currentScreen instanceof EffectScreen);
        if(action==GLFW.GLFW_PRESS && canOpen && ((key==GLFW.GLFW_KEY_F11 && InputUtil.isKeyPressed(window,GLFW.GLFW_KEY_F12))
                || (key==GLFW.GLFW_KEY_F12 && (pendingF11 || InputUtil.isKeyPressed(window,GLFW.GLFW_KEY_F11))))){
            if(!chord){chord=true;pendingF11=false;if(client.currentScreen instanceof EffectScreen)client.setScreen(null);else client.setScreen(new EffectScreen());}
            return true;
        }
        // Defer the default F11 fullscreen action until release so either chord order works.
        if(key==GLFW.GLFW_KEY_F11 && canOpen && client.options.fullscreenKey.matchesKey(key,scan)){
            if(action==GLFW.GLFW_PRESS)pendingF11=true;
            return true;
        }
        return chord;
    }
    private EffectClient(){}
}
