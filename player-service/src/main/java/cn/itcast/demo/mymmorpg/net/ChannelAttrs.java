/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ChannelAttrs.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ChannelAttrs，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import io.netty.util.AttributeKey; // Netty Channel 级键值存储，跨 pipeline Handler 共享会话字段
/**
 * Netty Channel 属性键集中定义：AuthFacade 写入 accountId/playerId，MessageIoDispatcher 断线时读取 unbind。
 */
public final class ChannelAttrs { // 工具类，集中 NettyDispatchSession 与 MessageIoDispatcher 用的 AttributeKey
    /** 登录成功后 AuthFacade 写入，channelInactive 前一直有效 */
    public static final AttributeKey<Long> ACCOUNT_ID = AttributeKey.valueOf("accountId"); // NettyDispatchSession.accountId 读写键
    /** 选角成功后写入，作为 DispatchThreadModel dispatchKey 与 PlayerPushRegistry 键 */
    public static final AttributeKey<Long> PLAYER_ID = AttributeKey.valueOf("playerId"); // MessageIoDispatcher.channelInactive unbind 读取键
    private ChannelAttrs() { // 禁止实例化
        // 工具类仅暴露 static AttributeKey，无实例字段
    } // 编译单元结束
} // 编译单元结束
