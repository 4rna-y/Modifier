package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 「選択画面を出すべきか」の判定。 */
class SelectionStoreTest {

    private static final String WORLD_A = "11111111-1111-1111-1111-111111111111";
    private static final String WORLD_B = "22222222-2222-2222-2222-222222222222";

    @Test
    @DisplayName("未選択なら出す")
    void unselectedNeedsSelection() {
        assertTrue(SelectionStore.needsSelection(null, null, WORLD_A));
        assertTrue(SelectionStore.needsSelection("", WORLD_A, WORLD_A));
    }

    @Test
    @DisplayName("同じワールドで選択済みなら出さない")
    void selectedInTheSameWorldIsDone() {
        assertFalse(SelectionStore.needsSelection("swift_foot", WORLD_A, WORLD_A));
    }

    @Test
    @DisplayName("ワールドが作り直されていたら選び直し")
    void newWorldNeedsReselection() {
        assertTrue(SelectionStore.needsSelection("swift_foot", WORLD_A, WORLD_B));
    }

    @Test
    @DisplayName("ワールドの記録が無い選択は信用しない")
    void selectionWithoutWorldNeedsReselection() {
        assertTrue(SelectionStore.needsSelection("swift_foot", null, WORLD_A));
    }
}
