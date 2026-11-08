package studio.plugglab.migratingmarkets.caravan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import studio.plugglab.migratingmarkets.MigratingMarkets;
import studio.plugglab.migratingmarkets.data.CustomRouteStore;
import studio.plugglab.migratingmarkets.data.DataStore;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final CustomRouteStore customRouteStore;
    private final List<Caravan> caravans = new ArrayList<>();
    private final Set<String> customCaravanIds = new HashSet<>();
    private final NamespacedKey caravanTagKey;

    private BukkitTask dayTask;
    private BukkitTask movementTask;

    public CaravanManager(MigratingMarkets plugin, ConfigManager configManager, DataStore dataStore,
                           CustomRouteStore customRouteStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.customRouteStore = customRouteStore;
        this.caravanTagKey = new NamespacedKey(plugin, "caravan_id");
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
        customCaravanIds.clear();
        cleanupStrayEntities();
        for (ConfigManager.RouteDefinition def : configManager.getRoutes().values()) {
            if (def.requiresPlugin != null && !Bukkit.getPluginManager().isPluginEnabled(def.requiresPlugin)) {
                plugin.getLogger().warning("Skipping caravan route '" + def.id + "' — requires plugin '"
                        + def.requiresPlugin + "' which isn't installed.");
                continue;
            }
            List<MarketItem> items = configManager.freshItemsFor(def.specialty);
            Caravan caravan = new Caravan(def.id, def.displayName, def.specialty, def.waypoints, items);
            caravans.add(caravan);
        }
        for (ConfigManager.RouteDefinition def : customRouteStore.load()) {
            if (configManager.getRoutes().containsKey(def.id)) {
                plugin.getLogger().warning("Custom route '" + def.id
                        + "' has the same id as a config.yml route — skipping the custom one.");
                continue;
            }
            List<MarketItem> items = configManager.freshItemsFor(def.specialty);
            Caravan caravan = new Caravan(def.id, def.displayName, def.specialty, def.waypoints, items);
            caravans.add(caravan);
            customCaravanIds.add(def.id);
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

    /**
     * Removes any leftover villager tagged as a caravan from every loaded
     * world before spawning fresh ones. This is the fix for caravans
     * "spawning infinitely": entities used to be marked persistent with
     * no tag and no cleanup, so any restart that skipped a clean
     * onDisable (crash, /stop mid-tick, /reload) left the old NPCs saved
     * in the world forever while initialize() spawned new ones on top —
     * duplicates piled up across restarts. Entities are no longer
     * persistent either (see spawnEntity), so this is now mostly a
     * one-time cleanup for anyone upgrading from an older build.
     */
    private void cleanupStrayEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(caravanTagKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleaned up " + removed + " leftover caravan NPC(s) from previous run(s).");
        }
    }

    private void spawnEntity(Caravan caravan) {
        if (caravan.getWaypoints().isEmpty()) {
            return; // newly created custom route with no waypoints yet
        }
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
        // NOT persistent: we fully own this entity's lifecycle (spawn/despawn
        // tied to plugin state, position saved separately in caravan-state.yml)
        // so it must never survive outside the plugin managing it — that's
        // exactly what caused duplicate caravans to pile up across restarts.
        villager.setPersistent(false);
        villager.getPersistentDataContainer().set(caravanTagKey, PersistentDataType.STRING, caravan.getId());
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
            if (caravan.getWaypoints().isEmpty()) continue;
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
            if (caravan.getWaypoints().isEmpty()) continue;
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
                villager.setPersistent(false);
                villager.getPersistentDataContainer().set(caravanTagKey, PersistentDataType.STRING, caravan.getId());
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
        if (caravan.getWaypoints().size() < 2) return; // needs at least 2 stops to travel between
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

    /**
     * A player's customer level at this specific caravan (1 to
     * max-customer-level), based on how much they've bought here.
     * Item levels above this are locked — this is what actually gates
     * item access; previously nothing did, so any player could buy any
     * level-5 item on their very first visit.
     */
    public int getCustomerLevel(Caravan caravan, UUID player) {
        int purchases = caravan.getPurchaseCount(player);
        int perLevel = Math.max(1, configManager.getPurchasesPerCustomerLevel());
        int level = 1 + (purchases / perLevel);
        return Math.min(configManager.getMaxCustomerLevel(), level);
    }

    /** Purchases still needed to reach the next customer level (0 if already at max). */
    public int getPurchasesToNextLevel(Caravan caravan, UUID player) {
        int currentLevel = getCustomerLevel(caravan, player);
        if (currentLevel >= configManager.getMaxCustomerLevel()) {
            return 0;
        }
        int perLevel = Math.max(1, configManager.getPurchasesPerCustomerLevel());
        int purchases = caravan.getPurchaseCount(player);
        int nextThreshold = currentLevel * perLevel;
        return Math.max(0, nextThreshold - purchases);
    }

    // ---------------------------------------------------------------
    // Owner-created routes (server owners building their own caravans
    // in-game, separate from anything defined in config.yml)
    // ---------------------------------------------------------------

    public boolean isCustomCaravan(String id) {
        return customCaravanIds.contains(id);
    }

    public List<Caravan> getCustomCaravans() {
        return caravans.stream().filter(c -> customCaravanIds.contains(c.getId())).toList();
    }

    /** True if the id is free to use (not a config.yml route, not an existing custom one). */
    public boolean isIdAvailable(String id) {
        return getById(id) == null && !configManager.getRoutes().containsKey(id);
    }

    /**
     * Creates a new owner-built caravan with no waypoints yet — it won't
     * actually spawn an NPC until addWaypoint gives it somewhere to be.
     * `specialty` can be any known specialty from config.yml for a
     * starter inventory, or anything else (e.g. "custom") for an empty
     * shop the owner fills in entirely through the editor GUI.
     */
    public Caravan createCustomCaravan(String id, String displayName, String specialty) {
        List<MarketItem> items = configManager.freshItemsFor(specialty);
        Caravan caravan = new Caravan(id, displayName, specialty, new ArrayList<>(), items);
        caravans.add(caravan);
        customCaravanIds.add(id);
        customRouteStore.save(getCustomCaravans());
        return caravan;
    }

    /** Adds a waypoint to a custom caravan, spawning it immediately if this is its first stop. */
    public void addWaypoint(Caravan caravan, Waypoint waypoint) {
        boolean wasEmpty = caravan.getWaypoints().isEmpty();
        caravan.getWaypoints().add(waypoint);
        if (wasEmpty) {
            spawnEntity(caravan);
        }
        customRouteStore.save(getCustomCaravans());
    }

    /** Removes a waypoint from a custom caravan, despawning it if none are left. */
    public void removeWaypoint(Caravan caravan, int index) {
        caravan.getWaypoints().remove(index);
        if (caravan.getWaypoints().isEmpty()) {
            despawnEntity(caravan);
            caravan.setCurrentWaypointIndex(0);
        } else if (caravan.getCurrentWaypointIndex() >= caravan.getWaypoints().size()) {
            caravan.setCurrentWaypointIndex(0);
        }
        customRouteStore.save(getCustomCaravans());
    }

    /** Permanently removes a custom caravan: despawns it, drops it from the list, and un-persists it. */
    public void deleteCustomCaravan(Caravan caravan) {
        despawnEntity(caravan);
        caravans.remove(caravan);
        customCaravanIds.remove(caravan.getId());
        customRouteStore.save(getCustomCaravans());
    }
}
