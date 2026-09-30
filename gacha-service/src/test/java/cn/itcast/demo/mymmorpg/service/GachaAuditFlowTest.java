package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.gacha.GachaConfigService;
import cn.itcast.demo.mymmorpg.gacha.GachaDrawEngine;
import cn.itcast.demo.mymmorpg.port.BagItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaScRsp;
import cn.itcast.demo.mymmorpg.client.GachaBagGrantClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pity V2 审计落库流程：抽卡成功后审计流可导出。
 */
public class GachaAuditFlowTest {

    private GachaService gachaService;
    private GachaAuditLogService auditLogService;
    private final Map<String, String> store = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        lists.clear();
        GachaConfigService config = new GachaConfigService(
                new ObjectMapper(), new DefaultResourceLoader(), "config/gacha/Banners.json");
        config.reload();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForList()).thenReturn(listOps);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(java.time.Duration.class));
        when(listOps.leftPush(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            lists.computeIfAbsent(key, k -> new ArrayList<>()).add(0, val);
            return 1L;
        });
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            long end = ((Number) inv.getArgument(2)).longValue();
            List<String> list = lists.get(key);
            if (list != null && list.size() > end + 1) {
                lists.put(key, new ArrayList<>(list.subList(0, (int) end + 1)));
            }
            return null;
        }).when(listOps).trim(anyString(), anyLong(), anyLong());
        when(listOps.range(anyString(), anyLong(), anyLong())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long end = ((Number) inv.getArgument(2)).longValue();
            List<String> list = lists.getOrDefault(key, List.of());
            int to = (int) Math.min(end, list.size() - 1L);
            if (list.isEmpty() || to < 0) {
                return List.of();
            }
            return new ArrayList<>(list.subList(0, to + 1));
        });
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);

        BagItemGrantPort bagGrant = mock(BagItemGrantPort.class);
        when(bagGrant.grantItemsIdempotent(anyLong(), anyString(), anyList())).thenReturn(BagRetCode.OK);
        ObjectProvider<BagItemGrantPort> bagProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bagGrant);
        ObjectProvider<GachaBagGrantClient> grantClient = mock(ObjectProvider.class);
        when(grantClient.getIfAvailable()).thenReturn(null);

        auditLogService = new GachaAuditLogService(null);
        ObjectProvider<GachaAuditLogService> auditProvider = mock(ObjectProvider.class);
        when(auditProvider.getIfAvailable()).thenReturn(auditLogService);

        gachaService = new GachaService(config, new GachaDrawEngine(), redis, bagProvider, grantClient, auditProvider);
    }

    @Test
    public void doGacha_writesPityV2AuditExport() throws Exception {
        DoGachaScRsp draw = DoGachaScRsp.parseFrom(
                gachaService.handleDoGacha(42L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(10).build()).payload());
        assertThat(draw.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(draw.getItemsCount()).isEqualTo(10);

        Map<String, Object> export = auditLogService.auditExport(42L, 20);
        assertThat(export.get("ok")).isEqualTo(true);
        assertThat(export.get("algo")).isEqualTo("PITY_V2");
        assertThat(((Number) export.get("count")).intValue()).isEqualTo(10);

        Map<String, Object> summary = gachaService.auditSummary(42L, 20);
        assertThat(summary.get("ok")).isEqualTo(true);
        assertThat(summary.get("algo")).isEqualTo("PITY_V2");
        assertThat(summary.get("pityAuditExport")).isNotNull();
    }
}
