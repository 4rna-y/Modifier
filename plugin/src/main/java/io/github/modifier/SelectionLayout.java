package io.github.modifier;

import java.util.OptionalInt;

/** 選択画面のスロット配置。 */
public final class SelectionLayout {

    /** 9x3 のチェスト GUI。 */
    public static final int SIZE = 27;

    /** 提示できる選択肢の最大数。 */
    public static final int MAX_CHOICES = 3;

    private SelectionLayout() {
    }

    /** 選択肢を中央の行に均等に並べたときのスロット番号。 */
    public static int[] slotsFor(int count) {
        return switch (count) {
            case 1 -> new int[] {13};
            case 2 -> new int[] {12, 14};
            case 3 -> new int[] {11, 13, 15};
            default -> throw new IllegalArgumentException(
                    "選択肢は 1〜" + MAX_CHOICES + " 個: " + count);
        };
    }

    /** スロット番号から選択肢の番号を引く。選択肢のスロットでなければ空。 */
    public static OptionalInt indexOf(int slot, int count) {
        int[] slots = slotsFor(count);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
