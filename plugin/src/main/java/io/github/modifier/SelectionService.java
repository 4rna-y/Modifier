package io.github.modifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/** 選択画面の提示を受け持つ。 */
public final class SelectionService {

    public static final int DEFAULT_CHOICE_COUNT = 3;
    public static final String DEFAULT_TITLE = "<dark_gray>モディファイアを選択";

    private final ModifierPlugin plugin;
    private final ModifierRegistry registry;
    private final SelectionStore store;
    private final ModifierEffects effects;

    /**
     * 提示中の選択肢。開き直しても中身が変わらないよう、プレイヤーごとに覚えておく。
     * 覚えていないと画面を閉じ直すだけで引き直せてしまう。
     */
    private final Map<UUID, List<Modifier>> offers = new HashMap<>();
    /** 選択肢の抽選。テストから固定できるよう受け取る。 */
    private final Random random;

    public SelectionService(ModifierPlugin plugin, ModifierRegistry registry,
            SelectionStore store, ModifierEffects effects, Random random) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.effects = effects;
        this.random = random;
    }

    /**
     * 選択をやり直す。
     *
     * <p>保存済みの選択を捨てて効果を外し、提示済みの選択肢も忘れてから開き直す。
     * 選択を残したまま開くと {@code needsSelection} が false のままなので、
     * クリックしても確定処理が走らない。
     */
    public void reselect(Player player) {
        store.clear(player);
        // 選択が無くなったので、掛かっていた常時効果がここで外れる
        effects.apply(player);
        forget(player.getUniqueId());
        open(player);
    }

    /** 選択画面を開く。すでに提示済みなら同じ選択肢を出す。 */
    public void open(Player player) {
        List<Modifier> choices = offers.computeIfAbsent(player.getUniqueId(),
                uuid -> registry.pick(choiceCount(), random));

        if (choices.isEmpty()) {
            plugin.getSLF4JLogger().warn(
                    "モディファイアが1つも登録されていないため、選択画面を開けません。");
            return;
        }
        player.openInventory(new SelectionMenu(choices, title()).getInventory());
    }

    /** 提示済みの選択肢を忘れる。次に開くときは引き直しになる。 */
    public void forget(UUID playerId) {
        offers.remove(playerId);
    }

    private int choiceCount() {
        int configured = plugin.getConfig().getInt("selection.choice-count", DEFAULT_CHOICE_COUNT);
        return Math.clamp(configured, 1, SelectionLayout.MAX_CHOICES);
    }

    private Component title() {
        return MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("selection.title", DEFAULT_TITLE));
    }
}
