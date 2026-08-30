package io.github.modifier;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/** 常時効果で使う attribute の付け外し。 */
public final class Attributes {

    private Attributes() {
    }

    /**
     * 割合で増減する修正を、何度呼んでも二重にかからない形で付ける。
     *
     * <p>{@code addTransientModifier} を使うので、プレイヤーのデータには保存されない。
     * プラグインを外した後に効果が残る、という事故が起きない。
     *
     * @param amount {@code 0.1} なら +10%、{@code -0.1} なら -10%
     */
    public static void setScalar(Player player, Attribute attribute, NamespacedKey key,
            double amount) {
        set(player, attribute, key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    /**
     * 加算合成 ({@code ADD_SCALAR}) 版。
     *
     * <p>同じ attribute に複数の修正を重ねるとき、乗算ではなく足し算で効かせたい場合に使う。
     * 例: -30% と +75% を重ねて差し引き +45% にする。
     */
    public static void setAddScalar(Player player, Attribute attribute, NamespacedKey key,
            double amount) {
        set(player, attribute, key, amount, AttributeModifier.Operation.ADD_SCALAR);
    }

    private static void set(Player player, Attribute attribute, NamespacedKey key,
            double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(key);
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    /** 付けた修正を外す。付いていなければ何もしない。 */
    public static void clear(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(key);
        }
    }
}
