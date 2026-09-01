package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.ItemCatalogue;
import net.eclipse.havocorders.util.NumberUtil;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemPickerScreen extends Screen {

    public ItemPickerScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "ITEM-PICKER";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("SETTINGS.ITEMS-PER-PAGE", 12));
    }

    private List<ItemCatalogue.Entry> results() {
        return plugin.catalogue().search(session.getItemFilter(),
                session.getItemQuery().toLowerCase(Locale.ROOT));
    }

    private Map<String, String> screenPlaceholders(List<ItemCatalogue.Entry> results) {
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getItemPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("results", NumberUtil.count(results.size()));
        map.put("filter", plugin.categoryName(session.getItemFilter()));
        map.put("query", session.getItemQuery().isEmpty() ? "none" : session.getItemQuery());
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(screenPlaceholders(results()));
    }

    @Override
    protected List<DialogBody> body() {
        List<ItemCatalogue.Entry> results = results();
        List<DialogBody> body = Dialogs.body(lines("BODY"), screenPlaceholders(results));
        if (results.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.component(string("EMPTY", "&7No matches."))));
        }
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", screenPlaceholders(results()),
                () -> new NewOrderScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        List<ItemCatalogue.Entry> results = results();
        Map<String, String> screen = screenPlaceholders(results);
        int pages = totalPages(results.size(), perPage());
        List<ActionButton> buttons = new ArrayList<>();

        for (ItemCatalogue.Entry entry : slice(results, session.getItemPage(), perPage())) {
            Map<String, String> placeholders = new HashMap<>(screen);
            placeholders.put("name", entry.name());
            buttons.add(configButton("ITEM", placeholders, (view, audience) -> {
                if (entry.stack().getType() == Material.ENCHANTED_BOOK) {
                    click();
                    new EnchantPickerScreen(plugin, player).show();
                    return;
                }
                session.setDraftItem(entry.stack());
                success();
                new NewOrderScreen(plugin, player).show();
            }));
        }

        if (session.getItemPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, (view, audience) -> {
                session.setItemPage(session.getItemPage() - 1);
                click();
                show();
            }));
        }
        if (session.getItemPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, (view, audience) -> {
                session.setItemPage(session.getItemPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("FILTER", screen, (view, audience) -> {
            session.setItemFilter(session.getItemFilter().next());
            click();
            show();
        }));
        buttons.add(configButton("SEARCH", screen, (view, audience) -> {
            click();
            new SearchScreen(plugin, player, session.getItemQuery(), value -> {
                session.setItemQuery(value);
                new ItemPickerScreen(plugin, player).show();
            }, () -> new ItemPickerScreen(plugin, player).show()).show();
        }));
        return buttons;
    }
}
