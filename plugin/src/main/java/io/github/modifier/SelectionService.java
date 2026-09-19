package io.github.modifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    /**
     * 次の選択確定で開始アイテムを配らない人。
     *
     * <p>リセットチケットでの選び直し用。配ってしまうと、チケットを使うたびに
     * モディファイアごとの装備 (地雷系の弓など) を増やせてしまう。
     * {@code /m select} (管理者) は今まで通り配る。
     */
    private final Set<UUID> skipStartingItems = new HashSet<>();
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
        reselect(player, true);
    }

    /**
     * 選択をやり直す。
     *
     * @param giveStartingItems 選び直した先の開始アイテムを配るか。
     *     リセットチケット経由は false (使うたびに装備を増やせてしまうため)
     */
    public void reselect(Player player, boolean giveStartingItems) {
        store.clear(player);
        // 選択が無くなったので、掛かっていた常時効果がここで外れる
        effects.apply(player);
        forget(player.getUniqueId());
        markStartingItems(player.getUniqueId(), giveStartingItems);
        open(player);
    }

    /** 次の選択確定で開始アイテムを配るかどうかを覚える。 */
    void markStartingItems(UUID playerId, boolean give) {
        if (give) {
            skipStartingItems.remove(playerId);
        } else {
            skipStartingItems.add(playerId);
        }
    }

    /**
     * 今回の選択確定で開始アイテムを飛ばすか。1 回読んだら忘れる。
     *
     * <p>覚えたままにすると、次に {@code /m select} で選び直したときまで配られなくなる。
     */
    public boolean consumeSkipStartingItems(UUID playerId) {
        return skipStartingItems.remove(playerId);
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
        // 選び直し (/m select) で開く場合もここを通るので、守るのはここで
        SelectionGuard.protect(player);
        player.openInventory(new SelectionMenu(choices, title()).getInventory());
    }

    /** 提示済みの選択肢を忘れる。次に開くときは引き直しになる。 */
    public void forget(UUID playerId) {
        offers.remove(playerId);
    }

    /** 退出したので、選び直しの途中だった記録ごと捨てる。 */
    public void forgetAll(UUID playerId) {
        offers.remove(playerId);
        skipStartingItems.remove(playerId);
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
