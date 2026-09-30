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
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryScRsp;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GachaServiceTest {

    private GachaService gachaService;
    private BagItemGrantPort bagGrant;
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

        bagGrant = mock(BagItemGrantPort.class);
        when(bagGrant.grantItemsIdempotent(anyLong(), anyString(), anyList())).thenReturn(BagRetCode.OK);
        ObjectProvider<BagItemGrantPort> bagProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bagGrant);
        ObjectProvider<GachaBagGrantClient> grantClient = mock(ObjectProvider.class);
        when(grantClient.getIfAvailable()).thenReturn(null);

        gachaService = new GachaService(config, new GachaDrawEngine(), redis, bagProvider, grantClient);
    }

    @Test
    public void getInfo_andDoGacha_ten() throws Exception {
        GetGachaInfoScRsp info = GetGachaInfoScRsp.parseFrom(
                gachaService.handleGetGachaInfo(9L,
                        cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq.getDefaultInstance())
                        .payload());
        assertThat(info.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(info.getBannersCount()).isGreaterThan(0);

        DoGachaScRsp draw = DoGachaScRsp.parseFrom(
                gachaService.handleDoGacha(9L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(10).build()).payload());
        assertThat(draw.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(draw.getItemsCount()).isEqualTo(10);
        assertThat(draw.getCeilingNum()).isEqualTo(10);

        GetGachaHistoryScRsp hist = GetGachaHistoryScRsp.parseFrom(
                gachaService.handleGetGachaHistory(9L, GetGachaHistoryCsReq.newBuilder()
                        .setLimit(20).build()).payload());
        assertThat(hist.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(hist.getEntriesCount()).isEqualTo(10);
    }

    @Test
    public void doGacha_rejectsInvalidTimes() throws Exception {
        DoGachaScRsp draw = DoGachaScRsp.parseFrom(
                gachaService.handleDoGacha(9L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(5).build()).payload());
        assertThat(draw.getRetcode()).isEqualTo(RetCode.GACHA_INVALID_TIMES);
    }

    @Test
    public void doGacha_failsWhenGrantUnavailable() throws Exception {
        GachaConfigService config = new GachaConfigService(
                new ObjectMapper(), new DefaultResourceLoader(), "config/gacha/Banners.json");
        config.reload();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        ObjectProvider<BagItemGrantPort> bagProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<GachaBagGrantClient> grantClient = mock(ObjectProvider.class);
        when(grantClient.getIfAvailable()).thenReturn(null);
        GachaService svc = new GachaService(config, new GachaDrawEngine(), redis, bagProvider, grantClient);

        DoGachaScRsp draw = DoGachaScRsp.parseFrom(
                svc.handleDoGacha(9L, DoGachaCsReq.newBuilder()
                        .setBannerType(2).setTimes(1).build()).payload());
        assertThat(draw.getRetcode()).isEqualTo(RetCode.INTERNAL_ERROR);
    }

    @Test
    public void exchangeCeiling_notReady() throws Exception {
        store.put("gacha:ceiling:9", "100,0");
        ExchangeGachaCeilingScRsp rsp = ExchangeGachaCeilingScRsp.parseFrom(
                gachaService.handleExchangeCeiling(9L, ExchangeGachaCeilingCsReq.getDefaultInstance()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.GACHA_CEILING_NOT_READY);
        assertThat(rsp.getCeilingClaimed()).isEqualTo(0);
    }

    @Test
    public void exchangeCeiling_success_grantsRewardAndHistory() throws Exception {
        store.put("gacha:ceiling:9", "300,0");
        ExchangeGachaCeilingScRsp rsp = ExchangeGachaCeilingScRsp.parseFrom(
                gachaService.handleExchangeCeiling(9L, ExchangeGachaCeilingCsReq.getDefaultInstance()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getItemId()).isEqualTo(11001);
        assertThat(rsp.getCeilingClaimed()).isEqualTo(300);
        assertThat(store.get("gacha:ceiling:9")).isEqualTo("300,300");
        verify(bagGrant).grantItemsIdempotent(eq(9L),
                argThat(k -> k != null && k.startsWith("gacha:ceiling:9:")), anyList());

        GetGachaHistoryScRsp hist = GetGachaHistoryScRsp.parseFrom(
                gachaService.handleGetGachaHistory(9L, GetGachaHistoryCsReq.newBuilder()
                        .setLimit(5).build()).payload());
        assertThat(hist.getEntriesCount()).isGreaterThanOrEqualTo(1);
        assertThat(hist.getEntries(0).getItemId()).isEqualTo(11001);
        assertThat(hist.getEntries(0).getRarity()).isEqualTo(5);
    }

    @Test
    public void exchangeCeiling_grantFail_keepsClaimed() throws Exception {
        store.put("gacha:ceiling:9", "300,0");
        when(bagGrant.grantItemsIdempotent(anyLong(), anyString(), anyList())).thenReturn(BagRetCode.BAG_FULL);
        ExchangeGachaCeilingScRsp rsp = ExchangeGachaCeilingScRsp.parseFrom(
                gachaService.handleExchangeCeiling(9L, ExchangeGachaCeilingCsReq.getDefaultInstance()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.INTERNAL_ERROR);
        assertThat(store.get("gacha:ceiling:9")).isEqualTo("300,0");
    }
}
