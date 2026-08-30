package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * 黒の剣士。
 *
 * <p>敵対モブへ与えたダメージの10%だけ自分の HP が回復する。矢による攻撃も対象。
 */
public final class BlackSwordsmanModifier extends BaseModifier {

    public static final double LIFESTEAL_RATIO = 0.10;

    public BlackSwordsmanModifier() {
        super("black_swordsman", "黒の剣士", Material.NETHERITE_SWORD,
                "敵対モブへ与えたダメージの",
                "10% を吸収して HP が回復する");
    }

    @Override
    public int weight() {
        // 対モブ限定の吸収。生存に効くが上限がある。
        return 8;
    }

    @Override
    public void onDealtDamage(Player player, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        double amount = event.getFinalDamage() * LIFESTEAL_RATIO;
        if (amount <= 0) {
            return;
        }
        player.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }
}
