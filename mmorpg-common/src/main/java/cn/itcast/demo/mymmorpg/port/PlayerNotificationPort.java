/**
 * 文件说明
 * 模块：mmorpg-common / 端口接口
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/port/PlayerNotificationPort.java
 * 类型：接口
 * 职责：定义 PlayerNotificationPort，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.port;

import java.util.Collection;

/**
 * 向在线玩家推送协议消息（单播、广播、批量）。
 */
public interface PlayerNotificationPort {

    void send(long playerId, int msgId, byte[] payload);

    void broadcastAllOnline(int msgId, byte[] payload, Long excludePlayerId);

    default boolean isOnline(long playerId) {
        return false;
    }

    default void sendToPlayers(Collection<Long> playerIds, int msgId, byte[] payload) {
        if (playerIds == null) {
            return;
        }
        for (Long pid : playerIds) {
            if (pid != null && pid > 0) {
                send(pid, msgId, payload);
            }
        }
    }

    default void unbind(long playerId) {
        // no-op
    }

    /**
     * 踢线：下行 Kick 通知后关闭本节点连接。默认 no-op。
     *
     * @param reason 1=重复登录 2=运维 3=令牌失效
     */
    default void kick(long playerId, int reason, String message) {
        // no-op
    }
}