package io.github.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 死神ルーレット。
 *
 * <p>死ぬはずだったとき、一度だけなかったことになる。その代わり、参加中のランダムな
 * 他プレイヤーが死亡地点へ引きずり込まれ、HP と満腹度を半分にされる。
 *
 * <p>他のプレイヤーが誰も居なければ発動せず、普通に死ぬ (チャージは温存される)。
 */
public final class ReaperRouletteModifier extends BaseModifier {

    /**
     * 発動時に鳴らす音。リソースパックの {@code assets/modifier/sounds.json} に同名で登録してある。
     * パックを適用していないクライアントでは何も鳴らない。
     */
    public static final Key SOUND = Key.key("modifier", "reaper_roulette");

    /** 巻き込まれた側の HP の下限。死なせはしない。 */
    public static final double VICTIM_MIN_HEALTH = 1.0;

    private final SelectionStore store;
    private final Random random;

    public ReaperRouletteModifier(SelectionStore store, Random random) {
        super("reaper_roulette", "死神ルーレット", Material.SKELETON_SKULL,
                "一度だけ死がなかったことになるが",
                "ランダムな他プレイヤーが死亡地点へ引きずり込まれ",
                "HP と満腹度を半分にされる",
                "他に誰も居なければ発動しない");
        this.store = store;
        this.random = random;
    }

    @Override
    public int weight() {
        // 自分は助かるが他人を巻き込む。ワールドの生死と他人への押し付けの両方に触る。
        return 3;
    }

    @Override
    public boolean interceptDeath(Player self, EntityDamageEvent event) {
        List<Player> others = new ArrayList<>(self.getServer().getOnlinePlayers());
        others.remove(self);
        // 死亡画面の途中の相手は引きずり込めない (テレポートが空振りする)。
        // 観戦者を巻き込んでも意味が無いので外す
        others.removeIf(p -> p.isDead() || p.getGameMode() == GameMode.SPECTATOR);
        if (others.isEmpty()) {
            // 巻き込む相手が居ないなら発動しない。チャージは減らさない
            return false;
        }
        if (!store.chargeAvailable(self)) {
            return false;
        }
        store.consumeCharge(self);

        Location deathPoint = self.getLocation();
        Revival.revive(self);

        Player victim = others.get(random.nextInt(others.size()));
        victim.setHealth(halvedHealth(victim.getHealth()));
        victim.setFoodLevel(victim.getFoodLevel() / 2);
        victim.teleportAsync(deathPoint);
        victim.sendMessage(Component.text(
                "死神のルーレットに巻き込まれた……", NamedTextColor.DARK_RED));
        self.sendMessage(Component.text(
                victim.getName() + " が身代わりに引きずり込まれた。", NamedTextColor.DARK_RED));
        // 助かった側と巻き込まれた側の両方に、それぞれの耳元で鳴らす
        Sound sound = Sound.sound(SOUND, Sound.Source.PLAYER, 1.0f, 1.0f);
        self.playSound(sound);
        victim.playSound(sound);
        return true;
    }

    /** 半減後の HP。0 にはならない。 */
    static double halvedHealth(double health) {
        return Math.max(VICTIM_MIN_HEALTH, health / 2.0);
    }
}
