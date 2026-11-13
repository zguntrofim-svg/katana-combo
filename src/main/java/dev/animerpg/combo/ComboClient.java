package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class ComboClient implements ClientModInitializer {
    /** Register a renderer/animation adapter here. Called on the client thread. */
    public interface AnimationListener {
        void update(UUID player, Identifier animation, int elapsedTicks, int durationTicks);
    }
    public static final Event<AnimationListener> ANIMATION = EventFactory.createArrayBacked(AnimationListener.class,
        listeners -> (player, animation, elapsed, duration) -> {
            for (AnimationListener listener : listeners) listener.update(player, animation, elapsed, duration);
        });
    private static final Map<UUID, ComboPackets.State> STATES = new HashMap<>();
    public static final int RECOVERY_TICKS = 7;
    private static final String[] NAMES = {"horizontal", "reverse_diagonal", "overhead", "jump_counter"};
    @Override public void onInitializeClient() {
        KatanaModel.init();
        DefenseClient.init();
        ClientPlayNetworking.registerGlobalReceiver(ComboPackets.State.ID, (state, context) -> {
            if (state.step() >= 0 && state.step() < ComboTiming.DURATION.length) STATES.put(state.player(), state);
            else {
                var previous=STATES.get(state.player());
                var world=context.client().world;
                // A regular end packet starts visual recovery; an interrupted attack stops immediately.
                if(previous==null || world==null || world.getTime()-previous.started()<ComboTiming.DURATION[previous.step()]-1)
                    stop(state.player());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { STATES.clear(); return; }
            for (var iterator = STATES.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next(); var s = entry.getValue();
                long age = Math.max(0, client.world.getTime() - s.started());
                int duration = ComboTiming.DURATION[s.step()]+RECOVERY_TICKS;
                if (age >= duration) {
                    ANIMATION.invoker().update(s.player(), null, 0, 0); iterator.remove();
                } else ANIMATION.invoker().update(s.player(), Identifier.of("katana_combo", NAMES[s.step()]), (int)age, duration);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (UUID player : Set.copyOf(STATES.keySet())) stop(player);
        });
    }
    public static ComboPackets.State animationState(UUID player) { return STATES.get(player); }
    private static void stop(UUID player) { STATES.remove(player); ANIMATION.invoker().update(player, null, 0, 0); }
    public static boolean interceptAttack(MinecraftClient client) {
        if (client.player == null || !ComboMod.eligible(client.player)) return false;
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return false;
        if (!ClientPlayNetworking.canSend(ComboPackets.Request.ID)) return false;
        ClientPlayNetworking.send(new ComboPackets.Request());
        return true;
    }
}
