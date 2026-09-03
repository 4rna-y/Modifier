package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.scoreboard.numbers.FixedFormat;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ネームタグの下段。スコアボードの呼び出しだけを見る。 */
@DisplayName("ネームタグの下段")
class NameTagDisplayTest {

    private Server server;
    private Scoreboard board;
    private Objective objective;
    private Score score;
    private Mocks.FakePlayer me;
    private NameTagDisplay display;

    @BeforeEach
    void setUp() {
        World world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);

        // Criteria.DUMMY は Bukkit の静的な入口を通るので、器のサーバーに答えさせる
        Mocks.installCurrentTick(server, 0);
        when(org.bukkit.Bukkit.getServer().getScoreboardCriteria(anyString()))
                .thenAnswer(i -> mock(Criteria.class));

        board = mock(Scoreboard.class);
        ScoreboardManager manager = mock(ScoreboardManager.class);
        when(manager.getMainScoreboard()).thenReturn(board);
        when(server.getScoreboardManager()).thenReturn(manager);

        objective = mock(Objective.class);
        score = mock(Score.class);
        when(objective.getScore("Me")).thenReturn(score);
        when(board.registerNewObjective(eq(NameTagDisplay.OBJECTIVE_NAME), any(Criteria.class),
                any(Component.class))).thenReturn(objective);

        display = new NameTagDisplay(server);
    }

    @Test
    @DisplayName("目標を作って below_name に置き、点数の代わりに名前を出す")
    void showsTheNameBelowTheTag() {
        Modifier fat = new FatModifier();
        display.show(me.player(), fat);

        verify(board).registerNewObjective(eq(NameTagDisplay.OBJECTIVE_NAME), any(Criteria.class),
                eq(Component.empty()));
        verify(objective).setDisplaySlot(DisplaySlot.BELOW_NAME);
        verify(score).setScore(0);

        ArgumentCaptor<NumberFormat> format = ArgumentCaptor.forClass(NumberFormat.class);
        verify(score).numberFormat(format.capture());
        FixedFormat fixed = assertInstanceOf(FixedFormat.class, format.getValue());
        assertEquals(fat.displayName(), fixed.component());
    }

    @Test
    @DisplayName("目標がすでにあれば作り直さない")
    void reusesTheObjective() {
        when(board.getObjective(NameTagDisplay.OBJECTIVE_NAME)).thenReturn(objective);
        when(objective.getDisplaySlot()).thenReturn(DisplaySlot.BELOW_NAME);

        display.show(me.player(), new FatModifier());

        verify(board, never()).registerNewObjective(anyString(), any(Criteria.class),
                any(Component.class));
        verify(objective, never()).setDisplaySlot(any());
        verify(score).setScore(0);
    }

    @Test
    @DisplayName("消すと点数ごと消える")
    void hidesByResettingTheScore() {
        when(board.getObjective(NameTagDisplay.OBJECTIVE_NAME)).thenReturn(objective);
        when(score.isScoreSet()).thenReturn(true);

        display.hide(me.player());
        verify(score).resetScore();
    }

    @Test
    @DisplayName("目標が無ければ消すものも無い")
    void hidingWithoutTheObjectiveIsHarmless() {
        display.hide(me.player());
        verify(board, never()).registerNewObjective(anyString(), any(Criteria.class),
                any(Component.class));
    }

    @Test
    @DisplayName("停止時に目標ごと消す")
    void shutdownUnregisters() {
        when(board.getObjective(NameTagDisplay.OBJECTIVE_NAME)).thenReturn(objective);
        display.shutdown();
        verify(objective).unregister();
    }
}
