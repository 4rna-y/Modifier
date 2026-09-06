package io.github.modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 下半身強者。
 *
 * <p>落下ダメージが 90% 減る代わりに、それ以外のダメージが 50% 増え、常に空腹になる。
 *
 * <p>空腹はリーダーのオーラと同じ要領で、{@link #tick} が切れる前に掛け直す。
 * ambient でパーティクル無しなので画面はうるさくならない。効果を外せば空腹も消す。
 */
public final class StrongLegsModifier extends BaseModifier {

    public static final double FALL_DAMAGE_MULTIPLIER = -0.90;
    /** 落下以外のダメージに掛ける倍率。 */
    public static final double OTHER_DAMAGE_MULTIPLIER = 1.5;
    /** 空腹を掛け直す間隔。 */
    public static final long APPLY_INTERVAL_TICKS = 40;
    /** 空腹の長さ。間隔より長くして途切れないようにする。 */
    public static final int HUNGER_DURATION_TICKS = 80;

    private final Map<UUID, Long> lastApplied = new HashMap<>();

    public StrongLegsModifier() {
        super("strong_legs", "下半身強者", Material.IRON_LEGGINGS,
                "落下ダメージ -90%",
                "それ以外のダメージ +50%",
                "常に空腹");
    }

    @Override
    public int weight() {
        // 落下 -90% は強いが、被ダメ +50% と空腹が常に付きまとう。誰にも迷惑は掛けず、取引が明快。一番よく出す。
        return 6;
    }

    @Override
    public void apply(Player player) {
        Attributes.setScalar(player, Attribute.FALL_DAMAGE_MULTIPLIER, key("fall"), FALL_DAMAGE_MULTIPLIER);
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.FALL_DAMAGE_MULTIPLIER, key("fall"));
        lastApplied.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.HUNGER);
    }

    @Override
    public void onDamaged(Player player, EntityDamageEvent event) {
        // 落下は attribute 側で減らしてあるので、ここでは触らない
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        event.setDamage(event.getDamage() * OTHER_DAMAGE_MULTIPLIER);
    }

    @Override
    public void tick(Player player) {
        long now = Bukkit.getCurrentTick();
        Long last = lastApplied.get(player.getUniqueId());
        if (last != null && now - last < APPLY_INTERVAL_TICKS) {
            return;
        }
        lastApplied.put(player.getUniqueId(), now);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, HUNGER_DURATION_TICKS, 0, true, false, true));
    }
}
