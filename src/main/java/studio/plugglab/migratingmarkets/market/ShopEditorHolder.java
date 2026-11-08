package studio.plugglab.migratingmarkets.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import studio.plugglab.migratingmarkets.caravan.Caravan;

public class ShopEditorHolder implements InventoryHolder {

    private final Caravan caravan;
    private Inventory inventory;

    public ShopEditorHolder(Caravan caravan) {
        this.caravan = caravan;
    }

    public Caravan getCaravan() {
        return caravan;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
