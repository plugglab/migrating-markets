package studio.plugglab.migratingmarkets.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A single stop along a caravan's route. The configured X/Z pin down
 * where the stop is; the Y is always resolved dynamically against the
 * world's actual terrain so caravans never spawn floating in the air
 * or buried underground, even if the config value is stale or wrong.
 */
public class Waypoint {

    private final String worldName;
    private final double x;
    private final double configuredY;
    private final double z;
    private final String name;

    public Waypoint(String worldName, double x, double y, double z, String name) {
        this.worldName = worldName;
        this.x = x;
        this.configuredY = y;
        this.z = z;
        this.name = name;
    }

    /**
     * Resolves to the highest solid block at (x, z) plus one, so the
     * caravan always lands standing on the ground. Falls back to the
     * configured Y only if the world can't report terrain height yet
     * (e.g. the chunk genuinely isn't generated).
     */
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = world.getHighestBlockYAt(blockX, blockZ);
        double resolvedY = groundY > world.getMinHeight() ? groundY + 1.0 : configuredY;
        return new Location(world, x, resolvedY, z);
    }

    /**
     * Location using the configured Y, WITHOUT touching terrain. Safe to
     * call for far-away / unloaded stops (direction hints, distance
     * checks, compass targets) because it never forces a synchronous
     * chunk load the way {@link #toLocation()} does.
     */
    public Location toRawLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, configuredY, z);
    }

    /** Whether the chunk this stop sits in is currently loaded. */
    public boolean isChunkLoaded() {
        World world = Bukkit.getWorld(worldName);
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    /** The originally-given Y (fallback only — toLocation() prefers live terrain height). */
    public double getConfiguredY() {
        return configuredY;
    }
}
