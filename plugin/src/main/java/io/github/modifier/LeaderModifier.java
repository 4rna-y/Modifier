package io.github.modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * リーダー。
 *
 * <p>半径5m以内の全プレイヤー (自分を含む) に、採掘速度上昇・攻撃力増加・耐性のバフと
 * 空腹のデバフを与え続ける。
 */
public final class LeaderModifier extends BaseModifier {

    public static final double RADIUS = 5.0;
    /** オーラを掛け直す間隔。 */
    public static final long APPLY_INTERVAL_TICKS = 40;
    /** 効果の長さ。間隔より長くして途切れないようにする。 */
    public static final int EFFECT_DURATION_TICKS = 80;

    private final Map<UUID, Long> lastApplied = new HashMap<>();

    public LeaderModifier() {
        super("leader", "リーダー", Material.GOLDEN_HELMET,
                "半径5m の全プレイヤー (自分含む) に",
                "採掘速度上昇・攻撃力増加・耐性を配るが",
                "空腹も配ってしまう");
    }

    @Override
    public int weight() {
        // 自分にも掛かるが、価値は周りに人が居てこそ。ソロだと空腹付きの微妙な自己バフ。
        return 4;
    }

    @Override
    public void tick(Player player) {
        // tick() は短い周期で来るので、ここで間引く。
        // ワールドの時刻はワールドごとに違うので、サーバー全体の tick 数を使う
        long now = org.bukkit.Bukkit.getCurrentTick();
        Long last = lastApplied.get(player.getUniqueId());
        if (last != null && now - last < APPLY_INTERVAL_TICKS) {
            return;
        }
        lastApplied.put(player.getUniqueId(), now);

        // getNearbyPlayers は箱型検索なので、球 (半径5m) に絞り直す。自分は常に含まれる
        Location center = player.getLocation();
        for (Player target : center.getNearbyPlayers(RADIUS)) {
            if (target.getLocation().distanceSquared(center) > RADIUS * RADIUS) {
                continue;
            }
            aura(target, PotionEffectType.HASTE);
            aura(target, PotionEffectType.STRENGTH);
            aura(target, PotionEffectType.RESISTANCE);
            aura(target, PotionEffectType.HUNGER);
        }
    }

    private static void aura(Player target, PotionEffectType type) {
        // ambient + パーティクル無し。オーラなので画面がうるさくならないようにする
        target.addPotionEffect(new PotionEffect(
                type, EFFECT_DURATION_TICKS, 0, true, false, true));
    }

    @Override
    public void remove(Player player) {
        lastApplied.remove(player.getUniqueId());
    }
}
