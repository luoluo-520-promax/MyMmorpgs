package cn.itcast.demo.mymmorpg.quest;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuestGraph DAG + ConditionEvaluator AND/OR/NOT。
 */
public class QuestGraphAndConditionFlowTest {

    @Test
    public void graph_unlockRequiresPrerequisites() {
        QuestTemplate a = tpl(1);
        QuestTemplate b = tpl(2);
        b.prerequisiteQuestIds = List.of(1);
        QuestTemplate c = tpl(3);
        c.prerequisiteQuestIds = List.of(1, 2);

        QuestGraph g = QuestGraph.fromTemplates(List.of(a, b, c));
        assertThat(g.isUnlocked(1, Set.of())).isTrue();
        assertThat(g.isUnlocked(2, Set.of())).isFalse();
        assertThat(g.isUnlocked(2, Set.of(1))).isTrue();
        assertThat(g.isUnlocked(3, Set.of(1))).isFalse();
        assertThat(g.isUnlocked(3, Set.of(1, 2))).isTrue();
        assertThat(g.hasCycle()).isFalse();
        assertThat(g.topologicalOrder()).containsExactly(1, 2, 3);
    }

    @Test
    public void graph_detectsCycle() {
        QuestTemplate a = tpl(1);
        a.prerequisiteQuestIds = List.of(2);
        QuestTemplate b = tpl(2);
        b.prerequisiteQuestIds = List.of(1);
        QuestGraph g = QuestGraph.fromTemplates(List.of(a, b));
        assertThat(g.hasCycle()).isTrue();
        assertThat(g.findCycle()).isNotEmpty();
    }

    @Test
    public void conditionEvaluator_andOrNot() {
        Map<String, Object> ctx = Map.of("level", 15, "vip", 0, "claimedQuestIds", List.of(10));
        assertThat(QuestConditionEvaluator.evaluate(
                "{\"op\":\"AND\",\"children\":[{\"op\":\"LEVEL_MIN\",\"value\":10},{\"op\":\"VIP_MIN\",\"value\":1}]}",
                ctx)).isFalse();
        assertThat(QuestConditionEvaluator.evaluate(
                "{\"op\":\"OR\",\"children\":[{\"op\":\"LEVEL_MIN\",\"value\":10},{\"op\":\"VIP_MIN\",\"value\":1}]}",
                ctx)).isTrue();
        assertThat(QuestConditionEvaluator.evaluate(
                "{\"op\":\"AND\",\"children\":[{\"op\":\"LEVEL_MIN\",\"value\":10},{\"op\":\"NOT\",\"child\":{\"op\":\"VIP_MIN\",\"value\":1}}]}",
                ctx)).isTrue();
        assertThat(QuestConditionEvaluator.evaluate(
                "{\"op\":\"QUEST_CLAIMED\",\"value\":10}", ctx)).isTrue();
        assertThat(QuestConditionEvaluator.evaluate("", ctx)).isTrue();
        assertThat(QuestConditionEvaluator.evaluate("{bad", ctx)).isFalse();
    }

    private static QuestTemplate tpl(int id) {
        QuestTemplate t = new QuestTemplate();
        t.questId = id;
        t.name = "q" + id;
        t.target = 1;
        return t;
    }
}
