/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/WebSocketConfig.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：注册 Spring WebSocket 端点 /ws/player，挂载二进制 Protobuf 游戏协议处理器。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import cn.itcast.demo.mymmorpg.net.PlayerBinaryWebSocketHandler; // 解析二进制帧、路由到 GameMessageFactory 各 Facade
import org.springframework.context.annotation.Configuration; // WebSocket 路由配置类
import org.springframework.web.socket.config.annotation.EnableWebSocket; // 启用 Servlet 容器 WebSocket 支持（与 Netty Socket 并存）
import org.springframework.web.socket.config.annotation.WebSocketConfigurer; // 实现 registerWebSocketHandlers 注册路径
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry; // addHandler 绑定 URL 与 Handler

@Configuration // 由组件扫描加载，在 Servlet 容器启动后生效
@EnableWebSocket // 导入 WebSocketHandlerRegistry Bean，否则 registerWebSocketHandlers 不会被调用

public class WebSocketConfig implements WebSocketConfigurer { // WebSocketConfig 类型定义
    /** 玩家二进制 WebSocket 处理器：网关 /ws 转发后可直连本端点做 HTTP 联调 */

    private final PlayerBinaryWebSocketHandler playerBinaryWebSocketHandler; // WebSocketConfig 字段
    /**
     * 构造器注入 Handler，保证注册时 Handler 已完成依赖注入（Session、LoginAdmission 等）。
     */

    public WebSocketConfig(PlayerBinaryWebSocketHandler playerBinaryWebSocketHandler) { // WebSocket 二进制帧入口 Handler
        this.playerBinaryWebSocketHandler = playerBinaryWebSocketHandler; // 构造器注入 playerBinaryWebSocketHandler
    } // WebSocketConfig 类体结束
    /**
     * 将 /ws/player 映射到二进制处理器；开发阶段允许任意 Origin，生产应由网关统一鉴权并限制 Origin。
     */

    @Override // 实现接口/父类方法
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { // WebSocket 二进制帧入口 Handler
        registry.addHandler(playerBinaryWebSocketHandler, "/ws/player") // 与网关路由、接口文档路径一致
                .setAllowedOrigins("*"); // 本地 H5/Unity WebGL 联调免 CORS；生产建议改为具体域名列表
    } // 编译单元结束
} // 编译单元结束
