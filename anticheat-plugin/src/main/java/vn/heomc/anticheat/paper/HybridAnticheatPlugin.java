package vn.heomc.anticheat.paper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.messaging.PluginMessageListener;
import vn.heomc.anticheat.core.BoundedEvidenceBuffer;
import vn.heomc.anticheat.core.Challenge;
import vn.heomc.anticheat.core.ChallengeVerifier;
import vn.heomc.anticheat.core.MonotonicClock;
import vn.heomc.anticheat.core.EvidenceRecord;
import vn.heomc.anticheat.core.MovementExemption;

/** MVP observe-only adapter. Không kick/ban và không gọi Bukkit từ packet thread. */
public final class HybridAnticheatPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    public static final String CHANNEL = "hac:response";
    private ChallengeVerifier verifier;
    private BoundedEvidenceBuffer evidence;
    private final ConcurrentMap<UUID, Long> lastResponseNanos = new ConcurrentHashMap<>();
    private MovementPacketAdapter movementAdapter;
    private BukkitTask movementTickTask;
    private AtomicLong movementServerTick;

    @Override public void onEnable() {
        saveDefaultConfig();
        loadRuntimeConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "hac:challenge");
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("hac").setExecutor(new HacCommand(this));
        registerPacketObserveHook();
        getLogger().info("HybridAnticheat enabled enforcement=OBSERVE packet-hook="
                + (movementAdapter == null ? "disabled" : "observe") + " packet-dependency="
                + (getServer().getPluginManager().getPlugin("packetevents") != null ? "present" : "absent"));
    }

    @Override public void onDisable() {
        unregisterPacketObserveHook();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "hac:challenge");
        if (verifier != null) verifier.clear();
        lastResponseNanos.clear();
    }

    private void registerPacketObserveHook() {
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().warning("PacketEvents unavailable; movement observe hook disabled");
            return;
        }
        movementServerTick = new AtomicLong();
        movementAdapter = new MovementPacketAdapter(this, evidence, movementServerTick,
                positiveMovementDouble("movement.horizontal-allowance-per-tick", 0.8),
                positiveMovementDouble("movement.vertical-allowance-per-tick", 0.9),
                positiveMovementDouble("movement.max-coordinate-magnitude", 30_000_000.0),
                positiveMovementLong("movement.max-elapsed-ticks", 20),
                positiveMovementLong("movement.teleport-confirm-timeout-ticks", 40),
                positiveMovementLong("movement.velocity-grace-ticks", 3),
                positiveMovementDouble("movement.max-violation-score", 10),
                positiveMovementDouble("movement.violation-increment", 1),
                nonNegativeMovementDouble("movement.violation-decay-per-tick", 0.05));
        movementTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            movementServerTick.incrementAndGet();
            if (movementAdapter != null) movementAdapter.processTick();
        }, 1L, 1L);
        movementAdapter.start();
        getLogger().info("PacketEvents movement observe hook registered; enforcement=OBSERVE");
    }

    private void unregisterPacketObserveHook() {
        if (movementTickTask != null) {
            movementTickTask.cancel();
            movementTickTask = null;
        }
        movementServerTick = null;
        if (movementAdapter != null) {
            movementAdapter.stop();
            movementAdapter = null;
        }
    }

    void loadRuntimeConfig() {
        reloadConfig();
        String configuredEnforcement = getConfig().getString("enforcement", "OBSERVE");
        if (!"OBSERVE".equalsIgnoreCase(configuredEnforcement)) {
            getLogger().warning("Only OBSERVE is supported in MVP; configured enforcement was ignored: "
                    + configuredEnforcement);
        }
        long ttl = positiveConfig("challenge-ttl-millis", 10000);
        int payloadBytes = positiveConfig("max-payload-bytes", 4096);
        int evidenceRecords = positiveConfig("max-evidence-records", 256);
        verifier = new ChallengeVerifier(new MonotonicClock(), ttl, payloadBytes, 128);
        evidence = new BoundedEvidenceBuffer(evidenceRecords);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (movementAdapter != null) movementAdapter.open(player.getUniqueId());
        Challenge challenge = verifier.open(player.getUniqueId(), UUID.randomUUID());
        byte[] payload = ("v1|" + challenge.connectionId() + "|" + challenge.nonce()).getBytes(StandardCharsets.UTF_8);
        player.sendPluginMessage(this, "hac:challenge", payload);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (movementAdapter != null) movementAdapter.close(playerId);
        UUID connectionId = verifier.connectionId(playerId);
        if (connectionId != null) verifier.close(playerId, connectionId);
        lastResponseNanos.remove(playerId);
    }

    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled() && movementAdapter != null) {
            movementAdapter.resetAndClearTeleport(event.getPlayer().getUniqueId(), MovementExemption.TELEPORT);
        }
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (movementAdapter != null) {
            movementAdapter.resetAndClearTeleport(event.getPlayer().getUniqueId(), MovementExemption.RESPAWN);
        }
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (movementAdapter != null) {
            movementAdapter.resetAndClearTeleport(event.getPlayer().getUniqueId(), MovementExemption.WORLD_CHANGE);
        }
    }

    @EventHandler public void onVelocity(PlayerVelocityEvent event) {
        var velocity = event.getVelocity();
        boolean finite = Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY())
                && Double.isFinite(velocity.getZ());
        boolean nonZero = velocity.lengthSquared() > 0.0;
        if (!event.isCancelled() && finite && nonZero && movementAdapter != null) {
            movementAdapter.resetWithWindow(event.getPlayer().getUniqueId(), MovementExemption.VELOCITY);
        }
    }

    @EventHandler public void onVehicleEnter(VehicleEnterEvent event) {
        if (!event.isCancelled() && event.getEntered() instanceof Player player && movementAdapter != null) {
            movementAdapter.setVehicleMode(player.getUniqueId(), true);
        }
    }

    @EventHandler public void onVehicleExit(VehicleExitEvent event) {
        if (!event.isCancelled() && event.getExited() instanceof Player player && movementAdapter != null) {
            movementAdapter.setVehicleMode(player.getUniqueId(), false);
        }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        long now = System.nanoTime();
        Long previous = lastResponseNanos.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 50_000_000L) {
            return;
        }
        var result = verifier.acceptWire(player.getUniqueId(), message);
        if (result.acceptance() == vn.heomc.anticheat.core.Acceptance.ACCEPTED) {
            getLogger().info("Verifier response accepted; enforcement=OBSERVE");
        } else {
            evidence.add(EvidenceRecord.of(player.getUniqueId(), result.acceptance().name(), result.reason()));
        }
    }

    private int positiveConfig(String key, int fallback) {
        int value = getConfig().getInt(key, fallback);
        if (value < 1) {
            getLogger().warning("Invalid config " + key + "; using fallback " + fallback);
            return fallback;
        }
        return value;
    }

    private long positiveConfig(String key, long fallback) {
        long value = getConfig().getLong(key, fallback);
        if (value < 1) {
            getLogger().warning("Invalid config " + key + "; using fallback " + fallback);
            return fallback;
        }
        return value;
    }

    private double positiveMovementDouble(String key, double fallback) {
        double value = getConfig().getDouble(key, fallback);
        if (!Double.isFinite(value) || value <= 0) {
            getLogger().warning("Invalid movement config " + key + "; using fallback " + fallback);
            return fallback;
        }
        return value;
    }

    private double nonNegativeMovementDouble(String key, double fallback) {
        double value = getConfig().getDouble(key, fallback);
        if (!Double.isFinite(value) || value < 0) {
            getLogger().warning("Invalid movement config " + key + "; using fallback " + fallback);
            return fallback;
        }
        return value;
    }

    private long positiveMovementLong(String key, long fallback) {
        long value = getConfig().getLong(key, fallback);
        if (value < 1) {
            getLogger().warning("Invalid movement config " + key + "; using fallback " + fallback);
            return fallback;
        }
        return value;
    }

    int sessionCount() { return verifier == null ? 0 : verifier.sessionCount(); }
    int evidenceCount() { return evidence.size(); }
    long movementPacketsCaptured() { return movementAdapter == null ? 0 : movementAdapter.packetsCaptured(); }
    long movementSamplesProcessed() { return movementAdapter == null ? 0 : movementAdapter.samplesProcessed(); }
    long movementSignalsEmitted() { return movementAdapter == null ? 0 : movementAdapter.signalsEmitted(); }
    long movementFlyingPacketsObserved() { return movementAdapter == null ? 0 : movementAdapter.flyingPacketsObserved(); }
    long movementInvalidPacketsRejected() { return movementAdapter == null ? 0 : movementAdapter.invalidPacketsRejected(); }
    long movementTeleportConfirmsObserved() { return movementAdapter == null ? 0 : movementAdapter.teleportConfirmsObserved(); }
    long movementVehiclePacketsObserved() { return movementAdapter == null ? 0 : movementAdapter.vehiclePacketsObserved(); }
    long movementSuppressedByTeleport() { return movementAdapter == null ? 0 : movementAdapter.movementSuppressedByTeleport(); }
    long movementSuppressedByVehicle() { return movementAdapter == null ? 0 : movementAdapter.movementSuppressedByVehicle(); }
    long movementStatusPacketsObserved() { return movementAdapter == null ? 0 : movementAdapter.movementStatusPacketsObserved(); }
    String enforcement() { return "OBSERVE"; }
    void reloadConfigOnly() {
        reloadConfig();
        getLogger().info("Config reloaded; verifier sessions and evidence preserved. Runtime limits require restart.");
    }
}
