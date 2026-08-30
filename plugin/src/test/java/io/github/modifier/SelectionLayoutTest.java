package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 選択画面のスロット配置。 */
class SelectionLayoutTest {

    @Test
    @DisplayName("選択肢は中央の行に均等に並ぶ")
    void choicesAreCenteredOnTheMiddleRow() {
        assertArrayEquals(new int[] {13}, SelectionLayout.slotsFor(1));
        assertArrayEquals(new int[] {12, 14}, SelectionLayout.slotsFor(2));
        assertArrayEquals(new int[] {11, 13, 15}, SelectionLayout.slotsFor(3));
    }

    @Test
    @DisplayName("配置は 9x3 の中に収まる")
    void slotsFitInTheInventory() {
        for (int count = 1; count <= SelectionLayout.MAX_CHOICES; count++) {
            for (int slot : SelectionLayout.slotsFor(count)) {
                assertTrue(slot >= 0 && slot < SelectionLayout.SIZE,
                        "スロット " + slot + " が範囲外");
            }
        }
    }

    @Test
    @DisplayName("スロットから選択肢の番号を引ける")
    void slotMapsBackToIndex() {
        assertEquals(OptionalInt.of(0), SelectionLayout.indexOf(11, 3));
        assertEquals(OptionalInt.of(1), SelectionLayout.indexOf(13, 3));
        assertEquals(OptionalInt.of(2), SelectionLayout.indexOf(15, 3));
    }

    @Test
    @DisplayName("選択肢以外のスロットは空を返す")
    void otherSlotsAreNotChoices() {
        assertEquals(OptionalInt.empty(), SelectionLayout.indexOf(0, 3));
        assertEquals(OptionalInt.empty(), SelectionLayout.indexOf(12, 3));
        // プレイヤーインベントリ側のスロット番号
        assertEquals(OptionalInt.empty(), SelectionLayout.indexOf(30, 3));
    }

    @Test
    @DisplayName("扱えない選択肢の数は弾く")
    void rejectsUnsupportedCounts() {
        assertThrows(IllegalArgumentException.class, () -> SelectionLayout.slotsFor(0));
        assertThrows(IllegalArgumentException.class, () -> SelectionLayout.slotsFor(4));
    }
}
