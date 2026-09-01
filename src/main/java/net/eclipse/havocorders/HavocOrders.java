package net.eclipse.havocorders;

import net.eclipse.havocorders.command.OrdersCommand;
import net.eclipse.havocorders.command.ToggleAlertsCommand;
import net.eclipse.havocorders.integration.OrdersPlaceholders;
import net.eclipse.havocorders.economy.EconomyHook;
import net.eclipse.havocorders.economy.SellPrices;
import net.eclipse.havocorders.manager.DropJob;
import net.eclipse.havocorders.manager.InventoryScanner;
import net.eclipse.havocorders.manager.ItemCatalogue;
import net.eclipse.havocorders.manager.OrderManager;
import net.eclipse.havocorders.manager.Profiles;
import net.eclipse.havocorders.manager.SessionManager;
import net.eclipse.havocorders.model.SortOption;
import net.eclipse.havocorders.storage.LegacyImporter;
import net.eclipse.havocorders.storage.SqlStorage;
import net.eclipse.havocorders.util.Category;
import net.eclipse.havocorders.util.NumberUtil;
import net.eclipse.havocorders.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.SQLException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class HavocOrders extends JavaPlugin {

    private FileConfiguration dialogs;

    private EconomyHook economy;
    private SellPrices sellPrices;
    private SqlStorage storage;
    private OrderManager orderManager;
    private ItemCatalogue catalogue;
    private SessionManager sessions;
    private InventoryScanner inventories;
    private Profiles profiles;
    private LegacyImporter importer;

    private final Set<Material> blocked = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDialogs();
        loadBlockedItems();
        NumberUtil.setAbbreviate(getConfig().getBoolean("SETTINGS.ABBREVIATE-NUMBERS", true));

        economy = new EconomyHook(this);
        if (!economy.setup()) {
            getLogger().severe("Disabling HavocOrders - Vault economy is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        sellPrices = new SellPrices(this);

        storage = new SqlStorage(this);
        try {
            storage.initialise();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not initialise the database - disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        orderManager = new OrderManager(this, storage);
        orderManager.loadAll();

        profiles = new Profiles(this, storage);
        profiles.loadAll();

        importer = new LegacyImporter(this);
        runAutoImport();

        catalogue = new ItemCatalogue(this);
        catalogue.build();

        inventories = new InventoryScanner(this);

        sessions = new SessionManager();
        getServer().getPluginManager().registerEvents(sessions, this);

        PluginCommand command = getCommand("orders");
        if (command != null) {
            OrdersCommand executor = new OrdersCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        PluginCommand toggle = getCommand("toggleorderalerts");
        if (toggle != null) toggle.setExecutor(new ToggleAlertsCommand(this));

        registerPlaceholders();

        long expiryTicks = Math.max(20L, getConfig().getInt("SETTINGS.EXPIRY-CHECK-SECONDS", 60) * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> orderManager.tickExpiry(), expiryTicks, expiryTicks);

        // Single batched writer. Nothing else touches the database at runtime.
        long saveTicks = Math.max(20L, getConfig().getInt("SETTINGS.SAVE-INTERVAL-SECONDS", 30) * 20L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            orderManager.flush();
            profiles.flush();
        }, saveTicks, saveTicks);

        getLogger().info("HavocOrders enabled. Dialogs require Paper/Purpur 1.21.7+ and a 1.21.6+ client.");
    }

    /** PlaceholderAPI is optional; skip quietly when it is not installed. */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            new OrdersPlaceholders(this).register();
            getLogger().info("Registered PlaceholderAPI expansion 'havocorders'.");
        } catch (Throwable ex) {
            getLogger().warning("Could not register placeholders: " + ex.getMessage());
        }
    }

    /** ON/OFF text used by the placeholders, styled in config. */
    public String statusText(boolean enabled) {
        return getConfig().getString("PLACEHOLDERS." + (enabled ? "ENABLED-TEXT" : "DISABLED-TEXT"),
                enabled ? "ON" : "OFF");
    }

    /** Picks up a legacy orders.db dropped into the plugin folder. */
    private void runAutoImport() {
        if (!getConfig().getBoolean("IMPORT.ENABLED", true)) return;
        File file = importer.defaultFile();
        if (!file.isFile()) return;

        getLogger().info("Found " + file.getName() + ", importing legacy orders...");
        try {
            LegacyImporter.Report report = importer.importFrom(file);
            for (String line : importer.summary(report)) getLogger().info(line);
            if (getConfig().getBoolean("IMPORT.RENAME-WHEN-DONE", true)) {
                importer.markDone(file);
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Legacy import failed; nothing was changed.", ex);
        }
    }

    @Override
    public void onDisable() {
        if (profiles != null) profiles.flush();
        if (orderManager != null) {
            orderManager.flush();
            storage.saveAll(orderManager.snapshot());
        }
        if (storage != null) storage.close();
    }

    // ------------------------------------------------------------------ config

    public void reloadDialogs() {
        File file = new File(getDataFolder(), "dialogs.yml");
        if (!file.exists()) saveResource("dialogs.yml", false);
        dialogs = YamlConfiguration.loadConfiguration(file);
    }

    private void loadBlockedItems() {
        blocked.clear();
        for (String raw : getConfig().getStringList("ITEM-RESTRICTIONS.BLOCKED-ITEMS")) {
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null) {
                getLogger().warning("Unknown blocked item: " + raw);
                continue;
            }
            blocked.add(material);
        }
    }

    public void reloadEverything() {
        reloadConfig();
        reloadDialogs();
        loadBlockedItems();
        NumberUtil.setAbbreviate(getConfig().getBoolean("SETTINGS.ABBREVIATE-NUMBERS", true));
        sellPrices.reload();
        catalogue.build();
    }

    public boolean isBlocked(Material material) {
        return material == null || material == Material.AIR || blocked.contains(material);
    }

    public ConfigurationSection dialogSection(String path) {
        return dialogs.getConfigurationSection("DIALOGS." + path);
    }

    public String sortName(SortOption option) {
        return dialogs.getString("NAMES.SORT." + option.getConfigKey(), Text.pretty(option.name()));
    }

    public String categoryName(Category category) {
        return dialogs.getString("NAMES.FILTER." + category.name(), Text.pretty(category.name()));
    }

    public String message(String path) {
        String prefix = getConfig().getString("MESSAGES.PREFIX", "");
        String message = getConfig().getString("MESSAGES." + path, "");
        return message.isEmpty() ? "" : prefix + message;
    }

    // ------------------------------------------------------------------ accessors

    public EconomyHook economy() {
        return economy;
    }

    public SellPrices sellPrices() {
        return sellPrices;
    }

    public OrderManager orders() {
        return orderManager;
    }

    public ItemCatalogue catalogue() {
        return catalogue;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public InventoryScanner inventories() {
        return inventories;
    }

    public Profiles profiles() {
        return profiles;
    }

    public LegacyImporter importer() {
        return importer;
    }

    // ------------------------------------------------------------------ scheduling

    public void sync(Runnable runnable) {
        if (!isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            getServer().getScheduler().runTask(this, runnable);
        }
    }

    public void async(Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    /**
     * Releases queued loot a few stacks per tick at the player's feet.
     *
     * Stacks are cut from the job as they are dropped rather than pre-built, so dropping
     * a million items holds one template and a counter instead of thousands of stacks.
     * Dropping everything in one tick is the classic way to stall a server; this doesn't.
     */
    public void spreadDrop(Player player, Deque<DropJob> jobs, int perTick) {
        Location fallback = player.getLocation();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (jobs.isEmpty()) {
                    cancel();
                    return;
                }

                boolean online = player.isOnline();
                Location target = online ? player.getLocation() : fallback;
                // An offline player's remaining loot lands where they were standing,
                // rather than being silently lost.
                int budget = online ? perTick : Integer.MAX_VALUE;

                for (int index = 0; index < budget && !jobs.isEmpty(); index++) {
                    DropJob job = jobs.peek();
                    ItemStack stack = job.nextStack();
                    if (stack == null) {
                        jobs.poll();
                        index--;
                        continue;
                    }
                    target.getWorld().dropItemNaturally(target, stack);
                }

                if (jobs.isEmpty()) cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }
}
