package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;

/**
 * デブ。
 *
 * <p>移動速度が30%低下し、満腹度の減りが10%速くなる代わりに、受けるダメージが25%減る。
 */
public final class FatModifier extends BaseModifier {

    public static final double SPEED_MULTIPLIER = -0.30;
    public static final float EXHAUSTION_MULTIPLIER = 1.10f;
    public static final double DAMAGE_MULTIPLIER = 0.75;

    public FatModifier() {
        super("fat", "デブ", Material.BREAD,
                "移動速度 -30%",
                "満腹度の減り +10%",
                "受けるダメージ -25%");
    }

    @Override
    public int weight() {
        // 被ダメ -25% は強いが移動 -30% も本当に痛い。取引が明快で引いて不快でない。
        return 8;
    }

    @Override
    public void apply(Player player) {
        Attributes.setScalar(player, Attribute.MOVEMENT_SPEED, key("speed"), SPEED_MULTIPLIER);
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.MOVEMENT_SPEED, key("speed"));
    }

    @Override
    public void onExhaustion(Player player, EntityExhaustionEvent event) {
        event.setExhaustion(event.getExhaustion() * EXHAUSTION_MULTIPLIER);
    }

    @Override
    public void onDamaged(Player player, EntityDamageEvent event) {
        // 素のダメージを削る。防具などの計算はこの後に掛かるので、最終ダメージも同じ割合で減る。
        event.setDamage(event.getDamage() * DAMAGE_MULTIPLIER);
    }
}
