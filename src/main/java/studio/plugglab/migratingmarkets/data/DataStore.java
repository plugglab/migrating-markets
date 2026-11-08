package studio.plugglab.migratingmarkets.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.market.MarketItem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists caravan state (position along route, days at stop, escort
 * reputation, customer purchases, mid-journey position) AND the full
 * per-item state — price, level, stock, category, live multiplier — so
 * admin edits made via /caravanadmin or the in-game shop editor survive
 * restarts.
 *
 * Saved items are matched back to the config-built template by
 * identity (material + rarity + give-command), not by list index, so
 * removing or reordering items can no longer shift saved prices onto
 * the wrong material. Items an admin removed are remembered in
 * `removed-items` so they don't silently come back from config.yml.
 *
 * Also reads the v1.0 format (`prices.<i>` / `stock.<i>`) so upgrading
 * servers keep their live prices and reputation.
 */
public class DataStore {

    private final JavaPlugin plugin;
    private final File file;
    /** Ids whose saved section must be dropped on the next save (deleted custom routes). */
    private final Set<String> forgotten = new HashSet<>();
    private final Object ioLock = new Object();
    /** Snapshot ordering: an older async snapshot must never overwrite a newer (e.g. shutdown) save. */
    private final java.util.concurrent.atomic.AtomicLong snapshotSeq = new java.util.concurrent.atomic.AtomicLong();
    private long lastWrittenSeq;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "caravan-state.yml");
    }

    /** Drops a caravan's saved state on the next save (used when an owner-created route is deleted). */
    public void forget(String caravanId) {
        forgotten.add(caravanId);
    }

    /** Synchronous save — used on shutdown/reload. */
    public void save(List<Caravan> caravans) {
        String data = serialize(caravans);
        write(data, snapshotSeq.incrementAndGet());
    }

    /**
     * Builds the YAML on the calling (main) thread — where all caravan
     * state is mutated — then writes the file off-thread so the periodic
     * market-day save never blocks the server tick on disk I/O.
     */
    public void saveAsync(List<Caravan> caravans) {
        String data = serialize(caravans);
        long seq = snapshotSeq.incrementAndGet();
        if (!plugin.isEnabled()) {
            write(data, seq);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data, seq));
    }

    private String serialize(List<Caravan> caravans) {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<String> current = new HashSet<>();
        for (Caravan caravan : caravans) {
            current.add(caravan.getId());
            String base = "caravans." + caravan.getId() + ".";
            yaml.set(base + "waypoint-index", caravan.getCurrentWaypointIndex());
            yaml.set(base + "state", caravan.getState().name());
            yaml.set(base + "days-at-stop", caravan.getDaysAtCurrentStop());

            Location travel = caravan.getTravelLocation();
            if (caravan.getState() == Caravan.State.TRAVELING && travel != null && travel.getWorld() != null) {
                yaml.set(base + "travel.world", travel.getWorld().getName());
                yaml.set(base + "travel.x", travel.getX());
                yaml.set(base + "travel.y", travel.getY());
                yaml.set(base + "travel.z", travel.getZ());
            }

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
                yaml.set(itemBase + "customized", item.isCustomized());
                if (item.getGiveCommand() != null) {
                    yaml.set(itemBase + "give-command", item.getGiveCommand());
                }
                if (item.getLuckyRarity() != null) {
                    yaml.set(itemBase + "rarity", item.getLuckyRarity());
                }
            }
            if (!caravan.getRemovedItemKeys().isEmpty()) {
                yaml.set(base + "removed-items", new ArrayList<>(caravan.getRemovedItemKeys()));
            }

            for (var entry : caravan.getReputationMap().entrySet()) {
                yaml.set(base + "reputation." + entry.getKey(), entry.getValue());
            }
            for (var entry : caravan.getPurchaseCountMap().entrySet()) {
                yaml.set(base + "purchases." + entry.getKey(), entry.getValue());
            }
        }

        // Keep saved state for caravans that exist but aren't loaded right
        // now (e.g. the fortune caravan while LuckyBlock is temporarily
        // removed, or a route whose world failed to load). Previously every
        // save rewrote the file from loaded caravans only, permanently
        // wiping those players' reputation and customer levels.
        if (file.exists()) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection oldCaravans = old.getConfigurationSection("caravans");
            if (oldCaravans != null) {
                for (String id : oldCaravans.getKeys(false)) {
                    if (current.contains(id) || forgotten.contains(id)) continue;
                    yaml.set("caravans." + id, oldCaravans.get(id));
                }
            }
        }
        return yaml.saveToString();
    }

    private void write(String data, long seq) {
        synchronized (ioLock) {
            if (seq <= lastWrittenSeq) return; // a newer snapshot already hit the disk
            lastWrittenSeq = seq;
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                // Write to a temp file first and move it over, so a crash
                // mid-write can never leave a truncated caravan-state.yml.
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save caravan-state.yml", e);
            }
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
            if (index >= 0 && index < caravan.getWaypoints().size()) {
                caravan.setCurrentWaypointIndex(index);
            }
            String stateName = yaml.getString(base + "state");
            if (stateName != null) {
                try {
                    caravan.setState(Caravan.State.valueOf(stateName));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (caravan.getWaypoints().size() < 2) {
                caravan.setState(Caravan.State.AT_MARKET); // nowhere to travel to
            }
            int days = yaml.getInt(base + "days-at-stop", 0);
            for (int i = 0; i < days; i++) {
                caravan.incrementDaysAtStop();
            }

            if (caravan.getState() == Caravan.State.TRAVELING && yaml.isSet(base + "travel.world")) {
                var world = Bukkit.getWorld(yaml.getString(base + "travel.world", ""));
                if (world != null) {
                    caravan.setTravelLocation(new Location(world, yaml.getDouble(base + "travel.x"),
                            yaml.getDouble(base + "travel.y"), yaml.getDouble(base + "travel.z")));
                }
            }

            for (String key : yaml.getStringList(base + "removed-items")) {
                caravan.getRemovedItemKeys().add(key);
            }

            if (yaml.isSet(base + "item-count")) {
                applyItems(yaml, base, caravan);
            } else {
                applyLegacyItems(yaml, base, caravan);
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

    /** v1.0 format: only live multiplier + stock, keyed by index into the (unchanged) config list. */
    private void applyLegacyItems(YamlConfiguration yaml, String base, Caravan caravan) {
        List<MarketItem> items = caravan.getInventory();
        for (int i = 0; i < items.size(); i++) {
            if (yaml.isSet(base + "prices." + i)) {
                items.get(i).setMultiplier(yaml.getDouble(base + "prices." + i, 1.0));
            }
            if (yaml.isSet(base + "stock." + i)) {
                items.get(i).setCurrentStock(yaml.getInt(base + "stock." + i, items.get(i).getMaxStock()));
            }
        }
    }

    private void applyItems(YamlConfiguration yaml, String base, Caravan caravan) {
        int savedCount = yaml.getInt(base + "item-count", 0);
        List<MarketItem> template = new ArrayList<>(caravan.getInventory());
        List<MarketItem> result = new ArrayList<>();

        for (int i = 0; i < savedCount; i++) {
            String itemBase = base + "items." + i + ".";
            if (!yaml.isSet(itemBase + "material")) {
                continue;
            }
            Material material = Material.matchMaterial(yaml.getString(itemBase + "material", ""));
            if (material == null) {
                plugin.getLogger().warning("Skipping corrupt saved item at " + itemBase + " for caravan '"
                        + caravan.getId() + "'.");
                continue;
            }
            String giveCommand = yaml.getString(itemBase + "give-command", null);
            String rarity = yaml.getString(itemBase + "rarity", null);
            double multiplier = yaml.getDouble(itemBase + "multiplier", 1.0);
            int currentStock = yaml.getInt(itemBase + "current-stock", -1);
            boolean customized = yaml.getBoolean(itemBase + "customized", false);

            MarketItem probe = new MarketItem(material, 1, "x", 1, 1, giveCommand, rarity);
            MarketItem match = null;
            for (MarketItem candidate : template) {
                if (candidate.getIdentityKey().equals(probe.getIdentityKey())) {
                    match = candidate;
                    break;
                }
            }

            MarketItem item;
            if (match != null) {
                template.remove(match);
                item = match;
                if (customized) {
                    // Admin edited it in game — their values win over config.yml.
                    item.setBasePrice(yaml.getDouble(itemBase + "base-price", item.getBasePrice()));
                    item.setCategory(yaml.getString(itemBase + "category", item.getCategory()));
                    item.restoreLevel(yaml.getInt(itemBase + "level", item.getLevel()),
                            yaml.getInt(itemBase + "max-stock", item.getMaxStock()));
                    item.setCustomized(true);
                }
            } else {
                // Not in the current config template — added at runtime via
                // /caravanadmin additem or the shop editor. Rebuild it fully.
                double basePrice = yaml.getDouble(itemBase + "base-price", 1.0);
                String category = yaml.getString(itemBase + "category", "custom");
                int level = Math.max(1, yaml.getInt(itemBase + "level", 1));
                int maxStock = Math.max(1, yaml.getInt(itemBase + "max-stock", 10));
                item = new MarketItem(material, basePrice, category, level, maxStock, giveCommand, rarity);
                item.setCustomized(true);
            }
            item.setMultiplier(multiplier);
            if (currentStock >= 0) {
                item.setCurrentStock(currentStock);
            }
            result.add(item);
        }

        // Template items with no saved counterpart: either newly added to
        // config.yml since the last save (keep them) or explicitly removed
        // by an admin (stay removed).
        for (MarketItem leftover : template) {
            if (!caravan.getRemovedItemKeys().contains(leftover.getIdentityKey())) {
                result.add(leftover);
            }
        }

        caravan.getInventory().clear();
        caravan.getInventory().addAll(result);
    }
}
