package io.github.modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * ドパガキ。
 *
 * <p>攻撃力が常に30%下がるが、敵対モブを殺すと5秒間だけ75%上がる。
 * どちらも加算で効くので、上がっている間は差し引き +45%。殺し続ければ維持できる。
 */
public final class DopagakiModifier extends BaseModifier {

    public static final double ATTACK_PENALTY = -0.30;
    public static final double RAGE_BONUS = 0.75;
    public static final long RAGE_DURATION_TICKS = 5 * 20;

    private final Plugin plugin;
    private final Map<UUID, ScheduledTask> rageTasks = new HashMap<>();

    public DopagakiModifier(Plugin plugin) {
        super("dopagaki", "ドパガキ", Material.GOLDEN_SWORD,
                "攻撃力 -30%",
                "敵対モブを殺すと 5秒間",
                "攻撃力 +75% (差し引き +45%)");
        this.plugin = plugin;
    }

    @Override
    public int weight() {
        // 常時の -30% をキル後の +75% で取り返す。腕が出る。
        return 8;
    }

    @Override
    public void apply(Player player) {
        Attributes.setAddScalar(player, Attribute.ATTACK_DAMAGE, key("penalty"), ATTACK_PENALTY);
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.ATTACK_DAMAGE, key("penalty"));
        Attributes.clear(player, Attribute.ATTACK_DAMAGE, key("rage"));
        cancelRageTask(player.getUniqueId());
    }

    @Override
    public void onKill(Player player, LivingEntity victim) {
        if (!(victim instanceof Enemy)) {
            return;
        }
        Attributes.setAddScalar(player, Attribute.ATTACK_DAMAGE, key("rage"), RAGE_BONUS);
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);

        // 殺すたびにタイマーを引き直す
        cancelRageTask(player.getUniqueId());
        ScheduledTask task = player.getScheduler().runDelayed(plugin, scheduled -> {
            Attributes.clear(player, Attribute.ATTACK_DAMAGE, key("rage"));
            rageTasks.remove(player.getUniqueId());
        }, null, RAGE_DURATION_TICKS);
        if (task != null) {
            rageTasks.put(player.getUniqueId(), task);
        }
    }

    private void cancelRageTask(UUID playerId) {
        ScheduledTask task = rageTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
