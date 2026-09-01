package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.manager.OrderManager;
import net.eclipse.havocorders.util.NumberUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class SellConfirmScreen extends Screen {

    public SellConfirmScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "SELL-CONFIRM";
    }

    private Map<String, String> placeholders() {
        OrderManager.SellPreview preview = plugin.orders().previewSell(player);
        return Map.of(
                "amount", NumberUtil.count(preview.sellableItems()),
                "unsellable", NumberUtil.count(preview.unsellableItems()),
                "total", NumberUtil.money(preview.total()));
    }

    @Override
    protected Component title() {
        return titleFrom(placeholders());
    }

    @Override
    protected List<DialogBody> body() {
        return Dialogs.body(lines("BODY"), placeholders());
    }

    @Override
    protected ActionButton exitButton() {
        return backButton("BACK", placeholders(), () -> new CollectScreen(plugin, player).show());
    }

    @Override
    protected List<ActionButton> buttons() {
        Map<String, String> placeholders = placeholders();
        return List.of(
                configButton("CONFIRM", placeholders, (view, audience) -> {
                    double earned = plugin.orders().sellAll(player);
                    if (earned > 0) success();
                    else deny();
                    new CollectScreen(plugin, player).show();
                })
        );
    }
}
