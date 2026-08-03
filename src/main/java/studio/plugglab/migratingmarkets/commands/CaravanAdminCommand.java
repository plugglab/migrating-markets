package studio.plugglab.migratingmarkets.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CaravanAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "forcemove", "teleport", "stop");

    private final CaravanManager caravanManager;

    public CaravanAdminCommand(CaravanManager caravanManager) {
        this.caravanManager = caravanManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                caravanManager.reload();
                sender.sendMessage(Component.text("Caravans reloaded from config.").color(NamedTextColor.GREEN));
            }
            case "list" -> {
                if (caravanManager.getCaravans().isEmpty()) {
                    sender.sendMessage(Component.text("No caravans are currently loaded.").color(NamedTextColor.GRAY));
                    return true;
                }
                for (Caravan c : caravanManager.getCaravans()) {
                    Component line = Component.text(c.getId() + " -> ").color(NamedTextColor.WHITE)
                            .append(c.getDisplayNameComponent())
                            .append(Component.text(" [" + c.getState() + "] waypoint#" + c.getCurrentWaypointIndex())
                                    .color(NamedTextColor.GRAY));
                    sender.sendMessage(line);
                }
            }
            case "forcemove" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /caravanadmin forcemove <id>").color(NamedTextColor.RED));
                    return true;
                }
                Caravan c = caravanManager.getById(args[1]);
                if (c == null) {
                    sendUnknownId(sender, args[1]);
                    return true;
                }
                caravanManager.forceMove(c);
                Component msg = Component.text("Forced ").color(NamedTextColor.GREEN)
                        .append(c.getDisplayNameComponent())
                        .append(Component.text(" to depart.").color(NamedTextColor.GREEN));
                sender.sendMessage(msg);
            }
            case "teleport" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /caravanadmin teleport <id> <waypointIndex>")
                            .color(NamedTextColor.RED));
                    return true;
                }
                Caravan c = caravanManager.getById(args[1]);
                if (c == null) {
                    sendUnknownId(sender, args[1]);
                    return true;
                }
                int index;
                try {
                    index = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Waypoint index must be a number.").color(NamedTextColor.RED));
                    return true;
                }
                if (index < 0 || index >= c.getWaypoints().size()) {
                    sender.sendMessage(Component.text("Index out of range (0-" + (c.getWaypoints().size() - 1) + ").")
                            .color(NamedTextColor.RED));
                    return true;
                }
                caravanManager.teleportToWaypoint(c, index);
                Component msg = Component.text("Teleported ").color(NamedTextColor.GREEN)
                        .append(c.getDisplayNameComponent())
                        .append(Component.text(" to waypoint " + index + " (" + c.getCurrentWaypoint().getName() + ").")
                                .color(NamedTextColor.GREEN));
                sender.sendMessage(msg);
            }
            case "stop" -> {
                caravanManager.shutdown();
                sender.sendMessage(Component.text("All caravans despawned and state saved.").color(NamedTextColor.YELLOW));
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand '" + args[0] + "'.").color(NamedTextColor.RED));
                sendUsage(sender);
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /caravanadmin <reload|list|forcemove|teleport|stop> [id] [index]")
                .color(NamedTextColor.RED));
    }

    private void sendUnknownId(CommandSender sender, String attempted) {
        String known = caravanManager.getCaravans().stream().map(Caravan::getId).collect(Collectors.joining(", "));
        sender.sendMessage(Component.text("Unknown caravan id '" + attempted + "'. Known ids: " + known)
                .color(NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("migratingmarkets.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return startsWith(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("forcemove") || sub.equals("teleport"))) {
            List<String> ids = caravanManager.getCaravans().stream().map(Caravan::getId).toList();
            return startsWith(ids, args[1]);
        }

        if (args.length == 3 && sub.equals("teleport")) {
            Caravan c = caravanManager.getById(args[1]);
            if (c == null) return List.of();
            List<String> indices = new ArrayList<>();
            for (int i = 0; i < c.getWaypoints().size(); i++) {
                indices.add(String.valueOf(i));
            }
            return startsWith(indices, args[2]);
        }

        return List.of();
    }

    private List<String> startsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
