package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.CaravanInventoryHolder;
import studio.plugglab.migratingmarkets.market.MarketGUI;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.util.ConfigManager;

public class MarketGUIListener implements Listener {

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final LuckyBlockBridge luckyBlockBridge;

    public MarketGUIListener(CaravanManager caravanManager, ConfigManager configManager,
                              EconomyProvider economy, LuckyBlockBridge luckyBlockBridge) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
        this.economy = economy;
        this.luckyBlockBridge = luckyBlockBridge;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaravanInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) {
            return;
        }
        Caravan caravan = holder.getCaravan();
        if (slot >= caravan.getInventory().size()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MarketItem item = caravan.getInventory().get(slot);
        ClickType click = event.getClick();

        if (click == ClickType.RIGHT) {
            handleSell(player, item);
        } else if (click == ClickType.SHIFT_LEFT) {
            handleBuy(player, caravan, item, 8);
        } else if (click == ClickType.LEFT) {
            handleBuy(player, caravan, item, 1);
        }

        refreshSlot(event.getInventory(), slot, item, caravan, player);
    }

    private void handleBuy(Player player, Caravan caravan, MarketItem item, int requested) {
        int customerLevel = caravanManager.getCustomerLevel(caravan, player.getUniqueId());
        if (item.getLevel() > customerLevel) {
            int needed = caravanManager.getPurchasesToNextLevel(caravan, player.getUniqueId());
            player.sendMessage(Component.text("This is a level " + item.getLevel() + " item — you're a level "
                    + customerLevel + " customer here. Buy " + needed + " more item(s) from this caravan to level up.")
                    .color(NamedTextColor.RED));
            return;
        }

        int quantity = Math.min(requested, item.getCurrentStock());
        if (quantity <= 0) {
            player.sendMessage(Component.text("Out of stock — check back after the next market day.")
                    .color(NamedTextColor.RED));
            return;
        }

        double discount = caravanManager.getEscortDiscount(caravan, player.getUniqueId());
        double unitPrice = item.getCurrentPrice() * (1 - discount);
        double totalCost = unitPrice * quantity;

        if (!economy.has(player, totalCost)) {
            player.sendMessage(Component.text("You need " + economy.format(totalCost) + " for that.")
                    .color(NamedTextColor.RED));
            return;
        }
        if (!economy.withdraw(player, totalCost)) {
            player.sendMessage(Component.text("Payment failed.").color(NamedTextColor.RED));
            return;
        }

        if (item.isLuckyItem()) {
            if (!luckyBlockBridge.isAvailable() || !luckyBlockBridge.give(player, item.getLuckyRarity(), quantity)) {
                player.sendMessage(Component.text("The LuckyBlock plugin isn't installed on this server.")
                        .color(NamedTextColor.RED));
                economy.deposit(player, totalCost); // refund since we can't deliver
                return;
            }
        } else if (item.isCommandItem()) {
            if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled(configManager.getLuckyPluginName())) {
                player.sendMessage(Component.text("The bridged plugin isn't installed on this server.")
                        .color(NamedTextColor.RED));
                economy.deposit(player, totalCost); // refund since we can't deliver
                return;
            }
            luckyBlockBridge.dispatchGiveCommand(player, item.getGiveCommand(), quantity);
        } else {
            ItemStack stack = new ItemStack(item.getMaterial(), quantity);
            var leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
        }

        item.onBought(quantity, configManager.getPriceShiftPerTrade(), configManager.getMaxPriceMultiplier());
        caravan.addPurchases(player.getUniqueId(), quantity);
        player.sendMessage(Component.text("Bought " + quantity + "x for " + economy.format(totalCost) + ".")
                .color(NamedTextColor.GREEN));
    }

    private void handleSell(Player player, MarketItem item) {
        if (item.isCommandItem()) {
            player.sendMessage(Component.text("This item can't be sold back.").color(NamedTextColor.RED));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() == 0) {
            player.sendMessage(Component.text("Hold the item you want to sell.").color(NamedTextColor.RED));
            return;
        }
        if (item.isLuckyItem()) {
            // Lucky block items all share the same display material (a player head
            // skin), so rarity can't be distinguished by Material alone — any lucky
            // block sells back at this slot's price.
            if (!luckyBlockBridge.isLuckyBlockItem(held)) {
                player.sendMessage(Component.text("Hold a lucky block to sell.").color(NamedTextColor.RED));
                return;
            }
        } else if (held.getType() != item.getMaterial()) {
            player.sendMessage(Component.text("Hold the item you want to sell.").color(NamedTextColor.RED));
            return;
        }
        int quantity = held.getAmount();
        double unitSellPrice = item.getCurrentPrice() * 0.5;
        double totalPayout = unitSellPrice * quantity;

        held.setAmount(0);
        economy.deposit(player, totalPayout);

        item.onSold(quantity, configManager.getPriceShiftPerTrade(), configManager.getMinPriceMultiplier());
        player.sendMessage(Component.text("Sold " + quantity + "x for " + economy.format(totalPayout) + ".")
                .color(NamedTextColor.YELLOW));
    }

    private void refreshSlot(Inventory inventory, int slot, MarketItem item, Caravan caravan, Player player) {
        double discount = caravanManager.getEscortDiscount(caravan, player.getUniqueId());
        int customerLevel = caravanManager.getCustomerLevel(caravan, player.getUniqueId());
        inventory.setItem(slot, MarketGUI.buildItemStack(item, discount, economy, luckyBlockBridge, customerLevel));
    }
}
