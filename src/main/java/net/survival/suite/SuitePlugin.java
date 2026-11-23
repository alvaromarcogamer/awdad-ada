package net.survival.suite;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin unico: ADMIN PANEL + COSMETICCRAFT + PETCRAFT. */
public final class SuitePlugin extends JavaPlugin {
    private AdminModule admin;
    private CosmeticModule cosmetics;
    private PetModule pets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        admin = new AdminModule(this);
        cosmetics = new CosmeticModule(this);
        pets = new PetModule(this);
        admin.enable();
        cosmetics.enable();
        pets.enable();
        Bukkit.getPluginManager().registerEvents(new MenuListener(admin, cosmetics, pets), this);
        getLogger().info("ServerSuite activado (AdminPanel, CosmeticCraft, PETCRAFT).");
    }

    @Override
    public void onDisable() {
        if (admin != null) admin.disable();
        if (cosmetics != null) cosmetics.disable();
        if (pets != null) pets.disable();
    }

    void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("El comando /" + name + " no esta declarado en plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
    }
}
