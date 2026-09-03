package io.github.modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 歌姫。
 *
 * <p>音符ブロックを鳴らすと、半径10m の全プレイヤー (自分を含む) に2秒間の再生を配る。
 * 10秒に一度しか歌えない。
 *
 * <p>「鳴らす」はプレイヤー自身の操作 (右クリックか殴る) だけ。レッドストーンで鳴ったものは
 * 誰の歌か分からないので数えない。バニラが鳴らさない状況 (上が塞がっている、
 * スニークしながら手に何か持って右クリック) でも歌わない。
 */
public final class DivaModifier extends BaseModifier {

    public static final double RADIUS = 10.0;
    public static final int DURATION_TICKS = 2 * 20;
    /**
     * 再生 II。I は 50 tick に 1 回しか回復せず、2秒だと一度も回復しないまま終わりうる。
     * II なら 25 tick ごとなので、2秒で 1〜2 回 (1〜2 HP) は確実に入る。
     */
    public static final int AMPLIFIER = 1;
    public static final long COOLDOWN_TICKS = 10 * 20;

    private final Map<UUID, Long> lastSung = new HashMap<>();

    public DivaModifier() {
        super("diva", "歌姫", Material.NOTE_BLOCK,
                "音符ブロックを鳴らすと、半径10m の",
                "全プレイヤー (自分含む) に 2秒間 再生を配る",
                "(10秒に一度)");
    }

    @Override
    public int weight() {
        // 下方修正は無いが、音符ブロックを用意し、周りに人が居てこそ。ソロだと微妙な自己回復。
        return 4;
    }

    @Override
    public void onInteract(Player player, PlayerInteractEvent event) {
        if (!isPlayingNoteBlock(player, event)) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        Long last = lastSung.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        lastSung.put(player.getUniqueId(), now);

        // getNearbyPlayers は箱型検索なので、球に絞り直す。自分は常に含まれる
        Location center = player.getLocation();
        for (Player target : center.getNearbyPlayers(RADIUS)) {
            if (target.getLocation().distanceSquared(center) > RADIUS * RADIUS) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, DURATION_TICKS, AMPLIFIER));
        }
    }

    /** バニラがその操作で音符ブロックを鳴らすか。 */
    private static boolean isPlayingNoteBlock(Player player, PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return false;
        }
        // 右クリックは両手ぶん飛んでくるので、利き手の分だけ数える
        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return false;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return false;
        }
        // 上が塞がっていると鳴らない
        if (!block.getRelative(BlockFace.UP).getType().isAir()) {
            return false;
        }
        // スニークしながら何か持って右クリックすると、ブロックではなく手の物が使われる
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()
                && (!player.getInventory().getItemInMainHand().getType().isAir()
                        || !player.getInventory().getItemInOffHand().getType().isAir())) {
            return false;
        }
        return true;
    }

    @Override
    public void remove(Player player) {
        lastSung.remove(player.getUniqueId());
    }
}
