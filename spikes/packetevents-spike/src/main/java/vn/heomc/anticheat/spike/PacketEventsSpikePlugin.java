package vn.heomc.anticheat.spike;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable probe. Not production security code. */
public final class PacketEventsSpikePlugin extends JavaPlugin {
    private PacketListenerAbstract listener;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().warning("SPIKE_PACKETEVENTS_UNAVAILABLE");
            return;
        }
        listener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                    logPosition(event, new WrapperPlayClientPlayerPosition(event).getPosition());
                } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    WrapperPlayClientPlayerPositionAndRotation packet =
                            new WrapperPlayClientPlayerPositionAndRotation(event);
                    logPosition(event, packet.getPosition());
                }
            }

            @Override
            public void onPacketSend(PacketSendEvent event) {
                getLogger().info("SPIKE_PACKETEVENTS_PACKET_PATH_READY type="
                        + event.getPacketType().getName());
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        getLogger().info("SPIKE_PACKETEVENTS enabled version=2.13.0 receive-movement=registered");
    }

    private void logPosition(PacketReceiveEvent event, Vector3d position) {
        User user = event.getUser();
        getLogger().info("SPIKE_PACKETEVENTS_MOVEMENT_CALLBACK player="
                + (user == null ? "unknown" : user.getUUID())
                + " finite=" + (Double.isFinite(position.getX())
                && Double.isFinite(position.getY()) && Double.isFinite(position.getZ())));
    }

    @Override
    public void onDisable() {
        if (listener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        }
    }
}
