package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * アホ。
 *
 * <p>落下ダメージが70%減る代わりに、採掘速度が70%落ち、道具や防具の耐久値減少が3倍になる。
 */
public final class AhoModifier extends BaseModifier {

    public static final double FALL_DAMAGE_MULTIPLIER = -0.70;
    public static final double MINING_SPEED_MULTIPLIER = -0.70;
    public static final int DURABILITY_MULTIPLIER = 3;

    public AhoModifier() {
        super("aho", "アホ", Material.HAY_BLOCK,
                "落下ダメージ -70%",
                "採掘速度 -70%",
                "道具と装備の耐久値減少 3倍");
    }

    @Override
    public int weight() {
        // 採掘 -70% と耐久3倍が重く、落下軽減が見合っていない。
        return 5;
    }

    @Override
    public void apply(Player player) {
        Attributes.setScalar(player, Attribute.FALL_DAMAGE_MULTIPLIER,
                key("fall"), FALL_DAMAGE_MULTIPLIER);
        Attributes.setScalar(player, Attribute.BLOCK_BREAK_SPEED,
                key("mining"), MINING_SPEED_MULTIPLIER);
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.FALL_DAMAGE_MULTIPLIER, key("fall"));
        Attributes.clear(player, Attribute.BLOCK_BREAK_SPEED, key("mining"));
    }

    @Override
    public void onItemDamage(Player player, PlayerItemDamageEvent event) {
        event.setDamage(event.getDamage() * DURABILITY_MULTIPLIER);
    }
}
