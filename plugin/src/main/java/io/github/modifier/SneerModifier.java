package io.github.modifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 冷笑。
 *
 * <p>死ぬはずだったとき、一度だけなかったことになり、リスポーン地点へ戻る。
 */
public final class SneerModifier extends BaseModifier {

    private final SelectionStore store;

    public SneerModifier(SelectionStore store) {
        super("sneer", "冷笑", Material.TOTEM_OF_UNDYING,
                "一度だけ死がなかったことになり",
                "リスポーン地点へ戻る");
        this.store = store;
    }

    @Override
    public int weight() {
        // 死を打ち消すが、対象は自分の死で、本人は効果を知っている。無キャより軽い。
        return 3;
    }

    @Override
    public boolean interceptDeath(Player self, EntityDamageEvent event) {
        if (!store.chargeAvailable(self)) {
            return false;
        }
        store.consumeCharge(self);
        Revival.revive(self);

        Location respawn = self.getRespawnLocation();
        if (respawn == null) {
            respawn = self.getServer().getWorlds().get(0).getSpawnLocation();
        }
        self.teleportAsync(respawn);
        self.sendMessage(Component.text("……死ぬわけがない。", NamedTextColor.DARK_GRAY));
        return true;
    }
}
