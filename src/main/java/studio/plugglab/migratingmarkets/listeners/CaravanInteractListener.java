package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.MarketGUI;
import studio.plugglab.migratingmarkets.util.ConfigManager;

public class CaravanInteractListener implements Listener {

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final LuckyBlockBridge luckyBlockBridge;

    public CaravanInteractListener(CaravanManager caravanManager, ConfigManager configManager,
                                   EconomyProvider economy, LuckyBlockBridge luckyBlockBridge) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
        this.economy = economy;
        this.luckyBlockBridge = luckyBlockBridge;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Caravan caravan = caravanManager.getByEntityId(event.getRightClicked().getUniqueId());
        if (caravan == null) {
            if (caravanManager.isCaravanEntity(event.getRightClicked())) {
                event.setCancelled(true); // leftover NPC — never open vanilla trading on it
            }
            return;
        }
        // Always cancel: no vanilla trade screen, name-tagging or leashing caravan NPCs.
        event.setCancelled(true);
        // The event fires once per hand; only react to the main hand so
        // messages/GUIs aren't doubled.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("migratingmarkets.use")) {
            player.sendMessage(Component.text("You don't have permission to trade with caravans.").color(NamedTextColor.RED));
            return;
        }
        if (caravan.getState() != Caravan.State.AT_MARKET) {
            player.sendMessage(Component.text("This caravan is on the move — it can't trade right now.")
                    .color(NamedTextColor.GRAY));
            return;
        }
        player.openInventory(MarketGUI.build(caravan, caravanManager, configManager, economy, luckyBlockBridge, player));
    }
}
