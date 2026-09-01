package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.ItemCatalogue;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnchantPickerScreen extends Screen {

    public EnchantPickerScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "ENCHANT-PICKER";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("SETTINGS.ITEMS-PER-PAGE", 12));
    }

    private Map<String, String> screenPlaceholders(int size) {
        int pages = totalPages(size, perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getEnchantPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(screenPlaceholders(plugin.catalogue().enchantments().size()));
    }

    @Override
    protected List<DialogBody> body() {
        return Dialogs.body(lines("BODY"),
                screenPlaceholders(plugin.catalogue().enchantments().size()));
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", screenPlaceholders(plugin.catalogue().enchantments().size()),
                () -> new ItemPickerScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        List<ItemCatalogue.EnchantEntry> all = plugin.catalogue().enchantments();
        Map<String, String> screen = screenPlaceholders(all.size());
        int pages = totalPages(all.size(), perPage());
        List<ActionButton> buttons = new ArrayList<>();

        for (ItemCatalogue.EnchantEntry entry : slice(all, session.getEnchantPage(), perPage())) {
            Map<String, String> placeholders = new HashMap<>(screen);
            placeholders.put("enchantment", entry.enchantmentName());
            placeholders.put("level", entry.levelLabel());
            buttons.add(configButton("ENCHANT", placeholders, (view, audience) -> {
                session.setDraftItem(entry.book());
                success();
                new NewOrderScreen(plugin, player).show();
            }));
        }

        if (session.getEnchantPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, (view, audience) -> {
                session.setEnchantPage(session.getEnchantPage() - 1);
                click();
                show();
            }));
        }
        if (session.getEnchantPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, (view, audience) -> {
                session.setEnchantPage(session.getEnchantPage() + 1);
                click();
                show();
            }));
        }
        return buttons;
    }
}
