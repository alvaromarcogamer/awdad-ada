package net.survival.suite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** PETCRAFT: tienda y coleccion de mascotas, PetCoins. Datos en plugins/ServerSuite/players.yml. */
final class PetModule implements CommandExecutor, Listener {
    private static final String PREFIX = ChatColor.DARK_AQUA + "[PETCRAFT] " + ChatColor.GRAY;
    private static final String SHOP_TITLE = ChatColor.DARK_PURPLE + "Tienda de mascotas";
    private static final String COLLECTION_TITLE = ChatColor.DARK_AQUA + "Tus mascotas";

    private record PetDef(String id, String name, EntityType type, Material icon, int price, String rarity, ChatColor color) { }

    private final SuitePlugin plugin;
    private final Map<String, PetDef> pets = new LinkedHashMap<>();
    private final Map<UUID, UUID> activePets = new HashMap<>();
    private YamlConfiguration data;
    private File dataFile;
    private BukkitTask followTask;

    PetModule(SuitePlugin plugin) { this.plugin = plugin; }

    void enable() {
        loadPets();
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String name : List.of("pets", "petshop", "petcoins", "pet")) plugin.bind(name, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updatePets, 10L, 5L);
        plugin.getLogger().info("PETCRAFT: mascotas cargadas: " + pets.size());
    }

    void disable() {
        if (followTask != null) followTask.cancel();
        for (UUID entityId : activePets.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        activePets.clear();
        saveData();
    }

    private void loadPets() {
        pets.clear();
        addPet("bee", "Abeja", EntityType.BEE, Material.HONEYCOMB, 100, "Común", ChatColor.WHITE);
        addPet("rabbit", "Conejo", EntityType.RABBIT, Material.RABBIT_FOOT, 180, "Común", ChatColor.WHITE);
        addPet("fox", "Zorro", EntityType.FOX, Material.SWEET_BERRIES, 350, "Rara", ChatColor.BLUE);
        addPet("allay", "Allay mágico", EntityType.ALLAY, Material.AMETHYST_SHARD, 700, "Épica", ChatColor.DARK_PURPLE);
        addPet("blaze", "Mini blaze", EntityType.BLAZE, Material.BLAZE_POWDER, 1400, "Legendaria", ChatColor.GOLD);
        addPet("axolotl", "Ajolote", EntityType.AXOLOTL, Material.TROPICAL_FISH, 2400, "Mítica", ChatColor.RED);
    }

    private void addPet(String id, String name, EntityType type, Material icon, int price, String rarity, ChatColor color) {
        pets.put(id, new PetDef(id, name, type, icon, price, rarity, color));
    }

    private int getCoins(UUID uuid) { return data.getInt("players." + uuid + ".petcoins", 0); }
    private void setCoins(UUID uuid, int amount) { data.set("players." + uuid + ".petcoins", Math.max(0, amount)); saveData(); }
    private boolean owns(UUID uuid, String petId) { return data.getStringList("players." + uuid + ".pets").contains(petId); }
    private void addOwned(UUID uuid, String petId) {
        List<String> owned = data.getStringList("players." + uuid + ".pets");
        if (!owned.contains(petId)) owned.add(petId);
        data.set("players." + uuid + ".pets", owned);
        saveData();
    }
    private void saveData() {
        if (data == null || dataFile == null) return;
        try { data.save(dataFile); } catch (IOException ex) { plugin.getLogger().severe("No se pudieron guardar los datos: " + ex.getMessage()); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("pets")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Este comando es solo para jugadores."); return true; }
            openCollection(player); return true;
        }
        if (cmd.equals("petshop")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Este comando es solo para jugadores."); return true; }
            openShop(player); return true;
        }
        if (cmd.equals("petcoins")) return handleCoins(sender, args);
        if (cmd.equals("pet")) return handlePet(sender, args);
        return false;
    }

    private boolean handleCoins(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Uso: /petcoins give <jugador> <cantidad>"); return true; }
            sender.sendMessage(PREFIX + "Tienes " + ChatColor.GOLD + getCoins(player.getUniqueId()) + " PetCoins" + ChatColor.GRAY + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("daily")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Solo los jugadores pueden reclamar la recompensa diaria."); return true; }
            String path = "players." + player.getUniqueId() + ".lastDaily";
            long now = System.currentTimeMillis();
            long last = data.getLong(path, 0L);
            long day = 24L * 60L * 60L * 1000L;
            if (now - last < day) {
                long remaining = day - (now - last);
                long hours = remaining / 3600000L;
                long minutes = (remaining % 3600000L) / 60000L;
                player.sendMessage(PREFIX + "Ya has reclamado tu recompensa. Vuelve en " + hours + "h " + minutes + "m.");
                return true;
            }
            int reward = plugin.getConfig().getInt("daily-reward", 50);
            setCoins(player.getUniqueId(), getCoins(player.getUniqueId()) + reward);
            data.set(path, now); saveData();
            player.sendMessage(PREFIX + "¡Has recibido " + ChatColor.GOLD + reward + " PetCoins" + ChatColor.GRAY + "!");
            return true;
        }
        if ((args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take")) && sender.hasPermission("petcraft.admin")) {
            if (args.length < 3) { sender.sendMessage(PREFIX + "Uso: /petcoins " + args[0] + " <jugador> <cantidad>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(PREFIX + "Cantidad no válida."); return true; }
            if (target == null || amount < 1) { sender.sendMessage(PREFIX + "Jugador o cantidad no válidos."); return true; }
            int updated = args[0].equalsIgnoreCase("give") ? getCoins(target.getUniqueId()) + amount : Math.max(0, getCoins(target.getUniqueId()) - amount);
            setCoins(target.getUniqueId(), updated);
            sender.sendMessage(PREFIX + "Saldo actualizado. Nuevo saldo: " + updated + " PetCoins.");
            target.sendMessage(PREFIX + "Tu saldo de PetCoins ahora es " + ChatColor.GOLD + updated + ChatColor.GRAY + ".");
            return true;
        }
        sender.sendMessage(PREFIX + "Uso: /petcoins [balance|daily|give|take]");
        return true;
    }

    private boolean handlePet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Este comando es solo para jugadores."); return true; }
        if (args.length == 0) { player.sendMessage(PREFIX + "Uso: /pet summon <mascota> o /pet remove"); return true; }
        if (args[0].equalsIgnoreCase("remove")) { removePet(player); player.sendMessage(PREFIX + "Has guardado tu mascota."); return true; }
        if (args[0].equalsIgnoreCase("summon")) {
            if (args.length < 2) { player.sendMessage(PREFIX + "Uso: /pet summon <mascota>"); return true; }
            summonPet(player, args[1].toLowerCase(Locale.ROOT)); return true;
        }
        if (args[0].equalsIgnoreCase("give") && player.hasPermission("petcraft.admin")) {
            if (args.length < 3) { player.sendMessage(PREFIX + "Uso: /pet give <jugador> <mascota>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            String petId = args[2].toLowerCase(Locale.ROOT);
            if (target == null || !pets.containsKey(petId)) { player.sendMessage(PREFIX + "Jugador o mascota no válidos."); return true; }
            addOwned(target.getUniqueId(), petId);
            player.sendMessage(PREFIX + "Mascota entregada.");
            target.sendMessage(PREFIX + "¡Has recibido la mascota " + pets.get(petId).name() + "!");
            return true;
        }
        player.sendMessage(PREFIX + "Uso: /pet summon <mascota> | /pet remove");
        return true;
    }

    private void openShop(Player player) {
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.PET_SHOP, 27, SHOP_TITLE);
        Inventory inv = holder.getInventory();
        int[] slots = {10, 11, 12, 13, 14, 15};
        int i = 0;
        for (PetDef pet : pets.values()) {
            int slot = slots[i++];
            inv.setItem(slot, Items.makeList(pet.icon(), pet.color() + pet.name(), Arrays.asList(
                    ChatColor.GRAY + "Rareza: " + pet.color() + pet.rarity(),
                    ChatColor.GRAY + "Precio: " + ChatColor.GOLD + pet.price() + " PetCoins",
                    owns(player.getUniqueId(), pet.id()) ? ChatColor.GREEN + "Ya la tienes" : ChatColor.YELLOW + "Pulsa para comprar")));
            holder.keys.put(slot, pet.id());
        }
        inv.setItem(22, Items.makeList(Material.GOLD_INGOT, ChatColor.GOLD + "Tu saldo", Collections.singletonList(ChatColor.GRAY.toString() + getCoins(player.getUniqueId()) + " PetCoins")));
        player.openInventory(inv);
    }

    private void openCollection(Player player) {
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.PET_COLLECTION, 27, COLLECTION_TITLE);
        Inventory inv = holder.getInventory();
        int slot = 10;
        for (PetDef pet : pets.values()) {
            if (!owns(player.getUniqueId(), pet.id())) continue;
            inv.setItem(slot, Items.makeList(pet.icon(), pet.color() + pet.name(), Arrays.asList(ChatColor.GRAY + "Rareza: " + pet.rarity(), ChatColor.YELLOW + "Pulsa para invocarla")));
            holder.keys.put(slot, pet.id());
            slot++;
            if (slot == 17) break;
        }
        inv.setItem(22, Items.makeList(Material.BARRIER, ChatColor.RED + "Guardar mascota", Collections.singletonList(ChatColor.GRAY + "Pulsa para retirar tu mascota activa")));
        player.openInventory(inv);
    }

    void click(Player player, MenuHolder holder, int slot) {
        if (holder.type == MenuHolder.Type.PET_SHOP) {
            String petId = holder.keys.get(slot);
            PetDef pet = petId == null ? null : pets.get(petId);
            if (pet == null) return;
            if (owns(player.getUniqueId(), pet.id())) { player.sendMessage(PREFIX + "Ya tienes esta mascota."); return; }
            int balance = getCoins(player.getUniqueId());
            if (balance < pet.price()) { player.sendMessage(PREFIX + "No tienes suficientes PetCoins. Necesitas " + pet.price() + "."); return; }
            setCoins(player.getUniqueId(), balance - pet.price());
            addOwned(player.getUniqueId(), pet.id());
            player.sendMessage(PREFIX + "¡Has comprado " + pet.name() + " por " + pet.price() + " PetCoins!");
            openShop(player);
        } else {
            if (slot == 22) { removePet(player); player.sendMessage(PREFIX + "Has guardado tu mascota."); player.closeInventory(); return; }
            String petId = holder.keys.get(slot);
            if (petId != null) { summonPet(player, petId); player.closeInventory(); }
        }
    }

    private void summonPet(Player player, String petId) {
        PetDef pet = pets.get(petId);
        if (pet == null) { player.sendMessage(PREFIX + "No existe esa mascota."); return; }
        if (!owns(player.getUniqueId(), petId) && !player.hasPermission("petcraft.admin")) { player.sendMessage(PREFIX + "Todavía no has desbloqueado esa mascota."); return; }
        removePet(player);
        Location spawn = player.getLocation().clone().add(1.2, 0.2, 1.2);
        Entity entity = player.getWorld().spawnEntity(spawn, pet.type());
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setSilent(true);
            living.setInvulnerable(true);
            living.setGravity(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setCustomName(pet.color() + pet.name() + ChatColor.GRAY + " · " + player.getName());
            living.setCustomNameVisible(true);
        }
        entity.setPersistent(false);
        activePets.put(player.getUniqueId(), entity.getUniqueId());
        player.sendMessage(PREFIX + "Has invocado a " + pet.color() + pet.name() + ChatColor.GRAY + "!");
    }

    private void removePet(Player player) {
        UUID entityId = activePets.remove(player.getUniqueId());
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
    }

    private void updatePets() {
        Iterator<Map.Entry<UUID, UUID>> iterator = activePets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Entity pet = Bukkit.getEntity(entry.getValue());
            if (player == null || !player.isOnline() || pet == null || !pet.isValid()) {
                if (pet != null) pet.remove();
                iterator.remove();
                continue;
            }
            if (!player.getWorld().equals(pet.getWorld())) pet.teleport(player.getLocation());
            Location target = player.getLocation().clone().subtract(player.getLocation().getDirection().normalize().multiply(1.35));
            target.setY(player.getLocation().getY() + 0.65 + Math.sin(System.currentTimeMillis() / 350.0) * 0.12);
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(0);
            if (pet.getLocation().distanceSquared(target) > 0.16) pet.teleport(target);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { removePet(event.getPlayer()); }
}
