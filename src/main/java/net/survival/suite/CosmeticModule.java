package net.survival.suite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** COSMETICCRAFT: auras y estelas. Datos en plugins/ServerSuite/cosmetics.yml. */
final class CosmeticModule implements CommandExecutor, Listener {
    private static final String MENU = ChatColor.LIGHT_PURPLE + "COSMETICCRAFT";
    private final SuitePlugin plugin;
    private final Map<UUID, String> auras = new HashMap<>();
    private final Map<UUID, String> trails = new HashMap<>();
    private YamlConfiguration data;
    private File dataFile;
    private BukkitTask auraTask;

    CosmeticModule(SuitePlugin plugin) { this.plugin = plugin; }

    void enable() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        plugin.bind("cosmetics", this);
        plugin.bind("aura", this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String aura = data.getString(key + ".aura", "none");
                String trail = data.getString(key + ".trail", "none");
                if (!"none".equals(aura)) auras.put(id, aura);
                if (!"none".equals(trail)) trails.put(id, trail);
            } catch (IllegalArgumentException ignored) { }
        }
        auraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnAuras, 10L, 10L);
    }

    void disable() {
        if (auraTask != null) auraTask.cancel();
        for (UUID id : new HashSet<>(auras.keySet())) putChoice(id);
        for (UUID id : new HashSet<>(trails.keySet())) putChoice(id);
        flush();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Este comando solo puede usarlo un jugador."); return true; }
        if (command.getName().equalsIgnoreCase("aura")) {
            if (args.length == 0) { openMenu(player); return true; }
            if (!player.hasPermission("cosmeticcraft.admin")) { player.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
            String id = args[0].toLowerCase(Locale.ROOT);
            if (id.equals("off")) { auras.remove(player.getUniqueId()); saveChoice(player.getUniqueId()); player.sendMessage(ChatColor.GRAY + "Aura desactivada."); return true; }
            if (validAura(id)) { auras.put(player.getUniqueId(), id); saveChoice(player.getUniqueId()); player.sendMessage(ChatColor.GREEN + "Aura aplicada: " + id); }
            else player.sendMessage(ChatColor.RED + "Auras: flame, heart, portal, soul, sparkle, off");
            return true;
        }
        openMenu(player);
        return true;
    }

    private boolean validAura(String id) { return List.of("flame", "heart", "portal", "soul", "sparkle").contains(id); }

    private void openMenu(Player player) {
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.COSMETICS, 27, MENU);
        Inventory inv = holder.getInventory();
        inv.setItem(10, Items.make(Material.BLAZE_POWDER, ChatColor.GOLD + "Aura de fuego", "Partículas de llamas", "Pulsa para activar"));
        inv.setItem(11, Items.make(Material.POPPY, ChatColor.RED + "Aura de corazones", "Corazones alrededor del jugador"));
        inv.setItem(12, Items.make(Material.ENDER_PEARL, ChatColor.DARK_PURPLE + "Aura del portal", "Partículas del End"));
        inv.setItem(13, Items.make(Material.SOUL_LANTERN, ChatColor.AQUA + "Aura de almas", "Llamas de alma"));
        inv.setItem(14, Items.make(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Aura brillante", "Chispas mágicas"));
        inv.setItem(16, Items.make(Material.FEATHER, ChatColor.YELLOW + "Estela de chispas", "Deja partículas al caminar"));
        inv.setItem(22, Items.make(Material.BARRIER, ChatColor.RED + "Desactivar cosméticos", "Quita tu aura y estela"));
        player.openInventory(inv);
    }

    void click(Player player, int slot) {
        String aura = switch (slot) { case 10 -> "flame"; case 11 -> "heart"; case 12 -> "portal"; case 13 -> "soul"; case 14 -> "sparkle"; default -> null; };
        UUID id = player.getUniqueId();
        if (aura != null) { auras.put(id, aura); saveChoice(id); player.sendMessage(ChatColor.GREEN + "Has equipado el aura: " + aura + "."); player.closeInventory(); }
        else if (slot == 16) { trails.put(id, "sparkle"); saveChoice(id); player.sendMessage(ChatColor.GREEN + "Estela de chispas activada."); player.closeInventory(); }
        else if (slot == 22) { auras.remove(id); trails.remove(id); saveChoice(id); player.sendMessage(ChatColor.GRAY + "Cosméticos desactivados."); player.closeInventory(); }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) return;
        if (trails.containsKey(event.getPlayer().getUniqueId())) {
            Location at = from.clone().add(0.5, 0.1, 0.5);
            event.getPlayer().getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 2, 0.12, 0.02, 0.12, 0.01);
        }
    }

    private void spawnAuras() {
        for (Map.Entry<UUID, String> entry : auras.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            Location at = player.getLocation().add(0, 1.0, 0);
            Particle particle = switch (entry.getValue()) {
                case "flame" -> Particle.FLAME;
                case "heart" -> Particle.HEART;
                case "portal" -> Particle.PORTAL;
                case "soul" -> Particle.SOUL;
                default -> Particle.END_ROD;
            };
            player.getWorld().spawnParticle(particle, at, 5, 0.35, 0.45, 0.35, 0.01);
        }
    }

    private void putChoice(UUID id) {
        String key = id.toString();
        data.set(key + ".aura", auras.getOrDefault(id, "none"));
        data.set(key + ".trail", trails.getOrDefault(id, "none"));
    }

    private void saveChoice(UUID id) { putChoice(id); flush(); }

    private void flush() {
        try { data.save(dataFile); } catch (IOException ex) { plugin.getLogger().severe("No se pudo guardar cosmetics.yml: " + ex.getMessage()); }
    }
}
