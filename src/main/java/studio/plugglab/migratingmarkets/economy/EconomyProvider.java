package studio.plugglab.migratingmarkets.economy;

import org.bukkit.entity.Player;

/**
 * Abstracts "money" so the market GUI doesn't need to know whether a
 * server is using plain emeralds or a Vault-backed economy plugin.
 */
public interface EconomyProvider {

    boolean has(Player player, double amount);

    /** Returns true if the withdrawal succeeded. */
    boolean withdraw(Player player, double amount);

    /** Returns true if the deposit succeeded. */
    boolean deposit(Player player, double amount);

    /**
     * Gives back exactly what {@link #withdraw} took for this amount (used
     * when a purchase can't be delivered). Defaults to deposit; providers
     * that round on withdraw override it so refunds are never short.
     */
    default boolean refund(Player player, double amount) {
        return deposit(player, amount);
    }

    /** Human-readable amount, e.g. "12 emeralds" or "$12.00". */
    String format(double amount);

    /** Short name for the currency, used in GUI lore, e.g. "emeralds". */
    String currencyName();
}
