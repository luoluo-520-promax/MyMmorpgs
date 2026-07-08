/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/GameServerLayer.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 GameServerLayer，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.rpc.CrossMessageUtil; // 向中心服发 RPC 请求
import cn.itcast.demo.mymmorpg.rpc.Rpc_G2C_FetchFightServerNodes; // GAME->CENTRE 拉取战斗节点列表
import cn.itcast.demo.mymmorpg.net.GameContext; // 静态 Spring Bean 查找与 serverType
import cn.itcast.demo.mymmorpg.net.ServerLayer; // 进程角色 init/onCenterServerConnected 契约
import org.slf4j.Logger; // SLF4J 日志接口
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
/**
 * 游戏逻辑服（server.type=GAME）：启用 BattleRpcForwarder 跨服转发，中心服连接后注册 FIGHT 节点到 RpcClientRouter。
 */
public class GameServerLayer implements ServerLayer { // server.type=GAME 时 ServerStartup 注册，BattleRpcForwarder 仅 GAME 生效
    private static final Logger log = LoggerFactory.getLogger(GameServerLayer.class); // 记录 GAME 服 init 日志
    @Override // 实现接口/父类方法
    public void init() { // ServerStartup.serverLayer Bean 创建后立即调用
        log.info("GameServerLayer init"); // GAME 服 Netty/WebSocket/dispatch 已由 Spring 自动装配，此处为扩展点
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void onCenterServerConnected() { // 与 CENTRE RPC 连接建立后回调
        // 中心服就绪后拉取 FIGHT 列表，BattleRpcForwarder.pickFightNode 依赖 RpcClientRouter 中的节点
        GameContext.getBean(CrossMessageUtil.class).requestToCenter(new Rpc_G2C_FetchFightServerNodes()); // GAME->CENTRE RPC 拉战斗节点
    } // 编译单元结束
} // 编译单元结束
