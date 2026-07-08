/**
 * 文件说明：BuffService 单元测试类。
 * 职责：验证 Buff 查询、施加、移除等核心路径与异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.BuffConfig;
import cn.itcast.demo.mymmorpg.model.PeriodicBuffRegistry;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetEntityBuffsCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetEntityBuffsScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RemoveBuffCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RemoveBuffScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.BuffPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BuffService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class BuffServiceTest {

    private static final Logger log = LoggerFactory.getLogger(BuffServiceTest.class);

    @Mock
    private ConfigQueryService configQueryService;
    @Mock
    private BattleScenePort battleScenePort;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private BuffEventPublisher buffEventPublisher;
    @Mock
    private BuffPolicy buffPolicy;
    @Mock
    private PlayerNotificationPort playerNotificationPort;
    @Mock
    private PeriodicBuffRegistry periodicBuffRegistry;

    private AutoCloseable mocks;
    private BuffService buffService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        buffService = new BuffService(
                configQueryService,
                battleScenePort,
                playerRepository,
                stringRedisTemplate,
                objectMapper,
                buffEventPublisher,
                buffPolicy,
                playerNotificationPort,
                periodicBuffRegistry);
        log.info("[测试前置] BuffService 已初始化 | redisKeyPrefix=buff:runtime:");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void handleGetEntityBuffs_playerNotSelected() throws Exception {
        long requesterPlayerId = 0L;
        log.info("[测试开始] 场景=未选角色查询Buff | requesterPlayerId={} | 期望retcode={}",
                requesterPlayerId, RetCode.PLAYER_NOT_SELECTED);

        ProtocolMessage msg = buffService.handleGetEntityBuffs(
                requesterPlayerId, GetEntityBuffsCsReq.newBuilder().build());
        GetEntityBuffsScRsp rsp = GetEntityBuffsScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=未选角色查询Buff | retcode={} | buffCount={}", rsp.getRetcode(), rsp.getBuffsCount());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
    }

    @Test
    public void handleGetEntityBuffs_self_emptyList() throws Exception {
        long playerId = 100L;
        String redisKey = "buff:runtime:" + playerId;
        log.info("[测试开始] 场景=查询自身Buff列表为空 | playerId={} | redisKey={}", playerId, redisKey);

        when(valueOps.get(redisKey)).thenReturn(null);
        ProtocolMessage msg = buffService.handleGetEntityBuffs(
                playerId, GetEntityBuffsCsReq.newBuilder().setEntityId(0).build());
        GetEntityBuffsScRsp rsp = GetEntityBuffsScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=查询自身Buff列表为空 | retcode={} | entityId={} | buffCount={}",
                rsp.getRetcode(), rsp.getEntityId(), rsp.getBuffsCount());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getEntityId()).isEqualTo(playerId);
        assertThat(rsp.getBuffsCount()).isZero();
    }

    @Test
    public void handleGetEntityBuffs_otherEntity_notVisible() throws Exception {
        long requesterPlayerId = 100L;
        long targetEntityId = 200L;
        log.info("[测试开始] 场景=查询他人Buff不可见 | requesterPlayerId={} | targetEntityId={} | 期望retcode={}",
                requesterPlayerId, targetEntityId, RetCode.BUFF_ENTITY_NOT_VISIBLE);

        when(battleScenePort.isPlayerInScene(requesterPlayerId)).thenReturn(false);
        ProtocolMessage msg = buffService.handleGetEntityBuffs(
                requesterPlayerId, GetEntityBuffsCsReq.newBuilder().setEntityId(targetEntityId).build());
        GetEntityBuffsScRsp rsp = GetEntityBuffsScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=查询他人Buff不可见 | retcode={} | entityId={}", rsp.getRetcode(), rsp.getEntityId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BUFF_ENTITY_NOT_VISIBLE);
    }

    @Test
    public void applyBuff_newBuff_persistsAndNotifies() throws Exception {
        long entityId = 100L;
        int buffId = 501;
        BuffConfig cfg = buildBuffConfig(buffId, "攻击强化", 5000, 3, 1);
        log.info("[测试开始] 场景=施加新Buff | entityId={} | buffId={} | duration={}ms | stackLimit={}",
                entityId, buffId, cfg.getDuration(), cfg.getStackLimit());

        when(buffPolicy.canApplyBuff(entityId, buffId)).thenReturn(true);
        when(configQueryService.findBuffById(buffId)).thenReturn(cfg);
        when(valueOps.get("buff:runtime:" + entityId)).thenReturn(null);
        when(playerRepository.existsById(entityId)).thenReturn(true);

        buffService.applyBuff(entityId, buffId);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("buff:runtime:" + entityId), jsonCaptor.capture());
        List<BuffService.ActiveBuffEntry> saved = objectMapper.readValue(
                jsonCaptor.getValue(), objectMapper.getTypeFactory().constructCollectionType(List.class, BuffService.ActiveBuffEntry.class));

        log.info("Buff施加: entityId={}, buffId={}, stackCount={}, expireAtMs={}",
                entityId, saved.get(0).buffId, saved.get(0).stackCount, saved.get(0).expireAtMs);
        log.info("[测试断言] 场景=施加新Buff | savedCount={} | stackCount={} | 已发布添加事件 | 已推送通知",
                saved.size(), saved.get(0).stackCount);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).buffId).isEqualTo(buffId);
        assertThat(saved.get(0).stackCount).isEqualTo(1);
        verify(buffEventPublisher).publishBuffAdded(entityId, buffId, 1);
        verify(playerNotificationPort).send(eq(entityId), eq(MessageId.BUFF_ADD_SC_NOTIFY), any());
        verify(periodicBuffRegistry).syncEntityBuffs(eq(entityId), any());
    }

    @Test
    public void applyBuff_stackExistingBuff() throws Exception {
        long entityId = 100L;
        int buffId = 501;
        BuffConfig cfg = buildBuffConfig(buffId, "攻击强化", 5000, 3, 2);
        BuffService.ActiveBuffEntry existing = new BuffService.ActiveBuffEntry();
        existing.buffId = buffId;
        existing.stackCount = 1;
        existing.expireAtMs = System.currentTimeMillis() + 3000L;
        String existingJson = objectMapper.writeValueAsString(List.of(existing));

        log.info("[测试开始] 场景=Buff叠层 | entityId={} | buffId={} | beforeStack={} | stackLimit={}",
                entityId, buffId, existing.stackCount, cfg.getStackLimit());

        when(buffPolicy.canApplyBuff(entityId, buffId)).thenReturn(true);
        when(configQueryService.findBuffById(buffId)).thenReturn(cfg);
        when(valueOps.get("buff:runtime:" + entityId)).thenReturn(existingJson);
        when(playerRepository.existsById(entityId)).thenReturn(true);

        buffService.applyBuff(entityId, buffId);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("buff:runtime:" + entityId), jsonCaptor.capture());
        List<BuffService.ActiveBuffEntry> saved = objectMapper.readValue(
                jsonCaptor.getValue(), objectMapper.getTypeFactory().constructCollectionType(List.class, BuffService.ActiveBuffEntry.class));

        log.info("[测试断言] 场景=Buff叠层 | afterStack={} | 已发布更新事件", saved.get(0).stackCount);
        assertThat(saved.get(0).stackCount).isEqualTo(2);
        verify(buffEventPublisher).publishBuffUpdated(eq(entityId), eq(buffId), anyLong(), eq(2));
        verify(playerNotificationPort).send(eq(entityId), eq(MessageId.BUFF_UPDATE_SC_NOTIFY), any());
    }

    @Test
    public void handleRemoveBuff_buffNotOnEntity() throws Exception {
        long playerId = 100L;
        int buffId = 999;
        log.info("[测试开始] 场景=移除不存在的Buff | playerId={} | buffId={} | 期望retcode={}",
                playerId, buffId, RetCode.BUFF_NOT_ON_ENTITY);

        when(buffPolicy.canRemoveBuff(playerId, playerId, buffId)).thenReturn(true);
        when(configQueryService.findBuffById(buffId)).thenReturn(buildBuffConfig(buffId, "test", -1, 1, 1));
        when(valueOps.get("buff:runtime:" + playerId)).thenReturn(null);

        RemoveBuffCsReq req = RemoveBuffCsReq.newBuilder().setBuffId(buffId).build();
        RemoveBuffScRsp rsp = RemoveBuffScRsp.parseFrom(
                buffService.handleRemoveBuff(playerId, req).payload());

        log.info("[测试断言] 场景=移除不存在的Buff | retcode={} | targetEntityId={} | buffId={}",
                rsp.getRetcode(), rsp.getTargetEntityId(), rsp.getBuffId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BUFF_NOT_ON_ENTITY);
    }

    @Test
    public void handleRemoveBuff_success() throws Exception {
        long playerId = 100L;
        int buffId = 501;
        BuffService.ActiveBuffEntry entry = new BuffService.ActiveBuffEntry();
        entry.buffId = buffId;
        entry.stackCount = 1;
        entry.expireAtMs = -1L;
        String json = objectMapper.writeValueAsString(List.of(entry));

        log.info("[测试开始] 场景=主动移除Buff | playerId={} | buffId={} | stackCount={}",
                playerId, buffId, entry.stackCount);

        when(buffPolicy.canRemoveBuff(playerId, playerId, buffId)).thenReturn(true);
        when(configQueryService.findBuffById(buffId)).thenReturn(buildBuffConfig(buffId, "test", -1, 1, 1));
        when(valueOps.get("buff:runtime:" + playerId)).thenReturn(json);
        when(playerRepository.existsById(playerId)).thenReturn(true);

        RemoveBuffCsReq req = RemoveBuffCsReq.newBuilder().setBuffId(buffId).build();
        RemoveBuffScRsp rsp = RemoveBuffScRsp.parseFrom(
                buffService.handleRemoveBuff(playerId, req).payload());

        log.info("[测试断言] 场景=主动移除Buff | retcode={} | reason=CANCEL | 已推送移除通知",
                rsp.getRetcode());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        verify(buffEventPublisher).publishBuffRemoved(playerId, buffId, BuffService.REMOVE_REASON_CANCEL);
        verify(playerNotificationPort).send(eq(playerId), eq(MessageId.BUFF_REMOVE_SC_NOTIFY), any());
    }

    @Test
    public void applyBuff_invalidParams_ignored() {
        log.info("[测试开始] 场景=无效参数施加Buff | entityId=0 | buffId=0");

        buffService.applyBuff(0L, 0);

        log.info("[测试断言] 场景=无效参数施加Buff | 未写入Redis | 未发布事件");
        verify(valueOps, never()).set(anyString(), anyString());
        verify(buffEventPublisher, never()).publishBuffAdded(anyLong(), anyInt(), anyInt());
    }

    private static BuffConfig buildBuffConfig(int id, String name, int duration, int effectType, int stackLimit) {
        BuffConfig cfg = new BuffConfig();
        cfg.setId(id);
        cfg.setName(name);
        cfg.setDuration(duration);
        cfg.setEffectType(effectType);
        cfg.setStackLimit(stackLimit);
        cfg.setDescription("test buff");
        return cfg;
    }
}
