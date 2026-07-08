/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerSelectionAccessManager.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：选角前置校验——协议账号与会话账号一致、角色归属校验。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 选角前置校验——协议账号与会话账号一致、角色归属校验

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 玩家实体，含 accountId 归属字段
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 选角失败时写入 SelectPlayerScRsp.retcode
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // 按 playerId + accountId 联合查询，防跨号选角
import org.springframework.stereotype.Service; // 选角归属校验 Bean，SelectPlayerHandler 调用防跨号选角

import java.util.Optional; // 角色不存在或不属于该账号时返回 empty

/**
 * 选角访问控制：防止伪造协议中的 accountId 选他人角色，或会话与请求账号不一致。
 */
@Service // 校验协议 accountId 与会话一致，findByIdAndAccountId 确认角色归属
public class PlayerSelectionAccessManager { // 选角访问控制：防止伪造协议中的 accountId 选他人角色，或会话与请求账号不一致

    /** 玩家表仓储，findByIdAndAccountId 保证角色归属当前登录账号 */
    private final PlayerRepository playerRepository; // 玩家表仓储，findByIdAndAccountId 保证角色归属当前登录账号

    /**
     * 构造器：选角链路只读查询 player 表，无需 Redis。
     */
    public PlayerSelectionAccessManager(PlayerRepository playerRepository) { // 构造器：选角链路只读查询 player 表，无需 Redis
        this.playerRepository = playerRepository; // findByIdAndAccountId 防跨号选角
    }

    /**
     * 校验协议请求中的 accountId 是否与当前连接会话已认证的账号一致。
     * 防止客户端篡改 CsReq 中的 accountId 尝试操作他人账号下角色。
     *
     * @param requestAccountId      协议 SelectPlayerCsReq 携带的账号 ID
     * @param boundSessionAccountId 连接上下文在登录成功后绑定的账号 ID
     * @return {@link RetCode#OK} 或 {@link RetCode#NOT_LOGGED_IN}
     */
    public int validateSessionMatchesRequest(long requestAccountId, long boundSessionAccountId) { // 校验协议请求中的 accountId 是否与当前连接会话已认证的账号一致
        if (boundSessionAccountId <= 0 || requestAccountId != boundSessionAccountId) { // 会话未登录或 accountId 被篡改
            return RetCode.NOT_LOGGED_IN; // 携带 RetCode.NOT_LOGGED_IN 构造协议响应
        }
        return RetCode.OK; // 会话与请求账号一致，可继续选角
    }

    /**
     * 查询指定角色是否属于当前账号，用于选角与删除角色等需要归属校验的场景。
     *
     * @param playerId  待选角色 ID
     * @param accountId 当前登录账号 ID
     * @return 归属匹配时返回 Player 实体，否则 empty
     */
    public Optional<Player> findPlayerOwnedByAccount(long playerId, long accountId) { // 查询指定角色是否属于当前账号，用于选角与删除角色等需要归属校验的场景
        return playerRepository.findByIdAndAccountId(playerId, accountId); // 联合主键校验角色归属
    }
}
