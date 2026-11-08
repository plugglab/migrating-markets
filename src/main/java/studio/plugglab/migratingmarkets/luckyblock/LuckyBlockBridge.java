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
 *
 * The API classes are NOT shaded into this jar, so on a server without
 * LuckyBlock every call would throw NoClassDefFoundError — which used to
 * crash onEnable (isAvailable() is called there). Every API touch now
 * goes through {@link #api()}, which treats a missing class as "not
 * installed".
 */
public class LuckyBlockBridge {

    private volatile boolean classMissing;

    /** The live API, or null if LuckyBlock isn't installed/enabled. */
    private LuckyBlockAPI api() {
        if (classMissing) return null;
        try {
            return LuckyBlockAPI.getOrNull();
        } catch (LinkageError e) {
            classMissing = true;
            return null;
        }
    }

    /** Whether the LuckyBlock plugin is actually loaded right now. */
    public boolean isAvailable() {
        return api() != null;
    }

    /**
     * Builds a real lucky block ItemStack of the given rarity, or null if
     * the bridge isn't available or the rarity name doesn't match
     * COMMON/RARE/EPIC/LEGENDARY.
     */
    public ItemStack buildItem(String rarityName, int amount) {
        LuckyBlockAPI api = api();
        if (api == null || rarityName == null) {
            return null;
        }
        try {
            LuckyBlockRarity rarity = LuckyBlockRarity.valueOf(rarityName.toUpperCase());
            return api.buildLuckyBlockItem(rarity, amount);
        } catch (IllegalArgumentException | LinkageError e) {
            return null;
        }
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
        LuckyBlockAPI api = api();
        if (api == null || stack == null) return false;
        try {
            return api.isLuckyBlockItem(stack);
        } catch (LinkageError e) {
            return false;
        }
    }

    /**
     * Whether a held stack is a lucky block of exactly this rarity. Used
     * for sell-back so a COMMON block can't be sold at a LEGENDARY slot's
     * price (previously any lucky block sold at whatever slot was
     * clicked — a straight money exploit).
     */
    public boolean isLuckyBlockOfRarity(ItemStack stack, String rarityName) {
        if (!isLuckyBlockItem(stack)) return false;
        ItemStack reference = buildItem(rarityName, 1);
        if (reference == null || !reference.hasItemMeta() || !stack.hasItemMeta()) return false;
        // Compare the string tags LuckyBlock stamps on its items (its rarity
        // key) rather than isSimilar(): the skull profile on each built item
        // isn't guaranteed to be identical between calls.
        var refPdc = reference.getItemMeta().getPersistentDataContainer();
        var heldPdc = stack.getItemMeta().getPersistentDataContainer();
        boolean comparedAny = false;
        for (var key : refPdc.getKeys()) {
            var type = org.bukkit.persistence.PersistentDataType.STRING;
            if (!refPdc.has(key, type)) continue;
            comparedAny = true;
            if (!heldPdc.has(key, type) || !refPdc.get(key, type).equals(heldPdc.get(key, type))) return false;
        }
        return comparedAny || reference.isSimilar(stack);
    }
}
