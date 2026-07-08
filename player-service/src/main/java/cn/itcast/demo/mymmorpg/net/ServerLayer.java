/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ServerLayer.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ServerLayer，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
/**
 * 按 server.type 区分的进程角色初始化契约：GAME/CENTRE/FIGHT/GATE 各自实现 init 与中心服连接回调。
 * <p>ServerStartup 根据 server.type 注册对应 Bean，BaseServer @DependsOn serverLayer。</p>
 */
public interface ServerLayer { // ServerStartup 按 server.type 实例化 Game/Centre/Fight/Gate 实现
    /** 进程启动时调用一次，加载角色专属资源（战斗服管线、网关路由等） */
    void init(); // ServerStartup.serverLayer Bean 创建后立即 sync 调用
    /**
     * 与中心服 RPC 连接建立后回调：GAME 服在此拉取 FIGHT 节点列表供 BattleRpcForwarder 使用。
     */
    default void onCenterServerConnected() { // 默认空实现，GameServerLayer override 拉 FIGHT 节点
        // CENTRE/FIGHT/GATE 无需连接中心服回调
    } // 编译单元结束
} // 编译单元结束
