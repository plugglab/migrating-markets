package studio.plugglab.migratingmarkets;

import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.commands.CaravanAdminCommand;
import studio.plugglab.migratingmarkets.commands.MarketCommand;
import studio.plugglab.migratingmarkets.data.DataStore;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.economy.EmeraldEconomyProvider;
import studio.plugglab.migratingmarkets.economy.VaultEconomyProvider;
import studio.plugglab.migratingmarkets.listeners.CaravanInteractListener;
import studio.plugglab.migratingmarkets.listeners.EscortListener;
import studio.plugglab.migratingmarkets.listeners.MarketGUIListener;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.util.ConfigManager;

/**
 * MigratingMarkets — advanced traveling caravan market system.
 * Author: PluggLab Studio
 */
public class MigratingMarkets extends JavaPlugin {

    private ConfigManager configManager;
    private DataStore dataStore;
    private CaravanManager caravanManager;
    private EconomyProvider economyProvider;
    private LuckyBlockBridge luckyBlockBridge;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        economyProvider = setupEconomy();
        luckyBlockBridge = new LuckyBlockBridge();

        dataStore = new DataStore(this);
        caravanManager = new CaravanManager(this, configManager, dataStore);
        caravanManager.initialize();

        getServer().getPluginManager().registerEvents(
                new CaravanInteractListener(caravanManager, economyProvider, luckyBlockBridge), this);
        getServer().getPluginManager().registerEvents(
                new MarketGUIListener(caravanManager, configManager, economyProvider, luckyBlockBridge), this);
        getServer().getPluginManager().registerEvents(
                new EscortListener(caravanManager, configManager), this);

        MarketCommand marketCommand = new MarketCommand(caravanManager, configManager, economyProvider, luckyBlockBridge);
        getCommand("market").setExecutor(marketCommand);
        getCommand("market").setTabCompleter(marketCommand);

        CaravanAdminCommand adminCommand = new CaravanAdminCommand(caravanManager);
        getCommand("caravanadmin").setExecutor(adminCommand);
        getCommand("caravanadmin").setTabCompleter(adminCommand);

        if (luckyBlockBridge.isAvailable()) {
            getLogger().info("Lucky-block bridge active — found '" + configManager.getLuckyPluginName() + "'.");
        }

        getLogger().info("MigratingMarkets enabled — " + caravanManager.getCaravans().size()
                + " caravan(s) loaded, economy: " + economyProvider.currencyName() + ". (PluggLab Studio)");
    }

    private EconomyProvider setupEconomy() {
        if ("vault".equalsIgnoreCase(configManager.getEconomyProvider())) {
            VaultEconomyProvider vault = VaultEconomyProvider.tryCreate(this);
            if (vault != null) {
                return vault;
            }
            getLogger().warning("economy.provider is set to 'vault' but no Vault economy plugin was found — "
                    + "falling back to emeralds.");
        }
        return new EmeraldEconomyProvider();
    }

    @Override
    public void onDisable() {
        if (caravanManager != null) {
            caravanManager.shutdown();
        }
        getLogger().info("MigratingMarkets disabled — caravan state saved.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CaravanManager getCaravanManager() {
        return caravanManager;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }
}
