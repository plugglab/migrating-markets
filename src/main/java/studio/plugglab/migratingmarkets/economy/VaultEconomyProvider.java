package studio.plugglab.migratingmarkets.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

/**
 * Wraps whatever economy plugin is registered through Vault (Essentials,
 * CMI, etc). Only constructed when Vault and a real economy provider are
 * both confirmed present — see VaultEconomyProvider#tryCreate.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    /** Returns null if Vault isn't installed or has no economy plugin registered. */
    public static VaultEconomyProvider tryCreate(org.bukkit.plugin.Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        var rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return null;
        }
        return new VaultEconomyProvider(rsp.getProvider());
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

    @Override
    public String currencyName() {
        String plural = economy.currencyNamePlural();
        return (plural == null || plural.isBlank()) ? "currency" : plural;
    }
}
