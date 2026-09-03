package io.github.modifier;

import org.bukkit.Material;

/**
 * 後悔。
 *
 * <p>後悔する。するだけでなにもしない。本当になにもしない。
 */
public final class RegretModifier extends BaseModifier {

    public RegretModifier() {
        super("regret", "後悔", Material.WITHER_ROSE,
                "後悔する。するだけでなにもしない。");
    }

    @Override
    public int weight() {
        // なにもしないのに一番よく出る。引くたびに後悔できるように。
        return 10;
    }
}
