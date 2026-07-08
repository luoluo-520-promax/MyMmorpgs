/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/GatewayRateLimitProperties.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：绑定 game.gateway.rate-limit.* 配置，供 RateLimitGlobalFilter 读取。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(prefix = "game.gateway.rate-limit")
public class GatewayRateLimitProperties {

    /** 限流过滤器总开关，false 时跳过 Redis INCR 逻辑 */
    private boolean enabled = true;

    /** 全网关每秒最大请求数，对应 Redis 键 gateway:rl:total:{second}，默认 2000 */
    private int totalPerSecond = 2000;

    /** 单用户（accountId 或 IP）每秒最大请求数，默认 30；设为 0 表示不限制用户维度 */
    private int userPerSecond = 30;

    /** 不计入限流的路径，默认 /actuator/** 避免健康检查触发 429 */
    private List<String> whitelist = new ArrayList<>(List.of("/actuator/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTotalPerSecond() {
        return totalPerSecond;
    }

    public void setTotalPerSecond(int totalPerSecond) {
        this.totalPerSecond = totalPerSecond;
    }

    public int getUserPerSecond() {
        return userPerSecond;
    }

    public void setUserPerSecond(int userPerSecond) {
        this.userPerSecond = userPerSecond;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
