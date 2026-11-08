package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.CaravanInventoryHolder;
import studio.plugglab.migratingmarkets.market.MarketGUI;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Feedback;

public class MarketGUIListener implements Listener {

    private static final int BULK_BUY = 8;

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

    /**
     * Dragging was never cancelled, so items could be dragged into the shop
     * window (and lost, or used to mess with the display items). Any drag
     * touching the top inventory is now blocked.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaravanInventoryHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaravanInventoryHolder holder)) {
            return;
        }
        // Cancel EVERY click while the shop is open (including shift-clicks,
        // number-key swaps and double-click collects from the player's own
        // inventory) — the shop's display items must never be movable.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MarketGUI.SIZE) {
            return; // click in the player's own inventory — ignored
        }
        if (slot == MarketGUI.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        Caravan caravan = holder.getCaravan();
        // Stale window: caravan left, was deleted/reloaded, or markets were stopped.
        if (!caravanManager.isRunning() || caravanManager.getById(caravan.getId()) != caravan
                || caravan.getState() != Caravan.State.AT_MARKET) {
            player.closeInventory();
            player.sendMessage(Component.text("This caravan has closed its stall.").color(NamedTextColor.GRAY));
            return;
        }
        if (!player.hasPermission("migratingmarkets.use")) {
            player.closeInventory();
            return;
        }
        if (slot >= MarketGUI.ITEM_SLOTS || slot >= caravan.getInventory().size()) {
            return;
        }

        MarketItem item = caravan.getInventory().get(slot);
        ClickType click = event.getClick();
        boolean ok;
        switch (click) {
            case LEFT -> ok = handleBuy(player, caravan, item, 1);
            case SHIFT_LEFT -> ok = handleBuy(player, caravan, item, BULK_BUY);
            case RIGHT -> ok = handleSellHeld(player, item);
            case SHIFT_RIGHT -> ok = handleSellAll(player, item);
            default -> {
                return;
            }
        }
        if (configManager.isGuiSounds()) {
            Feedback.play(player, ok ? (click.isRightClick() ? configManager.getSellSound() : configManager.getBuySound())
                    : configManager.getDenySound());
        }
        refresh(event.getInventory(), caravan, player);
    }

    private boolean handleBuy(Player player, Caravan caravan, MarketItem item, int requested) {
        int customerLevel = caravanManager.getCustomerLevel(caravan, player.getUniqueId());
        if (item.getLevel() > customerLevel) {
            int needed = caravanManager.getPurchasesToNextLevel(caravan, player.getUniqueId());
            player.sendMessage(Component.text("This is a level " + item.getLevel() + " item — you're a level "
                    + customerLevel + " customer here. Buy " + needed + " more item(s) from this caravan to level up.")
                    .color(NamedTextColor.RED));
            return false;
        }

        int quantity = Math.min(requested, item.getCurrentStock());
        if (quantity <= 0) {
            player.sendMessage(Component.text("Out of stock — check back after the next market day.")
                    .color(NamedTextColor.RED));
            return false;
        }

        double discount = caravanManager.getEscortDiscount(caravan, player.getUniqueId());
        double unitPrice = item.getCurrentPrice() * (1 - discount);
        double totalCost = unitPrice * quantity;

        if (!economy.has(player, totalCost)) {
            player.sendMessage(Component.text("You need " + economy.format(totalCost) + " for that.")
                    .color(NamedTextColor.RED));
            return false;
        }
        if (!economy.withdraw(player, totalCost)) {
            player.sendMessage(Component.text("Payment failed.").color(NamedTextColor.RED));
            return false;
        }

        if (item.isLuckyItem()) {
            if (!luckyBlockBridge.give(player, item.getLuckyRarity(), quantity)) {
                player.sendMessage(Component.text("The LuckyBlock plugin isn't installed on this server — refunded.")
                        .color(NamedTextColor.RED));
                economy.refund(player, totalCost);
                return false;
            }
        } else if (item.isCommandItem()) {
            if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled(configManager.getLuckyPluginName())) {
                player.sendMessage(Component.text("The bridged plugin isn't installed on this server — refunded.")
                        .color(NamedTextColor.RED));
                economy.refund(player, totalCost);
                return false;
            }
            luckyBlockBridge.dispatchGiveCommand(player, item.getGiveCommand(), quantity);
        } else {
            int maxStack = Math.max(1, item.getMaterial().getMaxStackSize());
            int remaining = quantity;
            while (remaining > 0) {
                int amount = Math.min(maxStack, remaining);
                var leftover = player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
                leftover.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
                remaining -= amount;
            }
        }

        int levelBefore = customerLevel;
        item.onBought(quantity, configManager.getPriceShiftPerTrade(), configManager.getMaxPriceMultiplier());
        caravan.addPurchases(player.getUniqueId(), quantity);
        player.sendMessage(Component.text("Bought " + quantity + "x " + MarketGUI.prettyName(item.getMaterial())
                + " for " + economy.format(totalCost) + ".").color(NamedTextColor.GREEN));
        int levelAfter = caravanManager.getCustomerLevel(caravan, player.getUniqueId());
        if (levelAfter > levelBefore) {
            player.sendMessage(Component.text("★ You're now a level " + levelAfter + " customer at ")
                    .color(NamedTextColor.GOLD).append(caravan.getDisplayNameComponent())
                    .append(Component.text("! New goods unlocked.").color(NamedTextColor.GOLD)));
            if (configManager.isGuiSounds()) Feedback.play(player, "minecraft:entity.player.levelup", 0.7f, 1.2f);
        }
        return true;
    }

    /** Whether this stack may be sold into this shop slot. */
    private boolean matches(ItemStack stack, MarketItem item) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return false;
        if (item.isLuckyItem()) {
            // Must be a lucky block of THIS slot's rarity — any rarity used to
            // sell at the clicked slot's price (buy COMMON, sell as LEGENDARY).
            return luckyBlockBridge.isLuckyBlockOfRarity(stack, item.getLuckyRarity());
        }
        if (stack.getType() != item.getMaterial()) return false;
        if (luckyBlockBridge.isLuckyBlockItem(stack)) return false;
        if (!configManager.isAllowSellingDamaged() && stack.getItemMeta() instanceof Damageable d && d.getDamage() > 0) {
            return false;
        }
        return true;
    }

    private boolean handleSellHeld(Player player, MarketItem item) {
        if (item.isCommandItem()) {
            player.sendMessage(Component.text("This item can't be sold back.").color(NamedTextColor.RED));
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!matches(held, item)) {
            player.sendMessage(Component.text(item.isLuckyItem()
                    ? "Hold a lucky block of this rarity to sell it here."
                    : "Hold an undamaged " + MarketGUI.prettyName(item.getMaterial()) + " to sell it here.")
                    .color(NamedTextColor.RED));
            return false;
        }
        int quantity = held.getAmount();
        player.getInventory().setItemInMainHand(null);
        payout(player, item, quantity);
        return true;
    }

    /** Sells every matching stack in the player's main inventory (not armor/offhand). */
    private boolean handleSellAll(Player player, MarketItem item) {
        if (item.isCommandItem()) {
            player.sendMessage(Component.text("This item can't be sold back.").color(NamedTextColor.RED));
            return false;
        }
        Inventory inv = player.getInventory();
        int quantity = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (matches(stack, item)) {
                quantity += stack.getAmount();
                inv.setItem(i, null);
            }
        }
        if (quantity == 0) {
            player.sendMessage(Component.text("You don't have any of this to sell.").color(NamedTextColor.RED));
            return false;
        }
        payout(player, item, quantity);
        return true;
    }

    private void payout(Player player, MarketItem item, int quantity) {
        double totalPayout = item.getCurrentPrice() * configManager.getSellPriceRatio() * quantity;
        economy.deposit(player, totalPayout);
        item.onSold(quantity, configManager.getPriceShiftPerTrade(), configManager.getMinPriceMultiplier());
        player.sendMessage(Component.text("Sold " + quantity + "x for " + economy.format(totalPayout) + ".")
                .color(NamedTextColor.YELLOW));
    }

    private void refresh(Inventory inventory, Caravan caravan, Player player) {
        MarketGUI.render(inventory, caravan, caravanManager, configManager, economy, luckyBlockBridge, player);
    }
}
