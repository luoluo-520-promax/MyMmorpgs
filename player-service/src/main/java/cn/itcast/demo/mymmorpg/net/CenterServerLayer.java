/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/CenterServerLayer.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 CenterServerLayer，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.net.ServerLayer; // 进程角色 init 契约
import org.slf4j.Logger; // SLF4J 日志接口
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
/**
 * 中心服（server.type=CENTRE）：维护 FIGHT/GAME 节点注册表，响应 Rpc_G2C_FetchFightServerNodes 等跨服 RPC。
 */
public class CenterServerLayer implements ServerLayer { // server.type=CENTRE 时 ServerStartup 注册
    private static final Logger log = LoggerFactory.getLogger(CenterServerLayer.class); // 记录中心服 init 日志
    @Override // 实现接口/父类方法
    public void init() { // ServerStartup.serverLayer Bean 创建后立即调用
        log.info("CenterServerLayer init"); // 中心服节点注册表、RPC 路由等扩展点占位
    } // 编译单元结束
} // 编译单元结束
