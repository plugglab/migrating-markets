package studio.plugglab.migratingmarkets.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the 54-slot shop GUI for a caravan. Currency is whatever the
 * active EconomyProvider says (emeralds by default, or a Vault-backed
 * balance). Items show their level and remaining stock so higher-level
 * goods visibly restock deeper each market-day.
 */
public class MarketGUI {

    public static final int SIZE = 54;

    public static Inventory build(Caravan caravan, CaravanManager caravanManager, EconomyProvider economy,
                                   LuckyBlockBridge luckyBlockBridge, Player viewer) {
        CaravanInventoryHolder holder = new CaravanInventoryHolder(caravan);
        Component title = Component.text().append(caravan.getDisplayNameComponent())
                .append(Component.text(" — Market").color(NamedTextColor.DARK_PURPLE))
                .build();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        List<MarketItem> items = caravan.getInventory();
        double discount = caravanManager.getEscortDiscount(caravan, viewer.getUniqueId());
        int customerLevel = caravanManager.getCustomerLevel(caravan, viewer.getUniqueId());

        int slot = 0;
        for (MarketItem item : items) {
            if (slot >= 45) break;
            inv.setItem(slot, buildItemStack(item, discount, economy, luckyBlockBridge, customerLevel));
            slot++;
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        String title2 = caravanManager.getEscortTitle(caravan, viewer.getUniqueId());
        ItemStack info = new ItemStack(Material.EMERALD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Your Standing").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Customer level: " + customerLevel).color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        int toNext = caravanManager.getPurchasesToNextLevel(caravan, viewer.getUniqueId());
        if (toNext > 0) {
            lore.add(Component.text("(" + toNext + " more purchase(s) to level up)").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("(max level here)").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (title2 != null) {
            lore.add(Component.text(title2).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(String.format("%.0f%% discount active", discount * 100))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Defend this caravan from mobs to earn discounts.").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        return inv;
    }

    public static ItemStack buildItemStack(MarketItem item, double discount, EconomyProvider economy,
                                            LuckyBlockBridge luckyBlockBridge, int customerLevel) {
        boolean locked = item.getLevel() > customerLevel;
        ItemStack stack = null;
        boolean usedRealLuckyItem = false;
        if (item.isLuckyItem() && luckyBlockBridge != null) {
            // Show the real skinned lucky-block item as the icon, keeping its own
            // name/lore from the LuckyBlock plugin instead of a generic placeholder.
            stack = luckyBlockBridge.buildItem(item.getLuckyRarity(), 1);
            usedRealLuckyItem = stack != null;
        }
        if (stack == null) {
            stack = new ItemStack(item.getMaterial());
        }
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = new ArrayList<>();
        if (usedRealLuckyItem) {
            // Keep the API's own flavor name/lore, just tack on shop info below it.
            List<Component> existingLore = meta.lore();
            if (existingLore != null) lore.addAll(existingLore);
            lore.add(Component.empty());
        } else {
            Component name = Component.text((locked ? "🔒 " : "") + prettyName(item.getMaterial()))
                    .color(locked ? NamedTextColor.DARK_GRAY : levelColor(item.getLevel()))
                    .decoration(TextDecoration.ITALIC, false);
            if (item.isCommandItem()) {
                name = name.append(Component.text(" ⚡").color(NamedTextColor.LIGHT_PURPLE));
            }
            meta.displayName(name);
        }

        double price = item.getCurrentPrice() * (1 - discount);
        lore.add(Component.text("Level " + item.getLevel() + " · " + item.getCategory())
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        if (locked) {
            lore.add(Component.text("Locked — requires customer level " + item.getLevel())
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("You are level " + customerLevel + " here. Keep buying to level up!")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
            return stack;
        }
        lore.add(Component.text("Stock: " + item.getCurrentStock() + " / " + item.getMaxStock())
                .color(item.getCurrentStock() > 0 ? NamedTextColor.GRAY : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Buy: " + economy.format(price))
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        if (!item.isCommandItem()) {
            lore.add(Component.text("Sell: " + economy.format(item.getCurrentPrice() * 0.5))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Left-click: buy 1").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift-click: buy 8").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (item.isCommandItem()) {
            lore.add(Component.text("(bridged item — not sellable)").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Right-click: sell held stack").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static NamedTextColor levelColor(int level) {
        return switch (Math.max(1, Math.min(level, 5))) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.GREEN;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.GOLD;
        };
    }

    private static String prettyName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
