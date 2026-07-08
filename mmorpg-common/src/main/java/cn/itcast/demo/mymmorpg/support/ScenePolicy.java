/**
 * 场景进入策略接口：判断是否允许玩家进入指定 mapId，
 * 可由 Groovy 脚本热更新实现（等级限制、副本门票、活动地图开放时间等）。
 */
package cn.itcast.demo.mymmorpg.support;

public interface ScenePolicy { // 进图前策略校验，与 SceneService 配合使用

    /**
     * 进入场景前的策略校验。
     *
     * @param mapId    目标地图/场景配置 ID
     * @param playerId 尝试进入的角色 ID
     * @return true 允许传送/进图，false 拒绝并返回错误码给客户端
     */
    boolean allowEnterScene(int mapId, long playerId); // 例如：等级不足、未持有副本钥匙、活动未开放时返回 false
}
