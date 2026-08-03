package studio.plugglab.migratingmarkets.economy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Default economy: buys/sells happen through physical emeralds in the
 * player's inventory, mirroring vanilla villager trading. Amounts are
 * treated as whole emeralds (fractions are rounded up on withdraw,
 * down on deposit, so the caravan never loses value on a round-trip).
 */
public class EmeraldEconomyProvider implements EconomyProvider {

    @Override
    public boolean has(Player player, double amount) {
        return countEmeralds(player) >= Math.ceil(amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        int needed = (int) Math.ceil(amount);
        if (countEmeralds(player) < needed) {
            return false;
        }
        int remaining = needed;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == Material.EMERALD) {
                int take = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                remaining -= take;
            }
        }
        return true;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        int give = (int) Math.floor(amount);
        while (give > 0) {
            int stackSize = Math.min(64, give);
            var leftover = player.getInventory().addItem(new ItemStack(Material.EMERALD, stackSize));
            leftover.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
            give -= stackSize;
        }
        return true;
    }

    @Override
    public String format(double amount) {
        return (long) Math.ceil(amount) + " emeralds";
    }

    @Override
    public String currencyName() {
        return "emeralds";
    }

    private int countEmeralds(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.EMERALD) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
