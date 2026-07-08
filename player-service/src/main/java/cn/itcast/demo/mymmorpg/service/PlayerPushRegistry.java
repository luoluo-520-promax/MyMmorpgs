/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerPushRegistry.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：维护 playerId 与 Netty/WebSocket 连接的双向映射，下行推送协议帧。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 维护 playerId 与 Netty/WebSocket 连接的双向映射，下行推送协议帧

import cn.itcast.demo.mymmorpg.net.BinaryFrameSender; // WebSocket 二进制帧封装（msgId + payload）
import cn.itcast.demo.mymmorpg.net.GameMessage; // Netty 侧游戏消息帧，writeAndFlush 写出
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 跨模块下行推送端口契约
import io.netty.channel.ChannelHandlerContext; // Netty TCP 连接上下文
import org.slf4j.Logger; // 推送失败时 debug 日志
import org.slf4j.LoggerFactory; // Logger 工厂
import org.springframework.stereotype.Component; // 下行推送 Bean，SceneActorService/SkillService/PreloadService 发送 Notify
import org.springframework.web.socket.WebSocketSession; // Spring WebSocket 会话

import java.util.Collection; // sendToPlayers 批量目标
import java.util.HashSet; // onlinePlayerIds 去重合并
import java.util.Set; // 在线玩家 ID 集合
import java.util.concurrent.ConcurrentHashMap; // 多连接并发 bind/unbind 安全

/**
 * 玩家推送注册表：选角成功后 bind 连接，服务端主动 Notify（场景同步、技能冷却、预加载就绪）经此下发。
 * Netty 与 WebSocket 互斥，同一 playerId 只保留一种连接类型。
 */
@Component // 维护 playerId↔Netty/WS 双向映射，实现 PlayerNotificationPort 单播与广播
public class PlayerPushRegistry implements PlayerNotificationPort { // 玩家推送注册表：选角成功后 bind 连接，服务端主动 Notify（场景同步、技能冷却、预加载就绪）经此下发

    private static final Logger log = LoggerFactory.getLogger(PlayerPushRegistry.class); // WebSocket 推送失败 debug 日志

    /** playerId -> Netty ChannelHandlerContext，TCP 长连接通道 */
    private final ConcurrentHashMap<Long, ChannelHandlerContext> nettyByPlayer = new ConcurrentHashMap<>(); // playerId -> Netty ChannelHandlerContext，TCP 长连接通道

    /** playerId -> WebSocketSession，WS 长连接（与 Netty 二选一） */
    private final ConcurrentHashMap<Long, WebSocketSession> wsByPlayer = new ConcurrentHashMap<>(); // playerId -> WebSocketSession，WS 长连接（与 Netty 二选一）

    /**
     * 选角或 Netty 握手成功后绑定：写入 netty 映射并移除同 playerId 的 ws 映射。
     *
     * @param playerId 已选角色 ID
     * @param ctx      Netty 通道上下文
     */
    public void bindNetty(long playerId, ChannelHandlerContext ctx) { // 选角或 Netty 握手成功后绑定：写入 netty 映射并移除同 playerId 的 ws 映射
        nettyByPlayer.put(playerId, ctx); // 登记 Netty 下行通道
        wsByPlayer.remove(playerId); // 同 playerId 仅保留一种连接类型
    }

    /**
     * WebSocket 选角成功后绑定：写入 ws 映射并移除 netty 映射。
     *
     * @param playerId 已选角色 ID
     * @param session  Spring WebSocket 会话
     */
    public void bindWebSocket(long playerId, WebSocketSession session) { // WebSocket 选角成功后绑定：写入 ws 映射并移除 netty 映射
        wsByPlayer.put(playerId, session); // 登记 WebSocket 下行通道
        nettyByPlayer.remove(playerId); // 同 playerId 仅保留一种连接类型
    }

    /**
     * 登出或断线时解除双向绑定，防止向已关闭连接推送。
     *
     * @param playerId 角色 ID
     */
    @Override
    public void unbind(long playerId) { // 登出或断线时解除双向绑定，防止向已关闭连接推送
        nettyByPlayer.remove(playerId); // 移除 Netty 映射
        wsByPlayer.remove(playerId); // 移除 WebSocket 映射
    }

