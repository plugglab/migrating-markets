package studio.plugglab.migratingmarkets.caravan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import studio.plugglab.migratingmarkets.MigratingMarkets;
import studio.plugglab.migratingmarkets.data.DataStore;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns the lifecycle of every caravan: spawning its NPC on solid ground,
 * letting it wander naturally while parked, walking it between waypoints
 * over real time, running the market-day clock that drives price/stock
 * resets, and announcing arrivals/departures with properly colored chat.
 */
public class CaravanManager {

    private final MigratingMarkets plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final List<Caravan> caravans = new ArrayList<>();

    private BukkitTask dayTask;
    private BukkitTask movementTask;

    public CaravanManager(MigratingMarkets plugin, ConfigManager configManager, DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
    }

    public List<Caravan> getCaravans() {
        return caravans;
    }

    public Caravan getById(String id) {
        for (Caravan c : caravans) {
            if (c.getId().equalsIgnoreCase(id)) return c;
        }
        return null;
    }

    /** Finds the caravan whose NPC entity this UUID belongs to, if any. */
    public Caravan getByEntityId(UUID entityId) {
        for (Caravan c : caravans) {
            if (entityId.equals(c.getEntityId())) return c;
        }
        return null;
    }

    public void initialize() {
        caravans.clear();
        for (ConfigManager.RouteDefinition def : configManager.getRoutes().values()) {
            if (def.requiresPlugin != null && Bukkit.getPluginManager().getPlugin(def.requiresPlugin) == null) {
                plugin.getLogger().warning("Skipping caravan route '" + def.id + "' — requires plugin '"
                        + def.requiresPlugin + "' which isn't installed.");
                continue;
            }
            List<MarketItem> items = configManager.freshItemsFor(def.specialty);
            Caravan caravan = new Caravan(def.id, def.displayName, def.specialty, def.waypoints, items);
            caravans.add(caravan);
        }
        dataStore.applyTo(caravans);
        for (Caravan caravan : caravans) {
            spawnEntity(caravan);
        }
        startClocks();
    }

    public void shutdown() {
        if (dayTask != null) dayTask.cancel();
        if (movementTask != null) movementTask.cancel();
        for (Caravan caravan : caravans) {
            despawnEntity(caravan);
        }
        dataStore.save(caravans);
    }

    public void reload() {
        shutdown();
        configManager.load();
        initialize();
    }

    // ---------------------------------------------------------------
    // Entity lifecycle
    // ---------------------------------------------------------------

