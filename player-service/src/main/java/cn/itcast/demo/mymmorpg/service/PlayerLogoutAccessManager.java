/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerLogoutAccessManager.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：登出前置校验——账号会话是否有效、已选角玩家是否需要拆除场景/定时器等运行时状态。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 登出前置校验——账号会话是否有效、已选角玩家是否需要拆除场景/定时器等运行时状态

import cn.itcast.demo.mymmorpg.protocol.RetCode; // 协议层统一 retcode，供 PlayerLogoutHandler 直接写入 ScRsp
import org.springframework.stereotype.Service; // 登出前置校验 Bean，PlayerLogoutHandler 调用 validateAccountSession

/**
 * 登出访问条件判定：区分「仅账号登出」与「已选角需清理大世界运行时」两种路径。
 */
@Service // 区分「仅账号登出」与「已选角需拆除场景/定时器/推送运行时」
public class PlayerLogoutAccessManager { // 登出访问条件判定：区分「仅账号登出」与「已选角需清理大世界运行时」两种路径

    /**
     * 校验当前 WebSocket/Netty 连接上绑定的账号 ID 是否有效。
     * 网关鉴权通过后 accountId 会写入会话；此处再次拦截未登录或会话已失效的连接。
     *
     * @param boundAccountId 连接上下文里缓存的账号 ID（来自登录/选角流程）
     * @return {@link RetCode#OK} 表示可继续登出；{@link RetCode#NOT_LOGGED_IN} 表示连接未绑定有效账号
     */
    public int validateAccountSession(long boundAccountId) { // 校验当前 WebSocket/Netty 连接上绑定的账号 ID 是否有效
        if (boundAccountId <= 0) { // 玩家从未完成登录，或会话已被顶号清空
            return RetCode.NOT_LOGGED_IN; // 携带 RetCode.NOT_LOGGED_IN 构造协议响应
        }
        return RetCode.OK; // 账号会话有效，允许执行 Token 吊销与在线标记清理
    }

    /**
     * 判断登出时是否需要拆除角色侧运行时：场景实体、脏数据定时刷盘、推送通道绑定等。
     * 仅登录未选角时 boundPlayerId 为空或 0，只需清理账号级状态即可。
     *
     * @param boundPlayerId 当前连接已选中的角色 ID，未选角时为 null 或 <= 0
     * @return true 表示需要调用 SceneActorService.onPlayerLeave、PlayerTimerPersistenceService.stopPlayerTimer 等
     */
    public boolean needsRuntimeTeardown(Long boundPlayerId) { // 判断登出时是否需要拆除角色侧运行时：场景实体、脏数据定时刷盘、推送通道绑定等
        return boundPlayerId != null && boundPlayerId > 0; // 已选角需清场景/定时器/在线态
    }
}
