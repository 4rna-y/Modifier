package io.github.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;

/**
 * モディファイアの効果をプレイヤーへ届ける。
 *
 * <p>イベントの購読はここ1箇所で行い、選択中のモディファイアへ振り分ける。
 * モディファイアごとに {@code Listener} を登録しないので、選び直しは保存先を
 * 書き換えて {@link #apply} を呼ぶだけで済む。
 *
 * <p>優先度の設計:
 * <ul>
 *   <li>NORMAL — 被弾側の加工 (デブの軽減、ピエロの無効化など)</li>
 *   <li>HIGH — 攻撃側の反応 (ヒーラーの肩代わり、黒の剣士の吸収など)。
 *       被弾側の加工が済んだ最終ダメージを見る</li>
 *   <li>HIGHEST — 致死判定。全ての加工が終わった後に「死ぬかどうか」を見る</li>
 * </ul>
 */
public final class ModifierEffects implements Listener {

    private final ModifierPlugin plugin;
    private final ModifierRegistry registry;
    private final SelectionStore store;
    /** 救済できる者が複数居るときの順番を決める。テストから固定できるよう受け取る。 */
    private final Random random;
    /** ネームタグの下段の表示。選択に合わせて {@link #apply} が更新する。 */
    private final NameTagDisplay nameTag;

    /** 今そのプレイヤーへ実際に掛けてあるもの。付け替えのときに外す相手を知るために持つ。 */
    private final Map<UUID, Modifier> applied = new HashMap<>();
    private final Map<UUID, ScheduledTask> tickTasks = new HashMap<>();

