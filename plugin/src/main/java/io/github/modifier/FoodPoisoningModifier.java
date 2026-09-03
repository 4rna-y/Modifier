package io.github.modifier;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 食中毒。
 *
 * <p>食べ物を食べると75%の確率で空腹と吐き気になる。
 */
public final class FoodPoisoningModifier extends BaseModifier {

    public static final double CHANCE = 0.75;
    public static final int HUNGER_DURATION_TICKS = 20 * 20;
    /** 吐き気は演出のかかりが遅いので、これより短くすると何も見えないまま終わる。 */
    public static final int NAUSEA_DURATION_TICKS = 15 * 20;

    private final Random random;

    public FoodPoisoningModifier(Random random) {
        super("food_poisoning", "食中毒", Material.ROTTEN_FLESH,
                "食べ物を食べると 75% の確率で",
                "空腹と吐き気になる");
        this.random = random;
    }

    @Override
    public int weight() {
        // 食事のたび 75%。上振れが無く、しかも食べないわけにいかないので回避もできない。
        return 4;
    }

    @Override
    public void onConsume(Player player, PlayerItemConsumeEvent event) {
        // 「食べ物」だけが対象。ポーションや牛乳では起きない
        if (!event.getItem().getType().isEdible()) {
            return;
        }
        if (random.nextDouble() >= CHANCE) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, HUNGER_DURATION_TICKS, 0));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, NAUSEA_DURATION_TICKS, 0));
    }
}
