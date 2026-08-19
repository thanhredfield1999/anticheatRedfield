package vn.heomc.anticheat.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

final class HacCommand implements CommandExecutor {
    private final HybridAnticheatPlugin plugin;
    HacCommand(HybridAnticheatPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("HybridAnticheat enforcement=" + plugin.enforcement() + " sessions=" + plugin.sessionCount()
                    + " evidence=" + plugin.evidenceCount() + " movementPackets=" + plugin.movementPacketsCaptured()
                    + " movementSamples=" + plugin.movementSamplesProcessed()
                    + " movementSignals=" + plugin.movementSignalsEmitted()
                    + " flyingPackets=" + plugin.movementFlyingPacketsObserved()
                    + " statusPackets=" + plugin.movementStatusPacketsObserved()
                    + " invalidPackets=" + plugin.movementInvalidPacketsRejected()
                    + " teleportConfirms=" + plugin.movementTeleportConfirmsObserved()
                    + " vehiclePackets=" + plugin.movementVehiclePacketsObserved()
                    + " teleportSuppressed=" + plugin.movementSuppressedByTeleport()
                    + " vehicleSuppressed=" + plugin.movementSuppressedByVehicle());
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigOnly();
            sender.sendMessage("HybridAnticheat config reloaded; active verifier sessions preserved. Runtime limits require restart.");
            return true;
        }
        sender.sendMessage("Usage: /hac [status|reload]");
        return true;
    }
}
