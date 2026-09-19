package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 他プラグインと揃えなければならないアイテムの「契約」。
 *
 * <p>実物の組み立てはサーバーが要るので確かめられない。代わりに双方で一致していなければならない
 * もの (印のキー・値・土台・説明に焼き込む数字) をここに固定する。相手側にも同じ値を固定した
 * 試験があるので、どちらかを動かせば片方が落ちる。
 *
 * <ul>
 *   <li>復活剤 → RaidEvent の {@code CustomItems} ({@code custom: modifier_revival})</li>
 *   <li>リセットチケット → RaidEvent の {@code CustomItems} ({@code custom: modifier_reset_ticket})</li>
 * </ul>
 */
class CrossPluginItemsTest {

    @Test
    @DisplayName("印のキーと値は RaidEvent の写しと同じ")
    void stampContract() {
        assertEquals("modifier:item", RevivalItem.ITEM.toString());
        assertEquals("revival", RevivalItem.REVIVAL);
        assertEquals("reset_ticket", ResetTicket.RESET_TICKET);
    }

    @Test
    @DisplayName("説明に出すチャージ秒は、RaidEvent が焼き込む値と同じ")
    void ticketChargeSeconds() {
        // RaidEvent は Modifier の設定を読めないので、この値で説明文を組む。
        // 動かすとレイドで出たチケットと /m ticket のものが重ならなくなる
        assertEquals(3f, ResetTicket.CHARGE_SECONDS);
    }
}
