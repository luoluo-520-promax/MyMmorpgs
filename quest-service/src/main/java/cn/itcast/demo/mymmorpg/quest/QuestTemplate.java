package cn.itcast.demo.mymmorpg.quest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务静态配置模板（JSON / 热更）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestTemplate {

    public int questId;
    public String name = "";
    public String description = "";
    /** 1=主线 2=支线 3=日常 4=周常 */
    public int questType;
    public int target = 1;
    public int expReward;
    public int goldReward;
    /** 接取即完成（如新手引导） */
    public boolean completeOnAccept;
    /** 前置任务 ID（DAG）；全部完成/领奖后才可接取。 */
    public List<Integer> prerequisiteQuestIds = new ArrayList<>();
    /** AND/OR/NOT 条件树 JSON，见 {@link QuestConditionEvaluator}。 */
    public String conditionJson = "";
    /** 进度事件：BATTLE_WIN / SHOP_PAY / GACHA_DRAW */
    public String eventType = "";

    public QuestTemplate() {
    }

    public QuestTemplate(int questId, String name, String description, int questType,
                         int target, int expReward, int goldReward, boolean completeOnAccept) {
        this.questId = questId;
        this.name = name;
        this.description = description;
        this.questType = questType;
        this.target = target;
        this.expReward = expReward;
        this.goldReward = goldReward;
        this.completeOnAccept = completeOnAccept;
    }
}
