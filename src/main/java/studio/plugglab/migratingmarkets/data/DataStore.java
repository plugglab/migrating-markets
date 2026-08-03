package studio.plugglab.migratingmarkets.data;

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
 * Persists caravan state (position along route, days at stop, live
 * price multipliers, escort reputation) so caravans survive restarts
 * instead of resetting their whole economy.
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
            for (int i = 0; i < items.size(); i++) {
                yaml.set(base + "prices." + i, items.get(i).getMultiplier());
                yaml.set(base + "stock." + i, items.get(i).getCurrentStock());
            }

            for (var entry : caravan.getReputationMap().entrySet()) {
                yaml.set(base + "reputation." + entry.getKey(), entry.getValue());
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
            if (!yaml.contains(caravan.getId(), false) && !yaml.isSet(base + "waypoint-index")) {
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

            List<MarketItem> items = caravan.getInventory();
            for (int i = 0; i < items.size(); i++) {
                double multiplier = yaml.getDouble(base + "prices." + i, 1.0);
                items.get(i).setMultiplier(multiplier);
                if (yaml.isSet(base + "stock." + i)) {
                    items.get(i).setCurrentStock(yaml.getInt(base + "stock." + i, items.get(i).getMaxStock()));
                }
            }

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
        }
    }
}
