package io.github.modifier;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * モディファイアを選んでいる間、敵モブに殺されないように守る。
 *
 * <p>夜に参加すると、選択画面を見ている間に襲われる。スペクテイターにすると
 * 画面のクリックがサーバーへ届かなくなる (バニラは観戦者のクリックを捨てる) ので、
 * 代わりに無敵 ({@code setInvulnerable}) にする。
 *
 * <p>無敵フラグはプレイヤーデータに保存されるため、守っている最中にサーバーが落ちると
 * 無敵のまま残る。そこで守っている印 (PDC) を残し、参加時に印があれば外す。
 *
 * <p>守る前の状態は覚えない。{@code Player#isInvulnerable()} はフラグではなく「今ダメージが
 * 通るか」を答えるので、参加直後 (クライアントの読み込みが終わるまで) は必ず true になり、
 * それを覚えて戻すと永久に無敵になってしまう。管理者が手で付けた無敵は、選び終わると外れる。
 */
final class SelectionGuard {

    /** 守っている印。 */
    static final NamespacedKey GUARDED = new NamespacedKey("modifier", "guarded");

    private SelectionGuard() {
    }

    /** 守る。すでに守っていれば何もしない。 */
    static void protect(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(GUARDED, PersistentDataType.BOOLEAN)) {
            return;
        }
        pdc.set(GUARDED, PersistentDataType.BOOLEAN, true);
        player.setInvulnerable(true);
    }

    /** 守るのをやめる。守っていなければ何もしない。 */
    static void release(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(GUARDED, PersistentDataType.BOOLEAN)) {
            return;
        }
        pdc.remove(GUARDED);
        player.setInvulnerable(false);
    }
}
