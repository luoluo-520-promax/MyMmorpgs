package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
public class BagPolicyConfiguration {

    @Bean
    @ConditionalOnMissingBean(ItemPolicy.class)
    ItemPolicy itemPolicy() {
        return new ItemPolicy() {
            @Override
            public int parseExpReward(ItemConfig config) {
                return parseIntField(config == null ? null : config.getEffectParams(), "exp");
            }

            @Override
            public int parseHpRestore(ItemConfig config) {
                return parseIntField(config == null ? null : config.getEffectParams(), "hp");
            }

            @Override
            public int parseMpRestore(ItemConfig config) {
                return parseIntField(config == null ? null : config.getEffectParams(), "mp");
            }

            private int parseIntField(String json, String field) {
                if (json == null || json.isBlank()) {
                    return 0;
                }
                Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
                var matcher = pattern.matcher(json);
                return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
            }
        };
    }
}
