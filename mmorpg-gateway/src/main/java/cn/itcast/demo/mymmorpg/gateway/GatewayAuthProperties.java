/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/GatewayAuthProperties.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：绑定 game.gateway.auth.* 配置，供 AuthGlobalFilter 读取。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties; // 将 application.yml 中 game.gateway.auth 节点映射到本类字段

import java.util.ArrayList; // 默认可变列表，支持运行时通过 setter 替换整个白名单
import java.util.List;


@ConfigurationProperties(prefix = "game.gateway.auth") // yml 示例：game.gateway.auth.enabled=true
public class GatewayAuthProperties {

    /** 认证过滤器总开关，false 时 AuthGlobalFilter 直接放行所有请求 */
    private boolean enabled = true;

    /**
     * 免认证路径 Ant 模式列表，默认仅 /ws（WebSocket 握手）。
     * 可在 yml 中追加如 /api/auth/login、/actuator/** 等。
     */
    private List<String> whitelist = new ArrayList<>(List.of("/ws"));

    /** 供 AuthGlobalFilter 读取是否启用 Token 校验 */
    public boolean isEnabled() {
        return enabled;
    }

    /** Spring Boot 配置绑定 setter，对应 yml 中 game.gateway.auth.enabled */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回白名单路径模式列表，AuthGlobalFilter.isWhitelisted 使用 */
    public List<String> getWhitelist() {
        return whitelist;
    }

    /** 绑定 yml 中 game.gateway.auth.whitelist 数组 */
    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
