package studio.plugglab.migratingmarkets.luckyblock;

import com.lucky.luckyblock.LuckyBlockRarity;
import com.lucky.luckyblock.api.LuckyBlockAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Bridge to PluggLab Studio's LuckyBlock 2.0 plugin using its real
 * developer API (com.lucky.luckyblock.api.LuckyBlockAPI) — no reflection
 * or command-dispatch guessing needed since the API jar is available at
 * compile time (see pom.xml) and the actual plugin provides it at
 * runtime via LuckyBlockAPI.getOrNull().
 */
public class LuckyBlockBridge {

    /** Whether the LuckyBlock plugin is actually loaded right now. */
    public boolean isAvailable() {
        return LuckyBlockAPI.getOrNull() != null;
    }

    /**
     * Builds a real lucky block ItemStack of the given rarity, or null if
     * the bridge isn't available or the rarity name doesn't match
     * COMMON/RARE/EPIC/LEGENDARY.
     */
    public ItemStack buildItem(String rarityName, int amount) {
        LuckyBlockAPI api = LuckyBlockAPI.getOrNull();
        if (api == null) {
            return null;
        }
        LuckyBlockRarity rarity;
        try {
            rarity = LuckyBlockRarity.valueOf(rarityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return api.buildLuckyBlockItem(rarity, amount);
    }

    /** Gives `amount` lucky blocks of the given rarity directly into the player's inventory. */
    public boolean give(Player player, String rarityName, int amount) {
        ItemStack stack = buildItem(rarityName, amount);
        if (stack == null) {
            return false;
        }
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
        return true;
    }

    /**
     * Generic fallback for bridging to some OTHER command-driven plugin
     * via an item's `give-command` in config.yml — unrelated to the real
     * LuckyBlockAPI integration above. Dispatches the given command
     * template as console, substituting %player% and %amount%.
     */
    public void dispatchGiveCommand(Player player, String commandTemplate, int amount) {
        String command = commandTemplate
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(amount));
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
    }

    /** Whether a held ItemStack is a lucky block item (any rarity), for sell-back checks. */
    public boolean isLuckyBlockItem(ItemStack stack) {
        LuckyBlockAPI api = LuckyBlockAPI.getOrNull();
        return api != null && stack != null && api.isLuckyBlockItem(stack);
    }
}
