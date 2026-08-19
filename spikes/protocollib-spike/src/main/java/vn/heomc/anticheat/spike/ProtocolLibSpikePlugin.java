package vn.heomc.anticheat.spike;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable probe. Not production security code. */
public final class ProtocolLibSpikePlugin extends JavaPlugin {
    private PacketAdapter adapter;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().warning("SPIKE_PROTOCOLLIB_UNAVAILABLE");
            return;
        }
        adapter = new PacketAdapter(this, ListenerPriority.MONITOR) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                getLogger().info("SPIKE_PROTOCOLLIB_PACKET_PATH_READY type="
                        + event.getPacketType().name());
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
        getLogger().info("SPIKE_PROTOCOLLIB enabled version=5.4.0");
    }

    @Override
    public void onDisable() {
        if (adapter != null && ProtocolLibrary.getProtocolManager() != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        }
    }
}
