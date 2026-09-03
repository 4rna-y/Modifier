package io.github.modifier;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * 選んでいるモディファイアの名前を、ネームタグの下段に出す。
 *
 * <p>スコアボードの {@code below_name} 表示枠を使う。枠には本来「点数 + 目標の表示名」が
 * 出るが、点数ごとに固定の文言 ({@link NumberFormat#fixed}) を指定できるので、点数の
 * 代わりにモディファイア名を出し、目標の表示名は空にしておく。エンティティを増やさずに済み、
 * ネームタグと同じ条件 (距離・スニーク・可視性) で出る。
 *
 * <p>自分のネームタグは自分には見えないので、自分の分は表示されない。
 * メインのスコアボードは {@code scoreboard.dat} に保存されるので、停止時に目標ごと消す。
 */
public final class NameTagDisplay {

    public static final String OBJECTIVE_NAME = "modifier_choice";

    private final Server server;

    public NameTagDisplay(Server server) {
        this.server = server;
    }

    /** そのプレイヤーの下段にモディファイア名を出す。 */
    public void show(Player player, Modifier modifier) {
        Score score = objective().getScore(player.getName());
        score.setScore(0);
        score.numberFormat(NumberFormat.fixed(modifier.displayName()));
    }

    /** 下段の表示を消す。 */
    public void hide(Player player) {
        Objective objective = board().getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }
        Score score = objective.getScore(player.getName());
        if (score.isScoreSet()) {
            score.resetScore();
        }
    }

    /** 目標ごと消す。停止時に呼ぶ。 */
    public void shutdown() {
        Objective objective = board().getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
    }

    private Scoreboard board() {
        return server.getScoreboardManager().getMainScoreboard();
    }

    /** 表示用の目標。無ければ作り、下段の枠に置く。 */
    private Objective objective() {
        Scoreboard board = board();
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
        }
        if (objective.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        return objective;
    }
}
