/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/BalanceStrategy.java
 * 类型：接口
 * 职责：定义战斗服 RPC 会话的负载均衡策略，供 RpcClientRouter 在多个战斗服连接间挑选目标。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import java.util.List;

/**
 * 战斗服负载均衡策略：从已建立的 {@link IdSession} 列表中选出一条用于转发 RPC 消息。
 */
public interface BalanceStrategy {

    /**
     * 从候选战斗服 RPC 会话中挑选一条可用连接。
     *
     * @param sessions 当前已连接且可用的战斗服 Netty 会话列表
     * @return 被选中的会话；列表为空时返回 null
     */
    IdSession pick(List<IdSession> sessions);
}
