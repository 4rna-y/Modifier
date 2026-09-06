package io.github.modifier;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * よくばり。
 *
 * <p>選ぶと、他のモディファイアからランダムに 2 つ引いて、その両方の効果を受ける。
 * 引いたものは選択と一緒に保存し ({@link SelectionStore#setBundle})、効果を配るときは
 * {@link #resolveFor} が 2 つを束ねた {@link ModifierBundle} を返す。
 *
 * <p>抽選は他と同じ重み付き。よくばり自身は引かない。死を打ち消すものが混ざることもある。
 */
public final class GreedyModifier extends BaseModifier {

    public static final int PICKS = 2;

    private final ModifierRegistry registry;
    private final SelectionStore store;
    private final Random random;

    public GreedyModifier(ModifierRegistry registry, SelectionStore store, Random random) {
        super("greedy", "よくばり", Material.CHEST,
                "ランダムな 2 つのモディファイアの",
                "効果をまとめて受ける");
        this.registry = registry;
        this.store = store;
        this.random = random;
    }

    @Override
    public int weight() {
        // 2 つぶん強いが、下方修正だけの 2 つを引くこともある。死を打ち消すものを引く可能性があるので、極レアに寄せる。
        return 3;
    }

    @Override
    public void onChosen(Player player) {
        List<Modifier> picks = registry.pick(PICKS, random, other -> !other.id().equals(id()));
        store.setBundle(player, picks.stream().map(Modifier::id).toList());

        Component names = Component.empty();
        for (int i = 0; i < picks.size(); i++) {
            if (i > 0) {
                names = names.append(Component.text(" と ", NamedTextColor.GRAY));
            }
            names = names.append(picks.get(i).displayName());
        }
        player.sendMessage(Component.text("よくばりの中身: ", NamedTextColor.GRAY).append(names));
    }

    @Override
    public Modifier resolveFor(Player player) {
        List<Modifier> parts = store.bundle(player).stream()
                .map(registry::byId)
                .flatMap(Optional::stream)
                .toList();
        // 中身が無い (保存が消えた等) なら、なにもしない自分自身のまま
        return parts.isEmpty() ? this : new ModifierBundle(this, parts);
    }
}
