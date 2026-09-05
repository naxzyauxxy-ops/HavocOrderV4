package net.eclipse.havocorders.util;

import net.eclipse.havocorders.integration.SpawnerSupport;
import org.bukkit.inventory.ItemStack;

/**
 * Decides whether a carried item satisfies an order.
 *
 * Default is ItemStack#isSimilar, which compares every scrap of item data. That is right
 * for ordinary items and wrong for anything that carries mutable state, so integrations
 * can override the rule for the item types they own.
 */
public final class ItemMatching {

    private static SpawnerSupport spawners;

    private ItemMatching() {
    }

    public static void setSpawnerSupport(SpawnerSupport support) {
        spawners = support;
    }

    public static boolean matches(ItemStack template, ItemStack candidate) {
        if (template == null || candidate == null) return false;

        if (spawners != null && spawners.isAvailable() && spawners.isSpawner(template)) {
            return spawners.deliverable(template, candidate);
        }
        return template.isSimilar(candidate);
    }
}
