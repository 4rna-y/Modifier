package io.github.modifier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 不眠症。
 *
 * <p>ベッドで夜を明かすと、2分間移動速度が下がる。
 */
public final class InsomniaModifier extends BaseModifier {

    public static final int SLOWNESS_DURATION_TICKS = 2 * 60 * 20;

    public InsomniaModifier() {
        super("insomnia", "不眠症", Material.RED_BED,
                "ベッドで夜を明かすと",
                "2分間 移動速度が下がる");
    }

    @Override
    public int weight() {
        // 純粋な下方修正だが、寝なければ避けられる。食中毒より軽い。
        return 5;
    }

    @Override
    public void onNightSkipped(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, SLOWNESS_DURATION_TICKS, 0));
    }
}
