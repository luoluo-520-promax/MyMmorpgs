/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ServerStartup.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ServerStartup，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.net.CenterServerLayer; // server.type=CENTRE
import cn.itcast.demo.mymmorpg.net.FightServerLayer; // server.type=FIGHT
import cn.itcast.demo.mymmorpg.net.GameServerLayer; // server.type=GAME，BattleRpcForwarder 跨服
import cn.itcast.demo.mymmorpg.net.GateServerLayer; // server.type=GATE
import org.springframework.beans.factory.annotation.Value; // server.type 整数码 -> ServerType 枚举
import org.springframework.context.annotation.Bean; // 注册 serverLayer Bean，BaseServer @DependsOn
import org.springframework.context.annotation.Configuration; // 配置类，集中声明 Bean
import org.springframework.context.annotation.DependsOn; // 等待 gameContext 设置 GameContext.serverType
/**
 * 根据 server.type 注册对应 ServerLayer 并立即 init()，决定 BattleRpcForwarder 是否生效等进程角色行为。
 */
@Configuration // Spring 配置类，声明 serverLayer Bean
public class ServerStartup { // 进程角色选择：GAME/CENTRE/FIGHT/GATE
    @Bean("serverLayer") // Bean 名 serverLayer，BaseServer @DependsOn 等待其 init
    @DependsOn("gameContext") // GameContext 先写入静态 serverType
    public ServerLayer serverLayer(@Value("${server.type:1}") int typeCode) { // application.properties server.type
        ServerType serverType = ServerType.fromCode(typeCode); // 1=GAME 2=CENTRE 等
        ServerLayer container = switch (serverType) { // 按角色实例化 ServerLayer
            case GAME -> new GameServerLayer(); // 跨服 BattleRpcForwarder、onCenterServerConnected 拉 FIGHT
            case CENTRE -> new CenterServerLayer(); // 节点注册中心
            case FIGHT -> new FightServerLayer(); // 战斗演算，接收 RpcForwardClientMessage
            case GATE -> new GateServerLayer(); // 接入网关占位
        }; // 编译单元结束（含分号）
        container.init(); // 同步 init，再注册到 Spring 容器
        return container; // 注入 Spring，GameContext 可 getBean(ServerLayer.class)
    } // 编译单元结束
} // 编译单元结束
