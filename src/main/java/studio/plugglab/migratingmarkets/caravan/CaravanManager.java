package studio.plugglab.migratingmarkets.caravan;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import studio.plugglab.migratingmarkets.MigratingMarkets;
import studio.plugglab.migratingmarkets.data.CustomRouteStore;
import studio.plugglab.migratingmarkets.data.DataStore;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.CaravanInventoryHolder;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.market.ShopEditorHolder;
import studio.plugglab.migratingmarkets.util.ChatColorUtil;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Feedback;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the lifecycle of every caravan: spawning its NPC on solid ground,
 * letting it wander (on a leash) while parked, walking it between
 * waypoints over real time, running the market-day clock that drives
 * price/stock resets, and announcing arrivals/departures.
 */
public class CaravanManager {

    /** Movement/upkeep tick period. */
    private static final long MOVE_PERIOD_TICKS = 40L;
    private static final double SECONDS_PER_MOVE_TICK = MOVE_PERIOD_TICKS / 20.0;
    /** Max distance (blocks) the pathfinder is asked to cover per hop — vanilla pathing can't reach far targets. */
    private static final double HOP_DISTANCE = 16.0;
    private static final double ARRIVAL_DISTANCE = 4.0;

    private final MigratingMarkets plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final CustomRouteStore customRouteStore;
    private final LuckyBlockBridge luckyBlockBridge;
    // Copy-on-write: PlaceholderAPI may read this list off the main thread.
    private final List<Caravan> caravans = new CopyOnWriteArrayList<>();
    private final Set<String> customCaravanIds = new HashSet<>();
    private final NamespacedKey caravanTagKey;
    private final Map<String, BossBar> escortBars = new HashMap<>();
    /** Plain caravan names, for recognising untagged NPCs left behind by v1.0. */
    private final Set<String> legacyNames = new HashSet<>();
    private final Map<String, Set<UUID>> escortBarViewers = new HashMap<>();

    private BukkitTask dayTask;
    private BukkitTask movementTask;
    private volatile boolean running;
    private volatile long nextMarketDayAt;

