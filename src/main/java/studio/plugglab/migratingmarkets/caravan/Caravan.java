package studio.plugglab.migratingmarkets.caravan;

import net.kyori.adventure.text.Component;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.util.ChatColorUtil;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents one traveling market caravan: its route, current state,
 * live inventory, and per-player escort reputation.
 */
public class Caravan {

    public enum State {
        AT_MARKET,
        TRAVELING
    }

    private final String id;
    private final String displayName;
    private final String specialty;
    private final List<Waypoint> waypoints;
    private final List<MarketItem> inventory;
    private final Map<UUID, Integer> reputation = new HashMap<>();
    private final Map<UUID, Integer> purchaseCounts = new HashMap<>();

    private int currentWaypointIndex = 0;
    private State state = State.AT_MARKET;
    private int daysAtCurrentStop = 0;
    private UUID entityId;

    public Caravan(String id, String displayName, String specialty,
                    List<Waypoint> waypoints, List<MarketItem> inventory) {
        this.id = id;
        this.displayName = displayName;
        this.specialty = specialty;
        this.waypoints = waypoints;
        this.inventory = inventory;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The '&'-coded display name parsed into a real, correctly colored Component. */
    public Component getDisplayNameComponent() {
        return ChatColorUtil.ampersand(displayName);
    }

    /** Plain-text version of the name (no color codes) for places that need raw concatenation. */
    public String getPlainName() {
        return ChatColorUtil.stripToPlain(displayName);
    }

    public String getSpecialty() {
        return specialty;
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public List<MarketItem> getInventory() {
        return inventory;
    }

    public Waypoint getCurrentWaypoint() {
        return waypoints.get(currentWaypointIndex);
    }

    public Waypoint getNextWaypoint() {
        int next = (currentWaypointIndex + 1) % waypoints.size();
        return waypoints.get(next);
    }

    public void advanceToNextWaypoint() {
        currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
        daysAtCurrentStop = 0;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex = index;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getDaysAtCurrentStop() {
        return daysAtCurrentStop;
    }

    public void incrementDaysAtStop() {
        daysAtCurrentStop++;
    }

    public void resetDaysAtStop() {
        daysAtCurrentStop = 0;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    /** Escort reputation earned by a player defending this caravan. */
    public int getReputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public void addReputation(UUID player, int amount) {
        reputation.merge(player, amount, Integer::sum);
    }

    public Map<UUID, Integer> getReputationMap() {
        return reputation;
    }

    /** How many units this player has ever bought from this caravan — drives their customer level. */
    public int getPurchaseCount(UUID player) {
        return purchaseCounts.getOrDefault(player, 0);
    }

    public void addPurchases(UUID player, int amount) {
        purchaseCounts.merge(player, amount, Integer::sum);
    }

    public Map<UUID, Integer> getPurchaseCountMap() {
        return purchaseCounts;
    }
}
