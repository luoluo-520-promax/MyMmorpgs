/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/GatewayObserveProperties.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：绑定 game.gateway.observe.* 配置，控制 HTTP/WS 观测日志行为。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "game.gateway.observe")
public class GatewayObserveProperties {

    /** 观测功能总开关，false 时 HTTP 过滤器与 WS 装饰器均不记录 GW_* 日志 */
    private boolean enabled = true;

    /** HttpResponseObserveGlobalFilter 开关，可单独关闭 HTTP 延迟/字节统计 */
    private boolean httpEnabled = true;

    /** ObservabilityWebSocketService 开关，可单独关闭 WS 收发统计 */
    private boolean wsEnabled = true;

    /**
     * 日志采样率 [0.0, 1.0]：1.0 全量记录，0.1 约 10% 请求打日志，用于生产降采样。
     */
    private double sampleRate = 1.0d;

    /**
     * 观测日志级别：INFO 输出精简字段；DEBUG 额外输出 routeUri 等调试信息。
     * 值为 "DEBUG" 时走 log.debug，否则走 log.info。
     */
    private String logLevel = "INFO";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public void setHttpEnabled(boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }

    public boolean isWsEnabled() {
        return wsEnabled;
    }

    public void setWsEnabled(boolean wsEnabled) {
        this.wsEnabled = wsEnabled;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
