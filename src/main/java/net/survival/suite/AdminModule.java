package net.survival.suite;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** ADMIN PANEL: menu de administracion, mantenimiento e inspeccion de objetos de jugadores. */
final class AdminModule implements CommandExecutor, TabCompleter, Listener {
    private static final String MAIN = ChatColor.DARK_BLUE + "ADMIN PANEL";
    private static final String PLAYERS = ChatColor.DARK_BLUE + "Jugadores conectados";
    private final SuitePlugin plugin;
    private boolean maintenance;

    AdminModule(SuitePlugin plugin) { this.plugin = plugin; }

    void enable() {
        maintenance = plugin.getConfig().getBoolean("maintenance", false);
        plugin.bind("admin", this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void disable() {
        plugin.getConfig().set("maintenance", maintenance);
        plugin.saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Este comando solo puede usarlo un jugador."); return true; }
        if (!player.hasPermission("adminpanel.use")) { player.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
        if (args.length == 0) { openMain(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("inv") || sub.equals("ender")) {
            if (!player.hasPermission("adminpanel.inspect")) { player.sendMessage(ChatColor.RED + "No tienes permiso para inspeccionar jugadores."); return true; }
            if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "Uso: /admin " + sub + " <jugador>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { player.sendMessage(ChatColor.RED + "Ese jugador no esta conectado."); return true; }
            openInspect(player, target, sub.equals("ender"));
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Uso: /admin | /admin inv <jugador> | /admin ender <jugador>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("adminpanel.use")) return out;
        if (args.length == 1) {
            for (String s : List.of("inv", "ender")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("inv") || args[0].equalsIgnoreCase("ender"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }

    // ---- Menus ----

    private void openMain(Player player) {
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.ADMIN_MAIN, 27, MAIN);
        Inventory inv = holder.getInventory();
        inv.setItem(10, Items.make(Material.PLAYER_HEAD, ChatColor.AQUA + "Jugadores", "Ver jugadores conectados", "Pulsa para abrir"));
        inv.setItem(12, Items.make(Material.COMPASS, ChatColor.GREEN + "Control del servidor", "Consultar estado y cambiar hora"));
        inv.setItem(14, Items.make(Material.CLOCK, ChatColor.YELLOW + "Cambiar a día", "Establece el día en todos los mundos"));
        inv.setItem(15, Items.make(Material.CLOCK, ChatColor.DARK_PURPLE + "Cambiar a noche", "Establece la noche en todos los mundos"));
        inv.setItem(16, Items.make(Material.WATER_BUCKET, ChatColor.BLUE + "Clima despejado", "Quita la lluvia y las tormentas"));
        inv.setItem(22, Items.make(Material.REDSTONE_TORCH, (maintenance ? ChatColor.RED + "Desactivar mantenimiento" : ChatColor.GREEN + "Activar mantenimiento"), "Estado actual: " + (maintenance ? "ACTIVO" : "INACTIVO")));
        inv.setItem(26, Items.make(Material.BARRIER, ChatColor.RED + "Cerrar", "Cerrar este menú"));
        player.openInventory(inv);
    }

    private void openPlayers(Player admin) {
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.ADMIN_PLAYERS, 54, PLAYERS);
        Inventory inv = holder.getInventory();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            inv.setItem(slot, Items.head(target, ChatColor.YELLOW + target.getName(),
                    "Clic izquierdo: teletransportarte",
                    "Clic derecho: ver su inventario",
                    "Mayús + clic derecho: ver su cofre de Ender"));
            holder.players.put(slot, target.getUniqueId());
            slot++;
        }
        inv.setItem(49, Items.make(Material.ARROW, ChatColor.GRAY + "Volver", "Volver al panel principal"));
        admin.openInventory(inv);
    }

    /** Vista de solo lectura del inventario o cofre de Ender de un jugador conectado. */
    private void openInspect(Player admin, Player target, boolean ender) {
        String title = ChatColor.DARK_BLUE + (ender ? "Ender: " : "Inventario: ") + target.getName();
        MenuHolder holder = MenuHolder.create(MenuHolder.Type.ADMIN_INSPECT, 54, title);
        holder.target = target.getUniqueId();
        holder.ender = ender;
        Inventory inv = holder.getInventory();
        if (ender) {
            Inventory chest = target.getEnderChest();
            for (int i = 0; i < 27; i++) inv.setItem(i, copy(chest.getItem(i)));
        } else {
            PlayerInventory pi = target.getInventory();
            for (int i = 9; i <= 35; i++) inv.setItem(i - 9, copy(pi.getItem(i)));   // almacenamiento: filas 1-3
            for (int i = 0; i <= 8; i++) inv.setItem(27 + i, copy(pi.getItem(i)));   // barra rapida: fila 4
            inv.setItem(36, copy(pi.getHelmet()));
            inv.setItem(37, copy(pi.getChestplate()));
            inv.setItem(38, copy(pi.getLeggings()));
            inv.setItem(39, copy(pi.getBoots()));
            inv.setItem(40, copy(pi.getItemInOffHand()));
        }
        Location loc = target.getLocation();
        inv.setItem(45, Items.make(Material.ARROW, ChatColor.GRAY + "Volver", "Volver a la lista de jugadores"));
        inv.setItem(47, Items.make(Material.SUNFLOWER, ChatColor.YELLOW + "Actualizar", "Vuelve a leer los objetos"));
        inv.setItem(49, Items.head(target, ChatColor.AQUA + target.getName(),
                "Vida: " + String.format(Locale.ROOT, "%.1f", target.getHealth()),
                "Hambre: " + target.getFoodLevel(),
                "Nivel: " + target.getLevel(),
                "Modo: " + target.getGameMode().name(),
                "Mundo: " + loc.getWorld().getName(),
                "XYZ: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                ender ? "Cofre de Ender (27 casillas)" : "Fila 5: casco, peto, pantalones, botas, mano secundaria",
                "Pulsa un objeto para ver sus detalles en el chat"));
        inv.setItem(51, Items.make(ender ? Material.CHEST : Material.ENDER_CHEST,
                ChatColor.LIGHT_PURPLE + (ender ? "Ver inventario" : "Ver cofre de Ender"), "Cambiar de vista"));
        inv.setItem(53, Items.make(Material.BARRIER, ChatColor.RED + "Cerrar", "Cerrar esta vista"));
        admin.openInventory(inv);
    }

    private ItemStack copy(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? null : item.clone();
    }

    // ---- Clics ----

    void click(Player player, MenuHolder holder, int slot, InventoryClickEvent event) {
        if (!player.hasPermission("adminpanel.use")) { player.closeInventory(); return; }
        switch (holder.type) {
            case ADMIN_MAIN -> clickMain(player, slot);
            case ADMIN_PLAYERS -> clickPlayers(player, holder, slot, event.getClick());
            case ADMIN_INSPECT -> clickInspect(player, holder, slot);
            default -> { }
        }
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openPlayers(player);
            case 12 -> player.sendMessage(ChatColor.AQUA + "Servidor: " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + " jugadores conectados.");
            case 14 -> { setWorldTime(1000); player.sendMessage(ChatColor.GREEN + "Se ha establecido el día."); openMain(player); }
            case 15 -> { setWorldTime(13000); player.sendMessage(ChatColor.GREEN + "Se ha establecido la noche."); openMain(player); }
            case 16 -> {
                for (World world : Bukkit.getWorlds()) { world.setStorm(false); world.setThundering(false); }
                player.sendMessage(ChatColor.GREEN + "Clima despejado en todos los mundos.");
                openMain(player);
            }
            case 22 -> {
                if (!player.hasPermission("adminpanel.maintenance")) { player.sendMessage(ChatColor.RED + "No tienes permiso para cambiar el mantenimiento."); return; }
                maintenance = !maintenance;
                plugin.getConfig().set("maintenance", maintenance);
                plugin.saveConfig();
                player.sendMessage(ChatColor.YELLOW + "Mantenimiento: " + (maintenance ? "ACTIVADO" : "DESACTIVADO"));
                openMain(player);
            }
            case 26 -> player.closeInventory();
            default -> { }
        }
    }

    private void clickPlayers(Player player, MenuHolder holder, int slot, ClickType click) {
        if (slot == 49) { openMain(player); return; }
        UUID targetId = holder.players.get(slot);
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) { player.sendMessage(ChatColor.RED + "Ese jugador ya no está conectado."); return; }
        if (click.isRightClick()) {
            if (!player.hasPermission("adminpanel.inspect")) { player.sendMessage(ChatColor.RED + "No tienes permiso para inspeccionar jugadores."); return; }
            openInspect(player, target, click == ClickType.SHIFT_RIGHT);
        } else {
            player.teleport(target.getLocation());
            player.sendMessage(ChatColor.GREEN + "Teletransportado hasta " + target.getName() + ".");
            player.closeInventory();
        }
    }

    private void clickInspect(Player player, MenuHolder holder, int slot) {
        if (!player.hasPermission("adminpanel.inspect")) { player.closeInventory(); return; }
        if (slot == 45) { openPlayers(player); return; }
        if (slot == 53) { player.closeInventory(); return; }
        Player target = holder.target == null ? null : Bukkit.getPlayer(holder.target);
        if (target == null) { player.sendMessage(ChatColor.RED + "Ese jugador ya no está conectado."); return; }
        if (slot == 47) { openInspect(player, target, holder.ender); return; }
        if (slot == 51) { openInspect(player, target, !holder.ender); return; }
        if (slot >= 45) return;
        ItemStack item = holder.getInventory().getItem(slot);
        if (item == null || item.getType() == Material.AIR) return;
        describe(player, target, item);
    }

    private void describe(Player admin, Player target, ItemStack item) {
        admin.sendMessage(ChatColor.AQUA + "--- Objeto de " + target.getName() + " ---");
        admin.sendMessage(ChatColor.GRAY + "Tipo: " + ChatColor.WHITE + item.getType().name() + ChatColor.GRAY + "  Cantidad: " + ChatColor.WHITE + item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) admin.sendMessage(ChatColor.GRAY + "Nombre: " + ChatColor.WHITE + meta.getDisplayName());
        if (meta != null && meta.hasLore() && meta.getLore() != null) admin.sendMessage(ChatColor.GRAY + "Lore: " + ChatColor.WHITE + String.join(" | ", meta.getLore()));
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (!enchants.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) parts.add(e.getKey().getKey().getKey() + " " + e.getValue());
            admin.sendMessage(ChatColor.GRAY + "Encantamientos: " + ChatColor.WHITE + String.join(", ", parts));
        }
    }

    private void setWorldTime(long time) { for (World world : Bukkit.getWorlds()) world.setTime(time); }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (maintenance && !event.getPlayer().hasPermission("adminpanel.bypass")) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED + "El servidor está en mantenimiento. Inténtalo más tarde.");
        }
    }
}
