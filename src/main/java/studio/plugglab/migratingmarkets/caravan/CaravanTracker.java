package studio.plugglab.migratingmarkets.caravan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Feedback;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * /market where — guides a player to a caravan that's set up at a stop
 * with a live action-bar compass (direction arrow relative to where
 * they're facing + distance), and optionally points their compass at it.
 * Only parked caravans can be tracked; traveling ones stay a mystery.
 */
public class CaravanTracker {

    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private record Session(String caravanId, long endsAt) {
    }

    private final Plugin plugin;
    private final CaravanManager caravanManager;
    private final ConfigManager configManager;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    public CaravanTracker(Plugin plugin, CaravanManager caravanManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.caravanManager = caravanManager;
        this.configManager = configManager;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        sessions.clear();
    }

    public void track(Player player, Caravan caravan) {
        sessions.put(player.getUniqueId(), new Session(caravan.getId(),
                System.currentTimeMillis() + configManager.getTrackingDurationSeconds() * 1000L));
        Location target = caravanManager.getMarketLocation(caravan);
        if (target != null && configManager.isTrackingSetCompass()
                && target.getWorld().equals(player.getWorld())) {
            player.setCompassTarget(target);
        }
        tick(player, sessions.get(player.getUniqueId()));
    }

    public boolean untrack(Player player) {
        return sessions.remove(player.getUniqueId()) != null;
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !tick(player, entry.getValue())) {
                it.remove();
            }
        }
    }

    /** Returns false when the session should end. */
    private boolean tick(Player player, Session session) {
        if (System.currentTimeMillis() > session.endsAt()) {
            return false;
        }
        Caravan caravan = caravanManager.getById(session.caravanId());
        if (caravan == null || caravan.getState() != Caravan.State.AT_MARKET) {
            player.sendActionBar(Component.text("The caravan you were tracking has moved on.").color(NamedTextColor.GRAY));
            return false;
        }
        Location target = caravanManager.getMarketLocation(caravan);
        if (target == null) return false;
        if (!target.getWorld().equals(player.getWorld())) {
            player.sendActionBar(Component.text().append(caravan.getDisplayNameComponent())
                    .append(Component.text(" is in another world (" + target.getWorld().getName() + ")")
                            .color(NamedTextColor.GRAY)).build());
            return true;
        }
        Location here = player.getLocation();
        double distance = CaravanManager.horizontalDistance(here, target);
        if (distance <= configManager.getInteractionRadius() + 2) {
            player.sendActionBar(Component.text().append(caravan.getDisplayNameComponent())
                    .append(Component.text(" — you've arrived! Right-click the merchant to trade.")
                            .color(NamedTextColor.GREEN)).build());
            Feedback.play(player, "minecraft:entity.experience_orb.pickup", 0.6f, 1.4f);
            return false;
        }

        // Bearing to target relative to the player's facing direction.
        double angleToTarget = Math.toDegrees(Math.atan2(-(target.getX() - here.getX()), target.getZ() - here.getZ()));
        double relative = ((angleToTarget - here.getYaw()) % 360 + 360) % 360;
        String arrow = ARROWS[(int) Math.round(relative / 45.0) % 8];

        Component bar = Component.text()
                .append(Component.text(arrow + " ").color(NamedTextColor.GOLD))
                .append(caravan.getDisplayNameComponent())
                .append(Component.text(" " + Math.round(distance) + "m").color(NamedTextColor.WHITE))
                .append(configManager.isTrackingShowCoordinates()
                        ? Component.text(" (" + target.getBlockX() + ", " + target.getBlockZ() + ")").color(NamedTextColor.DARK_GRAY)
                        : Component.empty())
                .build();
        player.sendActionBar(bar);
        return true;
    }
}
