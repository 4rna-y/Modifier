package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 地雷系。
 *
 * <p>自分の放った矢が敵対モブに命中すると、命中地点で爆発する。爆発のたびに満腹度が1減る。
 * 爆発はブロックを壊さず、火も点けない。
 */
public final class LandmineModifier extends BaseModifier {

    /** 爆発の強さ。クリーパーが 3.0。 */
    public static final float EXPLOSION_POWER = 2.0f;
    public static final int HUNGER_COST = 1;
    public static final boolean SET_FIRE = false;
    public static final boolean BREAK_BLOCKS = false;

    public LandmineModifier() {
        super("landmine", "地雷系", Material.TNT,
                "自分の矢が敵対モブに当たると",
                "命中地点で爆発する",
                "爆発のたびに満腹度が 1 減る");
    }

    @Override
    public int weight() {
        // 弓限定で満腹度が対価。自分にも当たるので暴走しにくい。
        return 8;
    }

    @Override
    public void onProjectileHit(Player player, ProjectileHitEvent event) {
        // 「矢」だけが対象。トライデントは AbstractArrow を継承しているので明示的に外す
        if (!(event.getEntity() instanceof AbstractArrow)
                || event.getEntity() instanceof Trident) {
            return;
        }
        if (!(event.getHitEntity() instanceof Enemy target)) {
            return;
        }
        // source に射手を渡すことで、爆発ダメージの帰属もこのプレイヤーになる
        target.getWorld().createExplosion(player, target.getLocation(),
                EXPLOSION_POWER, SET_FIRE, BREAK_BLOCKS);
        player.setFoodLevel(Math.max(0, player.getFoodLevel() - HUNGER_COST));
    }
}
