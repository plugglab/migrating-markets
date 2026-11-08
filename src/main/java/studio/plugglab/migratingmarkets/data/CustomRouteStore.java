package studio.plugglab.migratingmarkets.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Lets server owners build their own caravans entirely in-game — via
 * /caravanadmin createroute/addwaypoint/removewaypoint/deleteroute and
 * the shop editor — without ever touching config.yml. Kept in a
 * separate file so a plugin update that ships a new default config.yml
 * can never clobber anything an owner built at runtime.
 */
public class CustomRouteStore {

    private final JavaPlugin plugin;
    private final File file;

    public CustomRouteStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-routes.yml");
    }

    public void save(List<Caravan> customCaravans) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Caravan caravan : customCaravans) {
            String base = "routes." + caravan.getId() + ".";
            yaml.set(base + "display-name", caravan.getDisplayName());
            yaml.set(base + "specialty", caravan.getSpecialty());

            List<Map<String, Object>> waypointMaps = new ArrayList<>();
            for (Waypoint wp : caravan.getWaypoints()) {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("world", wp.getWorldName());
                map.put("x", wp.getX());
                map.put("y", wp.getConfiguredY());
                map.put("z", wp.getZ());
                map.put("name", wp.getName());
                waypointMaps.add(map);
            }
            yaml.set(base + "waypoints", waypointMaps);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save custom-routes.yml", e);
        }
    }

    /** Returns owner-created route definitions, in the same shape ConfigManager uses for config.yml routes. */
    public List<ConfigManager.RouteDefinition> load() {
        List<ConfigManager.RouteDefinition> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var routesSection = yaml.getConfigurationSection("routes");
        if (routesSection == null) {
            return result;
        }
        for (String routeId : routesSection.getKeys(false)) {
            var r = routesSection.getConfigurationSection(routeId);
            if (r == null) continue;
            ConfigManager.RouteDefinition def = new ConfigManager.RouteDefinition();
            def.id = routeId;
            def.displayName = r.getString("display-name", routeId);
            def.specialty = r.getString("specialty", "custom");
            def.requiresPlugin = null;

            for (Map<?, ?> wp : r.getMapList("waypoints")) {
                try {
                    String world = String.valueOf(wp.get("world"));
                    double x = toDouble(wp.get("x"));
                    double y = toDouble(wp.get("y"));
                    double z = toDouble(wp.get("z"));
                    String name = String.valueOf(wp.get("name"));
                    def.waypoints.add(new Waypoint(world, x, y, z, name));
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipping corrupt waypoint in custom route '" + routeId + "'.");
                }
            }
            result.add(def);
        }
        return result;
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }
}
