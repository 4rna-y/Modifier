package io.github.modifier;

import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

/**
 * スウィフトネスブーツ。
 *
 * <p>歩行速度が10%上がり二段ジャンプができる代わりに、満腹度の減りが20%速くなる。
 *
 * <p>二段ジャンプはバニラに無いので、飛行フラグを借りて実装している。空中でジャンプを
 * 押すと飛行の切り替えが飛んでくるので、それを打ち消して上向きの速度を与える。
 * フラグは着地するまで戻さないので、跳べるのは1回だけになる。
 */
public final class SwiftnessBootsModifier extends BaseModifier {

    public static final double SPEED_MULTIPLIER = 0.10;
    public static final float EXHAUSTION_MULTIPLIER = 1.20f;

    /** 二段ジャンプの上向きの勢い。 */
    public static final double JUMP_UP = 0.62;

    public SwiftnessBootsModifier() {
        super("swiftness_boots", "スウィフトネスブーツ", Material.LEATHER_BOOTS,
                "歩行速度 +10%",
                "二段ジャンプができる",
                "満腹度の減り +20%");
    }

    @Override
    public int weight() {
        // 満腹度 +20% が実質的な対価になっている。
        return 8;
    }

    @Override
    public void apply(Player player) {
        Attributes.setScalar(player, Attribute.MOVEMENT_SPEED, key("speed"), SPEED_MULTIPLIER);
        if (isSurvivalLike(player)) {
            player.setAllowFlight(true);
            // 飛行中のまま入り直すと isFlying() が真のままになり、
            // onToggleFlight が毎回素通りして二段ジャンプが一生効かなくなる。
            // 飛行を許してから落とす (許す前に落とすと弾かれる)。
            player.setFlying(false);
            allowFallDamage(player);
        }
    }

    /**
     * 飛行を許したせいで消える落下ダメージを、明示的に戻す。
     *
     * <p>バニラは「飛行を許されたプレイヤー」の落下ダメージを丸ごと無効にする
     * ({@code Player#causeFallDamage} が {@code mayfly} で即 false を返す)。二段ジャンプは
     * その飛行フラグを借りて実装しているので、放っておくと<b>二段ジャンプを使わない限り
     * 落下ダメージが一切入らない</b>。崖から歩いて降りるだけでも無傷になってしまう。
     */
    private static void allowFallDamage(Player player) {
        if (player.hasFlyingFallDamage() != TriState.TRUE) {
            player.setFlyingFallDamage(TriState.TRUE);
        }
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.MOVEMENT_SPEED, key("speed"));
        // 落下ダメージの扱いはバニラへ戻す。飛行フラグと違って
        // クリエイティブでも邪魔になるので、ゲームモードによらず必ず戻す。
        player.setFlyingFallDamage(TriState.NOT_SET);
        // クリエイティブの飛行を奪わないよう、サバイバル系のときだけ戻す。
        if (isSurvivalLike(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @Override
    public void onExhaustion(Player player, EntityExhaustionEvent event) {
        event.setExhaustion(event.getExhaustion() * EXHAUSTION_MULTIPLIER);
    }

    @Override
    public void onToggleFlight(Player player, PlayerToggleFlightEvent event) {
        if (!isSurvivalLike(player) || player.isFlying()) {
            return;
        }
        event.setCancelled(true);
        // 着地するまで戻さない = 空中で跳べるのは1回だけ
        player.setAllowFlight(false);
        player.setFlying(false);

        // 加速するのは真上だけ。横向きの勢いは今のものをそのまま残すので、
        // 走っている途中でも視線の向きに引っ張られない。
        Vector jump = player.getVelocity().setY(JUMP_UP);
        player.setVelocity(jump);
        player.playSound(player, Sound.ENTITY_BREEZE_JUMP, 0.6f, 1.4f);
    }

    @Override
    public void tick(Player player) {
        if (!isSurvivalLike(player)) {
            // クリエイティブへ移ったら、飛行中に落ちて痛い思いをしないよう戻す
            if (player.hasFlyingFallDamage() != TriState.NOT_SET) {
                player.setFlyingFallDamage(TriState.NOT_SET);
            }
            return;
        }
        allowFallDamage(player);
        // 着地したら次の二段ジャンプを許す
        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }
}
