package studio.plugglab.migratingmarkets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.market.ShopEditorGUI;
import studio.plugglab.migratingmarkets.market.ShopEditorHolder;
import studio.plugglab.migratingmarkets.util.ConfigManager;

public class ShopEditorListener implements Listener {

    private static final double DEFAULT_PRICE = 10.0;
    private static final int DEFAULT_LEVEL = 1;
    private static final String DEFAULT_CATEGORY = "custom";

    private final ConfigManager configManager;

    public ShopEditorListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopEditorHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("migratingmarkets.admin")) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= ShopEditorGUI.MAX_ITEMS) {
            return;
        }

        Caravan caravan = holder.getCaravan();
        var inventory = caravan.getInventory();

        if (slot < inventory.size()) {
            // Filled slot: right-click removes it.
            if (event.getClick() == ClickType.RIGHT) {
                MarketItem removed = inventory.remove(slot);
                player.sendMessage(Component.text("Removed slot " + slot + " (" + removed.getMaterial() + ") from "
                        + caravan.getId() + ".").color(NamedTextColor.YELLOW));
                refresh(event, caravan);
            } else {
                player.sendMessage(Component.text("Right-click to remove this item, or use "
                        + "/caravanadmin setprice|setlevel " + caravan.getId() + " " + slot + " <value>.")
                        .color(NamedTextColor.GRAY));
            }
            return;
        }

        // Empty slot: left-click with a held item adds it.
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        if (inventory.size() >= ShopEditorGUI.MAX_ITEMS) {
            player.sendMessage(Component.text("This shop is full (45 item slots max).").color(NamedTextColor.RED));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage(Component.text("Hold an item in your main hand, then click an empty slot to add it.")
                    .color(NamedTextColor.GRAY));
            return;
        }

        int maxStock = Math.max(1, DEFAULT_LEVEL * configManager.getStockPerLevel());
        MarketItem newItem = new MarketItem(held.getType(), DEFAULT_PRICE, DEFAULT_CATEGORY, DEFAULT_LEVEL, maxStock);
        inventory.add(newItem);
        player.sendMessage(Component.text("Added " + held.getType() + " to " + caravan.getId()
                + " at slot " + (inventory.size() - 1) + " (price " + DEFAULT_PRICE + ", level " + DEFAULT_LEVEL
                + "). Fine-tune with /caravanadmin setprice|setlevel.").color(NamedTextColor.GREEN));
        refresh(event, caravan);
    }

    private void refresh(InventoryClickEvent event, Caravan caravan) {
        event.getInventory().clear();
        var refreshed = ShopEditorGUI.build(caravan);
        for (int i = 0; i < ShopEditorGUI.SIZE; i++) {
            event.getInventory().setItem(i, refreshed.getItem(i));
        }
    }
}
