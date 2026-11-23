package net.survival.suite;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

final class MenuListener implements Listener {
    private final AdminModule admin;
    private final CosmeticModule cosmetics;
    private final PetModule pets;

    MenuListener(AdminModule admin, CosmeticModule cosmetics, PetModule pets) {
        this.admin = admin;
        this.cosmetics = cosmetics;
        this.pets = pets;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!top.equals(event.getClickedInventory())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        switch (holder.type) {
            case ADMIN_MAIN, ADMIN_PLAYERS, ADMIN_INSPECT -> admin.click(player, holder, slot, event);
            case COSMETICS -> cosmetics.click(player, slot);
            case PET_SHOP, PET_COLLECTION -> pets.click(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
}
