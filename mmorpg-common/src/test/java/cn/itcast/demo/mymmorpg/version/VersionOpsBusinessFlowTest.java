package cn.itcast.demo.mymmorpg.version;

import cn.itcast.demo.mymmorpg.activity.ActivitySnapshotManager;
import cn.itcast.demo.mymmorpg.activity.PostMortemProcessor;
import cn.itcast.demo.mymmorpg.cache.CacheWarmUpService;
import cn.itcast.demo.mymmorpg.entity.ActivitySnapshot;
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.repository.ActivitySnapshotRepository;
import cn.itcast.demo.mymmorpg.service.ConfigQueryService;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import cn.itcast.demo.mymmorpg.world.battle.HistoricalConfigSnapshotStore;
import cn.itcast.demo.mymmorpg.world.battle.PatchBaselineContext;
import cn.itcast.demo.mymmorpg.world.content.GrayConditions;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import cn.itcast.demo.mymmorpg.world.content.SceneConfigResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版本运营七项能力全流程：预热 → 门控 → 灰度 → 快照回滚 → 缓存预热 → 善后 → 回放基线。
 */
public class VersionOpsBusinessFlowTest {

    private OpenWorldConfigPatchService configPatch;
    private ProtocolCompatMap compatMap;
    private VersionGateKeeper gateKeeper;
    private HashOperations<String, Object, Object> hashOps;
    private Map<String, String> redisKv;
    private Map<String, Map<Object, Object>> redisHashes;
    private List<ActivitySnapshot> savedSnapshots;
    private ActivitySnapshotManager snapshotManager;
    private CacheWarmUpService warmUpService;
    private PostMortemProcessor postMortem;
    private BattleReplayService replayService;
    private HistoricalConfigSnapshotStore historicalStore;
    private AtomicLong snapId;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        configPatch = new OpenWorldConfigPatchService();
        compatMap = new ProtocolCompatMap();
        compatMap.setMinSupportedVersion(10000L);
        compatMap.setCurrentProtocolHash("proto-v2-current");
        compatMap.registerRule("proto-v1", 10000L, ProtocolCompatMap.Policy.SOFT,
                Set.of(2600, 2601, 2602, 2603, 2604, 2605));
        compatMap.registerRule("proto-combat-v2", 15000L, ProtocolCompatMap.Policy.HARD, Set.of());

