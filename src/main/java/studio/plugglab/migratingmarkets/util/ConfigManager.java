package studio.plugglab.migratingmarkets.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.market.MarketItem;

import java.util.ArrayList;
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

        ConfigurationSection settings = cfg.getConfigurationSection("settings");
        if (settings != null) {
            minutesPerMarketDay = settings.getInt("minutes-per-market-day", 20);
            daysPerStop = settings.getInt("days-per-stop", 3);
            interactionRadius = settings.getDouble("interaction-radius", 4);
            escortRadius = settings.getDouble("escort-radius", 12);
            broadcastServerWide = settings.getBoolean("broadcast-server-wide", true);
            vagueHints = settings.getBoolean("vague-destination-hints", true);
            priceShiftPerTrade = settings.getDouble("price-shift-per-trade", 0.02);
            priceDecayPerDay = settings.getDouble("price-decay-per-day", 0.05);
            maxPriceMultiplier = settings.getDouble("max-price-multiplier", 2.5);
            minPriceMultiplier = settings.getDouble("min-price-multiplier", 0.4);
            stockPerLevel = settings.getInt("stock-per-level", 6);
            purchasesPerCustomerLevel = settings.getInt("purchases-per-customer-level", 8);
            maxCustomerLevel = settings.getInt("max-customer-level", 5);
        }

        ConfigurationSection economy = cfg.getConfigurationSection("economy");
        if (economy != null) {
            economyProvider = economy.getString("provider", "emerald");
        }

        ConfigurationSection luckyBridge = cfg.getConfigurationSection("lucky-block-bridge");
        if (luckyBridge != null) {
            luckyPluginName = luckyBridge.getString("plugin-name", "LuckyEngine");
            luckyGiveCommandTemplate = luckyBridge.getString("give-command-template", "lb give %player% %amount%");
        }

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
                    String world = String.valueOf(wp.get("world"));
                    double x = toDouble(wp.get("x"));
                    double y = toDouble(wp.get("y"));
                    double z = toDouble(wp.get("z"));
                    String name = String.valueOf(wp.get("name"));
                    def.waypoints.add(new Waypoint(world, x, y, z, name));
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
                        t.category = String.valueOf(itemMap.get("category"));
                        t.level = itemMap.containsKey("level") ? (int) toDouble(itemMap.get("level")) : 1;
                        t.giveCommand = itemMap.containsKey("give-command")
                                ? String.valueOf(itemMap.get("give-command")) : null;
                        t.luckyRarity = itemMap.containsKey("rarity")
                                ? String.valueOf(itemMap.get("rarity")) : null;
                        items.add(t);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().log(Level.WARNING, "Invalid material in specialty '" + specialtyId + "'", ex);
                    }
                }
                specialtyTemplates.put(specialtyId, items);
            }
        }

        escortTiers.clear();
        ConfigurationSection escort = cfg.getConfigurationSection("escort-reputation");
        if (escort != null) {
            for (Map<?, ?> tierMap : escort.getMapList("tiers")) {
                int threshold = (int) toDouble(tierMap.get("threshold"));
                double discount = toDouble(tierMap.get("discount"));
                String title = String.valueOf(tierMap.get("title"));
                escortTiers.add(new EscortTier(threshold, discount, title));
            }
            escortTiers.sort((a, b) -> Integer.compare(b.threshold, a.threshold));
        }
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    public Map<String, RouteDefinition> getRoutes() {
        return routes;
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

    public int getMinutesPerMarketDay() {
        return minutesPerMarketDay;
    }

    public int getDaysPerStop() {
        return daysPerStop;
    }

    public double getInteractionRadius() {
        return interactionRadius;
    }

    public double getEscortRadius() {
        return escortRadius;
    }

    public boolean isBroadcastServerWide() {
        return broadcastServerWide;
    }

    public boolean isVagueHints() {
        return vagueHints;
    }

    public double getPriceShiftPerTrade() {
        return priceShiftPerTrade;
    }

    public double getPriceDecayPerDay() {
        return priceDecayPerDay;
    }

    public double getMaxPriceMultiplier() {
        return maxPriceMultiplier;
    }

    public double getMinPriceMultiplier() {
        return minPriceMultiplier;
    }

    public int getStockPerLevel() {
        return stockPerLevel;
    }

    public int getPurchasesPerCustomerLevel() {
        return purchasesPerCustomerLevel;
    }

    public int getMaxCustomerLevel() {
        return maxCustomerLevel;
    }

    public String getEconomyProvider() {
        return economyProvider;
    }

    public String getLuckyPluginName() {
        return luckyPluginName;
    }

    public String getLuckyGiveCommandTemplate() {
        return luckyGiveCommandTemplate;
    }

    public List<EscortTier> getEscortTiers() {
        return escortTiers;
    }
}
