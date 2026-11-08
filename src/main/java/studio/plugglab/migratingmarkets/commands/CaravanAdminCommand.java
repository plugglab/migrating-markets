package studio.plugglab.migratingmarkets.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import studio.plugglab.migratingmarkets.caravan.Caravan;
import studio.plugglab.migratingmarkets.caravan.CaravanManager;
import studio.plugglab.migratingmarkets.market.MarketItem;
import studio.plugglab.migratingmarkets.market.ShopEditorGUI;
import studio.plugglab.migratingmarkets.util.ConfigManager;
import studio.plugglab.migratingmarkets.util.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CaravanAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "list", "forcemove", "teleport", "stop",
            "edit", "additem", "removeitem", "setprice", "setlevel",
            "createroute", "addwaypoint", "removewaypoint", "deleteroute");

    private static final List<String> COMMON_MATERIALS = List.of(
            "DIAMOND", "IRON_INGOT", "GOLD_INGOT", "EMERALD", "NETHERITE_INGOT",
            "ENCHANTED_BOOK", "ENDER_PEARL", "TOTEM_OF_UNDYING", "ELYTRA");

    private final CaravanManager caravanManager;
    private final ConfigManager configManager;

    public CaravanAdminCommand(CaravanManager caravanManager, ConfigManager configManager) {
        this.caravanManager = caravanManager;
        this.configManager = configManager;
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
            case "list" -> handleList(sender);
            case "forcemove" -> handleForceMove(sender, args);
            case "teleport" -> handleTeleport(sender, args);
            case "stop" -> {
                caravanManager.shutdown();
                sender.sendMessage(Component.text("All caravans despawned and state saved.").color(NamedTextColor.YELLOW));
            }
            case "edit" -> handleEdit(sender, args);
            case "additem" -> handleAddItem(sender, args);
            case "removeitem" -> handleRemoveItem(sender, args);
            case "setprice" -> handleSetPrice(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "createroute" -> handleCreateRoute(sender, args);
            case "addwaypoint" -> handleAddWaypoint(sender, args);
            case "removewaypoint" -> handleRemoveWaypoint(sender, args);
            case "deleteroute" -> handleDeleteRoute(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand '" + args[0] + "'.").color(NamedTextColor.RED));
                sendUsage(sender);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Existing subcommands
    // ---------------------------------------------------------------

    private void handleList(CommandSender sender) {
        if (caravanManager.getCaravans().isEmpty()) {
            sender.sendMessage(Component.text("No caravans are currently loaded.").color(NamedTextColor.GRAY));
            return;
        }
        for (Caravan c : caravanManager.getCaravans()) {
            Component line = Component.text(c.getId() + " -> ").color(NamedTextColor.WHITE)
                    .append(c.getDisplayNameComponent())
                    .append(Component.text(" [" + c.getState() + "] waypoint#" + c.getCurrentWaypointIndex()
                            + " · " + c.getInventory().size() + " items")
                            .color(NamedTextColor.GRAY));
            sender.sendMessage(line);
        }
    }

    private void handleForceMove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /caravanadmin forcemove <id>").color(NamedTextColor.RED));
            return;
        }
        Caravan c = caravanManager.getById(args[1]);
        if (c == null) {
            sendUnknownId(sender, args[1]);
            return;
        }
        caravanManager.forceMove(c);
        Component msg = Component.text("Forced ").color(NamedTextColor.GREEN)
                .append(c.getDisplayNameComponent())
                .append(Component.text(" to depart.").color(NamedTextColor.GREEN));
        sender.sendMessage(msg);
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /caravanadmin teleport <id> <waypointIndex>")
                    .color(NamedTextColor.RED));
            return;
        }
        Caravan c = caravanManager.getById(args[1]);
        if (c == null) {
            sendUnknownId(sender, args[1]);
            return;
        }
        Integer index = parseInt(sender, args[2], "Waypoint index");
        if (index == null) return;
        if (index < 0 || index >= c.getWaypoints().size()) {
            sender.sendMessage(Component.text("Index out of range (0-" + (c.getWaypoints().size() - 1) + ").")
                    .color(NamedTextColor.RED));
            return;
        }
        caravanManager.teleportToWaypoint(c, index);
        Component msg = Component.text("Teleported ").color(NamedTextColor.GREEN)
                .append(c.getDisplayNameComponent())
                .append(Component.text(" to waypoint " + index + " (" + c.getCurrentWaypoint().getName() + ").")
                        .color(NamedTextColor.GREEN));
        sender.sendMessage(msg);
    }

    // ---------------------------------------------------------------
    // Shop editing
    // ---------------------------------------------------------------

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use the shop editor GUI.").color(NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /caravanadmin edit <id>").color(NamedTextColor.RED));
            return;
        }
        Caravan c = caravanManager.getById(args[1]);
        if (c == null) {
            sendUnknownId(sender, args[1]);
            return;
        }
        player.openInventory(ShopEditorGUI.build(c));
    }

    private void handleAddItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /caravanadmin additem <id> <material> <price> <level> [category...]")
                    .color(NamedTextColor.RED));
            return;
        }
        Caravan c = caravanManager.getById(args[1]);
        if (c == null) {
            sendUnknownId(sender, args[1]);
            return;
        }
        if (c.getInventory().size() >= ShopEditorGUI.MAX_ITEMS) {
            sender.sendMessage(Component.text("That shop is full (45 item slots max).").color(NamedTextColor.RED));
            return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown material '" + args[2] + "'.").color(NamedTextColor.RED));
            return;
        }
        Double price = parseDouble(sender, args[3], "Price");
        if (price == null) return;
        Integer level = parseInt(sender, args[4], "Level");
        if (level == null) return;
        String category = args.length > 5
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length))
                : "custom";

        int maxStock = Math.max(1, level * configManager.getStockPerLevel());
        MarketItem item = new MarketItem(material, price, category, Math.max(1, level), maxStock);
        c.getInventory().add(item);
        sender.sendMessage(Component.text("Added " + material + " to " + c.getId() + " at slot "
                + (c.getInventory().size() - 1) + " (price " + price + ", level " + level + ").")
                .color(NamedTextColor.GREEN));
    }

    private void handleRemoveItem(CommandSender sender, String[] args) {
        MarketItem item = resolveItem(sender, args, "removeitem <id> <slot>");
        if (item == null) return;
        Caravan c = caravanManager.getById(args[1]);
        int slot = Integer.parseInt(args[2]);
        c.getInventory().remove(slot);
        sender.sendMessage(Component.text("Removed slot " + slot + " (" + item.getMaterial() + ") from " + c.getId() + ".")
                .color(NamedTextColor.YELLOW));
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /caravanadmin setprice <id> <slot> <price>")
                    .color(NamedTextColor.RED));
            return;
        }
        MarketItem item = resolveItem(sender, args, "setprice <id> <slot> <price>");
        if (item == null) return;
        Double price = parseDouble(sender, args[3], "Price");
        if (price == null) return;
        item.setBasePrice(price);
        sender.sendMessage(Component.text("Slot " + args[2] + " base price set to " + price + ".")
                .color(NamedTextColor.GREEN));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /caravanadmin setlevel <id> <slot> <level>")
                    .color(NamedTextColor.RED));
            return;
        }
        MarketItem item = resolveItem(sender, args, "setlevel <id> <slot> <level>");
        if (item == null) return;
        Integer level = parseInt(sender, args[3], "Level");
        if (level == null) return;
        item.setLevel(level, configManager.getStockPerLevel());
        sender.sendMessage(Component.text("Slot " + args[2] + " level set to " + item.getLevel()
                + " (stock cap now " + item.getMaxStock() + ").").color(NamedTextColor.GREEN));
    }

    /** Shared validation for slot-targeting subcommands (removeitem/setprice/setlevel). */
    private MarketItem resolveItem(CommandSender sender, String[] args, String usageTail) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /caravanadmin " + usageTail).color(NamedTextColor.RED));
            return null;
        }
        Caravan c = caravanManager.getById(args[1]);
        if (c == null) {
            sendUnknownId(sender, args[1]);
            return null;
        }
        Integer slot = parseInt(sender, args[2], "Slot");
        if (slot == null) return null;
        if (slot < 0 || slot >= c.getInventory().size()) {
            sender.sendMessage(Component.text("Slot out of range (0-" + (c.getInventory().size() - 1) + ") for "
                    + c.getId() + ". Use /caravanadmin edit " + c.getId() + " to see slot numbers.")
                    .color(NamedTextColor.RED));
            return null;
        }
        return c.getInventory().get(slot);
    }

    // ---------------------------------------------------------------
    // Owner-created routes
    // ---------------------------------------------------------------

    private void handleCreateRoute(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /caravanadmin createroute <id> <specialty> <displayName...>")
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Known specialties: " + String.join(", ", configManager.getSpecialtyNames())
                    + " (or any other word for an empty starter shop).").color(NamedTextColor.GRAY));
            return;
        }
        String id = args[1];
        if (!caravanManager.isIdAvailable(id)) {
            sender.sendMessage(Component.text("Id '" + id + "' is already in use by another caravan.")
                    .color(NamedTextColor.RED));
            return;
        }
        String specialty = args[2];
        String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        Caravan caravan = caravanManager.createCustomCaravan(id, displayName, specialty);
        sender.sendMessage(Component.text()
                .append(Component.text("Created ").color(NamedTextColor.GREEN))
                .append(caravan.getDisplayNameComponent())
                .append(Component.text(" (id: " + id + ", specialty: " + specialty + "). It won't spawn until you "
                        + "give it at least one stop: /caravanadmin addwaypoint " + id + " <name...>")
                        .color(NamedTextColor.GREEN)));
    }

    private void handleAddWaypoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can add a waypoint (it uses your current location).")
                    .color(NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /caravanadmin addwaypoint <id> <name...>")
                    .color(NamedTextColor.RED));
            return;
        }
        Caravan c = requireCustomCaravan(sender, args[1]);
        if (c == null) return;

        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        Location loc = player.getLocation();
        Waypoint waypoint = new Waypoint(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), name);
        caravanManager.addWaypoint(c, waypoint);
        sender.sendMessage(Component.text()
                .append(Component.text("Added stop \"" + name + "\" to ").color(NamedTextColor.GREEN))
                .append(c.getDisplayNameComponent())
                .append(Component.text(" (" + c.getWaypoints().size() + " stop(s) total).")
                        .color(NamedTextColor.GREEN)));
    }

    private void handleRemoveWaypoint(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /caravanadmin removewaypoint <id> <index>")
                    .color(NamedTextColor.RED));
            return;
        }
        Caravan c = requireCustomCaravan(sender, args[1]);
        if (c == null) return;
        Integer index = parseInt(sender, args[2], "Index");
        if (index == null) return;
        if (index < 0 || index >= c.getWaypoints().size()) {
            sender.sendMessage(Component.text("Index out of range (0-" + (c.getWaypoints().size() - 1) + ").")
                    .color(NamedTextColor.RED));
            return;
        }
        String removedName = c.getWaypoints().get(index).getName();
        caravanManager.removeWaypoint(c, index);
        sender.sendMessage(Component.text("Removed stop \"" + removedName + "\" from " + c.getId()
                + " (" + c.getWaypoints().size() + " stop(s) left).").color(NamedTextColor.YELLOW));
    }

    private void handleDeleteRoute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /caravanadmin deleteroute <id>").color(NamedTextColor.RED));
            return;
        }
        Caravan c = requireCustomCaravan(sender, args[1]);
        if (c == null) return;
        caravanManager.deleteCustomCaravan(c);
        sender.sendMessage(Component.text("Deleted caravan '" + args[1] + "'.").color(NamedTextColor.YELLOW));
    }

    /** Resolves an id to a caravan, rejecting config.yml-defined ones (those aren't ours to mutate here). */
    private Caravan requireCustomCaravan(CommandSender sender, String id) {
        Caravan c = caravanManager.getById(id);
        if (c == null) {
            sendUnknownId(sender, id);
            return null;
        }
        if (!caravanManager.isCustomCaravan(id)) {
            sender.sendMessage(Component.text("'" + id + "' is defined in config.yml, not owner-created — "
                    + "edit its waypoints there instead.").color(NamedTextColor.RED));
            return null;
        }
        return c;
    }

    private Integer parseInt(CommandSender sender, String raw, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(label + " must be a whole number.").color(NamedTextColor.RED));
            return null;
        }
    }

    private Double parseDouble(CommandSender sender, String raw, String label) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(label + " must be a number.").color(NamedTextColor.RED));
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /caravanadmin <reload|list|forcemove|teleport|stop|edit|"
                + "additem|removeitem|setprice|setlevel|createroute|addwaypoint|removewaypoint|deleteroute> [args]")
                .color(NamedTextColor.RED));
    }

    private void sendUnknownId(CommandSender sender, String attempted) {
        String known = caravanManager.getCaravans().stream().map(Caravan::getId).collect(Collectors.joining(", "));
        sender.sendMessage(Component.text("Unknown caravan id '" + attempted + "'. Known ids: " + known)
                .color(NamedTextColor.RED));
    }

    // ---------------------------------------------------------------
    // Tab completion
    // ---------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("migratingmarkets.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return startsWith(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        List<String> ids = caravanManager.getCaravans().stream().map(Caravan::getId).toList();

        if (args.length == 2 && List.of("forcemove", "teleport", "edit", "additem", "removeitem", "setprice", "setlevel",
                "addwaypoint", "removewaypoint", "deleteroute").contains(sub)) {
            return startsWith(ids, args[1]);
        }

        if (args.length == 3 && sub.equals("createroute")) {
            List<String> specialties = new ArrayList<>(configManager.getSpecialtyNames());
            specialties.add("custom");
            return startsWith(specialties, args[2]);
        }

        if (args.length == 3 && sub.equals("removewaypoint")) {
            Caravan c = caravanManager.getById(args[1]);
            if (c == null) return List.of();
            List<String> indices = new ArrayList<>();
            for (int i = 0; i < c.getWaypoints().size(); i++) indices.add(String.valueOf(i));
            return startsWith(indices, args[2]);
        }

        if (args.length == 3) {
            if (sub.equals("teleport") || sub.equals("removeitem") || sub.equals("setprice") || sub.equals("setlevel")) {
                Caravan c = caravanManager.getById(args[1]);
                if (c == null) return List.of();
                int bound = sub.equals("teleport") ? c.getWaypoints().size() : c.getInventory().size();
                List<String> indices = new ArrayList<>();
                for (int i = 0; i < bound; i++) indices.add(String.valueOf(i));
                return startsWith(indices, args[2]);
            }
            if (sub.equals("additem")) {
                return startsWith(COMMON_MATERIALS, args[2]);
            }
        }

        if (args.length == 4 && sub.equals("additem")) {
            return startsWith(List.of("1", "5", "10", "25", "50", "100"), args[3]);
        }
        if (args.length == 5 && sub.equals("additem")) {
            return startsWith(List.of("1", "2", "3", "4", "5"), args[4]);
        }

        return List.of();
    }

    private List<String> startsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
