package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.EscortTier;

/**
 * Players who kill hostile mobs near a TRAVELING caravan earn escort
 * reputation with that specific caravan, unlocking discounts once it
 * next sets up shop.
 */
public class EscortListener implements Listener {

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;

    public EscortListener(CaravanManager caravanManager, ConfigManager configManager) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onMonsterDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        if (!(dead instanceof Monster monster)) {
            return;
        }
        Player killer = monster.getKiller();
        if (killer == null) {
            return;
        }

        double radius = configManager.getEscortRadius();
        for (Caravan caravan : caravanManager.getCaravans()) {
            if (caravan.getState() != Caravan.State.TRAVELING || caravan.getEntityId() == null) {
                continue;
            }
            var entity = Bukkit.getEntity(caravan.getEntityId());
            if (entity == null || !entity.getWorld().equals(dead.getWorld())) {
                continue;
            }
            if (entity.getLocation().distanceSquared(dead.getLocation()) <= radius * radius) {
                int before = caravan.getReputation(killer.getUniqueId());
                caravan.addReputation(killer.getUniqueId(), 1);
                int after = before + 1;
                announceNewTier(killer, caravan, before, after);
            }
        }
    }

    private void announceNewTier(Player player, Caravan caravan, int before, int after) {
        for (EscortTier tier : configManager.getEscortTiers()) {
            if (before < tier.threshold && after >= tier.threshold) {
                String message = "You are now a " + tier.title + " of the " + caravan.getDisplayName()
                        + "! (" + (int) (tier.discount * 100) + "% discount)";
                player.sendMessage(Component.text(message).color(NamedTextColor.GOLD));
                return;
            }
        }
    }
}
