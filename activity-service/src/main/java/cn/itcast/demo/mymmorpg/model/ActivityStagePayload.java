package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动关卡/阶段配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityStagePayload {

    public int stageIndex;
    public String name = "";
    public String description = "";
    /** 阶段解锁时间戳（毫秒），0 表示跟随活动开始。 */
    public long unlockTime;
    public List<ActivityConditionPayload> unlockConditions = new ArrayList<>();
}
