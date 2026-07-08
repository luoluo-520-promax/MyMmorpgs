/**
 * 场景/大世界异步事件发布抽象：进图、离图、切线、移动等高频行为，
 * 通过 RocketMQ 广播供 AOI 同步、反外挂轨迹分析、跨服镜像等下游消费。
 */
package cn.itcast.demo.mymmorpg.service;

public interface SceneEventPublisher { // 场景事件对外发布契约：统一上报玩家在大世界里的位置与切线变化

    /** 玩家进入指定场景与分线（如主城 1 线） */
    void publishEnterScene(long playerId, int sceneId, int lineId); // 进入场景后触发分线同步、可视范围加载与在线统计

    /** 玩家离开场景（回城、下线、传送前） */
    void publishLeaveScene(long playerId, int sceneId); // 离开场景后通知下游清理 AOI、回收场景占用与停留时长

    /** 同场景内切换分线（缓解单线人数上限） */
    void publishSwitchLine(long playerId, int sceneId, int fromLine, int toLine); // 切换分线时驱动新旧分线之间的实体迁移与负载均衡

    /** 玩家在世界坐标移动，供位置同步与异常位移检测 */
    void publishMove(long playerId, int sceneId, float x, float y, float z); // 移动上报用于广播角色位置、刷新周围玩家视野与检测外挂瞬移
}
