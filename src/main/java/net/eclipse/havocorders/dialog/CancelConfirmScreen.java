package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.OrderManager;
import net.eclipse.havocorders.model.Order;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CancelConfirmScreen extends Screen {

    private final UUID orderId;

    public CancelConfirmScreen(HavocOrders plugin, Player player, UUID orderId) {
        super(plugin, player);
        this.orderId = orderId;
    }

    @Override
    protected String configPath() {
        return "CANCEL-CONFIRM";
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
        return Dialogs.body(lines("BODY"), Placeholders.of(order));
    }

    @Override
    protected ActionButton exitButton() {
        Order order = order();
        return backButton("BACK", order == null ? Map.of() : Placeholders.of(order),
                () -> new ManageOrderScreen(plugin, player, orderId).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Order order = order();
        Map<String, String> placeholders = order == null ? Map.of() : Placeholders.of(order);
        return List.of(
                configButton("CONFIRM", placeholders, (view, audience) -> {
                    Order current = order();
                    if (current == null) {
                        deny();
                        new MyOrdersScreen(plugin, player).show();
                        return;
                    }
                    OrderManager.Result result = plugin.orders().cancel(player, current);
                    tell(result.message());
                    if (result.success()) success();
                    else deny();
                    new MyOrdersScreen(plugin, player).show();
                })
        );
    }
}
