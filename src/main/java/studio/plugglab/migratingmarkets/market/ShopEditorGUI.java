package studio.plugglab.migratingmarkets.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import studio.plugglab.migratingmarkets.caravan.Caravan;

import java.util.ArrayList;
import java.util.List;

/**
 * Live in-game shop editor. Existing items show their slot number (for
 * use with /caravanadmin setprice|setlevel|removeitem) and can be
 * removed with a right-click. Empty slots accept a new item: hold
 * something in your main hand and left-click an empty slot to add it
 * as a level-1, 10-price starter entry — then fine-tune it with
 * /caravanadmin setprice / setlevel.
 */
public class ShopEditorGUI {

    public static final int SIZE = 54;
    public static final int MAX_ITEMS = 45;

    public static Inventory build(Caravan caravan) {
        ShopEditorHolder holder = new ShopEditorHolder(caravan);
        Component title = Component.text().append(caravan.getDisplayNameComponent())
                .append(Component.text(" — Shop Editor").color(NamedTextColor.DARK_RED))
                .build();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

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
        infoMeta.displayName(Component.text("How to edit").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Left-click empty slot: add held item").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  (starts at 10 price, level 1)").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click filled slot: remove it").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Then use /caravanadmin setprice|setlevel").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("<id> <slot> <value> to fine-tune.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        return inv;
    }

    private static ItemStack buildEditorStack(MarketItem item, int slot) {
        ItemStack stack = new ItemStack(item.getMaterial());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("[" + slot + "] " + prettyName(item.getMaterial()))
                .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level " + item.getLevel() + " · " + item.getCategory())
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Base price: " + item.getBasePrice()).color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Stock cap: " + item.getMaxStock()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (item.isBridgedItem()) {
            lore.add(Component.text(item.isLuckyItem() ? "Lucky rarity: " + item.getLuckyRarity() : "Bridged (command item)")
                    .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to remove").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
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
