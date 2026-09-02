package net.eclipse.havocorders.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.OrderManager;
import net.eclipse.havocorders.util.ItemNames;
import net.eclipse.havocorders.util.NumberUtil;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order builder. Amount and price are free-text so shorthand like 1k / 2.5m works;
 * whatever is typed is captured from the response view before navigating away, so the
 * draft survives a trip to the item picker.
 */
public class NewOrderScreen extends Screen {

    private static final String AMOUNT = "amount";
    private static final String PRICE = "price";

    public NewOrderScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "NEW-ORDER";
    }

    /**
     * Pulls the typed values into the session so they are not lost on navigation.
     * A blank or unreadable field keeps the previous value, which is what makes this
     * survive Geyser handing back empty text on Bedrock.
     */
    private void capture(DialogResponseView view) {
        Integer amount = NumberUtil.parseAmount(view.getText(AMOUNT), session.getDraftAmount());
        if (amount != null && amount > 0) session.setDraftAmount(amount);

        Double price = NumberUtil.parse(view.getText(PRICE));
        if (price != null && price > 0) session.setDraftPrice(price);
    }

    private Map<String, String> placeholders() {
        Map<String, String> map = new HashMap<>();
        ItemStack draft = session.getDraftItem();
        map.put("material", draft == null ? "none" : ItemNames.display(draft));
        map.put("amount", NumberUtil.count(session.getDraftAmount()));
        map.put("price", NumberUtil.money(session.getDraftPrice()));
        map.put("total", NumberUtil.money(session.getDraftTotal()));
        map.put("balance", NumberUtil.money(plugin.economy().balance(player)));
        return map;
    }

    @Override
    protected Component title() {
        return titleFrom(placeholders());
    }

    @Override
    protected List<DialogBody> body() {
        List<DialogBody> body = new ArrayList<>();
        ItemStack draft = session.getDraftItem();
        if (draft != null) body.add(itemBody(draft.clone()));
        body.addAll(Dialogs.body(lines("BODY"), placeholders()));
        return body;
    }

    @Override
    protected List<DialogInput> inputs() {
        return List.of(
                DialogInput.text(AMOUNT, Text.component(string("AMOUNT-LABEL", "&fAmount")))
                        .initial(String.valueOf(session.getDraftAmount()))
                        .build(),
                DialogInput.text(PRICE, Text.component(string("PRICE-LABEL", "&fPrice each")))
                        .initial(NumberUtil.exact(session.getDraftPrice()))
                        .build()
        );
    }

    @Override
    protected ActionButton exitButton() {
        // Note: the footer button cannot capture typed input, so the draft keeps
        // whatever was last confirmed on one of the grid buttons.
        return backButton("BACK", placeholders(), () -> new MyOrdersScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Map<String, String> placeholders = placeholders();
        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(configButton("CHOOSE-ITEM", placeholders, (view, audience) -> {
            capture(view);
            click();
            new ItemPickerScreen(plugin, player).show();
        }));

        buttons.add(configButton("HELD-ITEM", placeholders, (view, audience) -> {
            capture(view);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                deny();
                tell(plugin.message("NO-ITEM-SELECTED"));
            } else if (plugin.isBlocked(held.getType())) {
                deny();
                tell(plugin.message("BLOCKED-ITEM"));
            } else {
                session.setDraftItem(held);
                success();
            }
            show();
        }));

        buttons.add(configButton("CONFIRM", placeholders, (view, audience) -> {
            capture(view);
            OrderManager.Result result = plugin.orders().createOrder(
                    player, session.getDraftItem(), session.getDraftAmount(), session.getDraftPrice());
            tell(result.message());
            if (result.success()) {
                success();
                session.clearDraft();
                new MyOrdersScreen(plugin, player).show();
            } else {
                deny();
                show();
            }
        }));

        return buttons;
    }
}
