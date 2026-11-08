package studio.plugglab.migratingmarkets;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.caravan.CaravanTracker;
import studio.plugglab.migratingmarkets.commands.CaravanAdminCommand;
import studio.plugglab.migratingmarkets.commands.MarketCommand;
import studio.plugglab.migratingmarkets.data.CustomRouteStore;
import studio.plugglab.migratingmarkets.data.DataStore;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.economy.EmeraldEconomyProvider;
import studio.plugglab.migratingmarkets.economy.VaultEconomyProvider;
import studio.plugglab.migratingmarkets.listeners.CaravanEntityListener;
import studio.plugglab.migratingmarkets.listeners.CaravanInteractListener;
import studio.plugglab.migratingmarkets.listeners.EscortListener;
import studio.plugglab.migratingmarkets.listeners.JoinListener;
import studio.plugglab.migratingmarkets.listeners.MarketGUIListener;
import studio.plugglab.migratingmarkets.listeners.ShopEditorListener;
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
    private CaravanTracker tracker;
    private EconomyProvider economyProvider;
    private LuckyBlockBridge luckyBlockBridge;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        economyProvider = setupEconomy();
        luckyBlockBridge = new LuckyBlockBridge();

        dataStore = new DataStore(this);
        CustomRouteStore customRouteStore = new CustomRouteStore(this);
        caravanManager = new CaravanManager(this, configManager, dataStore, customRouteStore, luckyBlockBridge);
        caravanManager.initialize();

        tracker = new CaravanTracker(this, caravanManager, configManager);
        tracker.start();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new CaravanInteractListener(caravanManager, configManager, economyProvider, luckyBlockBridge), this);
        pm.registerEvents(new MarketGUIListener(caravanManager, configManager, economyProvider, luckyBlockBridge), this);
        pm.registerEvents(new EscortListener(caravanManager, configManager), this);
        pm.registerEvents(new ShopEditorListener(caravanManager, configManager), this);
        pm.registerEvents(new CaravanEntityListener(caravanManager), this);
        pm.registerEvents(new JoinListener(this), this);

        MarketCommand marketCommand = new MarketCommand(caravanManager, configManager, economyProvider,
                luckyBlockBridge, tracker);
        bind("market", marketCommand, marketCommand);

        CaravanAdminCommand adminCommand = new CaravanAdminCommand(this, caravanManager, configManager);
        bind("caravanadmin", adminCommand, adminCommand);

        if (pm.isPluginEnabled("PlaceholderAPI")) {
            try {
                new studio.plugglab.migratingmarkets.hooks.MarketPlaceholders(this, caravanManager).register();
                getLogger().info("Hooked into PlaceholderAPI (%migratingmarkets_...% placeholders).");
            } catch (LinkageError | RuntimeException e) {
                getLogger().warning("PlaceholderAPI is installed but the hook failed: " + e);
            }
        }

        if (luckyBlockBridge.isAvailable()) {
            getLogger().info("Lucky-block bridge active — LuckyBlock API found.");
        } else {
            getLogger().info("Lucky-block bridge inactive (LuckyBlock not installed) — the fortune caravan "
                    + "(requires-plugin: " + configManager.getLuckyPluginName() + ") will not spawn.");
        }

        if (configManager.isUpdateCheckerEnabled()) {
            updateChecker = new UpdateChecker(this);
            updateChecker.check();
        }

        getLogger().info("MigratingMarkets enabled — " + caravanManager.getCaravans().size()
                + " caravan(s) loaded, economy: " + economyProvider.currencyName() + ". (PluggLab Studio)");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private EconomyProvider setupEconomy() {
        if ("vault".equalsIgnoreCase(configManager.getEconomyProvider())) {
            try {
                VaultEconomyProvider vault = VaultEconomyProvider.tryCreate(this);
                if (vault != null) {
                    return vault;
                }
            } catch (LinkageError e) {
                // Vault classes not present at all
            }
            getLogger().warning("economy.provider is set to 'vault' but no Vault economy plugin was found — "
                    + "falling back to emeralds.");
        }
        return new EmeraldEconomyProvider();
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.stop();
        }
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

    public CaravanTracker getTracker() {
        return tracker;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
