package studio.plugglab.migratingmarkets.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.market.MarketItem;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    public static class RouteDefinition {
        public String id;
        public String displayName;
        public String specialty;
        public String requiresPlugin; // nullable — route is skipped if this plugin isn't installed
        public List<Waypoint> waypoints = new ArrayList<>();
    }

    private static class ItemTemplate {
        Material material;
        double basePrice;
        String category;
        int level;
        String giveCommand; // nullable
        String luckyRarity; // nullable
    }

    /**
     * Sections whose individual keys are merged into an existing
     * config.yml when missing (see migrateMissingKeys). Routes and
     * specialties are deliberately NOT here: merging those would bring
     * back routes/items an owner intentionally deleted.
     */
    private static final List<String> MIGRATABLE_SECTIONS = List.of(
            "settings", "announcements", "tracking", "escort-bossbar", "gui", "update-checker");

    private final JavaPlugin plugin;

    private int minutesPerMarketDay;
    private int daysPerStop;
    private double interactionRadius;
    private double escortRadius;
    private boolean broadcastServerWide;
    private boolean vagueHints;
    private double priceShiftPerTrade;
    private double priceDecayPerDay;
    private double maxPriceMultiplier;
    private double minPriceMultiplier;
    private int stockPerLevel;
    private int purchasesPerCustomerLevel;
    private int maxCustomerLevel;
    private double sellPriceRatio;
    private boolean allowSellingDamaged;
    private double maxWanderDistance;
    private double travelSpeed;
    private double offscreenBlocksPerSecond;
    private int maxTravelMinutes;

    private boolean announceTitle;
    private boolean announceSound;
    private boolean lastDayWarning;
    private String arrivalSound;
    private String departureSound;
    private final Map<String, String> messages = new HashMap<>();

    private boolean trackingEnabled;
    private int trackingDurationSeconds;
    private boolean trackingShowCoordinates;
    private boolean trackingSetCompass;

    private boolean escortBossBarEnabled;
    private String escortBossBarColor;

    private boolean guiSounds;
    private Material guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private String buySound;
    private String sellSound;
    private String denySound;

    private boolean updateCheckerEnabled;

    private String economyProvider = "emerald";

    private String luckyPluginName = "LuckyEngine";
    private String luckyGiveCommandTemplate = "lb give %player% %amount%";

    private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();
    private final Map<String, List<ItemTemplate>> specialtyTemplates = new LinkedHashMap<>();
    private final List<EscortTier> escortTiers = new ArrayList<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        migrateMissingKeys(cfg);

        // Every value is read with an in-code default (and sanity-clamped),
        // so a config.yml with a missing or partial `settings:` section can
        // never produce zeros — previously a missing section left
        // minutes-per-market-day at 0, scheduling a 0-tick timer.
        minutesPerMarketDay = Math.max(1, cfg.getInt("settings.minutes-per-market-day", 20));
        daysPerStop = Math.max(1, cfg.getInt("settings.days-per-stop", 3));
        interactionRadius = Math.max(1, cfg.getDouble("settings.interaction-radius", 4));
        escortRadius = Math.max(1, cfg.getDouble("settings.escort-radius", 12));
        broadcastServerWide = cfg.getBoolean("settings.broadcast-server-wide", true);
        vagueHints = cfg.getBoolean("settings.vague-destination-hints", true);
        priceShiftPerTrade = Math.max(0, cfg.getDouble("settings.price-shift-per-trade", 0.02));
        priceDecayPerDay = Math.max(0, cfg.getDouble("settings.price-decay-per-day", 0.05));
        maxPriceMultiplier = Math.max(1.0, cfg.getDouble("settings.max-price-multiplier", 2.5));
        minPriceMultiplier = Math.min(1.0, Math.max(0.01, cfg.getDouble("settings.min-price-multiplier", 0.4)));
        stockPerLevel = Math.max(1, cfg.getInt("settings.stock-per-level", 6));
        purchasesPerCustomerLevel = Math.max(1, cfg.getInt("settings.purchases-per-customer-level", 8));
        maxCustomerLevel = Math.max(1, cfg.getInt("settings.max-customer-level", 5));
        sellPriceRatio = Math.max(0, Math.min(1.0, cfg.getDouble("settings.sell-price-ratio", 0.5)));
        allowSellingDamaged = cfg.getBoolean("settings.allow-selling-damaged-items", false);
        maxWanderDistance = Math.max(0, cfg.getDouble("settings.max-wander-distance", 10));
        travelSpeed = Math.max(0.1, cfg.getDouble("settings.travel-speed", 1.0));
        offscreenBlocksPerSecond = Math.max(0.1, cfg.getDouble("settings.offscreen-blocks-per-second", 1.2));
        maxTravelMinutes = Math.max(1, cfg.getInt("settings.max-travel-minutes", 20));

        announceTitle = cfg.getBoolean("announcements.title", true);
        announceSound = cfg.getBoolean("announcements.sound", true);
        lastDayWarning = cfg.getBoolean("announcements.last-day-warning", true);
        arrivalSound = cfg.getString("announcements.arrival-sound", "minecraft:entity.villager.celebrate");
        departureSound = cfg.getString("announcements.departure-sound", "minecraft:block.note_block.bell");
        messages.clear();
        ConfigurationSection msgSection = cfg.getConfigurationSection("announcements.messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, msgSection.getString(key, ""));
            }
        }

        trackingEnabled = cfg.getBoolean("tracking.enabled", true);
        trackingDurationSeconds = Math.max(5, cfg.getInt("tracking.duration-seconds", 30));
        trackingShowCoordinates = cfg.getBoolean("tracking.show-coordinates", true);
        trackingSetCompass = cfg.getBoolean("tracking.set-compass-target", true);

        escortBossBarEnabled = cfg.getBoolean("escort-bossbar.enabled", true);
        escortBossBarColor = cfg.getString("escort-bossbar.color", "YELLOW");

        guiSounds = cfg.getBoolean("gui.sounds", true);
        guiFillerMaterial = parseMaterial(cfg.getString("gui.filler-material"), Material.GRAY_STAINED_GLASS_PANE);
        buySound = cfg.getString("gui.buy-sound", "minecraft:entity.experience_orb.pickup");
        sellSound = cfg.getString("gui.sell-sound", "minecraft:entity.villager.yes");
        denySound = cfg.getString("gui.deny-sound", "minecraft:entity.villager.no");

        updateCheckerEnabled = cfg.getBoolean("update-checker.enabled", true);

        economyProvider = cfg.getString("economy.provider", "emerald");

        luckyPluginName = cfg.getString("lucky-block-bridge.plugin-name", "LuckyEngine");
        luckyGiveCommandTemplate = cfg.getString("lucky-block-bridge.give-command-template", "lb give %player% %amount%");

        routes.clear();
        ConfigurationSection routesSection = cfg.getConfigurationSection("routes");
        if (routesSection != null) {
            for (String routeId : routesSection.getKeys(false)) {
                ConfigurationSection r = routesSection.getConfigurationSection(routeId);
                if (r == null) continue;
                RouteDefinition def = new RouteDefinition();
                def.id = routeId;
                def.displayName = r.getString("display-name", routeId);
                def.specialty = r.getString("specialty", "blacksmith");
                def.requiresPlugin = r.getString("requires-plugin", null);

                List<Map<?, ?>> wps = r.getMapList("waypoints");
                for (Map<?, ?> wp : wps) {
                    try {
                        String world = String.valueOf(wp.get("world"));
                        double x = toDouble(wp.get("x"));
                        double y = toDouble(wp.get("y"));
                        double z = toDouble(wp.get("z"));
                        String name = String.valueOf(wp.get("name"));
                        def.waypoints.add(new Waypoint(world, x, y, z, name));
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("Skipping invalid waypoint in route '" + routeId + "': " + wp);
                    }
                }
                if (def.waypoints.isEmpty()) {
                    plugin.getLogger().warning("Route '" + routeId + "' has no waypoints, skipping.");
                    continue;
                }
                routes.put(routeId, def);
            }
        }

        specialtyTemplates.clear();
        ConfigurationSection specialties = cfg.getConfigurationSection("specialties");
        if (specialties != null) {
            for (String specialtyId : specialties.getKeys(false)) {
                ConfigurationSection s = specialties.getConfigurationSection(specialtyId);
                if (s == null) continue;
                List<ItemTemplate> items = new ArrayList<>();
                for (Map<?, ?> itemMap : s.getMapList("items")) {
                    try {
                        ItemTemplate t = new ItemTemplate();
                        t.material = Material.valueOf(String.valueOf(itemMap.get("material")));
                        t.basePrice = toDouble(itemMap.get("base-price"));
                        t.category = itemMap.containsKey("category") ? String.valueOf(itemMap.get("category")) : "misc";
                        t.level = itemMap.containsKey("level") ? Math.max(1, (int) toDouble(itemMap.get("level"))) : 1;
                        t.giveCommand = itemMap.containsKey("give-command")
                                ? String.valueOf(itemMap.get("give-command")) : null;
                        t.luckyRarity = itemMap.containsKey("rarity")
                                ? String.valueOf(itemMap.get("rarity")) : null;
                        items.add(t);
                    } catch (IllegalArgumentException | NullPointerException ex) {
                        plugin.getLogger().log(Level.WARNING, "Invalid item in specialty '" + specialtyId + "': " + itemMap);
                    }
                }
                specialtyTemplates.put(specialtyId, items);
            }
        }

        escortTiers.clear();
        ConfigurationSection escort = cfg.getConfigurationSection("escort-reputation");
        if (escort != null) {
            for (Map<?, ?> tierMap : escort.getMapList("tiers")) {
                try {
                    int threshold = (int) toDouble(tierMap.get("threshold"));
                    double discount = Math.max(0, Math.min(0.95, toDouble(tierMap.get("discount"))));
                    String title = String.valueOf(tierMap.get("title"));
                    escortTiers.add(new EscortTier(threshold, discount, title));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Skipping invalid escort tier: " + tierMap);
                }
            }
            // Highest threshold first — getEscortTitle/announceNewTier rely on this order.
            escortTiers.sort((a, b) -> Integer.compare(b.threshold, a.threshold));
        }
    }

    /**
     * Adds keys that are new in this version (taken from the bundled
     * default config.yml) to an existing config.yml without touching
     * anything the owner already set.
     */
    private void migrateMissingKeys(FileConfiguration cfg) {
        var resource = plugin.getResource("config.yml");
        if (resource == null) return;
        YamlConfiguration defaults;
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return;
        }
        boolean changed = false;
        for (String root : MIGRATABLE_SECTIONS) {
            ConfigurationSection def = defaults.getConfigurationSection(root);
            if (def == null) continue;
            for (String key : def.getKeys(true)) {
                if (def.isConfigurationSection(key)) continue;
                String path = root + "." + key;
                if (!cfg.isSet(path)) {
                    cfg.set(path, def.get(key));
                    changed = true;
                }
            }
        }
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("Added new config options to config.yml (your existing values were kept).");
        }
    }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null) return fallback;
        Material m = Material.matchMaterial(raw);
        return m != null && m.isItem() ? m : fallback;
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    public Map<String, RouteDefinition> getRoutes() {
        return routes;
    }

    /** True if a config.yml route uses this id (case-insensitive). */
    public boolean hasRouteId(String id) {
        for (String key : routes.keySet()) {
            if (key.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** Known specialty ids, for tab completion when creating a custom route. */
    public java.util.Set<String> getSpecialtyNames() {
        return specialtyTemplates.keySet();
    }

    /** Fresh copy of a specialty's item pool (each caravan needs its own mutable pricing/stock state). */
    public List<MarketItem> freshItemsFor(String specialty) {
        List<ItemTemplate> template = specialtyTemplates.getOrDefault(specialty, List.of());
        List<MarketItem> copy = new ArrayList<>();
        for (ItemTemplate t : template) {
            int maxStock = Math.max(1, t.level * stockPerLevel);
            copy.add(new MarketItem(t.material, t.basePrice, t.category, t.level, maxStock, t.giveCommand, t.luckyRarity));
        }
        return copy;
    }

    public int getMinutesPerMarketDay() { return minutesPerMarketDay; }
    public int getDaysPerStop() { return daysPerStop; }
    public double getInteractionRadius() { return interactionRadius; }
    public double getEscortRadius() { return escortRadius; }
    public boolean isBroadcastServerWide() { return broadcastServerWide; }
    public boolean isVagueHints() { return vagueHints; }
    public double getPriceShiftPerTrade() { return priceShiftPerTrade; }
    public double getPriceDecayPerDay() { return priceDecayPerDay; }
    public double getMaxPriceMultiplier() { return maxPriceMultiplier; }
    public double getMinPriceMultiplier() { return minPriceMultiplier; }
    public int getStockPerLevel() { return stockPerLevel; }
    public int getPurchasesPerCustomerLevel() { return purchasesPerCustomerLevel; }
    public int getMaxCustomerLevel() { return maxCustomerLevel; }
    public double getSellPriceRatio() { return sellPriceRatio; }
    public boolean isAllowSellingDamaged() { return allowSellingDamaged; }
    public double getMaxWanderDistance() { return maxWanderDistance; }
    public double getTravelSpeed() { return travelSpeed; }
    public double getOffscreenBlocksPerSecond() { return offscreenBlocksPerSecond; }
    public int getMaxTravelMinutes() { return maxTravelMinutes; }

    public boolean isAnnounceTitle() { return announceTitle; }
    public boolean isAnnounceSound() { return announceSound; }
    public boolean isLastDayWarning() { return lastDayWarning; }
    public String getArrivalSound() { return arrivalSound; }
    public String getDepartureSound() { return departureSound; }

    /** An '&'-coded template from announcements.messages, or the fallback if unset. */
    public String getMessage(String key, String fallback) {
        String value = messages.get(key);
        return value == null ? fallback : value;
    }

    public boolean isTrackingEnabled() { return trackingEnabled; }
    public int getTrackingDurationSeconds() { return trackingDurationSeconds; }
    public boolean isTrackingShowCoordinates() { return trackingShowCoordinates; }
    public boolean isTrackingSetCompass() { return trackingSetCompass; }

    public boolean isEscortBossBarEnabled() { return escortBossBarEnabled; }
    public String getEscortBossBarColor() { return escortBossBarColor; }

    public boolean isGuiSounds() { return guiSounds; }
    public Material getGuiFillerMaterial() { return guiFillerMaterial; }
    public String getBuySound() { return buySound; }
    public String getSellSound() { return sellSound; }
    public String getDenySound() { return denySound; }

    public boolean isUpdateCheckerEnabled() { return updateCheckerEnabled; }

    public String getEconomyProvider() { return economyProvider; }
    public String getLuckyPluginName() { return luckyPluginName; }
    public String getLuckyGiveCommandTemplate() { return luckyGiveCommandTemplate; }
    public List<EscortTier> getEscortTiers() { return escortTiers; }
}
