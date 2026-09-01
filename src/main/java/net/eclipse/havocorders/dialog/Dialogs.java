package net.eclipse.havocorders.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.eclipse.havocorders.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin wrappers over the Paper dialog API so the screens stay readable.
 *
 * Every button uses a local {@link DialogAction#customClick} callback rather than a
 * registry key plus a PlayerCustomClickEvent listener. That keeps all the handling
 * inline with the screen that built it, and avoids a global event handler firing for
 * every dialog click on the server.
 */
public final class Dialogs {

    /**
     * Callbacks are cheap but not free, so they are bounded. Each screen is rebuilt and
     * re-shown after any click, so a handful of uses covers double-clicks and stale
     * screens without leaking callbacks for hours.
     */
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(32)
            .lifetime(Duration.ofMinutes(10))
            .build();

    private Dialogs() {
    }

    /**
     * @param exit    footer button (back / close), or null for none
     * @param columns grid width; the window scales with columns x button width
     */
    public static Dialog build(Component title,
                               List<DialogBody> body,
                               List<DialogInput> inputs,
                               List<ActionButton> buttons,
                               ActionButton exit,
                               int columns) {
        int safeColumns = Math.max(1, columns);
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons, exit, safeColumns)));
    }

    public static ActionButton button(Component label, Component tooltip, int width, DialogActionCallback callback) {
        return ActionButton.create(label, tooltip, width, DialogAction.customClick(callback, OPTIONS));
    }

    /** A button with no action, which simply closes the dialog. */
    public static ActionButton closeButton(ConfigurationSection section,
                                           Map<String, String> placeholders,
                                           int width) {
        String label = section == null ? "&7Close" : section.getString("LABEL", "&7Close");
        List<String> tooltip = section == null ? List.of() : section.getStringList("TOOLTIP");
        Component tooltipComponent = tooltip.isEmpty()
                ? null
                : Text.multiline(Text.apply(tooltip, placeholders));
        return ActionButton.create(Text.component(Text.apply(label, placeholders)),
                tooltipComponent, width, null);
    }

    // ------------------------------------------------------------------ config helpers

    /** Reads LABEL / TOOLTIP from a button section and builds the button. */
    public static ActionButton fromConfig(ConfigurationSection section,
                                          Map<String, String> placeholders,
                                          int width,
                                          DialogActionCallback callback) {
        String label = section == null ? "Button" : section.getString("LABEL", "Button");
        List<String> tooltip = section == null ? List.of() : section.getStringList("TOOLTIP");
        Component tooltipComponent = tooltip.isEmpty()
                ? null
                : Text.multiline(Text.apply(tooltip, placeholders));
        return button(Text.component(Text.apply(label, placeholders)), tooltipComponent, width, callback);
    }

    /**
     * Item preview body.
     *
     * Uses the explicit six-argument factory rather than the builder overload:
     * DialogBody.item(ItemStack) returns a builder, not a body. Size is in pixels and
     * the API caps it at 256; the vanilla default of 16 is tiny, so this is configurable.
     */
    public static DialogBody item(ItemStack stack, int size) {
        int clamped = Math.max(1, Math.min(256, size));
        return DialogBody.item(stack, null, true, true, clamped, clamped);
    }

    /** Turns a BODY list from config into plain-message body entries. */
    public static List<DialogBody> body(List<String> lines, Map<String, String> placeholders) {
        List<DialogBody> body = new ArrayList<>();
        if (lines == null) return body;
        for (String line : Text.apply(lines, placeholders)) {
            body.add(DialogBody.plainMessage(Text.component(line)));
        }
        return body;
    }
}
