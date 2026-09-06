package io.github.modifier;

import java.util.List;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * プレイヤーに効果を与えるモディファイア。
 *
 * <p>効果は2種類ある。
 * <ul>
 *   <li><b>常時</b> — {@link #apply}/{@link #remove} で attribute やフラグを付け外しする。</li>
 *   <li><b>状況発動</b> — {@code on...} のフックへ、選択中のプレイヤーの分だけ配られる。</li>
 * </ul>
 *
 * <p>イベントの購読は {@link ModifierEffects} が一括で行い、選択中のモディファイアへ
 * 振り分ける。モディファイアごとに {@code Listener} を登録しないので、選び直しは
 * 保存先を書き換えるだけで済む。
 *
 * <p>効果自身が与えるダメージ (反射・自傷) は {@link SyntheticDamage#run} を通すこと。
 * 通さないと、そのダメージがまたフックを誘発して連鎖しうる。
 */
public interface Modifier {

    /** 保存と参照に使う識別子。 */
    String id();

    /** 選択画面に出す名前。 */
    Component displayName();

    /** 選択画面に出す説明。1行1要素。隠し効果はここに書かない。 */
    List<Component> description();

    /**
     * アイコンの土台にするアイテム。
     *
     * <p>リソースパックを適用していないクライアントにはこの見た目のまま表示されるので、
     * パック無しでも意味が通るものを選ぶ。
     */
    Material iconBase();

    /**
     * 3択に出る重み。大きいほど出やすい。
     *
     * <p>出現率は「自分の重み ÷ 全体の重み」で決まるので、絶対値ではなく比だけが効く。
     * 組み込みの24種は合計 100 に揃えてあり、重みがそのまま相対的な出やすさになる
     * (実際の出現率は3つ引くぶん、これより高くなる)。
     *
     * <p>強さではなく「引いた人以外への影響」で決めている。とくに死を打ち消すものは
     * wiah と組み合わせるとワールドの生死そのものを動かすので、明確に低くしてある。
     *
     * @return 1 以上の重み
     */
    default int weight() {
        return DEFAULT_WEIGHT;
    }

    /** 重みを指定しなかったモディファイアの既定値。 */
    int DEFAULT_WEIGHT = 8;

    /**
     * アイコンに使う {@code minecraft:item_model} のキー。
     *
     * <p>リソースパック側に {@code assets/modifier/items/choice/<id>.json} を置くと反映される。
     * 無ければ {@link #iconBase()} の見た目になる。
     */
    default Key iconModel() {
        return Key.key("modifier", "choice/" + id());
    }

    /**
     * 常時効果を付ける。
     *
     * <p>参加時・選択時・リスポーン時に呼ばれるので、<b>何度呼ばれても二重にかからない</b>
     * ように書くこと。attribute は固定のキーで remove してから add する。
     */
    default void apply(Player player) {
    }

    /** 常時効果を外す。 */
    default void remove(Player player) {
    }

    /** ダメージを受けたとき。 */
    default void onDamaged(Player player, EntityDamageEvent event) {
    }

    /** 他のエンティティへダメージを与えたとき。矢が当たった場合も射手に届く。 */
    default void onDealtDamage(Player player, EntityDamageByEntityEvent event) {
    }

    /**
     * 受けたダメージが確定したとき (どのプラグインにもキャンセルされなかった場合のみ)。
     *
     * <p>反射のような「攻撃が実際に通ったときだけ起こすべき」反応はここで行う。
     * {@link #onDamaged} の時点では、後段でキャンセルされるかがまだ分からない。
     * イベントの改変はしないこと。
     */
    default void onDamagedConfirmed(Player player, EntityDamageEvent event) {
    }

    /** エンティティにとどめを刺したとき。 */
    default void onKill(Player player, LivingEntity victim) {
    }

    /** 満腹度が減るとき。{@code setExhaustion} で増減させる。 */
    default void onExhaustion(Player player, EntityExhaustionEvent event) {
    }

    /** ブロックを壊してアイテムが落ちるとき。 */
    default void onBlockDrops(Player player, BlockDropItemEvent event) {
    }

    /** 右クリック収穫 (スイートベリー等) のとき。 */
    default void onHarvest(Player player, PlayerHarvestBlockEvent event) {
    }

    /** 道具や防具の耐久値が減るとき。 */
    default void onItemDamage(Player player, PlayerItemDamageEvent event) {
    }

    /** 食べ物や薬を飲み食いし終わるとき。 */
    default void onConsume(Player player, PlayerItemConsumeEvent event) {
    }

    /** 満腹度が変化するとき。食事由来かは {@code event.getItem() != null} で分かる。 */
    default void onFoodChange(Player player, FoodLevelChangeEvent event) {
    }

    /** 自分の放った矢などが何かに当たったとき。 */
    default void onProjectileHit(Player player, ProjectileHitEvent event) {
    }

    /** ベッドで夜が明けたとき (寝ていた場合のみ)。 */
    default void onNightSkipped(Player player) {
    }

    /** 飛行の切り替えを試みたとき。二段ジャンプの実装に使う。 */
    default void onToggleFlight(Player player, PlayerToggleFlightEvent event) {
    }

    /**
     * クラフトの完成品が組まれたとき (作業台・手持ちのクラフト)。
     *
     * <p>プレビューの段階なので、ここで完成品を差し替えると実際に作られる物も変わる。
     * シフトクリックで連続して作る間は1個ごとに呼ばれる。
     */
    default void onCraftPrepared(Player player, PrepareItemCraftEvent event) {
    }

    /** インベントリのスロットをクリックしたとき。かまどの完成品の取り出しなどに使う。 */
    default void onInventoryClick(Player player, InventoryClickEvent event) {
    }

    /** ブロックや空中を左右クリックしたとき。右クリックは両手ぶん飛んでくる。 */
    default void onInteract(Player player, PlayerInteractEvent event) {
    }

    /**
     * 選んだときに受け取る道具。
     *
     * <p>選択の確定時に一度だけ持ち物へ入れる ({@link StartingItems#give})。
     * 参加やリスポーンでは配り直さない。選択画面と {@code /m} の説明にも添えられる。
     */
    default List<StartingItems.Item> startingItems() {
        return List.of();
    }

    /**
     * 選択が確定した直後に呼ばれる。効果を掛ける前。
     *
     * <p>選んだ時点で決めるもの (よくばりの中身の抽選など) はここで行い、保存する。
     */
    default void onChosen(Player player) {
    }

    /**
     * そのプレイヤーに実際に効かせるモディファイア。
     *
     * <p>ふつうは自分自身。よくばりのように「中身がプレイヤーごとに違う」ものは、
     * ここで中身を束ねたものを返す。{@link ModifierEffects} は必ずこれを通してから配る。
     */
    default Modifier resolveFor(Player player) {
        return this;
    }

    /** 釣り竿を使ったとき。投げた・掛かった・釣り上げた、のどの段階かは {@code event.getState()}。 */
    default void onFish(Player player, PlayerFishEvent event) {
    }

    /** アイテムを捨てたとき。 */
    default void onItemDrop(Player player, PlayerDropItemEvent event) {
    }

    /**
     * エンティティにとどめを刺し、その落とし物が決まるとき。
     *
     * <p>{@link #onKill} より前の、まだ書き換えてよい段で呼ばれる。落とし物の加工はこちらで、
     * 確定後の反応 (バフなど) は {@link #onKill} で行う。
     */
    default void onKillDrops(Player player, EntityDeathEvent event) {
    }

    /** ポーション効果が付く・変わる・消えるとき。食べ物由来かは {@code event.getCause()} で分かる。 */
    default void onPotionEffect(Player player, EntityPotionEffectEvent event) {
    }

    /** 自分が死んだとき (打ち消されなかった場合のみ)。落とし物の後始末に使う。 */
    default void onDeath(Player player, PlayerDeathEvent event) {
    }

    /** 定期的に呼ばれる。接地判定など、イベントで拾えないものに使う。 */
    default void tick(Player player) {
    }

    /**
     * 自分が死ぬ寸前に呼ばれる。
     *
     * <p>true を返すと致死ダメージが打ち消される。蘇生の後始末 ({@link Revival#revive} や
     * テレポート) は実装側で行うこと。
     */
    default boolean interceptDeath(Player self, EntityDamageEvent event) {
        return false;
    }

    /**
     * 他のプレイヤーが死ぬ寸前に呼ばれる。本人が {@link #interceptDeath} で
     * 助からなかった場合のみ。
     *
     * @param self  このモディファイアを選んでいるプレイヤー
     * @param dying 死にかけているプレイヤー
     */
    default boolean interceptOtherDeath(Player self, Player dying, EntityDamageEvent event) {
        return false;
    }
}
