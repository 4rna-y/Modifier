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
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
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
     * 組み込みの16種は合計 100 に揃えてあり、重みがそのまま相対的な出やすさになる
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
