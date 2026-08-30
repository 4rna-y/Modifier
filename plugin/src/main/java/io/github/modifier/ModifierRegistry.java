package io.github.modifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.bukkit.plugin.Plugin;

/** 利用できるモディファイアの一覧。 */
public final class ModifierRegistry {

    private final Map<String, Modifier> byId = new LinkedHashMap<>();

    /**
     * 組み込みのモディファイアを登録した状態で作る。
     *
     * @param plugin スケジューラが要る効果 (期限付きバフ等) が使う
     * @param store  一度きりの効果 (蘇生系) がチャージの保存に使う
     * @param random 確率もの・抽選ものが共有する乱数源
     */
    public static ModifierRegistry withBuiltins(Plugin plugin, SelectionStore store, Random random) {
        ModifierRegistry registry = new ModifierRegistry();
        registry.register(new FatModifier());
        registry.register(new SwiftnessBootsModifier());
        registry.register(new ShieldBashModifier());
        registry.register(new AhoModifier());
        registry.register(new SerfModifier(random));
        registry.register(new DopagakiModifier(plugin));
        registry.register(new ReaperRouletteModifier(store, random));
        registry.register(new SneerModifier(store));
        registry.register(new BlackSwordsmanModifier());
        registry.register(new LandmineModifier());
        registry.register(new HealerModifier());
        registry.register(new FoodPoisoningModifier(random));
        registry.register(new ClownModifier(random));
        registry.register(new InsomniaModifier());
        registry.register(new LeaderModifier());
        registry.register(new NokyaModifier(store));
        return registry;
    }

    public void register(Modifier modifier) {
        if (modifier.weight() < 1) {
            // 0 を許すと「登録されているのに一生出ない」状態が黙って作れてしまう。
            // 出したくないものは登録しないこと。
            throw new IllegalArgumentException(
                    "重みは 1 以上: " + modifier.id() + " が " + modifier.weight());
        }
        Modifier previous = byId.putIfAbsent(modifier.id(), modifier);
        if (previous != null) {
            throw new IllegalArgumentException("モディファイア id が重複している: " + modifier.id());
        }
    }

    /** 登録済みモディファイアの重みの合計。 */
    public int totalWeight() {
        return byId.values().stream().mapToInt(Modifier::weight).sum();
    }

    public Optional<Modifier> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** 登録順の一覧。 */
    public List<Modifier> all() {
        return List.copyOf(byId.values());
    }

    /**
     * 重みに従って、重複なしで {@code count} 個を選ぶ。
     *
     * <p>1つ引くたびに、引いたぶんを母集団から取り除いてから次を引く (非復元抽出)。
     * そのため「3択に出る確率」は重みの比そのものではなく、それより高くなる。
     *
     * <p>登録数が {@code count} に満たない場合は、あるだけ返す。
     */
    public List<Modifier> pick(int count, Random random) {
        List<Modifier> pool = new ArrayList<>(byId.values());
        List<Modifier> picked = new ArrayList<>();
        int wanted = Math.min(count, pool.size());
        // register が 1 以上を保証しているので、pool が空でない限り総和は正。
        int total = pool.stream().mapToInt(Modifier::weight).sum();

        while (picked.size() < wanted) {
            int roll = random.nextInt(total);
            for (Iterator<Modifier> candidates = pool.iterator(); candidates.hasNext(); ) {
                Modifier candidate = candidates.next();
                roll -= candidate.weight();
                if (roll < 0) {
                    picked.add(candidate);
                    total -= candidate.weight();
                    candidates.remove();
                    break;
                }
            }
        }
        return List.copyOf(picked);
    }
}
