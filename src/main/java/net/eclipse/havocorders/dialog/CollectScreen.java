package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.OrderManager;
import net.eclipse.havocorders.model.Order;
import net.eclipse.havocorders.util.NumberUtil;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loot waiting to be taken. Supports collecting per order, collecting everything that
 * fits, and dropping the current page, several pages, or the lot.
 *
 * One entry per order rather than one per stack: an order for a million diamonds would
 * otherwise render as 15,625 buttons and allocate that many records every time the
 * screen was drawn.
 */
public class CollectScreen extends Screen {

    /** Computed once per draw; see MyOrdersScreen for why. */
    private List<Order> cached;

    public CollectScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void show() {
        cached = null;
        super.show();
    }

    @Override
    protected String configPath() {
        return "COLLECT";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("SETTINGS.COLLECT-PER-PAGE", 8));
    }

    private int pageBatch() {
        return Math.max(2, plugin.getConfig().getInt("SETTINGS.DROP.PAGE-BATCH", 5));
    }

    private List<Order> results() {
        if (cached == null) cached = plugin.orders().collectable(player.getUniqueId());
        return cached;
    }

    private Map<String, String> screenPlaceholders(List<Order> results) {
        OrderManager.SellPreview preview = plugin.orders().previewSell(player);
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getCollectPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("per_page", String.valueOf(perPage()));
        map.put("pages_batch", String.valueOf(pageBatch()));
        map.put("total_items", NumberUtil.count(plugin.orders().collectableTotal(player.getUniqueId())));
        map.put("sell_total", NumberUtil.money(preview.total()));
        map.put("unsellable", NumberUtil.count(preview.unsellableItems()));
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(screenPlaceholders(results()));
    }

    @Override
    protected List<DialogBody> body() {
        List<Order> results = results();
        List<DialogBody> body = Dialogs.body(lines("BODY"), screenPlaceholders(results));
        if (results.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.component(string("EMPTY", "&7Nothing waiting."))));
        }
        return body;
    }

    /** Orders covered by a range of pages, starting at the current page. */
    private List<Order> ordersInPages(List<Order> results, int pageCount) {
        Set<Order> unique = new LinkedHashSet<>();
        int start = session.getCollectPage();
        for (int offset = 0; offset < pageCount; offset++) {
            unique.addAll(slice(results, start + offset, perPage()));
        }
        return new ArrayList<>(unique);
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", screenPlaceholders(results()),
                () -> new MyOrdersScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        List<Order> results = results();
        Map<String, String> screen = screenPlaceholders(results);
        int pages = totalPages(results.size(), perPage());
        List<ActionButton> buttons = new ArrayList<>();

        for (Order order : slice(results, session.getCollectPage(), perPage())) {
            int waiting = order.getCollectable();
            Map<String, String> placeholders = Placeholders.of(order);
            placeholders.put("amount", NumberUtil.count(waiting));
            placeholders.put("value", NumberUtil.money(
                    plugin.sellPrices().totalPrice(order.getItem(), waiting)));
            buttons.add(configButton("LOOT", placeholders, (view, audience) -> {
                // Takes whatever fits; the rest stays on the order for next time.
                int collected = plugin.orders().collect(player, order, order.getCollectable());
                if (collected > 0) success();
                else deny();
                show();
            }));
        }

        if (session.getCollectPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, (view, audience) -> {
                session.setCollectPage(session.getCollectPage() - 1);
                click();
                show();
            }));
        }
        if (session.getCollectPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, (view, audience) -> {
                session.setCollectPage(session.getCollectPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("COLLECT-ALL", screen, (view, audience) -> {
            int collected = plugin.orders().collectAll(player);
            if (collected > 0) success();
            else deny();
            show();
        }));

        buttons.add(configButton("DROP-PAGE", screen, (view, audience) ->
                drop(ordersInPages(results, 1))));

        if (pages > 1) {
            buttons.add(configButton("DROP-PAGES", screen, (view, audience) ->
                    drop(ordersInPages(results, pageBatch()))));
            buttons.add(configButton("DROP-ALL", screen, (view, audience) -> {
                click();
                new DropConfirmScreen(plugin, player).show();
            }));
        } else if (!results.isEmpty()) {
            buttons.add(configButton("DROP-ALL", screen, (view, audience) -> {
                click();
                new DropConfirmScreen(plugin, player).show();
            }));
        }

        buttons.add(configButton("SELL-ALL", screen, (view, audience) -> {
            click();
            new SellConfirmScreen(plugin, player).show();
        }));
        return buttons;
    }

    private void drop(List<Order> orders) {
        int dropped = plugin.orders().dropOrders(player, orders);
        if (dropped > 0) {
            success();
            tell(Text.apply(plugin.message("DROPPED-ALL"),
                    Map.of("amount", NumberUtil.count(dropped))));
        } else {
            deny();
        }
        session.setCollectPage(0);
        show();
    }
}
