package net.survival.suite;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Identifica los menus del plugin sin depender del titulo del inventario. */
public final class MenuHolder implements InventoryHolder {
    public enum Type { ADMIN_MAIN, ADMIN_PLAYERS, ADMIN_INSPECT, COSMETICS, PET_SHOP, PET_COLLECTION }

    public final Type type;
    public final Map<Integer, UUID> players = new HashMap<>();
    public final Map<Integer, String> keys = new HashMap<>();
    public UUID target;
    public boolean ender;
    private Inventory inventory;

    private MenuHolder(Type type) { this.type = type; }

    public static MenuHolder create(Type type, int size, String title) {
        MenuHolder holder = new MenuHolder(type);
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder;
    }

    @Override public Inventory getInventory() { return inventory; }
}
