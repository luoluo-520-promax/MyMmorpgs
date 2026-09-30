package cn.itcast.demo.mymmorpg.payment;

import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 为每个 {@link PaymentChannelAdapter} 注册 {@link PaymentGateway}，
 * 并由 {@link PaymentGatewayRouter} 按配置中心别名动态路由。
 * <p>
 * 别名配置：{@code shop.payment.gateway-routes.WX_PAY=WECHAT}
 */
@Configuration
public class PaymentGatewayConfiguration {

    @Bean
    public PaymentGatewayRouter paymentGatewayRouter(List<PaymentChannelAdapter> adapters, Environment env) {
        List<PaymentGateway> gateways = new ArrayList<>();
        if (adapters != null) {
            for (PaymentChannelAdapter adapter : adapters) {
                gateways.add(new AdapterPaymentGateway(adapter));
            }
        }
        Map<String, String> routes = new HashMap<>();
        // 常见别名默认映射，可被配置覆盖
        routes.put("WX_PAY", "WECHAT");
        routes.put("WECHAT_PAY", "WECHAT");
        routes.put("ALI_PAY", "ALIPAY");
        routes.put("APPLE", "APP_STORE");
        routes.put("GOOGLE", "GOOGLE_PLAY");
        if (env != null) {
            for (String key : List.of("WX_PAY", "WECHAT_PAY", "ALI_PAY", "APPLE", "GOOGLE",
                    "WECHAT", "ALIPAY", "APP_STORE", "GOOGLE_PLAY")) {
                String v = env.getProperty("shop.payment.gateway-routes." + key);
                if (v != null && !v.isBlank()) {
                    routes.put(key, v.trim());
                }
            }
        }
        return new PaymentGatewayRouter(gateways, routes);
    }
}
