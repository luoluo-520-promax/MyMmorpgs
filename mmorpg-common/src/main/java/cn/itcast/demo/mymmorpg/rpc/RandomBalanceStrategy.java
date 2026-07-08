/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RandomBalanceStrategy.java
 * 类型：类
 * 职责：随机负载均衡实现，将跨服 RPC 请求均匀打散到各战斗服连接。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡：每次从战斗服会话池中随机选取一条 Netty 连接。
 */
public class RandomBalanceStrategy implements BalanceStrategy {

    /** 用于在多个战斗服 RPC 会话间随机下标的生成器 */
    private final Random random = new Random();

    @Override
    public IdSession pick(List<IdSession> sessions) {
        if (CollectionUtils.isEmpty(sessions)) { // 尚无已注册的战斗服 RPC 连接，无法转发
            return null;
        }
        return sessions.get(random.nextInt(sessions.size())); // 随机挑选一条战斗服 Netty 会话
    }
}
