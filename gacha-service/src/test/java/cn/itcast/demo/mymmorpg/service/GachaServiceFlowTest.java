package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GachaBagGrantClient;
import cn.itcast.demo.mymmorpg.gacha.GachaConfigService;
import cn.itcast.demo.mymmorpg.gacha.GachaDrawEngine;
import cn.itcast.demo.mymmorpg.port.BagItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoScRsp;
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
 * 抽卡业务流程：卡池查询 → 单抽/十连 → 保底进度 → 历史 → 概率公示 → 审计 → 天井兑换。
 */
public class GachaServiceFlowTest {

    private GachaService gachaService;
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

        gachaService = new GachaService(config, new GachaDrawEngine(), redis, bagProvider, grantClient);
    }

    @Test
    public void fullDrawFlow_infoDrawHistoryPityAuditProbability() throws Exception {
        GetGachaInfoScRsp info = GetGachaInfoScRsp.parseFrom(
                gachaService.handleGetGachaInfo(42L, GetGachaInfoCsReq.getDefaultInstance()).payload());
        assertThat(info.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(info.getBannersCount()).isGreaterThan(0);

        DoGachaScRsp single = DoGachaScRsp.parseFrom(
                gachaService.handleDoGacha(42L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(1).build()).payload());
        assertThat(single.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(single.getItemsCount()).isEqualTo(1);
        assertThat(single.getCeilingNum()).isEqualTo(1);

        DoGachaScRsp ten = DoGachaScRsp.parseFrom(
                gachaService.handleDoGacha(42L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(10).build()).payload());
        assertThat(ten.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(ten.getItemsCount()).isEqualTo(10);
        assertThat(ten.getCeilingNum()).isEqualTo(11);

        GetGachaInfoScRsp after = GetGachaInfoScRsp.parseFrom(
                gachaService.handleGetGachaInfo(42L, GetGachaInfoCsReq.getDefaultInstance()).payload());
        assertThat(after.getCeilingNum()).isEqualTo(11);
        boolean pityAdvanced = after.getBannersList().stream()
                .anyMatch(b -> b.getBannerType() == 2 && (b.getPity5() > 0 || b.getPity4() > 0
                        || ten.getPity5() == 0 || ten.getPity4() == 0));
        assertThat(pityAdvanced).isTrue();

        Map<String, Object> audit = gachaService.auditSummary(42L, 20);
        assertThat(audit.get("ok")).isEqualTo(true);
        assertThat(audit.get("ceilingNum")).isEqualTo(11);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) audit.get("history");
        assertThat(history).hasSize(11);

        Map<String, Object> prob = gachaService.probabilityConfig();
        assertThat(prob.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> rates = (Map<String, Object>) prob.get("rates");
        assertThat(rates.get("softPity5")).isEqualTo(90);
        assertThat(rates.get("softPity4")).isEqualTo(10);
        assertThat(rates.get("upWinPercent")).isEqualTo(50);
        @SuppressWarnings("unchecked")
        List<?> banners = (List<?>) prob.get("banners");
        assertThat(banners).isNotEmpty();
    }

    @Test
    public void ceilingExchangeFlow_afterThreshold() throws Exception {
        store.put("gacha:ceiling:7", "300,0");
        ExchangeGachaCeilingScRsp rsp = ExchangeGachaCeilingScRsp.parseFrom(
                gachaService.handleExchangeCeiling(7L, ExchangeGachaCeilingCsReq.getDefaultInstance()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getItemId()).isEqualTo(11001);
        assertThat(rsp.getCeilingClaimed()).isEqualTo(300);

        Map<String, Object> audit = gachaService.auditSummary(7L, 5);
        assertThat(audit.get("ceilingClaimed")).isEqualTo(300);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) audit.get("history");
        assertThat(history.get(0).get("itemId")).isEqualTo(11001);
    }

    @Test
    public void reloadConfig_keepsProbabilityReadable() {
        gachaService.reloadConfig();
        Map<String, Object> prob = gachaService.probabilityConfig();
        assertThat(prob.get("ok")).isEqualTo(true);
    }

    @Test
    public void getGachaHistoryCsReq_limitCapped() throws Exception {
        for (int i = 0; i < 5; i++) {
            DoGachaScRsp draw = DoGachaScRsp.parseFrom(
                    gachaService.handleDoGacha(8L, DoGachaCsReq.newBuilder()
                            .setBannerType(2).setTimes(1).build()).payload());
            assertThat(draw.getRetcode()).isEqualTo(RetCode.OK);
        }
        var hist = cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryScRsp.parseFrom(
                gachaService.handleGetGachaHistory(8L, GetGachaHistoryCsReq.newBuilder()
                        .setLimit(3).build()).payload());
        assertThat(hist.getEntriesCount()).isEqualTo(3);
    }
}
