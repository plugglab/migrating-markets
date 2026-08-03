package studio.plugglab.migratingmarkets.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.MarketGUI;
import studio.plugglab.migratingmarkets.util.ConfigManager;

import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "browse", "track");

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final LuckyBlockBridge luckyBlockBridge;

    public MarketCommand(CaravanManager caravanManager, ConfigManager configManager, EconomyProvider economy,
                          LuckyBlockBridge luckyBlockBridge) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
        this.economy = economy;
        this.luckyBlockBridge = luckyBlockBridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> sendInfo(sender);
            case "browse" -> browse(sender);
            case "track" -> track(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand '" + args[0] + "'.").color(NamedTextColor.RED));
                sendUsage(sender);
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /market <info|browse|track>").color(NamedTextColor.RED));
    }

    private void sendInfo(CommandSender sender) {
        if (caravanManager.getCaravans().isEmpty()) {
            sender.sendMessage(Component.text("No caravans are currently active.").color(NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("=== Traveling Caravans ===").color(NamedTextColor.GOLD));
        for (Caravan caravan : caravanManager.getCaravans()) {
            Component status = caravan.getState() == Caravan.State.AT_MARKET
                    ? Component.text("at " + caravan.getCurrentWaypoint().getName()).color(NamedTextColor.WHITE)
                    : Component.text("traveling").color(NamedTextColor.WHITE);
            Component line = Component.text()
                    .append(caravan.getDisplayNameComponent())
                    .append(Component.text(" — ").color(NamedTextColor.GRAY))
                    .append(status)
                    .build();
            sender.sendMessage(line);
        }
    }

    private void browse(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can browse a market.").color(NamedTextColor.RED));
            return;
        }
        Location loc = player.getLocation();
        double radius = configManager.getInteractionRadius();
        for (Caravan caravan : caravanManager.getCaravans()) {
            if (caravan.getState() != Caravan.State.AT_MARKET) continue;
            Location wpLoc = caravan.getCurrentWaypoint().toLocation();
            if (wpLoc == null || !wpLoc.getWorld().equals(loc.getWorld())) continue;
            if (wpLoc.distanceSquared(loc) <= radius * radius) {
                player.openInventory(MarketGUI.build(caravan, caravanManager, economy, luckyBlockBridge, player));
                return;
            }
        }
        player.sendMessage(Component.text("No caravan is set up near you right now. Try /market track.")
                .color(NamedTextColor.GRAY));
    }

    private void track(CommandSender sender) {
        if (caravanManager.getCaravans().isEmpty()) {
            sender.sendMessage(Component.text("No caravans are currently active.").color(NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("=== Caravan Whereabouts ===").color(NamedTextColor.GOLD));
        for (Caravan caravan : caravanManager.getCaravans()) {
            Component line;
            if (caravan.getState() == Caravan.State.AT_MARKET) {
                int daysLeft = Math.max(0, configManager.getDaysPerStop() - caravan.getDaysAtCurrentStop());
                line = Component.text()
                        .append(caravan.getDisplayNameComponent())
                        .append(Component.text(" is at ").color(NamedTextColor.GRAY))
                        .append(Component.text(caravan.getCurrentWaypoint().getName()).color(NamedTextColor.WHITE))
                        .append(Component.text(" (" + daysLeft + " market-days left)").color(NamedTextColor.DARK_GRAY))
                        .build();
            } else {
                line = Component.text()
                        .append(caravan.getDisplayNameComponent())
                        .append(Component.text(" is on the road — nobody knows exactly where they'll set up next.")
                                .color(NamedTextColor.GRAY))
                        .build();
            }
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(lower)).collect(Collectors.toList());
        }
        return List.of();
    }
}