    private void spawnEntity(Caravan caravan) {
        Waypoint wp = caravan.getCurrentWaypoint();
        Location loc = wp.toLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn caravan '" + caravan.getId() + "': world not loaded.");
            return;
        }
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        // AI stays ON so the caravan wanders naturally at its stop instead
        // of standing frozen — it just won't path off toward the next
        // waypoint until we flip it into TRAVELING state.
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.customName(caravan.getDisplayNameComponent());
        villager.setCustomNameVisible(true);
        villager.setVillagerType(Villager.Type.PLAINS);
        caravan.setEntityId(villager.getUniqueId());
    }

    private void despawnEntity(Caravan caravan) {
        if (caravan.getEntityId() == null) return;
        var entity = Bukkit.getEntity(caravan.getEntityId());
        if (entity != null) entity.remove();
        caravan.setEntityId(null);
    }

    // ---------------------------------------------------------------
    // Clocks
    // ---------------------------------------------------------------

    private void startClocks() {
        long dayPeriodTicks = 20L * 60L * configManager.getMinutesPerMarketDay();
        dayTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickMarketDay();
            }
        }.runTaskTimer(plugin, dayPeriodTicks, dayPeriodTicks);

        // Movement is animated in small steps every few seconds while TRAVELING,
        // independent from the (much slower) market-day clock.
        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickMovement();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    private void tickMarketDay() {
        for (Caravan caravan : caravans) {
            if (caravan.getState() == Caravan.State.AT_MARKET) {
                caravan.incrementDaysAtStop();
                restock(caravan);
                if (caravan.getDaysAtCurrentStop() >= configManager.getDaysPerStop()) {
                    departCaravan(caravan);
                }
            }
        }
        dataStore.save(caravans);
    }

    private void restock(Caravan caravan) {
        for (MarketItem item : caravan.getInventory()) {
            item.restock(configManager.getPriceDecayPerDay());
        }
    }

    private void departCaravan(Caravan caravan) {
        caravan.setState(Caravan.State.TRAVELING);
        despawnEntity(caravan);
        Waypoint next = caravan.getNextWaypoint();
        String hint = configManager.isVagueHints() ? vagueDirection(caravan) : next.getName();
        Component message = Component.text()
                .append(Component.text("The "))
                .append(caravan.getDisplayNameComponent())
                .append(Component.text(" has packed up and is moving on. ").color(NamedTextColor.GRAY))
                .append(Component.text("Word is they're headed " + hint + ".").color(NamedTextColor.DARK_GRAY))
                .build();
        broadcast(message);
    }

    private String vagueDirection(Caravan caravan) {
        Waypoint from = caravan.getCurrentWaypoint();
        Waypoint to = caravan.getNextWaypoint();
        Location a = from.toLocation();
        Location b = to.toLocation();
        if (a == null || b == null) return "somewhere new";
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        String ns = dz < 0 ? "north" : "south";
        String ew = dx > 0 ? "east" : "west";
        if (Math.abs(dz) < 32) return "due " + ew;
        if (Math.abs(dx) < 32) return "due " + ns;
        return ns + "-" + ew;
    }

    private void tickMovement() {
        for (Caravan caravan : caravans) {
            if (caravan.getState() != Caravan.State.TRAVELING) continue;

            Waypoint destination = caravan.getNextWaypoint();
            Location dest = destination.toLocation();
            if (dest == null) continue;

            if (caravan.getEntityId() == null) {
                // Mid-journey placeholder entity so escorts have something to defend.
                Location start = caravan.getCurrentWaypoint().toLocation();
                if (start == null) continue;
                var world = start.getWorld();
                Villager villager = (Villager) world.spawnEntity(start, EntityType.VILLAGER);
                villager.setInvulnerable(false);
                villager.setPersistent(true);
                Component travelingName = Component.text()
                        .append(caravan.getDisplayNameComponent())
                        .append(Component.text(" (traveling)").color(NamedTextColor.YELLOW))
                        .build();
                villager.customName(travelingName);
                villager.setCustomNameVisible(true);
                caravan.setEntityId(villager.getUniqueId());
            }

            var entity = Bukkit.getEntity(caravan.getEntityId());
            if (!(entity instanceof Mob mob)) continue;

            if (mob.getLocation().distanceSquared(dest) < 6) {
                caravan.advanceToNextWaypoint();
                caravan.setState(Caravan.State.AT_MARKET);
                mob.remove();
                caravan.setEntityId(null);
                spawnEntity(caravan);
                announceArrival(caravan);
                continue;
            }

            mob.getPathfinder().moveTo(dest, 1.0);
        }
    }

    private void announceArrival(Caravan caravan) {
        Component message = Component.text()
                .append(Component.text("The ").color(NamedTextColor.GOLD))
                .append(caravan.getDisplayNameComponent())
                .append(Component.text(" has arrived at ").color(NamedTextColor.YELLOW))
                .append(Component.text(caravan.getCurrentWaypoint().getName()).color(NamedTextColor.WHITE))
                .append(Component.text("! Trade while they stay.").color(NamedTextColor.GRAY))
                .build();
        broadcast(message);
    }

    private void broadcast(Component message) {
        if (configManager.isBroadcastServerWide()) {
            Bukkit.broadcast(message);
        }
    }

    /** Manually force a caravan to depart immediately (admin command). */
    public void forceMove(Caravan caravan) {
        if (caravan.getState() == Caravan.State.AT_MARKET) {
            departCaravan(caravan);
        }
    }

    /** Manually teleport a caravan to a specific waypoint index (admin command). */
    public void teleportToWaypoint(Caravan caravan, int index) {
        despawnEntity(caravan);
        caravan.setCurrentWaypointIndex(index);
        caravan.setState(Caravan.State.AT_MARKET);
        caravan.resetDaysAtStop();
        spawnEntity(caravan);
    }

    public double getEscortDiscount(Caravan caravan, UUID player) {
        int rep = caravan.getReputation(player);
        double best = 0;
        for (var tier : configManager.getEscortTiers()) {
            if (rep >= tier.threshold) {
                best = Math.max(best, tier.discount);
            }
        }
        return best;
    }

    public String getEscortTitle(Caravan caravan, UUID player) {
        int rep = caravan.getReputation(player);
        String title = null;
        for (var tier : configManager.getEscortTiers()) {
            if (rep >= tier.threshold) {
                title = tier.title;
                break;
            }
        }
        return title;
    }
}
