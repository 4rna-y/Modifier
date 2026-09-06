package io.github.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * モディファイアを選んだときに、どれを選んでも受け取るウェルカムギフト。
 *
 * <p>ベッド・パン 4・木材 16・石炭 16 に加えて、5% でエンチャントされた金のリンゴ。
 * 配り方はモディファイア固有の道具と同じ ({@link StartingItems#give})。
 */
final class WelcomeGift {

    /** 必ずもらえるもの。 */
    static final List<StartingItems.Item> ALWAYS = List.of(
            StartingItems.Item.of(Material.RED_BED),
            StartingItems.Item.of(Material.BREAD, 4),
            StartingItems.Item.of(Material.OAK_PLANKS, 16),
            StartingItems.Item.of(Material.COAL, 16));

    static final double GOLDEN_APPLE_CHANCE = 0.05;
    static final StartingItems.Item GOLDEN_APPLE = StartingItems.Item.of(Material.ENCHANTED_GOLDEN_APPLE);

    private WelcomeGift() {
    }

    /** 今回の中身を引く。 */
    static List<StartingItems.Item> roll(Random random) {
        List<StartingItems.Item> items = new ArrayList<>(ALWAYS);
        if (random.nextDouble() < GOLDEN_APPLE_CHANCE) {
            items.add(GOLDEN_APPLE);
        }
        return List.copyOf(items);
    }

    /** 引いて渡す。当たりが入っていれば本人にだけ知らせる。 */
    static void give(Player player, Random random) {
        List<StartingItems.Item> items = roll(random);
        StartingItems.give(player, items);
        player.sendMessage(Component.text("ウェルカムギフトを受け取った。", NamedTextColor.GRAY));
        if (items.contains(GOLDEN_APPLE)) {
            player.sendMessage(Component.text("……エンチャントされた金のリンゴが入っている！", NamedTextColor.GOLD));
        }
    }
}
