package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** リセットチケット: 印の見分けと、選び直しで開始アイテムを配らない仕掛け。 */
class ResetTicketTest {

    private static ItemStack stamped(Material base, String value) {
        ItemStack item = Mocks.stampableItem(base, 1);
        item.getItemMeta().getPersistentDataContainer()
                .set(RevivalItem.ITEM, PersistentDataType.STRING, value);
        return item;
    }

    @Test
    @DisplayName("印の付いた紙だけをチケットと見なす")
    void onlyStampedPaperIsTicket() {
        assertTrue(ResetTicket.isResetTicket(stamped(Material.PAPER, ResetTicket.RESET_TICKET)));
        assertFalse(ResetTicket.isResetTicket(Mocks.stampableItem(Material.PAPER, 1)));
        assertFalse(ResetTicket.isResetTicket(null));
    }

    @Test
    @DisplayName("復活剤とチケットは同じキーを使うが混ざらない")
    void ticketAndRevivalDoNotCollide() {
        ItemStack ticket = stamped(Material.PAPER, ResetTicket.RESET_TICKET);
        ItemStack revival = stamped(Material.GOLDEN_APPLE, RevivalItem.REVIVAL);

        assertFalse(RevivalItem.isRevival(ticket));
        assertFalse(ResetTicket.isResetTicket(revival));
        assertTrue(RevivalItem.isRevival(revival));
    }

    @Test
    @DisplayName("開始アイテムを飛ばす印は1回読むと消える")
    void skipFlagIsConsumedOnce() {
        // 開始アイテムの印だけを見るので、依存は触らない
        SelectionService selection = new SelectionService(null, null, null, null, null);
        UUID id = UUID.randomUUID();

        assertFalse(selection.consumeSkipStartingItems(id), "何もしていなければ配る");

        selection.markStartingItems(id, false);
        assertTrue(selection.consumeSkipStartingItems(id), "チケット経由なので配らない");
        assertFalse(selection.consumeSkipStartingItems(id), "次の選び直しでは配る");
    }

    @Test
    @DisplayName("/m select の選び直しでは開始アイテムを配る")
    void adminReselectStillGivesStartingItems() {
        SelectionService selection = new SelectionService(null, null, null, null, null);
        UUID id = UUID.randomUUID();

        selection.markStartingItems(id, false);
        selection.markStartingItems(id, true);
        assertFalse(selection.consumeSkipStartingItems(id));
    }

    @Test
    @DisplayName("退出したら選び直しの記録も捨てる")
    void quitForgetsTheFlag() {
        SelectionService selection = new SelectionService(null, null, null, null, null);
        UUID id = UUID.randomUUID();

        selection.markStartingItems(id, false);
        selection.forgetAll(id);
        assertFalse(selection.consumeSkipStartingItems(id));
    }
}
