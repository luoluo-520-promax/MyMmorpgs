/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RoundBalanceStrategy.java
 * 类型：类
 * 职责：轮询负载均衡实现，按顺序依次使用各战斗服 RPC 连接。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程安全轮询负载均衡：多线程并发 RPC 转发时按递增计数依次选取战斗服会话。
 */
public class RoundBalanceStrategy implements BalanceStrategy {

    /** 跨线程安全的轮询计数器，保证并发环境下依次选取战斗服 */
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public IdSession pick(List<IdSession> sessions) {
        if (CollectionUtils.isEmpty(sessions)) { // 战斗服会话池为空，无法路由 RPC 消息
            return null;
        }
        int index = counter.getAndIncrement() % sessions.size(); // 取模实现环形轮询下标
        return sessions.get(index); // 返回本轮对应的战斗服 Netty 会话
    }
}
