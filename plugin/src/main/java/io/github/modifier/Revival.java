package io.github.modifier;

import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** 「死がなかったことになる」系モディファイアの蘇生処理。 */
final class Revival {

    /** 蘇生時に戻す HP の、最大 HP に対する割合。 */
    static final double HEALTH_RATIO = 0.5;

    private Revival() {
    }

    /**
     * 死を打ち消したプレイヤーを立て直す。
     *
     * <p>HP を最大値の半分へ戻し、燃えていれば消す。満腹度はそのまま。
     * 呼び出し側で致死ダメージのイベントをキャンセルしておくこと。
     */
    static void revive(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0;
        player.setHealth(Math.max(1.0, maxHealth * HEALTH_RATIO));
        player.setFireTicks(0);
        player.playSound(player, Sound.ITEM_TOTEM_USE, 1.0f, 1.3f);
    }
}