    public ModifierEffects(ModifierPlugin plugin, ModifierRegistry registry, SelectionStore store,
            Random random, NameTagDisplay nameTag) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.random = random;
        this.nameTag = nameTag;
    }

    /** そのプレイヤーが今選んでいるモディファイア。 */
    public Optional<Modifier> active(Player player) {
        return store.selectedId(player).flatMap(registry::byId);
    }

    /**
     * 選択に合わせて常時効果を掛け直す。
     *
     * <p>参加時・選択時・リスポーン時に呼ぶ。別のものが掛かっていれば先に外す。
     */
    public void apply(Player player) {
        Optional<Modifier> next = active(player);
        Modifier previous = applied.remove(player.getUniqueId());
        if (previous != null && (next.isEmpty() || !previous.id().equals(next.get().id()))) {
            previous.remove(player);
        }
        if (next.isEmpty()) {
            stopTicking(player);
            nameTag.hide(player);
            return;
        }
        next.get().apply(player);
        applied.put(player.getUniqueId(), next.get());
        startTicking(player);
        nameTag.show(player, next.get());
    }

    /** 掛けてあるものを外す。 */
    public void clear(Player player) {
        stopTicking(player);
        Modifier previous = applied.remove(player.getUniqueId());
        if (previous != null) {
            previous.remove(player);
        }
    }

    /** 停止時に全員から外す。attribute は transient なので実際には残らないが、飛行フラグ等を戻す。 */
    public void clearAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            clear(player);
        }
    }

    private void startTicking(Player player) {
        if (tickTasks.containsKey(player.getUniqueId())) {
            return;
        }
        long period = Math.max(1L,
                plugin.getConfig().getLong("effects.tick-period-ticks", 2L));
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin,
                scheduled -> active(player).ifPresent(modifier -> modifier.tick(player)),
                null, period, period);
        if (task != null) {
            tickTasks.put(player.getUniqueId(), task);
        }
    }

    private void stopTicking(Player player) {
        ScheduledTask task = tickTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    // ---- 状況発動の振り分け ------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (SyntheticDamage.active()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onDamaged(player, event));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealtDamage(EntityDamageByEntityEvent event) {
        if (SyntheticDamage.active()) {
            return;
        }
        Player attacker = attackerOf(event);
        if (attacker == null || attacker.equals(event.getEntity())) {
            return;
        }
        active(attacker).ifPresent(modifier -> modifier.onDealtDamage(attacker, event));
    }

    /**
     * 致死判定。
     *
     * <p>全ての加工が終わった後の最終ダメージで「死ぬかどうか」を判断し、
     * まず本人の、次に他プレイヤーのモディファイアへ救済の機会を配る。
     * 合成ダメージ (効果由来の自傷など) でも働く。ここで救われなかったら普通に死ぬ。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player dying)) {
            return;
        }
        if (event.getFinalDamage() < dying.getHealth()) {
            return;
        }
        // 不死のトーテムを構えているならバニラに任せる。ここで割り込むと
        // トーテムで助かるはずの死に一度きりのチャージを浪費してしまう
        if (holdsTotem(dying) && totemWorksAgainst(event.getCause())) {
            return;
        }
        Optional<Modifier> own = active(dying);
        if (own.isPresent() && own.get().interceptDeath(dying, event)) {
            event.setCancelled(true);
            return;
        }
        // 本人が助からなかったら、周りの (無キャ等の) 番。複数居たら運。
        List<Player> holders = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        holders.remove(dying);
        holders.removeIf(Player::isDead);   // 死亡画面の途中のプレイヤーは数えない
        Collections.shuffle(holders, random);
        for (Player holder : holders) {
            Optional<Modifier> modifier = active(holder);
            if (modifier.isPresent() && modifier.get().interceptOtherDeath(holder, dying, event)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** 反射など「攻撃が通ったときだけ」の反応。全プラグインの加工とキャンセルが確定した後。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageConfirmed(EntityDamageEvent event) {
        if (SyntheticDamage.active()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onDamagedConfirmed(player, event));
        }
    }

    // 死亡は他プラグインにキャンセルされうるので、確定した後にだけ反応する
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        active(killer).ifPresent(modifier -> modifier.onKill(killer, event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getEntity() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onExhaustion(player, event));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        active(player).ifPresent(modifier -> modifier.onBlockDrops(player, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        active(player).ifPresent(modifier -> modifier.onHarvest(player, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        active(player).ifPresent(modifier -> modifier.onItemDamage(player, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        // シェフの料理は、食べた人が何を選んでいようと効く
        ChefModifier.serve(player, event.getItem());
        active(player).ifPresent(modifier -> modifier.onConsume(player, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onFoodChange(player, event));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        active(shooter).ifPresent(modifier -> modifier.onProjectileHit(shooter, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onNightSkip(TimeSkipEvent event) {
        if (event.getSkipReason() != ClockTimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }
        // このイベントの時点では、夜を明かしたプレイヤーはまだベッドの中に居る
        for (Player player : event.getWorld().getPlayers()) {
            if (player.isSleeping()) {
                active(player).ifPresent(modifier -> modifier.onNightSkipped(player));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        active(player).ifPresent(modifier -> modifier.onToggleFlight(player, event));
    }

    @EventHandler
    public void onCraftPrepared(PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onCraftPrepared(player, event));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            active(player).ifPresent(modifier -> modifier.onInventoryClick(player, event));
        }
    }

    // 保護プラグインが右クリックを止めた場合 (useInteractedBlock が DENY) は
    // キャンセル扱いになるので、その操作には配らない
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        active(player).ifPresent(modifier -> modifier.onInteract(player, event));
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        // 死亡で attribute や飛行フラグが落ちるので掛け直す
        apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private static boolean holdsTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    /** トーテムが効く死因か。/kill と奈落はトーテムでも助からない。 */
    private static boolean totemWorksAgainst(EntityDamageEvent.DamageCause cause) {
        return cause != EntityDamageEvent.DamageCause.KILL
                && cause != EntityDamageEvent.DamageCause.VOID;
    }

    /** 攻撃者を解決する。直接殴った場合と、矢などの飛び道具の場合の両方を見る。 */
    private static Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
