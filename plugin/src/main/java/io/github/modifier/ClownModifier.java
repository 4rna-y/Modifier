package io.github.modifier;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * ピエロ。
 *
 * <p>5つの効果がそれぞれ独立に 20% で起きる。
 * <ol>
 *   <li>受ける攻撃がなかったことになる</li>
 *   <li>攻撃した相手が自分の周囲5mへランダムにテレポートする</li>
 *   <li>食べても満腹度が回復しない</li>
 *   <li>採掘したアイテムが消える</li>
 *   <li>攻撃した相手の周囲5mのプレイヤーの HP が 1 回復する</li>
 * </ol>
 */
public final class ClownModifier extends BaseModifier {

    public static final double CHANCE = 0.2;
    public static final double TELEPORT_RADIUS = 5.0;
    public static final double HEAL_RADIUS = 5.0;
    public static final double HEAL_AMOUNT = 1.0;

    private final Random random;

    public ClownModifier(Random random) {
        super("clown", "ピエロ", Material.SLIME_BALL,
                "20% で受ける攻撃が消える",
                "20% で攻撃した相手が自分の側へ飛ぶ",
                "20% で食べても満腹度が戻らない",
                "20% で採掘したアイテムが消える",
                "20% で攻撃した相手の周りが 1 回復する");
        this.random = random;
    }

    private boolean roll() {
        return random.nextDouble() < CHANCE;
    }

    @Override
    public int weight() {
        // 20% が5種。運が全部悪い方へ転ぶ日があるので気持ち下げる。
        return 7;
    }

    @Override
    public void onDamaged(Player player, EntityDamageEvent event) {
        // 「攻撃」だけを消す。落下や炎上などの環境ダメージは対象外
        if (!(event instanceof EntityDamageByEntityEvent)) {
            return;
        }
        if (roll()) {
            event.setCancelled(true);
            player.playSound(player, Sound.ENTITY_ALLAY_ITEM_THROWN, 0.7f, 1.5f);
        }
    }

    @Override
    public void onDealtDamage(Player player, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.equals(player)) {
            return;
        }
        if (roll()) {
            Location destination = randomSpotNear(player);
            // 視点は相手のものを保つ
            destination.setYaw(victim.getLocation().getYaw());
            destination.setPitch(victim.getLocation().getPitch());
            victim.teleportAsync(destination);
            victim.getWorld().playSound(victim.getLocation(),
                    Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        }
        if (roll()) {
            // getNearbyPlayers は箱型検索なので、球 (半径5m) に絞り直す
            Location around = victim.getLocation();
            for (Player nearby : around.getNearbyPlayers(HEAL_RADIUS)) {
                if (nearby.getLocation().distanceSquared(around) <= HEAL_RADIUS * HEAL_RADIUS) {
                    nearby.heal(HEAL_AMOUNT, EntityRegainHealthEvent.RegainReason.CUSTOM);
                }
            }
        }
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent event) {
        // 食事による回復だけを消す。自然減少には触らない
        if (event.getItem() == null || event.getFoodLevel() <= player.getFoodLevel()) {
            return;
        }
        if (roll()) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockDrops(Player player, BlockDropItemEvent event) {
        if (roll()) {
            event.getItems().clear();
        }
    }

    /**
     * 自分の周囲の、立てる場所をランダムに選ぶ。
     *
     * <p>壁の中へ飛ばさないよう数回探し、見つからなければ自分の足元に落とす。
     */
    private Location randomSpotNear(Player center) {
        Location base = center.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 1.5 + random.nextDouble() * (TELEPORT_RADIUS - 1.5);
            Location candidate = base.clone().add(
                    Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                Location spot = candidate.clone().add(0, dy, 0);
                if (isStandable(spot)) {
                    return spot.getBlock().getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return base;
    }

    private static boolean isStandable(Location feet) {
        return feet.getBlock().isPassable()
                && feet.clone().add(0, 1, 0).getBlock().isPassable()
                && !feet.clone().add(0, -1, 0).getBlock().isPassable();
    }
}
