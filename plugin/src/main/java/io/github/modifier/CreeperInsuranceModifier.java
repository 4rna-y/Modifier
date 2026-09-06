package io.github.modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * クリーパー保険。
 *
 * <p>クリーパーの爆発で死ぬはずだったとき、なかったことにしてリスポーン地点へ戻る。
 * 3分に一度しか下りない。冷笑と違って回数の上限は無い。
 *
 * <p>クールタイムはサーバーの tick で数え、メモリに持つ。再起動すれば新品に戻る。
 */
public final class CreeperInsuranceModifier extends BaseModifier {

    public static final long COOLDOWN_TICKS = 3 * 60 * 20;

    private final Map<UUID, Long> lastClaimed = new HashMap<>();

    public CreeperInsuranceModifier() {
        super("creeper_insurance", "クリーパー保険", Material.CREEPER_HEAD,
                "クリーパーの爆発で死ぬと、なかったことにして",
                "リスポーン地点へ戻る (3分に一度)");
    }

    @Override
    public int weight() {
        // 死因は限られるが、wiah と組めばワールドの死を打ち消す。冷笑と同じ枠。
        return 2;
    }

    /** クリーパーの爆発によるダメージか。帯電クリーパーも含む。 */
    static boolean isCreeperBlast(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Creeper;
    }

    @Override
    public boolean interceptDeath(Player self, EntityDamageEvent event) {
        if (!isCreeperBlast(event)) {
            return false;
        }
        long now = Bukkit.getCurrentTick();
        Long last = lastClaimed.get(self.getUniqueId());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return false;
        }
        lastClaimed.put(self.getUniqueId(), now);
        Revival.revive(self);
        Revival.sendHome(self);
        self.sendMessage(Component.text("保険が下りた。", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public void remove(Player player) {
        lastClaimed.remove(player.getUniqueId());
    }
}
