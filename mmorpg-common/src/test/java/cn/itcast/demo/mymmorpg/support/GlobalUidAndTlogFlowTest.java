package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.tlog.KafkaTLogEventPublisher;
import cn.itcast.demo.mymmorpg.tlog.LoggingTLogEventPublisher;
import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合服 / 观测基础流程：GlobalUID 批量发号唯一；TLog 管道可切换日志/Kafka 模拟。
 */
public class GlobalUidAndTlogFlowTest {

    @Test
    public void snowflakeBatchUniqueAcrossDatacenters() {
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(2, 1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 200; i++) {
            assertThat(ids.add(a.nextId())).isTrue();
            assertThat(ids.add(b.nextId())).isTrue();
        }
        assertThat(ids).hasSize(400);

        GlobalUidGenerator facade = new GlobalUidGenerator(3, 2);
        long x = facade.nextId();
        long y = facade.nextId();
        assertThat(y).isGreaterThan(x);
    }

    @Test
    public void tlogPublishersEmitBattleAndShopEvents() throws Exception {
        List<String> sink = new ArrayList<>();
        TLogEventPublisher custom = (type, playerId, fields) ->
                sink.add(type + ":" + playerId + ":" + fields.getOrDefault("sku", ""));

        custom.emit("gacha_draw", 9L, Map.of("sku", "char-banner", "pity", 74));
        custom.emit("shop_purchase", 9L, Map.of("sku", "monthly_card"));
        assertThat(sink).containsExactly(
                "gacha_draw:9:char-banner",
                "shop_purchase:9:monthly_card");

        // 默认实现可构造（不依赖 Spring）
        new LoggingTLogEventPublisher().emit("ping", 0L, Map.of("ok", true));
        KafkaTLogEventPublisher kafka = new KafkaTLogEventPublisher("mmorpg.tlog.test");
        kafka.emit("battle_end", 1L, Map.of("result", 1));
        Thread.sleep(50); // 等待异步线程打日志
    }
}
