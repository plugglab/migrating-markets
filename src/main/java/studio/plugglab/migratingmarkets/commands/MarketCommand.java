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
import studio.plugglab.migratingmarkets.caravan.CaravanTracker;
import studio.plugglab.migratingmarkets.economy.EconomyProvider;
import studio.plugglab.migratingmarkets.luckyblock.LuckyBlockBridge;
import studio.plugglab.migratingmarkets.market.MarketGUI;
import studio.plugglab.migratingmarkets.util.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "browse", "track", "where", "stopwhere");

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final LuckyBlockBridge luckyBlockBridge;
    private final CaravanTracker tracker;

    public MarketCommand(CaravanManager caravanManager, ConfigManager configManager, EconomyProvider economy,
                         LuckyBlockBridge luckyBlockBridge, CaravanTracker tracker) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
        this.economy = economy;
        this.luckyBlockBridge = luckyBlockBridge;
        this.tracker = tracker;
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
            case "where" -> where(sender, args);
            case "stopwhere" -> {
                if (sender instanceof Player player && tracker.untrack(player)) {
                    player.sendActionBar(Component.empty());
                    player.sendMessage(Component.text("Stopped tracking.").color(NamedTextColor.GRAY));
                }
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand '" + args[0] + "'.").color(NamedTextColor.RED));
                sendUsage(sender);
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /market <info|browse|track|where [id]|stopwhere>").color(NamedTextColor.RED));
    }

    private Component status(Caravan caravan) {
        if (!caravan.hasWaypoints()) {
            return Component.text("not placed yet").color(NamedTextColor.DARK_GRAY);
        }
        return caravan.getState() == Caravan.State.AT_MARKET
                ? Component.text("at " + caravan.getCurrentWaypoint().getName()).color(NamedTextColor.WHITE)
                : Component.text("traveling").color(NamedTextColor.WHITE);
    }

    private void sendInfo(CommandSender sender) {
        if (caravanManager.getCaravans().isEmpty()) {
            sender.sendMessage(Component.text("No caravans are currently active.").color(NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("=== Traveling Caravans ===").color(NamedTextColor.GOLD));
        for (Caravan caravan : caravanManager.getCaravans()) {
            sender.sendMessage(Component.text()
                    .append(caravan.getDisplayNameComponent())
                    .append(Component.text(" — ").color(NamedTextColor.GRAY))
                    .append(status(caravan))
                    .build());
        }
        sender.sendMessage(Component.text("Tip: /market where to get directions to the nearest market.")
                .color(NamedTextColor.DARK_GRAY));
    }

    private void browse(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can browse a market.").color(NamedTextColor.RED));
            return;
        }
        if (!caravanManager.isRunning()) {
            player.sendMessage(Component.text("The markets are closed right now.").color(NamedTextColor.GRAY));
            return;
        }
        Location loc = player.getLocation();
        double radius = configManager.getInteractionRadius();
        for (Caravan caravan : caravanManager.getCaravans()) {
            Location market = caravanManager.getMarketLocation(caravan);
            if (market == null || !market.getWorld().equals(loc.getWorld())) continue;
            if (market.distanceSquared(loc) <= radius * radius
                    || CaravanManager.horizontalDistance(market, loc) <= radius) {
                player.openInventory(MarketGUI.build(caravan, caravanManager, configManager, economy, luckyBlockBridge, player));
                return;
            }
        }
        player.sendMessage(Component.text("No caravan is set up near you right now. Try /market where.")
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
            if (!caravan.hasWaypoints()) {
                continue;
            } else if (caravan.getState() == Caravan.State.AT_MARKET) {
                int daysLeft = caravanManager.getDaysLeft(caravan);
                line = Component.text()
                        .append(caravan.getDisplayNameComponent())
                        .append(Component.text(" is at ").color(NamedTextColor.GRAY))
                        .append(Component.text(caravan.getCurrentWaypoint().getName()).color(NamedTextColor.WHITE))
                        .append(Component.text(" (" + daysLeft + " market-days left)").color(NamedTextColor.DARK_GRAY))
                        .build();
            } else {
                line = Component.text()
                        .append(caravan.getDisplayNameComponent())
                        .append(Component.text(" is on the road, headed " + caravanManager.getDestinationHint(caravan)
                                + " (" + Math.round(caravanManager.getTravelProgress(caravan) * 100) + "% of the way).")
                                .color(NamedTextColor.GRAY))
                        .build();
            }
            sender.sendMessage(line);
        }
    }

    /** /market where [id] — action-bar directions to a parked caravan (nearest one if no id). */
    private void where(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can get directions.").color(NamedTextColor.RED));
            return;
        }
        if (!configManager.isTrackingEnabled()) {
            player.sendMessage(Component.text("Caravan tracking is disabled on this server.").color(NamedTextColor.GRAY));
            return;
        }
        Caravan caravan;
        if (args.length >= 2) {
            caravan = caravanManager.getById(args[1]);
            if (caravan == null) {
                player.sendMessage(Component.text("Unknown caravan '" + args[1] + "'. See /market info.")
                        .color(NamedTextColor.RED));
                return;
            }
            if (!caravan.hasWaypoints() || caravan.getState() != Caravan.State.AT_MARKET) {
                player.sendMessage(Component.text().append(caravan.getDisplayNameComponent())
                        .append(Component.text(" is on the road — word is they're headed "
                                + caravanManager.getDestinationHint(caravan) + ".").color(NamedTextColor.GRAY)).build());
                return;
            }
        } else {
            caravan = caravanManager.getNearestMarket(player.getLocation());
            if (caravan == null) {
                player.sendMessage(Component.text("No caravan is set up in this world right now. Try /market track.")
                        .color(NamedTextColor.GRAY));
                return;
            }
        }
        tracker.track(player, caravan);
        player.sendMessage(Component.text("Tracking ").color(NamedTextColor.GREEN)
                .append(caravan.getDisplayNameComponent())
                .append(Component.text(" at " + caravan.getCurrentWaypoint().getName() + " for "
                        + configManager.getTrackingDurationSeconds() + "s — follow the arrow above your hotbar.")
                        .color(NamedTextColor.GREEN)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(lower)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("where")) {
            List<String> ids = new ArrayList<>();
            for (Caravan c : caravanManager.getCaravans()) {
                if (c.hasWaypoints() && c.getState() == Caravan.State.AT_MARKET
                        && c.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
                    ids.add(c.getId());
                }
            }
            return ids;
        }
        return List.of();
    }
}
