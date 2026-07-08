/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/FightServerLayer.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 FightServerLayer，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.net.ServerLayer; // 进程角色 init 契约
import org.slf4j.Logger; // SLF4J 日志接口
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
/**
 * 战斗服（server.type=FIGHT）：接收 GAME 服 RpcForwardClientMessage，本地执行 BattleMessage Facade 并回包。
 */
public class FightServerLayer implements ServerLayer { // server.type=FIGHT 时 ServerStartup 注册
    private static final Logger log = LoggerFactory.getLogger(FightServerLayer.class); // 记录战斗服 init 日志
    @Override // 实现接口/父类方法
    public void init() { // ServerStartup.serverLayer Bean 创建后立即调用
        log.info("FightServerLayer init"); // 战斗演算管线、技能/Buff、RpcForward 处理等扩展点占位
    } // 编译单元结束
} // 编译单元结束
