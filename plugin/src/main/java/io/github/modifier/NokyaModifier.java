package io.github.modifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 無キャ。
 *
 * <p>表向きは「なにも起こらない気がする。」だけ。
 *
 * <p>実際は、参加中の<b>他の</b>プレイヤーが死ぬはずだったとき、一度だけなかったことにして
 * そのプレイヤーを自分の位置へテレポートさせる。本人には表示しない隠し効果なので、
 * 説明文には書かないこと。本人の死亡では発動しない。
 */
public final class NokyaModifier extends BaseModifier {

    private final SelectionStore store;

    public NokyaModifier(SelectionStore store) {
        super("nokya", "無キャ", Material.BARRIER,
                "なにも起こらない気がする。");
        this.store = store;
    }

    @Override
    public int weight() {
        // 死を打ち消すものの中で最も重い。隠し効果なので誰も知らないまま
        // 「ワールドが1回生き延びる」が起きる。頻繁に起きてはいけない。
        return 2;
    }

    @Override
    public boolean interceptOtherDeath(Player self, Player dying, EntityDamageEvent event) {
        if (!store.chargeAvailable(self)) {
            return false;
        }
        store.consumeCharge(self);
        Revival.revive(dying);
        dying.teleportAsync(self.getLocation());
        dying.sendMessage(Component.text("なぜか助かった気がする……", NamedTextColor.GRAY));
        return true;
    }
}
