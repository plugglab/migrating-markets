package studio.plugglab.migratingmarkets.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import studio.plugglab.migratingmarkets.MigratingMarkets;
import studio.plugglab.migratingmarkets.util.ChatColorUtil;

/** Tells admins about a pending update on join (as in v1.0) and cleans up tracking on quit. */
public class JoinListener implements Listener {

    private final MigratingMarkets plugin;

    public JoinListener(MigratingMarkets plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission("migratingmarkets.admin") || plugin.getUpdateChecker() == null) {
            return;
        }
        String notice = plugin.getUpdateChecker().getAdminNotice();
        if (notice != null) {
            event.getPlayer().sendMessage(ChatColorUtil.section(notice));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getTracker() != null) {
            plugin.getTracker().untrack(event.getPlayer());
        }
    }
}