        redisKv = new ConcurrentHashMap<>();
        redisHashes = new ConcurrentHashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.hasKey(anyString())).thenAnswer(inv -> redisKv.containsKey(inv.getArgument(0)));
        doAnswer(inv -> {
            redisKv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any());
        when(hashOps.entries(anyString())).thenAnswer(inv ->
                new HashMap<>(redisHashes.getOrDefault(inv.getArgument(0), Map.of())));
        doAnswer(inv -> {
            redisHashes.computeIfAbsent(inv.getArgument(0), k -> new ConcurrentHashMap<>())
                    .put(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(hashOps).put(anyString(), any(), any());
        when(hashOps.delete(anyString(), any())).thenAnswer(inv -> {
            Map<Object, Object> h = redisHashes.get(inv.getArgument(0));
            if (h != null) {
                h.remove(inv.getArgument(1));
            }
            return 1L;
        });
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<Object, Object> m = (Map<Object, Object>) inv.getArgument(1);
            redisHashes.computeIfAbsent(inv.getArgument(0), k -> new ConcurrentHashMap<>()).putAll(m);
            return null;
        }).when(hashOps).putAll(anyString(), any());
        when(redis.keys(eq("activity:token:*"))).thenAnswer(inv ->
                redisHashes.keySet().stream()
                        .filter(k -> k.startsWith("activity:token:"))
                        .collect(Collectors.toSet()));
        when(redis.delete(anyString())).thenAnswer(inv -> {
            redisHashes.remove(inv.getArgument(0));
            redisKv.remove(inv.getArgument(0));
            return Boolean.TRUE;
        });

        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);

        gateKeeper = new VersionGateKeeper(compatMap, redisProvider);
        gateKeeper.bindOptionalVersion(12000L);

        snapId = new AtomicLong(1);
        savedSnapshots = new ArrayList<>();
        ActivitySnapshotRepository snapshotRepo = mock(ActivitySnapshotRepository.class);
        when(snapshotRepo.save(any(ActivitySnapshot.class))).thenAnswer(inv -> {
            ActivitySnapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(snapId.getAndIncrement());
            }
            savedSnapshots.add(s);
            return s;
        });
        when(snapshotRepo.findByPlayerIdAndVersionCodeOrderByCreatedAtMsDesc(anyLong(), anyString()))
                .thenAnswer(inv -> {
                    long pid = inv.getArgument(0);
                    String vc = inv.getArgument(1);
                    return savedSnapshots.stream()
                            .filter(s -> s.getPlayerId() == pid && vc.equals(s.getVersionCode()))
                            .sorted((a, b) -> Long.compare(b.getCreatedAtMs(), a.getCreatedAtMs()))
                            .toList();
                });

        snapshotManager = new ActivitySnapshotManager(snapshotRepo, redisProvider, new ObjectMapper());

        ConfigQueryService configQuery = mock(ConfigQueryService.class);
        when(configQuery.listAllMonsters()).thenReturn(List.of(new MonsterConfig()));
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        when(noRedis.getIfAvailable()).thenReturn(null);
        warmUpService = new CacheWarmUpService(configQuery, noRedis, new ObjectMapper());

        postMortem = new PostMortemProcessor(redisProvider);
        historicalStore = new HistoricalConfigSnapshotStore();
        replayService = new BattleReplayService(historicalStore);
    }

    @Test
    public void fullVersionLifecycle_sevenCapabilities() {
        String versionCode = "2.6.0";
        long now = System.currentTimeMillis();

        // 1) 倒计时预热
        gateKeeper.markPreheat(versionCode);
        assertThat(gateKeeper.isPreheatActive(versionCode)).isTrue();
        assertThat(redisKv).containsKey("version:preheat:" + versionCode);

        // 2) VersionGateKeeper：SOFT / HARD
        VersionGateKeeper.GateResult soft = gateKeeper.evaluateLogin(11000, "proto-v1", 42L);
        assertThat(soft.hardReject()).isFalse();
        assertThat(soft.blockedMsgIds()).contains(2600, 2605);
        assertThat(gateKeeper.blockedMsgIdsForAccount(42L)).contains(2600);

        VersionGateKeeper.GateResult hardOld = gateKeeper.evaluateLogin(9000, "proto-v1", 1L);
        assertThat(hardOld.hardReject()).isTrue();
        assertThat(hardOld.retCode()).isEqualTo(RetCode.CLIENT_TOO_OLD);

        VersionGateKeeper.GateResult hardCombat = gateKeeper.evaluateLogin(16000, "proto-combat-v2", 2L);
        assertThat(hardCombat.hardReject()).isTrue();

        VersionGateKeeper.GateResult currentOk = gateKeeper.evaluateLogin(13000, "proto-v2-current", 3L);
        assertThat(currentOk.hardReject()).isFalse();
        assertThat(currentOk.retCode()).isEqualTo(RetCode.OK);

        // 3) 灰度发布 + 熔断 + 全量发布
        configPatch.upsert("abyss-cell", "ABYSS", Map.of("floorHp", 1000), "baseline");
        configPatch.stagePatch("abyss-cell", "ABYSS", Map.of("floorHp", 2500), "deadbeef01", "gray-p20");
        Map<String, Object> grayPub = configPatch.publishStaging(
                new GrayConditions("p20-siege", 1001L, 10, 0, null, 100), now);
        assertThat(grayPub.get("ok")).isEqualTo(true);
        assertThat(grayPub.get("gray")).isEqualTo(true);

        SceneConfigResolver resolver = new SceneConfigResolver(configPatch);
        Map<String, Object> grayCell = resolver.resolveCell(10L, 100L, 1001, false, "abyss-cell");
        assertThat(grayCell.get("fromGray")).isEqualTo(true);
        assertThat(((Map<?, ?>) grayCell.get("payload")).get("floorHp")).isEqualTo(2500);

        Map<String, Object> baseCell = resolver.resolveCell(11L, 101L, 1002, false, "abyss-cell");
        assertThat(baseCell.get("fromGray")).isNull();
        assertThat(((Map<?, ?>) baseCell.get("payload")).get("floorHp")).isEqualTo(1000);

        assertThat(configPatch.rollbackGray("p20-siege").get("ok")).isEqualTo(true);
        assertThat(resolver.resolveCell(10L, 100L, 1001, false, "abyss-cell").get("fromGray")).isNull();

        configPatch.stagePatch("abyss-cell", "ABYSS", Map.of("floorHp", 3000), "deadbeef02", "live-p20");
        assertThat(configPatch.publishStaging(now + 1).get("ok")).isEqualTo(true);
        assertThat(configPatch.getCell("abyss-cell").get("payload")).isEqualTo(Map.of("floorHp", 3000));

        // 4) 活动快照 + 溢出回退
        long playerId = 90001L;
        snapshotManager.trackActivePlayer(playerId);
        redisHashes.put("player:act:" + playerId, new ConcurrentHashMap<>(Map.of("12", "{\"floor\":12}")));
        redisHashes.put("activity:token:" + playerId, new ConcurrentHashMap<>(Map.of("token", "100")));

        Map<String, Object> snapResult = snapshotManager.snapshotActivePlayers(versionCode);
        assertThat(snapResult.get("ok")).isEqualTo(true);
        assertThat(snapResult.get("saved")).isEqualTo(1);
        assertThat(savedSnapshots).isNotEmpty();

        redisHashes.put("activity:token:" + playerId, new ConcurrentHashMap<>(Map.of("token", "1000")));
        Map<String, Object> rollback = snapshotManager.rollbackPlayerData(playerId, versionCode);
        assertThat(rollback.get("ok")).isEqualTo(true);
        assertThat(rollback.get("compensationMailSent")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adjustments = (List<Map<String, Object>>) rollback.get("adjustments");
        assertThat(adjustments).isNotEmpty();
        assertThat(adjustments.get(0).get("deducted")).isEqualTo(900L);
        verify(hashOps).put(eq("activity:token:" + playerId), eq("token"), eq("100"));

        // 5) 缓存预热（无 Redis 时走本地双重检测）
        Map<String, Object> warm = warmUpService.warmUpForVersion(versionCode, List.of(
                "abyss:floor:config:12", "activity:config:current", "shop:product:config:current"));
        assertThat(warm.get("ok")).isEqualTo(true);
        assertThat((List<?>) warm.get("warmed")).hasSize(3);
        assertThat(warmUpService.fillHotKey("abyss:floor:config:12")).isTrue();

        // 6) 活动善后
        long endAt = now - 4L * 24 * 60 * 60 * 1000;
        postMortem.registerRule(new PostMortemProcessor.ExpiredTokenRule(88L, endAt, 7001, 10));
        assertThat(postMortem.isExchangeOnlyPeriod(88L, endAt + 24L * 60 * 60 * 1000)).isTrue();
        assertThat(postMortem.isExchangeOnlyPeriod(88L, endAt + 4L * 24 * 60 * 60 * 1000)).isFalse();

        redisHashes.put("activity:token:55", new ConcurrentHashMap<>(Map.of("item:7001", "100")));
        Map<String, Object> sweep = postMortem.sweepExpiredTokens(now);
        assertThat(sweep.get("ok")).isEqualTo(true);
        assertThat(sweep.get("convertedTokens")).isEqualTo(100);
        assertThat(sweep.get("mailTemplate")).isEqualTo("《逾期代币回收说明》");
        verify(hashOps).putAll(eq("mail:pending:55"), any());

        // 7) 战斗回放基线
        PatchBaselineContext baseline = new PatchBaselineContext(
                configPatch.currentConfigVersion(), "deadbeef02", 1001L, null);
        Map<String, Object> start = replayService.start("replay-ops-1", 42L, now, baseline);
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(start.get("configVersion")).isEqualTo(configPatch.currentConfigVersion());
        replayService.append("replay-ops-1", 1, now, "DAMAGE", Map.of("damage", 888));

        Map<String, Object> playback = replayService.playback("replay-ops-1", 2.0, 800.0, 900.0);
        assertThat(playback.get("ok")).isEqualTo(true);
        assertThat(playback.get("usingHistoricalBaseline")).isEqualTo(true);
        assertThat(playback.get("withinSeedExpectation")).isEqualTo(true);

        historicalStore.remove(configPatch.currentConfigVersion());
        Map<String, Object> expired = replayService.playback("replay-ops-1", 1.0, null, null);
        assertThat(expired.get("error")).isEqualTo("baseline_expired");
        assertThat(expired.get("message")).isEqualTo("该录像因版本更迭已失效");
    }
}
