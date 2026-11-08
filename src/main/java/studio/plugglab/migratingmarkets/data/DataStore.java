package studio.plugglab.migratingmarkets.data;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.market.MarketItem;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists caravan state (position along route, days at stop, escort
 * reputation) AND the full per-item state — price, level, stock,
 * category, live multiplier — so admin edits made via /caravanadmin or
 * the in-game shop editor survive restarts, not just the built-in
 * config.yml items. Items beyond what config.yml currently defines
 * (i.e. ones an admin added at runtime) are reconstructed entirely
 * from what's saved here.
 */
public class DataStore {

    private final JavaPlugin plugin;
    private final File file;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "caravan-state.yml");
    }

    public void save(List<Caravan> caravans) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Caravan caravan : caravans) {
            String base = "caravans." + caravan.getId() + ".";
            yaml.set(base + "waypoint-index", caravan.getCurrentWaypointIndex());
            yaml.set(base + "state", caravan.getState().name());
            yaml.set(base + "days-at-stop", caravan.getDaysAtCurrentStop());

            List<MarketItem> items = caravan.getInventory();
            yaml.set(base + "item-count", items.size());
            for (int i = 0; i < items.size(); i++) {
                MarketItem item = items.get(i);
                String itemBase = base + "items." + i + ".";
                yaml.set(itemBase + "material", item.getMaterial().name());
                yaml.set(itemBase + "base-price", item.getBasePrice());
                yaml.set(itemBase + "category", item.getCategory());
                yaml.set(itemBase + "level", item.getLevel());
                yaml.set(itemBase + "max-stock", item.getMaxStock());
                yaml.set(itemBase + "current-stock", item.getCurrentStock());
                yaml.set(itemBase + "multiplier", item.getMultiplier());
                if (item.getGiveCommand() != null) {
                    yaml.set(itemBase + "give-command", item.getGiveCommand());
                }
                if (item.getLuckyRarity() != null) {
                    yaml.set(itemBase + "rarity", item.getLuckyRarity());
                }
            }

            for (var entry : caravan.getReputationMap().entrySet()) {
                yaml.set(base + "reputation." + entry.getKey(), entry.getValue());
            }
            for (var entry : caravan.getPurchaseCountMap().entrySet()) {
                yaml.set(base + "purchases." + entry.getKey(), entry.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save caravan-state.yml", e);
        }
    }

    public void applyTo(List<Caravan> caravans) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Caravan caravan : caravans) {
            String base = "caravans." + caravan.getId() + ".";
            if (!yaml.isSet(base + "waypoint-index")) {
                continue;
            }
            int index = yaml.getInt(base + "waypoint-index", 0);
            if (index < caravan.getWaypoints().size()) {
                caravan.setCurrentWaypointIndex(index);
            }
            String stateName = yaml.getString(base + "state");
            if (stateName != null) {
                try {
                    caravan.setState(Caravan.State.valueOf(stateName));
                } catch (IllegalArgumentException ignored) {
                }
            }
            int days = yaml.getInt(base + "days-at-stop", 0);
            for (int i = 0; i < days; i++) {
                caravan.incrementDaysAtStop();
            }

            applyItems(yaml, base, caravan);

            if (yaml.isConfigurationSection(base + "reputation")) {
                for (String key : yaml.getConfigurationSection(base + "reputation").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int rep = yaml.getInt(base + "reputation." + key);
                        caravan.addReputation(uuid, rep);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (yaml.isConfigurationSection(base + "purchases")) {
                for (String key : yaml.getConfigurationSection(base + "purchases").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int count = yaml.getInt(base + "purchases." + key);
                        caravan.addPurchases(uuid, count);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private void applyItems(YamlConfiguration yaml, String base, Caravan caravan) {
        if (!yaml.isSet(base + "item-count")) {
            return;
        }
        int savedCount = yaml.getInt(base + "item-count", 0);
        List<MarketItem> items = caravan.getInventory();

        for (int i = 0; i < savedCount; i++) {
            String itemBase = base + "items." + i + ".";
            if (!yaml.isSet(itemBase + "material")) {
                continue;
            }
            double multiplier = yaml.getDouble(itemBase + "multiplier", 1.0);
            int currentStock = yaml.getInt(itemBase + "current-stock", -1);

            if (i < items.size()) {
                // Item still exists in the config-built template — apply
                // any admin edits (price/level/category) plus live state.
                MarketItem item = items.get(i);
                item.setBasePrice(yaml.getDouble(itemBase + "base-price", item.getBasePrice()));
                item.setCategory(yaml.getString(itemBase + "category", item.getCategory()));
                int savedLevel = yaml.getInt(itemBase + "level", item.getLevel());
                int savedMaxStock = yaml.getInt(itemBase + "max-stock", item.getMaxStock());
                if (savedLevel != item.getLevel() || savedMaxStock != item.getMaxStock()) {
                    // Re-derive stock-per-level from the saved data directly
                    // rather than assuming the current config's ratio.
                    int stockPerLevel = savedLevel > 0 ? Math.max(1, savedMaxStock / savedLevel) : savedMaxStock;
                    item.setLevel(savedLevel, stockPerLevel);
                }
                item.setMultiplier(multiplier);
                if (currentStock >= 0) {
                    item.setCurrentStock(currentStock);
                }
            } else {
                // Item doesn't exist in the current config template — it was
                // added at runtime (via /caravanadmin additem or the shop
                // editor GUI). Reconstruct it fully from saved data.
                try {
                    Material material = Material.valueOf(yaml.getString(itemBase + "material"));
                    double basePrice = yaml.getDouble(itemBase + "base-price", 1.0);
                    String category = yaml.getString(itemBase + "category", "custom");
                    int level = yaml.getInt(itemBase + "level", 1);
                    int maxStock = yaml.getInt(itemBase + "max-stock", 10);
                    String giveCommand = yaml.getString(itemBase + "give-command", null);
                    String rarity = yaml.getString(itemBase + "rarity", null);

                    MarketItem reconstructed = new MarketItem(material, basePrice, category, level, maxStock,
                            giveCommand, rarity);
                    reconstructed.setMultiplier(multiplier);
                    if (currentStock >= 0) {
                        reconstructed.setCurrentStock(currentStock);
                    }
                    items.add(reconstructed);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping corrupt saved item at " + itemBase + " for caravan '"
                            + caravan.getId() + "'.");
                }
            }
        }
    }
}
