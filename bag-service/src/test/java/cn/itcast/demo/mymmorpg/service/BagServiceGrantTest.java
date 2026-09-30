package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.GrantIdempotency;
import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerBagItem;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.port.SkinUnlockPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import cn.itcast.demo.mymmorpg.repository.GrantIdempotencyRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 经济底座：背包幂等发奖（抽卡/商城/邮件共用）。
 */
public class BagServiceGrantTest {

    private PlayerRepository playerRepository;
    private PlayerBagItemRepository bagItemRepository;
    private ConfigQueryService configQueryService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private GrantIdempotencyRepository grantIdempotencyRepository;
    private BagService bagService;
    private final List<PlayerBagItem> bagRows = new ArrayList<>();
    private final AtomicInteger slotSeq = new AtomicInteger(0);

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        bagRows.clear();
        slotSeq.set(0);
        playerRepository = mock(PlayerRepository.class);
        bagItemRepository = mock(PlayerBagItemRepository.class);
        configQueryService = mock(ConfigQueryService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        grantIdempotencyRepository = mock(GrantIdempotencyRepository.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(playerRepository.findById(9L)).thenReturn(Optional.of(new Player()));
        when(bagItemRepository.findByPlayerIdOrderBySlotIndexAsc(anyLong())).thenAnswer(inv -> new ArrayList<>(bagRows));
        when(bagItemRepository.countByPlayerId(anyLong())).thenAnswer(inv -> bagRows.size());
        when(bagItemRepository.save(any(PlayerBagItem.class))).thenAnswer(inv -> {
            PlayerBagItem row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId((long) (slotSeq.incrementAndGet()));
                bagRows.add(row);
            }
            return row;
        });
        ItemConfig cfg = new ItemConfig();
        cfg.setId(1001);
        cfg.setStackLimit(99);
        when(configQueryService.findItemById(1001)).thenReturn(cfg);

        ObjectProvider<EconomyLedgerService> ledger = mock(ObjectProvider.class);
        when(ledger.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<EquipRandomizer> equipRandomizer = mock(ObjectProvider.class);
        when(equipRandomizer.getIfAvailable()).thenReturn(null);

        bagService = new BagService(
                playerRepository,
                bagItemRepository,
                configQueryService,
                mock(ItemPolicy.class),
                mock(ItemEventPublisher.class),
                mock(PlayerProgressPort.class),
                mock(PlayerCachePort.class),
                redis,
                new ObjectMapper(),
                mock(PlayerDataLoadPort.class),
                mock(SkinUnlockPort.class),
                grantIdempotencyRepository,
                ledger,
                equipRandomizer,
                false);
    }

    @Test
    public void grant_firstTime_persistsIdempotencyAndItem() {
        when(grantIdempotencyRepository.existsByPlayerIdAndIdempotencyKey(9L, "gacha:1")).thenReturn(false);

        int rc = bagService.grantItemsIdempotent(9L, "gacha:1",
                List.of(ItemReward.newBuilder().setItemId(1001).setCount(2).build()));

        assertThat(rc).isEqualTo(BagRetCode.OK);
        assertThat(bagRows).hasSize(1);
        assertThat(bagRows.get(0).getCount()).isEqualTo(2);
        verify(grantIdempotencyRepository).saveAndFlush(any(GrantIdempotency.class));
        verify(redis).delete("bag:info:9");
    }

    @Test
    public void grant_dbIdempotentHit_skipsAdd() {
        when(grantIdempotencyRepository.existsByPlayerIdAndIdempotencyKey(9L, "gacha:1")).thenReturn(true);

        int rc = bagService.grantItemsIdempotent(9L, "gacha:1",
                List.of(ItemReward.newBuilder().setItemId(1001).setCount(1).build()));

        assertThat(rc).isEqualTo(BagRetCode.OK);
        assertThat(bagRows).isEmpty();
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(grantIdempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    public void grant_redisIdempotentHit_skipsAdd() {
        when(grantIdempotencyRepository.existsByPlayerIdAndIdempotencyKey(9L, "gacha:1")).thenReturn(false);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        int rc = bagService.grantItemsIdempotent(9L, "gacha:1",
                List.of(ItemReward.newBuilder().setItemId(1001).setCount(1).build()));

        assertThat(rc).isEqualTo(BagRetCode.OK);
        assertThat(bagRows).isEmpty();
        verify(grantIdempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    public void grant_bagFull_returnsBagFullAndClearsRedisKey() {
        when(grantIdempotencyRepository.existsByPlayerIdAndIdempotencyKey(9L, "shop:1")).thenReturn(false);
        when(bagItemRepository.countByPlayerId(9L)).thenReturn(BagService.DEFAULT_CAPACITY);

        int rc = bagService.grantItemsIdempotent(9L, "shop:1",
                List.of(ItemReward.newBuilder().setItemId(1001).setCount(1).build()));

        assertThat(rc).isEqualTo(BagRetCode.BAG_FULL);
        verify(redis).delete(eq("bag:grant:idem:9:shop:1"));
    }

    @Test
    public void grantItemsForMail_usesMailIdempotencyKey() {
        when(grantIdempotencyRepository.existsByPlayerIdAndIdempotencyKey(9L, "mail:100")).thenReturn(false);

        int rc = bagService.grantItemsForMail(9L, 100L,
                List.of(ItemReward.newBuilder().setItemId(1001).setCount(1).build()));

        assertThat(rc).isEqualTo(BagRetCode.OK);
        verify(valueOps).setIfAbsent(eq("bag:grant:idem:9:mail:100"), eq("1"), any(Duration.class));
    }

    @Test
    public void grant_playerNotFound() {
        when(playerRepository.findById(8L)).thenReturn(Optional.empty());
        int rc = bagService.grantItemsIdempotent(8L, "k", List.of());
        assertThat(rc).isEqualTo(RetCode.PLAYER_NOT_FOUND);
    }
}
