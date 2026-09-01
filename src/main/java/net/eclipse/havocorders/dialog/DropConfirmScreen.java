package net.eclipse.havocorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.eclipse.havocorders.HavocOrders;
import net.eclipse.havocorders.util.NumberUtil;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Second step for "drop everything", because it is easy to lose loot that way. */
public class DropConfirmScreen extends Screen {

    public DropConfirmScreen(HavocOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "DROP-CONFIRM";
    }

    private Map<String, String> placeholders() {
        return Map.of("amount",
                NumberUtil.count(plugin.orders().collectableTotal(player.getUniqueId())));
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
                    int dropped = plugin.orders().dropOrders(player,
                            plugin.orders().collectable(player.getUniqueId()));
                    if (dropped > 0) {
                        success();
                        tell(Text.apply(plugin.message("DROPPED-ALL"),
                                Map.of("amount", NumberUtil.count(dropped))));
                    } else {
                        deny();
                    }
                    session.setCollectPage(0);
                    new CollectScreen(plugin, player).show();
                })
        );
    }
}
