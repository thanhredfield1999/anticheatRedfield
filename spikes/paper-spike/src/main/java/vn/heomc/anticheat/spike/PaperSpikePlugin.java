package vn.heomc.anticheat.spike;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable probe. Not production security code. */
public final class PaperSpikePlugin extends JavaPlugin implements Listener, PluginMessageListener {
    static final String CHANNEL = "heomc:verifier";
    private static final int NONCE_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SPIKE_ONLY enabled; plugin-message phase is post-join play path.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        challenges.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        challenges.put(player.getUniqueId(), new Challenge(encoded, System.currentTimeMillis()));
        byte[] payload = ("challenge|" + encoded + "|" + player.getUniqueId())
                .getBytes(StandardCharsets.UTF_8);
        player.sendPluginMessage(this, CHANNEL, payload);
        getLogger().info("SPIKE_CHALLENGE player=" + player.getUniqueId()
                + " bytes=" + nonce.length + " phase=POST_JOIN_PLAY_UNVERIFIED");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        challenges.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length > 4096) return;
        String text = new String(message, StandardCharsets.UTF_8);
        String prefix = "response|";
        if (!text.startsWith(prefix)) {
            getLogger().warning("SPIKE_RESPONSE_REJECT player=" + player.getUniqueId()
                    + " reason=UNKNOWN_FORMAT");
            return;
        }
        String response = text.substring(prefix.length());
        Challenge challenge = challenges.get(player.getUniqueId());
        boolean matches = challenge != null && response.equals(challenge.nonce());
        long age = challenge == null ? -1L : System.currentTimeMillis() - challenge.issuedAtMillis();
        getLogger().info("SPIKE_RESPONSE player=" + player.getUniqueId()
                + " matches=" + matches + " ageMillis=" + age
                + " signal=CLIENT_PAYLOAD_ONLY_NOT_ATTESTATION");
        if (matches) challenges.remove(player.getUniqueId(), challenge);
    }

    private record Challenge(String nonce, long issuedAtMillis) {
    }
}
