package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * ヒーラー。
 *
 * <p>味方 (自分以外のプレイヤー) への攻撃がなかったことになり、本来与えるはずだった
 * ダメージの半分をその相手に回復として渡し、同じ量を自分が受ける。
 */
public final class HealerModifier extends BaseModifier {

    public static final double CONVERT_RATIO = 0.5;

    public HealerModifier() {
        super("healer", "ヒーラー", Material.GOLDEN_APPLE,
                "プレイヤーへの攻撃がなかったことになり",
                "与えるはずだったダメージの半分を相手に回復し",
                "同じ量を自分が受ける");
    }

    @Override
    public int weight() {
        // PvP を丸ごと自傷に変える。連携相手が居ないと純粋な自滅で、ハードコアでは重い。
        return 4;
    }

    @Override
    public void onDealtDamage(Player player, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target) || target.equals(player)) {
            return;
        }
        double damage = event.getFinalDamage();
        event.setCancelled(true);
        if (damage <= 0) {
            return;
        }
        double half = converted(damage);
        target.heal(half, EntityRegainHealthEvent.RegainReason.CUSTOM);
        target.playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
        // 自傷分。これがまた効果を誘発しないよう合成ダメージとして通す
        SyntheticDamage.run(() -> player.damage(half));
    }

    static double converted(double damage) {
        return damage * CONVERT_RATIO;
    }
}
