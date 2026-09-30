package cn.itcast.demo.mymmorpg.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 任务导入校验。 */
public final class QuestImportValidator {

    private QuestImportValidator() {
    }

    public static List<String> validate(List<QuestTemplate> quests) {
        List<String> errors = new ArrayList<>();
        if (quests == null || quests.isEmpty()) {
            errors.add("quests empty");
            return errors;
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < quests.size(); i++) {
            QuestTemplate q = quests.get(i);
            String prefix = "quests[" + i + "]";
            if (q == null) {
                errors.add(prefix + " null");
                continue;
            }
            if (q.questId <= 0) {
                errors.add(prefix + ".questId invalid");
            } else if (!seen.add(q.questId)) {
                errors.add(prefix + ".questId duplicate=" + q.questId);
            }
            if (q.questType < 1 || q.questType > 3) {
                errors.add(prefix + ".questType must be 1|2|3");
            }
            if (q.target <= 0) {
                errors.add(prefix + ".target must be > 0");
            }
            if (q.expReward < 0 || q.goldReward < 0) {
                errors.add(prefix + ".reward must be >= 0");
            }
        }
        return errors;
    }
}
