package studio.plugglab.migratingmarkets.market;

import org.bukkit.Material;

/**
 * A tradeable item offered by a caravan's specialty.
 *
 * Each item has a level (1-5): higher-level items carry a higher stock
 * cap per market-day restock (so a level-5 item might restock 30 units
 * while a level-1 item only restocks 6), on top of the usual live
 * buy/sell price drift.
 *
 * An item can either be a normal Material the player receives directly,
 * or — for bridged content like lucky blocks that this plugin doesn't
 * own — a "command item": buying it dispatches a configured console
 * command instead of granting an ItemStack. Command items use a display
 * material purely for the GUI icon and can't be sold back.
 */
public class MarketItem {

    private final Material material;
    private double basePrice;
    private String category;
    private int level;
    private int maxStock;
    private final String giveCommand; // null for normal items — generic command-bridge fallback
    private final String luckyRarity; // null for normal items — real LuckyBlock API rarity (COMMON/RARE/EPIC/LEGENDARY)

    private double multiplier = 1.0;
    private int currentStock;

    public MarketItem(Material material, double basePrice, String category, int level, int maxStock) {
        this(material, basePrice, category, level, maxStock, null, null);
    }

    public MarketItem(Material material, double basePrice, String category, int level, int maxStock,
                       String giveCommand, String luckyRarity) {
        this.material = material;
        this.basePrice = basePrice;
        this.category = category;
        this.level = level;
        this.maxStock = maxStock;
        this.giveCommand = giveCommand;
        this.luckyRarity = luckyRarity;
        this.currentStock = maxStock;
    }

    public Material getMaterial() {
        return material;
    }

    public String getCategory() {
        return category;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxStock() {
        return maxStock;
    }

    /** Admin edit: change the base price (e.g. via /caravanadmin setprice). */
    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    /** Admin edit: change the category label shown in the GUI. */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Admin edit: change the level, recomputing the stock cap from the
     * given stock-per-level setting and clamping current stock to fit.
     */
    public void setLevel(int level, int stockPerLevel) {
        this.level = Math.max(1, level);
        this.maxStock = Math.max(1, this.level * stockPerLevel);
        this.currentStock = Math.min(currentStock, maxStock);
    }

    public int getCurrentStock() {
        return currentStock;
    }

    /** True for the generic command-bridge fallback (not sellable — no physical item was ever given). */
    public boolean isCommandItem() {
        return giveCommand != null;
    }

    public String getGiveCommand() {
        return giveCommand;
    }

    /** True for a real LuckyBlock-API-backed item (sellable, since a real ItemStack is granted). */
    public boolean isLuckyItem() {
        return luckyRarity != null;
    }

    public String getLuckyRarity() {
        return luckyRarity;
    }

    /** True for any bridged item (command or lucky-rarity), used for GUI marking. */
    public boolean isBridgedItem() {
        return giveCommand != null || luckyRarity != null;
    }

    public double getCurrentPrice() {
        return round(basePrice * multiplier);
    }

    public boolean canBuy(int quantity) {
        return currentStock >= quantity;
    }

    /** Called when a player buys `quantity` units: price trends up, stock drops. */
    public void onBought(int quantity, double shiftPct, double maxMultiplier) {
        multiplier = Math.min(maxMultiplier, multiplier + shiftPct * quantity);
        currentStock = Math.max(0, currentStock - quantity);
    }

    /** Called when a player sells `quantity` units: price trends down, stock rises (capped). */
    public void onSold(int quantity, double shiftPct, double minMultiplier) {
        multiplier = Math.max(minMultiplier, multiplier - shiftPct * quantity);
        currentStock = Math.min(maxStock, currentStock + quantity);
    }

    /** Called once per market-day: price eases back toward base and stock fully replenishes. */
    public void restock(double decayPct) {
        if (multiplier > 1.0) {
            multiplier = Math.max(1.0, multiplier - decayPct);
        } else if (multiplier < 1.0) {
            multiplier = Math.min(1.0, multiplier + decayPct);
        }
        currentStock = maxStock;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setCurrentStock(int currentStock) {
        this.currentStock = Math.max(0, Math.min(maxStock, currentStock));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
