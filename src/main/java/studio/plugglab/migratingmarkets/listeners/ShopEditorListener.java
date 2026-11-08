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
import org.bukkit.inventory.ItemStack;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.market.ShopEditorGUI;
import studio.plugglab.migratingmarkets.market.ShopEditorHolder;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Feedback;

public class ShopEditorListener implements Listener {

    private static final double DEFAULT_PRICE = 10.0;
    private static final int DEFAULT_LEVEL = 1;
    private static final String DEFAULT_CATEGORY = "custom";

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;

    public ShopEditorListener(CaravanManager caravanManager, ConfigManager configManager) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopEditorHolder)) return;
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
        if (!(event.getInventory().getHolder() instanceof ShopEditorHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("migratingmarkets.admin")) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == ShopEditorGUI.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= ShopEditorGUI.MAX_ITEMS) {
            return;
        }

        Caravan caravan = holder.getCaravan();
        if (caravanManager.getById(caravan.getId()) != caravan) {
            // Caravan was reloaded/deleted since this editor was opened.
            player.closeInventory();
            player.sendMessage(Component.text("That caravan was reloaded — reopen the editor.").color(NamedTextColor.GRAY));
            return;
        }
        var inventory = caravan.getInventory();
        ClickType click = event.getClick();

        if (slot < inventory.size()) {
            MarketItem item = inventory.get(slot);
            switch (click) {
                case RIGHT -> {
                    MarketItem removed = inventory.remove(slot);
                    caravan.getRemovedItemKeys().add(removed.getIdentityKey());
                    player.sendMessage(Component.text("Removed slot " + slot + " (" + removed.getMaterial() + ") from "
                            + caravan.getId() + ".").color(NamedTextColor.YELLOW));
                    changed(player, caravan, event);
                }
                case SHIFT_LEFT -> {
                    int next = item.getLevel() >= configManager.getMaxCustomerLevel() ? 1 : item.getLevel() + 1;
                    setLevel(player, caravan, item, slot, next, event);
                }
                case NUMBER_KEY -> setLevel(player, caravan, item, slot, event.getHotbarButton() + 1, event);
                case SHIFT_RIGHT -> {
                    item.setCurrentStock(item.getMaxStock());
                    player.sendMessage(Component.text("Restocked slot " + slot + ".").color(NamedTextColor.GREEN));
                    changed(player, caravan, event);
                }
                default -> player.sendMessage(Component.text("Right-click to remove, shift-left to cycle level, or "
                        + "/caravanadmin setprice " + caravan.getId() + " " + slot + " <price>.")
                        .color(NamedTextColor.GRAY));
            }
            return;
        }

        // Empty slot: left-click with a held item adds it.
        if (click != ClickType.LEFT) {
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
        newItem.setCustomized(true);
        caravan.getRemovedItemKeys().remove(newItem.getIdentityKey());
        inventory.add(newItem);
        player.sendMessage(Component.text("Added " + held.getType() + " to " + caravan.getId()
                + " at slot " + (inventory.size() - 1) + " (price " + DEFAULT_PRICE + ", level " + DEFAULT_LEVEL
                + "). Fine-tune with /caravanadmin setprice|setlevel.").color(NamedTextColor.GREEN));
        changed(player, caravan, event);
    }

    private void setLevel(Player player, Caravan caravan, MarketItem item, int slot, int level, InventoryClickEvent event) {
        item.setLevel(level, configManager.getStockPerLevel());
        item.setCustomized(true);
        player.sendMessage(Component.text("Slot " + slot + " is now level " + item.getLevel()
                + " (stock cap " + item.getMaxStock() + ").").color(NamedTextColor.GREEN));
        changed(player, caravan, event);
    }

    /** Re-renders the editor, closes stale market windows for this caravan, and persists the edit. */
    private void changed(Player player, Caravan caravan, InventoryClickEvent event) {
        ShopEditorGUI.render(event.getInventory(), caravan);
        caravanManager.closeViewers(caravan, false);
        caravanManager.saveSoon();
        if (configManager.isGuiSounds()) {
            Feedback.play(player, "minecraft:ui.button.click", 0.6f, 1.2f);
        }
    }
}