    @Override // PlayerNotificationPort.send：优先 Netty TCP，回落 WebSocket 单播下行帧
    public void send(long playerId, int msgId, byte[] payload) { // 登出或断线时解除双向绑定，防止向已关闭连接推送
        var ctx = nettyByPlayer.get(playerId); // 优先 Netty TCP 通道
        if (ctx != null && ctx.channel().isActive()) { // 连接仍活跃
            ctx.writeAndFlush(new GameMessage(msgId, payload)); // 写出二进制游戏帧
            return; // Netty 已下发，不再走 WebSocket
        }
        var ws = wsByPlayer.get(playerId); // 回落 WebSocket 通道
        if (ws != null) { // 存在 WS 会话
            try { // WebSocket 半关闭或网络抖动时捕获异常，不阻断主流程
                BinaryFrameSender.sendWebSocket(ws, msgId, payload); // 与 Netty 一致的 msgId+payload 格式
            } catch (Exception e) { // sendWebSocket 写出失败，记录 debug 后吞掉
                log.debug("WebSocket 推送失败 playerId={}", playerId, e); // 半关闭连接等，不阻断主流程
            }
        }
    }

    /**
     * 判断本机是否有该玩家的活跃连接（Netty active 或 ws 映射存在）。
     * 与 PlayerSessionService Redis 在线标记互补：本方法仅感知本节点连接。
     */
    @Override
    public boolean isOnline(long playerId) { // 判断本机是否有该玩家的活跃连接（Netty active 或 ws 映射存在）
        var ctx = nettyByPlayer.get(playerId); // 查 Netty 映射
        if (ctx != null && ctx.channel().isActive()) { // TCP 仍活跃
            return true; // 本节点有活跃 Netty 连接
        }
        return wsByPlayer.containsKey(playerId); // 或存在 WebSocket 映射
    }

    /**
     * 合并 Netty 与 WebSocket 两表 keySet，得到本节点在线玩家 ID 集合（去重）。
     */
    public Set<Long> onlinePlayerIds() { // 合并 Netty 与 WebSocket 两表 keySet，得到本节点在线玩家 ID 集合（去重）
        Set<Long> ids = new HashSet<>(nettyByPlayer.size() + wsByPlayer.size()); // 预估容量避免扩容
        ids.addAll(nettyByPlayer.keySet()); // 合并 Netty 在线 playerId
        ids.addAll(wsByPlayer.keySet()); // 合并 WebSocket 在线 playerId
        return ids; // 本节点去重后的在线集合
    }

    /**
     * 向指定玩家集合逐个推送同一 msgId+payload，用于 AOI 广播的子集发送。
     *
     * @param playerIds 目标角色 ID 集合
     * @param msgId     协议消息号，如 SYNC_ENTITY_SC_NOTIFY
     * @param payload   Protobuf 序列化字节
     */
    @Override
    public void sendToPlayers(Collection<Long> playerIds, int msgId, byte[] payload) { // 向指定玩家集合逐个推送同一 msgId+payload，用于 AOI 广播的子集发送
        for (Long pid : playerIds) { // 逐个目标玩家
            if (pid != null && pid > 0) { // 跳过无效 playerId
                send(pid, msgId, payload); // 单播下行帧
            }
        }
    }

    @Override // PlayerNotificationPort.broadcastAllOnline：本节点全服广播，可排除发起者
    public void broadcastAllOnline(int msgId, byte[] payload, Long excludePlayerId) { // 向指定玩家集合逐个推送同一 msgId+payload，用于 AOI 广播的子集发送
        for (Long pid : onlinePlayerIds()) { // 本节点全部在线玩家
            if (excludePlayerId != null && excludePlayerId.equals(pid)) { // 跳过发起者
                continue; // 全服公告不发给自己
            }
            send(pid, msgId, payload); // 广播下行帧
        }
    }
}
