package io.github.modifier;

/**
 * モディファイア効果が自分で与えるダメージ (反射・自傷など) の目印。
 *
 * <p>効果の中から {@code damage()} を呼ぶと、そのダメージがまたイベントとして流れてきて
 * 別の効果を誘発し、連鎖しうる。ここを通して与えたダメージについては
 * {@link ModifierEffects} が攻撃・被弾のフックを配らない。
 *
 * <p>致死判定 (蘇生) は例外で、合成ダメージでも通常どおり働く。効果由来のダメージで
 * 死ぬときも救済は受けられるべきなので。
 */
final class SyntheticDamage {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private SyntheticDamage() {
    }

    /** {@code action} の間に発生したダメージイベントを合成ダメージとして扱う。 */
    static void run(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    static boolean active() {
        return DEPTH.get() > 0;
    }
}
