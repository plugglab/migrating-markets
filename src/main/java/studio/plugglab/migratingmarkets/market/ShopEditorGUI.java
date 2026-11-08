package studio.plugglab.migratingmarkets.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import studio.plugglab.migratingmarkets.caravan.Caravan;

import java.util.ArrayList;
import java.util.List;

/**
 * Live in-game shop editor. Existing items show their slot number (for
 * use with /caravanadmin setprice|setlevel|removeitem).
 * - Left-click an empty slot while holding an item: add it (price 10, level 1).
 * - Right-click an item: remove it.
 * - Shift + left-click an item: cycle its level (1 → max → 1).
 * - Number key 1-9 while hovering an item: set its level directly.
 * - Shift + right-click an item: restock it to full.
 */
public class ShopEditorGUI {

    public static final int SIZE = 54;
    public static final int MAX_ITEMS = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_CLOSE = 53;

    public static Inventory build(Caravan caravan) {
        ShopEditorHolder holder = new ShopEditorHolder(caravan);
        Component title = Component.text().append(caravan.getDisplayNameComponent())
                .append(Component.text(" — Shop Editor").color(NamedTextColor.DARK_RED))
                .build();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        render(inv, caravan);
        return inv;
    }

    public static void render(Inventory inv, Caravan caravan) {
        inv.clear();
        List<MarketItem> items = caravan.getInventory();
        for (int i = 0; i < items.size() && i < MAX_ITEMS; i++) {
            inv.setItem(i, buildEditorStack(items.get(i), i));
        }

        ItemStack border = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = MAX_ITEMS; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("How to edit", NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(text("Left-click empty slot: add held item", NamedTextColor.GRAY));
        lore.add(text("  (starts at 10 price, level 1)", NamedTextColor.DARK_GRAY));
        lore.add(text("Right-click item: remove it", NamedTextColor.GRAY));
        lore.add(text("Shift + left-click item: cycle level", NamedTextColor.GRAY));
        lore.add(text("Number key 1-9 on item: set level", NamedTextColor.GRAY));
        lore.add(text("Shift + right-click item: restock it", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("Prices: /caravanadmin setprice " + caravan.getId() + " <slot> <price>", NamedTextColor.DARK_GRAY));
        lore.add(text(items.size() + " / " + MAX_ITEMS + " slots used", NamedTextColor.DARK_GRAY));
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(SLOT_INFO, info);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(text("Close", NamedTextColor.RED));
        close.setItemMeta(closeMeta);
        inv.setItem(SLOT_CLOSE, close);
    }

    private static ItemStack buildEditorStack(MarketItem item, int slot) {
        ItemStack stack = new ItemStack(item.getMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(text("[" + slot + "] " + MarketGUI.prettyName(item.getMaterial()), NamedTextColor.AQUA));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        List<Component> lore = new ArrayList<>();
        lore.add(text("Level " + item.getLevel() + " · " + item.getCategory(), MarketGUI.levelColor(item.getLevel())));
        lore.add(text("Base price: " + item.getBasePrice() + "  (now " + item.getCurrentPrice() + ")", NamedTextColor.GREEN));
        lore.add(text("Stock: " + item.getCurrentStock() + " / " + item.getMaxStock(), NamedTextColor.GRAY));
        if (item.isBridgedItem()) {
            lore.add(text(item.isLuckyItem() ? "Lucky rarity: " + item.getLuckyRarity() : "Bridged (command item)",
                    NamedTextColor.LIGHT_PURPLE));
        }
        if (item.isCustomized()) {
            lore.add(text("Edited in game (overrides config.yml)", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(text("Right-click to remove", NamedTextColor.RED));
        lore.add(text("Shift-left: cycle level · 1-9: set level", NamedTextColor.GRAY));
        lore.add(text("Shift-right: restock", NamedTextColor.GRAY));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component text(String s, TextColor color) {
        return Component.text(s).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
