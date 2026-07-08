/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/support/ActivityServiceJmx.java
 * 2) 所属模块：activity-service / support
 * 3) 主要职责：JMX 运维接口——活动开关、充值进度、全服状态通知
 * 4) 系统位置：支撑层，供联调与运维通过 JConsole 等工具调用
 * 5) 变更建议：新增运维操作时保持 @ManagedOperation 描述清晰
 */
package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.entity.Activity; // 活动实体
import cn.itcast.demo.mymmorpg.repository.ActivityRepository; // 活动仓库
import cn.itcast.demo.mymmorpg.service.ActivityService; // 活动业务服务
import org.springframework.jmx.export.annotation.ManagedOperation; // JMX 可调用操作
import org.springframework.jmx.export.annotation.ManagedOperationParameter; // 操作参数说明
import org.springframework.jmx.export.annotation.ManagedResource; // JMX 资源注册
import org.springframework.stereotype.Component; // Spring 组件

/**
 * JMX：活动开关、充值进度（联调）、触发全服状态通知。
 */
@Component // 注册为 Spring Bean
@ManagedResource(
        objectName = "cn.itcast.demo.mymmorpg:type=ActivityService,name=Activity", // JMX ObjectName
        description = "活动系统" // JConsole 中显示的描述
)
public class ActivityServiceJmx { // 活动系统 JMX 门面

    /** 活动持久化仓库。 */
    private final ActivityRepository activityRepository; // 读写 activity 表
    /** 活动核心业务服务。 */
    private final ActivityService activityService; // 广播通知与进度更新

    /**
     * 构造器注入仓库与服务。
     *
     * @param activityRepository 活动 JPA 仓库
     * @param activityService    活动业务服务
     */
    public ActivityServiceJmx(ActivityRepository activityRepository, ActivityService activityService) {
        this.activityRepository = activityRepository; // 保存仓库引用
        this.activityService = activityService; // 保存服务引用
    }

    /**
     * 设置活动是否开启，并向在线玩家推送 ActivityStatusScNotify(807)。
     *
     * @param activityId 活动实例 ID
     * @param opened     true=开启
     * @return "ok" 或 "not_found"
     */
    @ManagedOperation(description = "设置活动是否开启，并向在线玩家推送 ActivityStatusScNotify(807)") // JMX 操作描述
    @ManagedOperationParameter(name = "activityId", description = "活动实例 ID") // 参数说明
    @ManagedOperationParameter(name = "opened", description = "true=开启") // 参数说明
    public String setActivityOpened(long activityId, boolean opened) {
        Activity a = activityRepository.findById(activityId).orElse(null); // 按 ID 查询活动
        if (a == null) { // 活动不存在
            return "not_found"; // 返回未找到
        }
        a.setOpened(opened); // 更新内存中的开关状态
        activityRepository.save(a); // 持久化到 MySQL
        activityService.setActivityOpenedAndNotify(a); // 广播 807 状态通知
        return "ok"; // 操作成功
    }

    /**
     * 增加玩家活动充值进度（首充类活动联调用）。
     *
     * @param playerId   玩家 ID
     * @param activityId 活动 ID
     * @param delta      增加的充值额度
     * @return "ok"
     */
    @ManagedOperation(description = "增加玩家活动充值进度（首充类活动联调用）") // JMX 操作描述
    @ManagedOperationParameter(name = "playerId", description = "玩家 ID") // 参数说明
    @ManagedOperationParameter(name = "activityId", description = "活动 ID") // 参数说明
    @ManagedOperationParameter(name = "delta", description = "增加的充值额度") // 参数说明
    public String addRechargeProgress(long playerId, long activityId, long delta) {
        activityService.addRechargeProgress(playerId, activityId, delta); // 委托服务累加充值进度
        return "ok"; // 操作成功
    }
}