    public CaravanManager(MigratingMarkets plugin, ConfigManager configManager, DataStore dataStore,
                           CustomRouteStore customRouteStore, LuckyBlockBridge luckyBlockBridge) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.customRouteStore = customRouteStore;
        this.luckyBlockBridge = luckyBlockBridge;
        this.caravanTagKey = new NamespacedKey(plugin, "caravan_id");
    }

    public List<Caravan> getCaravans() {
        return caravans;
    }

    public Caravan getById(String id) {
        if (id == null) return null;
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

    /** False after /caravanadmin stop (or before initialize) — markets are closed. */
    public boolean isRunning() {
        return running;
    }

    public NamespacedKey getCaravanTagKey() {
        return caravanTagKey;
    }

    public void initialize() {
        caravans.clear();
        customCaravanIds.clear();
        legacyNames.clear();
        for (ConfigManager.RouteDefinition def : configManager.getRoutes().values()) {
            legacyNames.add(ChatColorUtil.stripToPlain(def.displayName));
        }
        cleanupStrayEntities();
        List<Caravan> loaded = new ArrayList<>();
        for (ConfigManager.RouteDefinition def : configManager.getRoutes().values()) {
            if (def.requiresPlugin != null && !isRequiredPluginPresent(def.requiresPlugin)) {
                plugin.getLogger().warning("Skipping caravan route '" + def.id + "' — requires plugin '"
                        + def.requiresPlugin + "' which isn't installed.");
                continue;
            }
            List<MarketItem> items = configManager.freshItemsFor(def.specialty);
            // Own copy of the waypoint list so runtime changes never leak into the config definition.
            loaded.add(new Caravan(def.id, def.displayName, def.specialty, new ArrayList<>(def.waypoints), items));
        }
        for (ConfigManager.RouteDefinition def : customRouteStore.load()) {
            if (configManager.hasRouteId(def.id)) {
                plugin.getLogger().warning("Custom route '" + def.id
                        + "' has the same id as a config.yml route — skipping the custom one.");
                continue;
            }
            List<MarketItem> items = configManager.freshItemsFor(def.specialty);
            loaded.add(new Caravan(def.id, def.displayName, def.specialty, def.waypoints, items));
            customCaravanIds.add(def.id);
        }
        dataStore.applyTo(loaded);
        caravans.addAll(loaded);
        for (Caravan caravan : caravans) {
            if (caravan.getState() == Caravan.State.TRAVELING) {
                if (caravan.getTravelLocation() == null && caravan.hasWaypoints()) {
                    caravan.setTravelLocation(caravan.getCurrentWaypoint().toRawLocation());
                }
                caravan.setTravelStartedAt(System.currentTimeMillis());
            } else {
                trySpawnMarketEntity(caravan);
            }
        }
        running = true;
        startClocks();
    }

    /**
     * A route's requires-plugin is satisfied if that plugin is enabled — or,
     * when it names the configured lucky-block plugin, if the LuckyBlock API
     * itself is live. The real LuckyBlock jar registers as "LuckyBlock",
     * while the shipped config says "LuckyEngine", so the fortune caravan
     * used to be skipped even with LuckyBlock installed.
     */
    private boolean isRequiredPluginPresent(String name) {
        if (Bukkit.getPluginManager().isPluginEnabled(name)) return true;
        boolean isLuckyName = name.equalsIgnoreCase(configManager.getLuckyPluginName())
                || name.equalsIgnoreCase("LuckyBlock") || name.equalsIgnoreCase("LuckyEngine");
        return isLuckyName && luckyBlockBridge.isAvailable();
    }

    public void shutdown() {
        if (dayTask != null) dayTask.cancel();
        if (movementTask != null) movementTask.cancel();
        dayTask = null;
        movementTask = null;
        running = false;
        closeAllViewers();
        for (Caravan caravan : caravans) {
            if (caravan.getState() == Caravan.State.TRAVELING) {
                Entity entity = getLiveEntity(caravan);
                if (entity != null) caravan.setTravelLocation(entity.getLocation());
            }
            hideEscortBar(caravan.getId());
            despawnEntity(caravan);
        }
        dataStore.save(caravans);
    }

    public void reload() {
        shutdown();
        configManager.load();
        initialize();
    }

    /** Saves state off-thread; call after admin edits so they survive a crash. */
    public void saveSoon() {
        dataStore.saveAsync(caravans);
    }

    // ---------------------------------------------------------------
    // Entity lifecycle
    // ---------------------------------------------------------------

    /** True if this entity is a caravan NPC spawned by this plugin. */
    public boolean isCaravanEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(caravanTagKey, PersistentDataType.STRING);
    }

    /** True if this tagged entity is NOT the live NPC of any current caravan (i.e. a leftover duplicate). */
    public boolean isStrayCaravanEntity(Entity entity) {
        if (isCaravanEntity(entity)) {
            return getByEntityId(entity.getUniqueId()) == null;
        }
        return isLegacyCaravanEntity(entity);
    }

    /**
     * v1.0 spawned caravan villagers as persistent entities with NO tag, so
     * they piled up in the world across restarts and the tag-based cleanup
     * can't see them. Recognise them by their exact caravan name: parked
     * ones were invulnerable, traveling ones carried a " (traveling)" suffix.
     */
    private boolean isLegacyCaravanEntity(Entity entity) {
        if (!(entity instanceof Villager villager) || villager.customName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(villager.customName());
        if (name.endsWith(" (traveling)")) {
            return legacyNames.contains(name.substring(0, name.length() - " (traveling)".length()));
        }
        return villager.isInvulnerable() && legacyNames.contains(name);
    }

    /**
     * Removes any leftover villager tagged as a caravan from every loaded
     * world before spawning fresh ones (the duplicate-caravan fix). NPCs
     * left in chunks that aren't loaded yet are caught by
     * CaravanEntityListener when those chunks load.
     */
    private void cleanupStrayEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isCaravanEntity(entity) || isLegacyCaravanEntity(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleaned up " + removed + " leftover caravan NPC(s) from previous run(s).");
        }
    }

    /** The caravan's live NPC, or null if it isn't spawned / was unloaded / died. */
    public Entity getLiveEntity(Caravan caravan) {
        if (caravan.getEntityId() == null) return null;
        Entity entity = Bukkit.getEntity(caravan.getEntityId());
        if (entity == null || !entity.isValid()) return null;
        return entity;
    }

    /**
     * Spawns the parked NPC at the current stop, but only if that chunk is
     * already loaded. NPCs are non-persistent, so spawning into a chunk
     * that then unloads just deletes them again — and force-loading every
     * stop on startup was a pointless synchronous chunk load. The upkeep
     * tick spawns them as soon as a player loads the area.
     */
    private boolean trySpawnMarketEntity(Caravan caravan) {
        if (!caravan.hasWaypoints() || caravan.getState() != Caravan.State.AT_MARKET) {
            return false;
        }
        if (getLiveEntity(caravan) != null) {
            return true; // already spawned — never stack a second NPC
        }
        Waypoint wp = caravan.getCurrentWaypoint();
        if (!wp.isChunkLoaded()) {
            return false;
        }
        Location loc = wp.toLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Villager villager = spawnVillager(caravan, loc);
        // AI stays ON so the caravan wanders naturally at its stop (on a
        // leash, see upkeepParked) instead of standing frozen.
        villager.setInvulnerable(true);
        villager.customName(caravan.getDisplayNameComponent());
        caravan.setEntityId(villager.getUniqueId());
        return true;
    }

    private Villager spawnVillager(Caravan caravan, Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            // NOT persistent: we fully own this entity's lifecycle (position is
            // saved in caravan-state.yml), so it must never outlive the plugin.
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
            v.getPersistentDataContainer().set(caravanTagKey, PersistentDataType.STRING, caravan.getId());
            v.setCustomNameVisible(true);
            v.setVillagerType(Villager.Type.PLAINS);
        });
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
        nextMarketDayAt = System.currentTimeMillis() + dayPeriodTicks * 50L;
        dayTask = new BukkitRunnable() {
            @Override
            public void run() {
                nextMarketDayAt = System.currentTimeMillis() + dayPeriodTicks * 50L;
                tickMarketDay();
            }
        }.runTaskTimer(plugin, dayPeriodTicks, dayPeriodTicks);

        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickMovement();
            }
        }.runTaskTimer(plugin, MOVE_PERIOD_TICKS, MOVE_PERIOD_TICKS);
    }

    /** Milliseconds until the next market day (restock + stop countdown). */
    public long getMillisToNextMarketDay() {
        return Math.max(0, nextMarketDayAt - System.currentTimeMillis());
    }

    private void tickMarketDay() {
        for (Caravan caravan : caravans) {
            if (!caravan.hasWaypoints()) continue;
            if (caravan.getState() == Caravan.State.AT_MARKET) {
                caravan.incrementDaysAtStop();
                restock(caravan);
                if (caravan.getDaysAtCurrentStop() >= configManager.getDaysPerStop()) {
                    if (caravan.getWaypoints().size() >= 2) {
                        departCaravan(caravan);
                    } else {
                        caravan.resetDaysAtStop(); // single-stop caravan: nowhere to go, just stays open
                    }
                } else if (configManager.isLastDayWarning() && caravan.getWaypoints().size() >= 2
                        && caravan.getDaysAtCurrentStop() == configManager.getDaysPerStop() - 1) {
                    announce(caravan, "last-day", null, null, null);
                }
            }
        }
        dataStore.saveAsync(caravans);
    }

    public void restock(Caravan caravan) {
        for (MarketItem item : caravan.getInventory()) {
            item.restock(configManager.getPriceDecayPerDay());
        }
    }

    private void departCaravan(Caravan caravan) {
        Entity parked = getLiveEntity(caravan);
        Location start = parked != null ? parked.getLocation() : caravan.getCurrentWaypoint().toRawLocation();
        closeViewers(caravan, false);
        caravan.setState(Caravan.State.TRAVELING);
        despawnEntity(caravan);
        caravan.setTravelLocation(start);
        caravan.setTravelStartedAt(System.currentTimeMillis());
        caravan.setStuckTicks(0);
        announce(caravan, "departure", "title-departure", "subtitle-departure", configManager.getDepartureSound());
    }

    /** The public hint for where a departing/traveling caravan is headed. */
    public String getDestinationHint(Caravan caravan) {
        if (!caravan.hasWaypoints()) return "somewhere new";
        return configManager.isVagueHints() ? vagueDirection(caravan) : caravan.getNextWaypoint().getName();
    }

    private String vagueDirection(Caravan caravan) {
        // Raw (configured) coordinates: no terrain lookup, so no sync chunk loads for far stops.
        Location a = caravan.getCurrentWaypoint().toRawLocation();
        Location b = caravan.getNextWaypoint().toRawLocation();
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
            try {
                if (!caravan.hasWaypoints()) continue;
                if (caravan.getState() == Caravan.State.TRAVELING) {
                    tickTravel(caravan);
                } else {
                    upkeepParked(caravan);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Error ticking caravan '" + caravan.getId() + "': " + e);
            }
        }
    }

    /** Respawns a parked NPC that was unloaded/removed, and walks it back if it wandered too far. */
    private void upkeepParked(Caravan caravan) {
        Entity entity = getLiveEntity(caravan);
        if (entity == null) {
            caravan.setEntityId(null);
            trySpawnMarketEntity(caravan);
            return;
        }
        double leash = configManager.getMaxWanderDistance();
        if (leash <= 0 || !(entity instanceof Mob mob)) return;
        Location home = caravan.getCurrentWaypoint().toRawLocation();
        if (home == null || !home.getWorld().equals(entity.getWorld())) return;
        double dist = horizontalDistance(entity.getLocation(), home);
        if (dist > leash * 2) {
            Location ground = caravan.getCurrentWaypoint().toLocation();
            if (ground != null) entity.teleport(ground);
        } else if (dist > leash) {
            Location ground = caravan.getCurrentWaypoint().toLocation();
            if (ground != null) mob.getPathfinder().moveTo(ground, 0.6);
        }
    }

    /**
     * Moves a traveling caravan one step. Previously the villager was told
     * to path straight to a waypoint 150+ blocks away — far beyond what the
     * vanilla pathfinder can plan — so it usually never moved, and if its
     * chunk unloaded (it's non-persistent) the caravan was stuck on the
     * road forever. Now it walks in short hops, gets nudged forward if
     * stuck, keeps travelling "off-screen" at a configurable speed when
     * nobody is nearby, and always arrives within max-travel-minutes.
     */
    private void tickTravel(Caravan caravan) {
        Waypoint destination = caravan.getNextWaypoint();
        Location dest = destination.toRawLocation();
        if (dest == null) return; // destination world not loaded

        Location pos = caravan.getTravelLocation();
        if (pos == null || pos.getWorld() == null) {
            pos = caravan.getCurrentWaypoint().toRawLocation();
            if (pos == null) pos = dest.clone();
        }

        long elapsedMs = System.currentTimeMillis() - caravan.getTravelStartedAt();
        if (!pos.getWorld().equals(dest.getWorld())
                || elapsedMs > configManager.getMaxTravelMinutes() * 60_000L) {
            arrive(caravan);
            return;
        }

        Entity entity = getLiveEntity(caravan);
        if (entity == null) {
            caravan.setEntityId(null);
            if (isChunkLoaded(pos)) {
                entity = spawnTravelingEntity(caravan, groundAt(pos));
            } else {
                // Nobody around: advance the simulated position.
                pos = stepToward(pos, dest, configManager.getOffscreenBlocksPerSecond() * SECONDS_PER_MOVE_TICK);
                caravan.setTravelLocation(pos);
                if (horizontalDistance(pos, dest) < ARRIVAL_DISTANCE) arrive(caravan);
                hideEscortBar(caravan.getId());
                return;
            }
        }

        Location here = entity.getLocation();
        if (horizontalDistance(here, dest) < ARRIVAL_DISTANCE) {
            arrive(caravan);
            return;
        }

        // Stuck detection: barely moved since last tick → nudge forward one hop.
        double moved = caravan.getTravelLocation() == null ? 999
                : (here.getWorld().equals(caravan.getTravelLocation().getWorld())
                        ? horizontalDistance(here, caravan.getTravelLocation()) : 999);
        caravan.setStuckTicks(moved < 0.5 ? caravan.getStuckTicks() + 1 : 0);

        Location hop = stepToward(here, dest, Math.min(HOP_DISTANCE, horizontalDistance(here, dest)));
        if (!isChunkLoaded(hop)) {
            // Walking out of loaded terrain: hand over to the off-screen simulation.
            caravan.setTravelLocation(here);
            despawnEntity(caravan);
            hideEscortBar(caravan.getId());
            return;
        }
        Location hopGround = groundAt(hop);
        if (caravan.getStuckTicks() >= 3) {
            entity.teleport(hopGround);
            caravan.setStuckTicks(0);
            here = hopGround;
        } else if (entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(hopGround, configManager.getTravelSpeed());
        }
        caravan.setTravelLocation(here);
        updateEscortBar(caravan, entity, dest);
    }

    private Entity spawnTravelingEntity(Caravan caravan, Location at) {
        Villager villager = spawnVillager(caravan, at);
        villager.setInvulnerable(false);
        villager.customName(Component.text()
                .append(caravan.getDisplayNameComponent())
                .append(ChatColorUtil.ampersand(configManager.getMessage("traveling-suffix", " &e(traveling)")))
                .build());
        caravan.setEntityId(villager.getUniqueId());
        return villager;
    }

    private void arrive(Caravan caravan) {
        hideEscortBar(caravan.getId());
        despawnEntity(caravan);
        caravan.advanceToNextWaypoint();
        caravan.setState(Caravan.State.AT_MARKET);
        caravan.setTravelLocation(null);
        caravan.setStuckTicks(0);
        trySpawnMarketEntity(caravan);
        announce(caravan, "arrival", "title-arrival", "subtitle-arrival", configManager.getArrivalSound());
    }

    private static Location stepToward(Location from, Location to, double distance) {
        Vector delta = new Vector(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
        double len = delta.length();
        if (len <= distance || len < 1e-6) {
            return new Location(from.getWorld(), to.getX(), from.getY(), to.getZ());
        }
        delta.multiply(distance / len);
        return new Location(from.getWorld(), from.getX() + delta.getX(), from.getY(), from.getZ() + delta.getZ());
    }

    private static Location groundAt(Location loc) {
        World w = loc.getWorld();
        int y = w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        return new Location(w, loc.getX(), y > w.getMinHeight() ? y + 1.0 : loc.getY(), loc.getZ());
    }

    private static boolean isChunkLoaded(Location loc) {
        return loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 0..1 progress along the current leg, for the escort boss bar and placeholders. */
    public double getTravelProgress(Caravan caravan) {
        if (caravan.getState() != Caravan.State.TRAVELING || !caravan.hasWaypoints()) return 0;
        Location from = caravan.getCurrentWaypoint().toRawLocation();
        Location to = caravan.getNextWaypoint().toRawLocation();
        Location pos = caravan.getTravelLocation();
        if (from == null || to == null || pos == null || !pos.getWorld().equals(to.getWorld())) return 0;
        double total = horizontalDistance(from, to);
        if (total < 1) return 1;
        return Math.max(0, Math.min(1, 1 - horizontalDistance(pos, to) / total));
    }

    // ---------------------------------------------------------------
    // Escort boss bar
    // ---------------------------------------------------------------

    private void updateEscortBar(Caravan caravan, Entity entity, Location dest) {
        if (!configManager.isEscortBossBarEnabled()) {
            hideEscortBar(caravan.getId());
            return;
        }
        BossBar bar = escortBars.computeIfAbsent(caravan.getId(), id -> BossBar.bossBar(Component.empty(), 0f,
                parseBarColor(configManager.getEscortBossBarColor()), BossBar.Overlay.PROGRESS));
        int remaining = (int) Math.round(horizontalDistance(entity.getLocation(), dest));
        String template = configManager.getMessage("escort-bossbar",
                "&eEscorting %caravan% &7— &f%distance%m &7to go");
        bar.name(ChatColorUtil.ampersand(fill(template, caravan).replace("%distance%", String.valueOf(remaining))));
        bar.progress((float) getTravelProgress(caravan));

        double radius = configManager.getEscortRadius();
        Set<UUID> viewers = escortBarViewers.computeIfAbsent(caravan.getId(), id -> new HashSet<>());
        Set<UUID> inRange = new HashSet<>();
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(entity.getLocation()) <= radius * radius) {
                inRange.add(p.getUniqueId());
                if (viewers.add(p.getUniqueId())) p.showBossBar(bar);
            }
        }
        viewers.removeIf(uuid -> {
            if (inRange.contains(uuid)) return false;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(bar);
            return true;
        });
    }

    private void hideEscortBar(String caravanId) {
        BossBar bar = escortBars.remove(caravanId);
        Set<UUID> viewers = escortBarViewers.remove(caravanId);
        if (bar == null || viewers == null) return;
        for (UUID uuid : viewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(bar);
        }
    }

    private static BossBar.Color parseBarColor(String raw) {
        try {
            return BossBar.Color.valueOf(raw.toUpperCase());
        } catch (RuntimeException e) {
            return BossBar.Color.YELLOW;
        }
    }

    // ---------------------------------------------------------------
    // Announcements
    // ---------------------------------------------------------------

    private String fill(String template, Caravan caravan) {
        String stop = caravan.hasWaypoints() ? caravan.getCurrentWaypoint().getName() : "?";
        return template
                .replace("%caravan%", caravan.getDisplayName() + "&r")
                .replace("%stop%", stop)
                .replace("%hint%", getDestinationHint(caravan))
                .replace("%days%", String.valueOf(getDaysLeft(caravan)));
    }

    private void announce(Caravan caravan, String chatKey, String titleKey, String subtitleKey, String sound) {
        String chatTemplate = configManager.getMessage("chat-" + chatKey, defaultChat(chatKey));
        Component chat = chatTemplate.isBlank() ? null : ChatColorUtil.ampersand(fill(chatTemplate, caravan));
        Component title = null;
        Component subtitle = null;
        if (titleKey != null && configManager.isAnnounceTitle()) {
            title = ChatColorUtil.ampersand(fill(configManager.getMessage(titleKey, "%caravan%"), caravan));
            subtitle = ChatColorUtil.ampersand(fill(configManager.getMessage(subtitleKey, ""), caravan));
        }
        if (!configManager.isBroadcastServerWide()) {
            return; // settings.broadcast-server-wide: false keeps the server quiet, as before
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("migratingmarkets.notify")) continue;
            if (chat != null) player.sendMessage(chat);
            if (title != null) Feedback.title(player, title, subtitle);
            if (sound != null && configManager.isAnnounceSound()) Feedback.play(player, sound, 0.8f, 1.0f);
        }
        if (chat != null) Bukkit.getConsoleSender().sendMessage(chat);
    }

    private static String defaultChat(String key) {
        return switch (key) {
            case "arrival" -> "&6The %caravan% &ehas arrived at &f%stop%&e! &7Trade while they stay.";
            case "departure" -> "&7The %caravan% &7has packed up and is moving on. &8Word is they're headed %hint%.";
            case "last-day" -> "&7The %caravan% &7packs up after this market day — last chance to trade at &f%stop%&7!";
            default -> "";
        };
    }

    // ---------------------------------------------------------------
    // GUI viewers
    // ---------------------------------------------------------------

    /**
     * Closes every open market GUI (and optionally shop editor) for a
     * caravan. Called when it departs, is edited or deleted, so nobody can
     * keep trading through a stale window with shifted slot indices.
     */
    public void closeViewers(Caravan caravan, boolean includeEditors) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof CaravanInventoryHolder h && h.getCaravan() == caravan) {
                player.closeInventory();
            } else if (includeEditors && holder instanceof ShopEditorHolder h && h.getCaravan() == caravan) {
                player.closeInventory();
            }
        }
    }

    private void closeAllViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof CaravanInventoryHolder || holder instanceof ShopEditorHolder) {
                player.closeInventory();
            }
        }
    }

    // ---------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------

    /** Best-known position of a caravan: live NPC, else its stop / simulated road position. */
    public Location getApproximateLocation(Caravan caravan) {
        if (!caravan.hasWaypoints()) return null;
        if (caravan.getState() == Caravan.State.AT_MARKET) {
            return caravan.getCurrentWaypoint().toRawLocation();
        }
        return caravan.getTravelLocation();
    }

    /** Where to actually stand to trade: the parked NPC if spawned, else the stop itself. */
    public Location getMarketLocation(Caravan caravan) {
        if (!caravan.hasWaypoints() || caravan.getState() != Caravan.State.AT_MARKET) return null;
        Entity entity = getLiveEntity(caravan);
        return entity != null ? entity.getLocation() : caravan.getCurrentWaypoint().toRawLocation();
    }

    /** The nearest caravan currently set up at a stop in the same world, or null. */
    public Caravan getNearestMarket(Location from) {
        Caravan best = null;
        double bestDist = Double.MAX_VALUE;
        for (Caravan caravan : caravans) {
            if (caravan.getState() != Caravan.State.AT_MARKET || !caravan.hasWaypoints()) continue;
            Location loc = caravan.getCurrentWaypoint().toRawLocation();
            if (loc == null || !loc.getWorld().equals(from.getWorld())) continue;
            double d = horizontalDistance(loc, from);
            if (d < bestDist) {
                bestDist = d;
                best = caravan;
            }
        }
        return best;
    }

    public int getDaysLeft(Caravan caravan) {
        return Math.max(0, configManager.getDaysPerStop() - caravan.getDaysAtCurrentStop());
    }

    /** Manually force a caravan to depart immediately (admin command). Returns false if it can't. */
    public boolean forceMove(Caravan caravan) {
        if (caravan.getWaypoints().size() < 2) return false; // needs at least 2 stops to travel between
        if (caravan.getState() != Caravan.State.AT_MARKET) return false;
        departCaravan(caravan);
        return true;
    }

    /** Manually teleport a caravan to a specific waypoint index (admin command). */
    public void teleportToWaypoint(Caravan caravan, int index) {
        closeViewers(caravan, false);
        hideEscortBar(caravan.getId());
        despawnEntity(caravan);
        caravan.setCurrentWaypointIndex(index);
        caravan.setState(Caravan.State.AT_MARKET);
        caravan.setTravelLocation(null);
        caravan.resetDaysAtStop();
        if (!trySpawnMarketEntity(caravan)) {
            // Admin explicitly asked for it — load the stop's chunk and spawn now.
            Location loc = caravan.getCurrentWaypoint().toLocation();
            if (loc != null) {
                loc.getChunk().load();
                trySpawnMarketEntity(caravan);
            }
        }
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

    /** Highest tier title reached (tiers are sorted highest threshold first), or null. */
    public String getEscortTitle(Caravan caravan, UUID player) {
        int rep = caravan.getReputation(player);
        for (var tier : configManager.getEscortTiers()) {
            if (rep >= tier.threshold) {
                return tier.title;
            }
        }
        return null;
    }

    /**
     * A player's customer level at this specific caravan (1 to
     * max-customer-level), based on how much they've bought here.
     * Item levels above this are locked.
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
    // Owner-created routes
    // ---------------------------------------------------------------

    public boolean isCustomCaravan(String id) {
        Caravan c = getById(id);
        return c != null && customCaravanIds.contains(c.getId());
    }

    public List<Caravan> getCustomCaravans() {
        return caravans.stream().filter(c -> customCaravanIds.contains(c.getId())).toList();
    }

    /** True if the id is free to use (not a config.yml route, not an existing caravan) — case-insensitive. */
    public boolean isIdAvailable(String id) {
        return getById(id) == null && !configManager.hasRouteId(id);
    }

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
            caravan.setCurrentWaypointIndex(0);
            caravan.setState(Caravan.State.AT_MARKET);
            caravan.resetDaysAtStop();
            trySpawnMarketEntity(caravan);
        }
        customRouteStore.save(getCustomCaravans());
    }

    /**
     * Removes a waypoint from a custom caravan. Keeps the current index
     * pointing at the same stop when an earlier one is removed, and
     * re-parks the caravan if its current stop (or its route) disappears.
     */
    public void removeWaypoint(Caravan caravan, int index) {
        int current = caravan.getCurrentWaypointIndex();
        caravan.getWaypoints().remove(index);
        int size = caravan.getWaypoints().size();
        if (size == 0) {
            closeViewers(caravan, false);
            hideEscortBar(caravan.getId());
            despawnEntity(caravan);
            caravan.setCurrentWaypointIndex(0);
            caravan.setState(Caravan.State.AT_MARKET);
            caravan.setTravelLocation(null);
        } else {
            boolean currentRemoved = index == current;
            if (index < current) current--;
            if (current >= size) current = 0;
            caravan.setCurrentWaypointIndex(current);
            boolean mustRepark = (currentRemoved && caravan.getState() == Caravan.State.AT_MARKET)
                    || (caravan.getState() == Caravan.State.TRAVELING && size < 2);
            if (mustRepark) {
                closeViewers(caravan, false);
                hideEscortBar(caravan.getId());
                despawnEntity(caravan);
                caravan.setState(Caravan.State.AT_MARKET);
                caravan.setTravelLocation(null);
                caravan.resetDaysAtStop();
                trySpawnMarketEntity(caravan);
            }
        }
        customRouteStore.save(getCustomCaravans());
    }

    /** Permanently removes a custom caravan: despawns it, drops it from the list, and un-persists it. */
    public void deleteCustomCaravan(Caravan caravan) {
        closeViewers(caravan, true);
        hideEscortBar(caravan.getId());
        despawnEntity(caravan);
        caravans.remove(caravan);
        customCaravanIds.remove(caravan.getId());
        dataStore.forget(caravan.getId());
        customRouteStore.save(getCustomCaravans());
        saveSoon();
    }
}
