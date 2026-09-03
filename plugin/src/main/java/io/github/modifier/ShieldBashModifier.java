package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * シールドバッシュ。
 *
 * <p>盾で攻撃を防ぐと、防いだダメージの半分を攻撃してきた相手へ返す。
 */
public final class ShieldBashModifier extends BaseModifier {

    /** 防いだ量のうち、相手へ返す割合。 */
    public static final double REFLECT_RATIO = 0.5;

    public ShieldBashModifier() {
        super("shield_bash", "シールドバッシュ", Material.SHIELD,
                "盾で防いだダメージの半分を",
                "攻撃してきた相手へ返す");
    }

    /**
     * 盾が吸収した量を求める。
     *
     * <p>盾で防いだとき、ダメージ計算の {@code BLOCKING} の段に吸収した分が負の値で入る。
     * 防いでいない、あるいは吸収が無いときは 0。
     */
    public static double blockedAmount(EntityDamageEvent event) {
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            return 0;
        }
        return Math.abs(event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING));
    }

    /** 返すダメージ。 */
    public static double reflectedDamage(double blocked) {
        return blocked * REFLECT_RATIO;
    }

    @Override
    public int weight() {
        // 盾を構える技術が要る。受動的に強くならないのが良い。
        return 8;
    }

    @Override
    public void onDamagedConfirmed(Player player, EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (!player.isBlocking()) {
            return;
        }
        double blocked = blockedAmount(event);
        if (blocked <= 0) {
            return;
        }
        // 攻撃してきたのが生き物のときだけ返す (矢などの飛び道具そのものは対象外)
        if (!(byEntity.getDamager() instanceof LivingEntity attacker) || attacker.equals(player)) {
            return;
        }
        // 反射がさらに反射を呼ぶ連鎖を防ぐため、合成ダメージとして通す
        SyntheticDamage.run(() -> attacker.damage(reflectedDamage(blocked), player));
        player.playSound(player, Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.7f);
    }
}
