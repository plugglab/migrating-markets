package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.MarketGUI;

public class CaravanInteractListener implements Listener {

    private final CaravanManager caravanManager;
    private final EconomyProvider economy;
    private final LuckyBlockBridge luckyBlockBridge;

    public CaravanInteractListener(CaravanManager caravanManager, EconomyProvider economy, LuckyBlockBridge luckyBlockBridge) {
        this.caravanManager = caravanManager;
        this.economy = economy;
        this.luckyBlockBridge = luckyBlockBridge;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Caravan caravan = caravanManager.getByEntityId(event.getRightClicked().getUniqueId());
        if (caravan == null) {
            return;
        }
        Player player = event.getPlayer();
        if (caravan.getState() != Caravan.State.AT_MARKET) {
            player.sendMessage(Component.text("This caravan is on the move — it can't trade right now.")
                    .color(NamedTextColor.GRAY));
            return;
        }
        event.setCancelled(true);
        player.openInventory(MarketGUI.build(caravan, caravanManager, economy, luckyBlockBridge, player));
    }
}
