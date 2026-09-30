/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/ActivityPlayerProgressStoreTest.java
 * 2) 所属模块：activity-service / test
 * 3) 主要职责：ActivityPlayerProgressStore Redis Hash 读写单元测试
 * 4) 变更建议：变更 Redis key 格式或 TTL 时同步更新用例
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ActivityPlayerProgressStore 单元测试：验证 Hash key、load/save 与脏数据降级。
 */
public class ActivityPlayerProgressStoreTest {

    private static final Logger log = LoggerFactory.getLogger(ActivityPlayerProgressStoreTest.class);

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AutoCloseable mocks;
    private ActivityPlayerProgressStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForHash()).thenReturn((HashOperations) hashOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        store = new ActivityPlayerProgressStore(stringRedisTemplate, objectMapper);
        log.info("[测试前置] ActivityPlayerProgressStore 已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void redisKey_format() {
        long playerId = 42L;
        long activityId = 7L;
        String legacy = ActivityPlayerProgressStore.legacyRedisKey(playerId, activityId);
        String hash = ActivityPlayerProgressStore.hashKey(playerId);

        log.info("[测试开始] 场景=Redis键格式 | playerId={} | activityId={} | hashKey={} | legacyKey={}",
                playerId, activityId, hash, legacy);

        assertThat(hash).isEqualTo("player:act:42");
        assertThat(legacy).isEqualTo("activity:prog:42:7");
        assertThat(ActivityPlayerProgressStore.redisKey(playerId, activityId)).isEqualTo(legacy);
    }

    @Test
    public void load_returnsEmptyWhenKeyMissing() {
        long playerId = 1L;
        long activityId = 2L;
        when(hashOps.get("player:act:1", "2")).thenReturn(null);
        when(valueOps.get("activity:prog:1:2")).thenReturn(null);

        log.info("[测试开始] 场景=键不存在 | playerId={} | activityId={}", playerId, activityId);

        Optional<PlayerActivityProgress> result = store.load(playerId, activityId);

        log.info("[测试断言] 场景=键不存在 | present={}", result.isPresent());
        assertThat(result).isEmpty();
    }

    @Test
    public void load_deserializesProgress() throws Exception {
        long playerId = 5L;
        long activityId = 8L;

        PlayerActivityProgress expected = new PlayerActivityProgress();
        expected.rechargeAmount = 200L;
        expected.claimed.add(1);
        expected.signDays.add(3);
        String json = objectMapper.writeValueAsString(expected);
        when(hashOps.get("player:act:5", "8")).thenReturn(json);

        log.info("[测试开始] 场景=反序列化进度 | playerId={} | activityId={} | json={}",
                playerId, activityId, json);

        Optional<PlayerActivityProgress> result = store.load(playerId, activityId);

        log.info("[测试断言] 场景=反序列化进度 | rechargeAmount={} | claimed={} | signDays={}",
                result.get().rechargeAmount, result.get().claimed, result.get().signDays);

        assertThat(result).isPresent();
        assertThat(result.get().rechargeAmount).isEqualTo(200L);
        assertThat(result.get().claimed).isEqualTo(Set.of(1));
        assertThat(result.get().signDays).isEqualTo(Set.of(3));
    }

    @Test
    public void loadOrCreate_createsDefaultWhenMissing() {
        long playerId = 3L;
        long activityId = 4L;
        when(hashOps.get("player:act:3", "4")).thenReturn(null);
        when(valueOps.get("activity:prog:3:4")).thenReturn(null);

        log.info("[测试开始] 场景=loadOrCreate新建 | playerId={} | activityId={}", playerId, activityId);

        PlayerActivityProgress progress = store.loadOrCreate(playerId, activityId);

        log.info("[测试断言] 场景=loadOrCreate新建 | rechargeAmount={} | claimedSize={} | signDaysSize={}",
                progress.rechargeAmount, progress.claimed.size(), progress.signDays.size());

        assertThat(progress.rechargeAmount).isZero();
        assertThat(progress.claimed).isEmpty();
        assertThat(progress.signDays).isEmpty();
    }

    @Test
    public void save_writesJsonWithTtl() throws Exception {
        long playerId = 6L;
        long activityId = 9L;

        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.rechargeAmount = 88L;
        progress.claimed.add(2);

        log.info("[测试开始] 场景=保存进度 | playerId={} | activityId={} | rechargeAmount={} | claimed={} | ttlDays=120",
                playerId, activityId, progress.rechargeAmount, progress.claimed);

        store.save(playerId, activityId, progress);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(eq("player:act:6"), eq("9"), jsonCaptor.capture());
        verify(stringRedisTemplate).expire(eq("player:act:6"), eq(Duration.ofDays(120)));

        PlayerActivityProgress saved = objectMapper.readValue(jsonCaptor.getValue(), PlayerActivityProgress.class);

        log.info("[测试断言] 场景=保存进度 | savedJson={} | savedRecharge={} | savedClaimed={}",
                jsonCaptor.getValue(), saved.rechargeAmount, saved.claimed);

        assertThat(saved.rechargeAmount).isEqualTo(88L);
        assertThat(saved.claimed).containsExactly(2);
    }

    @Test
    public void load_returnsEmptyProgressOnInvalidJson() {
        long playerId = 7L;
        long activityId = 10L;
        String invalidJson = "{not-valid-json";
        when(hashOps.get("player:act:7", "10")).thenReturn(invalidJson);

        log.info("[测试开始] 场景=脏JSON降级 | playerId={} | activityId={} | invalidJson={}",
                playerId, activityId, invalidJson);

        Optional<PlayerActivityProgress> result = store.load(playerId, activityId);

        log.info("[测试断言] 场景=脏JSON降级 | present={} | rechargeAmount={} | claimedSize={}",
                result.isPresent(),
                result.isPresent() ? result.get().rechargeAmount : -1,
                result.isPresent() ? result.get().claimed.size() : -1);

        assertThat(result).isPresent();
        assertThat(result.get().rechargeAmount).isZero();
        assertThat(result.get().claimed).isEmpty();
    }
}
