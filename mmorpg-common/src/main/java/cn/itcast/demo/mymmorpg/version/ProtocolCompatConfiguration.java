package cn.itcast.demo.mymmorpg.version;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 协议兼容表默认规则：P19 新增 MsgId 2600-2605 对旧客户端 SOFT 屏蔽。
 */
@Configuration
public class ProtocolCompatConfiguration {

    @Bean
    public ProtocolCompatMap protocolCompatMap() {
        ProtocolCompatMap map = new ProtocolCompatMap();
        map.registerRule(
                "proto-v1",
                10000L,
                ProtocolCompatMap.Policy.SOFT,
                Set.of(2600, 2601, 2602, 2603, 2604, 2605));
        map.registerRule(
                "proto-combat-v2",
                15000L,
                ProtocolCompatMap.Policy.HARD,
                Set.of());
        return map;
    }
}
