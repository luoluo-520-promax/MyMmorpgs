package cn.itcast.demo.mymmorpg.payment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 配置中心驱动的支付网关路由：channel → gateway，无 if-else 渠道分支。
 */
public class PaymentGatewayRouter {

    private final Map<String, PaymentGateway> byChannel;
    private final Map<String, String> routeAliases;

    public PaymentGatewayRouter(List<PaymentGateway> gateways, Map<String, String> routes) {
        Map<String, PaymentGateway> map = new HashMap<>();
        if (gateways != null) {
            for (PaymentGateway g : gateways) {
                if (g == null || g.channel() == null || g.channel().isBlank()) {
                    continue;
                }
                map.put(g.channel().trim().toUpperCase(Locale.ROOT), g);
            }
        }
        this.byChannel = Collections.unmodifiableMap(map);
        Map<String, String> aliases = new HashMap<>();
        if (routes != null) {
            for (Map.Entry<String, String> e : routes.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                aliases.put(e.getKey().trim().toUpperCase(Locale.ROOT),
                        e.getValue().trim().toUpperCase(Locale.ROOT));
            }
        }
        this.routeAliases = Collections.unmodifiableMap(aliases);
    }

    public Optional<PaymentGateway> resolve(String channel) {
        if (channel == null || channel.isBlank()) {
            return Optional.empty();
        }
        String key = channel.trim().toUpperCase(Locale.ROOT);
        String mapped = routeAliases.getOrDefault(key, key);
        PaymentGateway gateway = byChannel.get(mapped);
        if (gateway == null || !gateway.available()) {
            return Optional.empty();
        }
        return Optional.of(gateway);
    }

    public Map<String, PaymentGateway> all() {
        return byChannel;
    }
}
