package studio.plugglab.migratingmarkets.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.util.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the 54-slot shop GUI for a caravan. The top five rows hold up to
 * 45 items; the bottom row is a control bar (caravan info, help, your
 * standing, close). Currency is whatever the active EconomyProvider says.
 */
public class MarketGUI {

    public static final int SIZE = 54;
    public static final int ITEM_SLOTS = 45;
    public static final int SLOT_CARAVAN_INFO = 45;
    public static final int SLOT_HELP = 47;
    public static final int SLOT_STANDING = 49;
    public static final int SLOT_CLOSE = 53;

    public static Inventory build(Caravan caravan, CaravanManager caravanManager, ConfigManager config,
                                  EconomyProvider economy, LuckyBlockBridge luckyBlockBridge, Player viewer) {
        CaravanInventoryHolder holder = new CaravanInventoryHolder(caravan);
        Component title = Component.text().append(caravan.getDisplayNameComponent())
                .append(Component.text(" — Market").color(NamedTextColor.DARK_GRAY))
                .build();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        render(inv, caravan, caravanManager, config, economy, luckyBlockBridge, viewer);
        return inv;
    }

    /** (Re)draws every slot — used after each trade so prices, stock and standing stay accurate. */
    public static void render(Inventory inv, Caravan caravan, CaravanManager caravanManager, ConfigManager config,
                              EconomyProvider economy, LuckyBlockBridge luckyBlockBridge, Player viewer) {
        inv.clear();
        List<MarketItem> items = caravan.getInventory();
        double discount = caravanManager.getEscortDiscount(caravan, viewer.getUniqueId());
        int customerLevel = caravanManager.getCustomerLevel(caravan, viewer.getUniqueId());

        for (int slot = 0; slot < items.size() && slot < ITEM_SLOTS; slot++) {
            inv.setItem(slot, buildItemStack(items.get(slot), discount, economy, luckyBlockBridge, customerLevel,
                    config.getSellPriceRatio()));
        }

        ItemStack filler = named(config.getGuiFillerMaterial(), Component.text(" "));
        for (int i = ITEM_SLOTS; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_CARAVAN_INFO, caravanInfo(caravan, caravanManager));
        inv.setItem(SLOT_HELP, help(economy));
        inv.setItem(SLOT_STANDING, standing(caravan, caravanManager, viewer, customerLevel, discount));
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, text("Close", NamedTextColor.RED)));
    }

    private static ItemStack caravanInfo(Caravan caravan, CaravanManager caravanManager) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(caravan.getDisplayNameComponent().decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (caravan.hasWaypoints()) {
            lore.add(text("Stop: ", NamedTextColor.GRAY).append(text(caravan.getCurrentWaypoint().getName(), NamedTextColor.WHITE)));
        }
        int days = caravanManager.getDaysLeft(caravan);
        lore.add(text("Leaves in: ", NamedTextColor.GRAY).append(text(days + " market-day(s)", NamedTextColor.WHITE)));
        long minutes = Math.max(1, Math.round(caravanManager.getMillisToNextMarketDay() / 60000.0));
        lore.add(text("Next restock: ", NamedTextColor.GRAY).append(text("~" + minutes + " min", NamedTextColor.WHITE)));
        lore.add(text("Specialty: ", NamedTextColor.GRAY).append(text(caravan.getSpecialty(), NamedTextColor.WHITE)));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack help(EconomyProvider economy) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text("How to trade", NamedTextColor.GOLD));
        meta.lore(List.of(
                text("Left-click: buy 1", NamedTextColor.GRAY),
                text("Shift + left-click: buy 8", NamedTextColor.GRAY),
                text("Right-click: sell the stack in your hand", NamedTextColor.GRAY),
                text("Shift + right-click: sell all matching items", NamedTextColor.GRAY),
                Component.empty(),
                text("Prices are in " + economy.currencyName() + ".", NamedTextColor.DARK_GRAY),
                text("▲ above usual price  ▼ below usual price", NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack standing(Caravan caravan, CaravanManager caravanManager, Player viewer,
                                      int customerLevel, double discount) {
        ItemStack info = new ItemStack(Material.EMERALD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("Your Standing", NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(text("Customer level: " + customerLevel, NamedTextColor.AQUA));
        int toNext = caravanManager.getPurchasesToNextLevel(caravan, viewer.getUniqueId());
        lore.add(text(toNext > 0 ? "(" + toNext + " more purchase(s) to level up)" : "(max level here)",
                NamedTextColor.DARK_GRAY));
        lore.add(text("Escort reputation: " + caravan.getReputation(viewer.getUniqueId()), NamedTextColor.GRAY));
        String escortTitle = caravanManager.getEscortTitle(caravan, viewer.getUniqueId());
        if (escortTitle != null) {
            lore.add(text(escortTitle, NamedTextColor.GOLD));
            lore.add(text(String.format("%.0f%% discount active", discount * 100), NamedTextColor.GRAY));
        } else {
            lore.add(text("Defend this caravan from mobs on the road", NamedTextColor.GRAY));
            lore.add(text("to earn discounts.", NamedTextColor.GRAY));
        }
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        return info;
    }

    public static ItemStack buildItemStack(MarketItem item, double discount, EconomyProvider economy,
                                           LuckyBlockBridge luckyBlockBridge, int customerLevel, double sellRatio) {
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
        if (meta == null) {
            return stack; // e.g. AIR configured by mistake
        }

        List<Component> lore = new ArrayList<>();
        if (usedRealLuckyItem) {
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        lore.add(text(stars(item.getLevel()) + " Level " + item.getLevel() + " · " + item.getCategory(),
                NamedTextColor.DARK_GRAY));
        if (locked) {
            lore.add(text("Locked — requires customer level " + item.getLevel(), NamedTextColor.RED));
            lore.add(text("You are level " + customerLevel + " here. Keep buying to level up!", NamedTextColor.GRAY));
            meta.lore(lore);
            stack.setItemMeta(meta);
            return stack;
        }
        lore.add(text("Stock: " + item.getCurrentStock() + " / " + item.getMaxStock(),
                item.getCurrentStock() > 0 ? NamedTextColor.GRAY : NamedTextColor.RED));

        double price = item.getCurrentPrice() * (1 - discount);
        Component buy = text("Buy: " + economy.format(price), NamedTextColor.GREEN).append(trendArrow(item));
        if (discount > 0) {
            buy = buy.append(text(String.format(" (-%.0f%%)", discount * 100), NamedTextColor.GOLD));
        }
        lore.add(buy);
        if (!item.isCommandItem()) {
            lore.add(text("Sell: " + economy.format(item.getCurrentPrice() * sellRatio), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(text("Left-click: buy 1 · Shift: buy 8", NamedTextColor.GRAY));
        if (item.isCommandItem()) {
            lore.add(text("(bridged item — not sellable)", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(text("Right-click: sell held · Shift: sell all", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component trendArrow(MarketItem item) {
        return switch (item.getTrend()) {
            case 1 -> text(" ▲", NamedTextColor.RED);
            case -1 -> text(" ▼", NamedTextColor.AQUA);
            default -> Component.empty();
        };
    }

    private static String stars(int level) {
        int n = Math.max(1, Math.min(level, 5));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    private static Component text(String s, TextColor color) {
        return Component.text(s).color(color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static NamedTextColor levelColor(int level) {
        return switch (Math.max(1, Math.min(level, 5))) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.GREEN;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.GOLD;
        };
    }

    public static String prettyName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
