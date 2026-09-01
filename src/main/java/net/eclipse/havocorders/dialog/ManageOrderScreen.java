package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.model.Order;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ManageOrderScreen extends Screen {

    private final UUID orderId;

    public ManageOrderScreen(HavocOrders plugin, Player player, UUID orderId) {
        super(plugin, player);
        this.orderId = orderId;
    }

    @Override
    protected String configPath() {
        return "MANAGE-ORDER";
    }

    private Order order() {
        return plugin.orders().byId(orderId);
    }

    @Override
    protected Component title() {
        Order order = order();
        return titleFrom(order == null ? Map.of() : Placeholders.of(order));
    }

    @Override
    protected List<DialogBody> body() {
        Order order = order();
        if (order == null) {
            return List.of(DialogBody.plainMessage(Text.component(plugin.message("ORDER_DELETED"))));
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(itemBody(order.getItemCopy(1)));
        body.addAll(Dialogs.body(lines("BODY"), Placeholders.of(order)));
        return body;
    }

    @Override
    protected ActionButton exitButton() {
        Order order = order();
        return backButton("BACK", order == null ? Map.of() : Placeholders.of(order),
                () -> new MyOrdersScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Order order = order();
        List<ActionButton> buttons = new ArrayList<>();
        Map<String, String> placeholders = order == null ? Map.of() : Placeholders.of(order);

        if (order != null) {
            if (order.getCollectable() > 0) {
                buttons.add(configButton("COLLECT", placeholders, (view, audience) -> {
                    click();
                    new CollectScreen(plugin, player).show();
                }));
            }
            buttons.add(configButton("CANCEL-ORDER", placeholders, (view, audience) -> {
                click();
                new CancelConfirmScreen(plugin, player, orderId).show();
            }));
        }

        return buttons;
    }
}
