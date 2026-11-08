package studio.plugglab.migratingmarkets.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;

/**
 * Protects and cleans up caravan NPCs:
 * - removes leftover tagged NPCs (e.g. persisted by an older build) as
 *   soon as the chunk holding them loads — the startup cleanup can only
 *   see loaded chunks, so duplicates in far-away chunks used to survive;
 * - stops caravan villagers turning into witches/zombie villagers;
 * - stops players from killing a traveling caravan (mobs still can —
 *   that's what escorting is for).
 */
public class CaravanEntityListener implements Listener {

    private final CaravanManager caravanManager;

    public CaravanEntityListener(CaravanManager caravanManager) {
        this.caravanManager = caravanManager;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (caravanManager.isStrayCaravanEntity(entity)) {
                entity.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (caravanManager.isCaravanEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!caravanManager.isCaravanEntity(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof Player
                || (damager instanceof Projectile p && p.getShooter() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
