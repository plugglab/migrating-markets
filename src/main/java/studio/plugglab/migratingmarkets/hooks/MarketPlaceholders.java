package studio.plugglab.migratingmarkets.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import studio.plugglab.migratingmarkets.MigratingMarkets;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;

/**
 * PlaceholderAPI expansion (only loaded when PlaceholderAPI is enabled).
 *
 *   %migratingmarkets_active%                 caravans currently set up at a stop
 *   %migratingmarkets_total%                  caravans loaded
 *   %migratingmarkets_nearest%                nearest set-up caravan (plain name)
 *   %migratingmarkets_nearest_distance%       blocks to it
 *   %migratingmarkets_nearest_stop%           its stop name
 *   %migratingmarkets_<id>_name%              plain display name
 *   %migratingmarkets_<id>_status%            "At <stop>" / "Traveling"
 *   %migratingmarkets_<id>_state%             AT_MARKET / TRAVELING
 *   %migratingmarkets_<id>_stop%              current stop name
 *   %migratingmarkets_<id>_days_left%         market-days before it leaves
 *   %migratingmarkets_<id>_progress%          travel progress 0-100
 *   %migratingmarkets_<id>_level%             viewer's customer level there
 *   %migratingmarkets_<id>_reputation%        viewer's escort reputation there
 *   %migratingmarkets_<id>_title%             viewer's escort title there ("" if none)
 *   %migratingmarkets_<id>_discount%          viewer's escort discount % there
 */
public class MarketPlaceholders extends PlaceholderExpansion {

    private final MigratingMarkets plugin;
    private final CaravanManager caravanManager;

    public MarketPlaceholders(MigratingMarkets plugin, CaravanManager caravanManager) {
        this.plugin = plugin;
        this.caravanManager = caravanManager;
    }

    @Override
    public String getIdentifier() {
        return "migratingmarkets";
    }

    @Override
    public String getAuthor() {
        return "PluggLab Studio";
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // survive /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();
        switch (p) {
            case "active" -> {
                return String.valueOf(caravanManager.getCaravans().stream()
                        .filter(c -> c.hasWaypoints() && c.getState() == Caravan.State.AT_MARKET).count());
            }
            case "total" -> {
                return String.valueOf(caravanManager.getCaravans().size());
            }
            case "nearest", "nearest_distance", "nearest_stop" -> {
                if (!(player instanceof Player online)) return "";
                Location here = online.getLocation();
                Caravan nearest = caravanManager.getNearestMarket(here);
                if (nearest == null) return "";
                if (p.equals("nearest")) return nearest.getPlainName();
                if (p.equals("nearest_stop")) return nearest.getCurrentWaypoint().getName();
                Location loc = nearest.getCurrentWaypoint().toRawLocation();
                return loc == null ? "" : String.valueOf(Math.round(CaravanManager.horizontalDistance(here, loc)));
            }
            default -> {
            }
        }

        // <id>_<field>: match the longest caravan id prefix (ids may contain '_').
        Caravan caravan = null;
        String field = null;
        for (Caravan c : caravanManager.getCaravans()) {
            String prefix = c.getId().toLowerCase() + "_";
            if (p.startsWith(prefix) && (caravan == null || c.getId().length() > caravan.getId().length())) {
                caravan = c;
                field = p.substring(prefix.length());
            }
        }
        if (caravan == null) return null;
        boolean parked = caravan.getState() == Caravan.State.AT_MARKET;
        String stop = caravan.hasWaypoints() ? caravan.getCurrentWaypoint().getName() : "";

        switch (field) {
            case "name":
                return caravan.getPlainName();
            case "status":
                if (!caravan.hasWaypoints()) return "Not placed";
                return parked ? "At " + stop : "Traveling";
            case "state":
                return caravan.getState().name();
            case "stop":
                return parked ? stop : "";
            case "days_left":
                return parked ? String.valueOf(caravanManager.getDaysLeft(caravan)) : "0";
            case "progress":
                return String.valueOf(Math.round(caravanManager.getTravelProgress(caravan) * 100));
            default:
                break;
        }
        if (player == null) return "";
        return switch (field) {
            case "level" -> String.valueOf(caravanManager.getCustomerLevel(caravan, player.getUniqueId()));
            case "reputation" -> String.valueOf(caravan.getReputation(player.getUniqueId()));
            case "title" -> {
                String t = caravanManager.getEscortTitle(caravan, player.getUniqueId());
                yield t == null ? "" : t;
            }
            case "discount" -> String.valueOf(Math.round(caravanManager.getEscortDiscount(caravan, player.getUniqueId()) * 100));
            default -> null;
        };
    }
}
