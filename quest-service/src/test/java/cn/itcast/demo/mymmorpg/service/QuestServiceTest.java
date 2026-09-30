/**
 * 文件说明：QuestService 单元测试类。
 * 职责：使用 Mockito 模拟依赖，验证任务列表、接取、进度推进、领奖等核心路径与异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import cn.itcast.demo.mymmorpg.repository.PlayerQuestProgressRepository;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListScRsp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class QuestServiceTest {

    private static final Logger log = LoggerFactory.getLogger(QuestServiceTest.class);

    @Mock
    private QuestConfigService questConfigService;
    @Mock
    private PlayerQuestProgressRepository questProgressRepository;
    @Mock
    private ObjectProvider<PlayerProgressPort> playerProgressPortProvider;
    @Mock
    private ObjectProvider<PlayerCachePort> playerCachePortProvider;
    @Mock
    private ObjectProvider<jforgame.commons.eventbus.EventBus> eventBusProvider;
    @Mock
    private PlayerProgressPort playerProgressPort;
    @Mock
    private PlayerCachePort playerCachePort;

    private AutoCloseable mocks;
    private QuestService questService;
    private final Map<String, PlayerQuestProgress> progressStore = new ConcurrentHashMap<>();

    @BeforeMethod
    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        progressStore.clear();
        when(playerProgressPortProvider.getIfAvailable()).thenReturn(playerProgressPort);
        when(playerCachePortProvider.getIfAvailable()).thenReturn(playerCachePort);
        when(eventBusProvider.getIfAvailable()).thenReturn(null);
        QuestTemplate q1001 = new QuestTemplate(1001, "初入世界", "完成新手引导", 1, 1, 100, 50, true);
        QuestTemplate q1002 = new QuestTemplate(1002, "日常巡逻", "完成日常巡逻目标", 3, 3, 50, 20, false);
        QuestTemplate q2001 = new QuestTemplate(2001, "拜访NPC", "拜访指定 NPC", 2, 1, 80, 30, false);
        when(questConfigService.listAll()).thenReturn(List.of(q1001, q1002, q2001));
        lenient().when(questConfigService.find(1001)).thenReturn(q1001);
        lenient().when(questConfigService.find(1002)).thenReturn(q1002);
        lenient().when(questConfigService.find(2001)).thenReturn(q2001);
        lenient().when(questProgressRepository.findByPlayerIdAndQuestId(anyLong(), anyInt())).thenAnswer(inv -> {
            String key = inv.getArgument(0) + ":" + inv.getArgument(1);
            return Optional.ofNullable(progressStore.get(key));
        });
        lenient().when(questProgressRepository.save(any(PlayerQuestProgress.class))).thenAnswer(inv -> {
            PlayerQuestProgress p = inv.getArgument(0);
            progressStore.put(p.getPlayerId() + ":" + p.getQuestId(), p);
            return p;
        });
        questService = new QuestService(questConfigService, questProgressRepository, playerProgressPortProvider,
                playerCachePortProvider, eventBusProvider);
        log.info("[测试前置] QuestService 已初始化 | templates=1001/1002/2001 | statusAvailable=0 | statusAccepted=1 | statusCompleted=2 | statusClaimed=3");
    }

    @AfterMethod
    @AfterEach
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetQuestList_invalidPlayer() throws Exception {
        long playerId = 0L;
        log.info("[测试开始] 场景=任务列表未选角色 | playerId={} | 期望retcode={}",
                playerId, RetCode.PLAYER_NOT_SELECTED);

        ProtocolMessage msg = questService.handleGetQuestList(playerId, GetQuestListCsReq.newBuilder().build());
        GetQuestListScRsp rsp = GetQuestListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=任务列表未选角色 | msgId={} | retcode={} | questsCount={}",
                msg.msgId(), rsp.getRetcode(), rsp.getQuestsCount());
        assertThat(msg.msgId()).isEqualTo(MessageId.GET_QUEST_LIST_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getQuestsCount()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleGetQuestList_success() throws Exception {
        long playerId = 10L;
        log.info("[测试开始] 场景=任务列表成功 | playerId={} | 期望retcode={} | 期望questsCount=3",
                playerId, RetCode.OK);

        GetQuestListScRsp rsp = GetQuestListScRsp.parseFrom(
                questService.handleGetQuestList(playerId, GetQuestListCsReq.newBuilder().build()).payload());

        log.info("[测试断言] 场景=任务列表成功 | retcode={} | questsCount={} | firstQuestId={} | firstStatus={} | secondQuestId={} | thirdQuestId={}",
                rsp.getRetcode(), rsp.getQuestsCount(),
                rsp.getQuests(0).getQuestId(), rsp.getQuests(0).getStatus(),
                rsp.getQuests(1).getQuestId(), rsp.getQuests(2).getQuestId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getQuestsCount()).isEqualTo(3);
        assertThat(rsp.getQuests(0).getQuestId()).isEqualTo(1001);
        assertThat(rsp.getQuests(0).getStatus()).isEqualTo(QuestService.STATUS_AVAILABLE);
        assertThat(rsp.getQuests(1).getQuestId()).isEqualTo(1002);
        assertThat(rsp.getQuests(2).getQuestId()).isEqualTo(2001);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAcceptQuest_invalidPlayer() throws Exception {
        long playerId = -1L;
        int questId = 1001;
        log.info("[测试开始] 场景=接取任务未选角色 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.PLAYER_NOT_SELECTED);

        AcceptQuestCsReq req = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp rsp = AcceptQuestScRsp.parseFrom(questService.handleAcceptQuest(playerId, req).payload());

        log.info("[测试断言] 场景=接取任务未选角色 | retcode={} | hasQuest={}",
                rsp.getRetcode(), rsp.hasQuest());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.hasQuest()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAcceptQuest_questNotFound() throws Exception {
        long playerId = 20L;
        int questId = 9999;
        log.info("[测试开始] 场景=接取任务不存在 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.QUEST_NOT_FOUND);

        AcceptQuestCsReq req = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp rsp = AcceptQuestScRsp.parseFrom(questService.handleAcceptQuest(playerId, req).payload());

        log.info("[测试断言] 场景=接取任务不存在 | retcode={} | hasQuest={}",
                rsp.getRetcode(), rsp.hasQuest());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.QUEST_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAcceptQuest_mainQuestAutoCompleted() throws Exception {
        long playerId = 30L;
        int questId = 1001;
        log.info("[测试开始] 场景=主线1001接取即完成 | playerId={} | questId={} | 期望retcode={} | 期望status={} | 期望progress=1 | 期望exp=100 | 期望gold=50",
                playerId, questId, RetCode.OK, QuestService.STATUS_COMPLETED);

        AcceptQuestCsReq req = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp rsp = AcceptQuestScRsp.parseFrom(questService.handleAcceptQuest(playerId, req).payload());

        log.info("[测试断言] 场景=主线1001接取即完成 | retcode={} | questId={} | name={} | status={} | progress={} | target={} | expReward={} | goldReward={}",
                rsp.getRetcode(), rsp.getQuest().getQuestId(), rsp.getQuest().getName(),
                rsp.getQuest().getStatus(), rsp.getQuest().getProgress(), rsp.getQuest().getTarget(),
                rsp.getQuest().getExpReward(), rsp.getQuest().getGoldReward());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getQuest().getQuestId()).isEqualTo(1001);
        assertThat(rsp.getQuest().getStatus()).isEqualTo(QuestService.STATUS_COMPLETED);
        assertThat(rsp.getQuest().getProgress()).isEqualTo(1);
        assertThat(rsp.getQuest().getExpReward()).isEqualTo(100);
        assertThat(rsp.getQuest().getGoldReward()).isEqualTo(50);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleAcceptQuest_alreadyAccepted() throws Exception {
        long playerId = 31L;
        int questId = 1002;
        log.info("[测试开始] 场景=重复接取日常任务 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.QUEST_ALREADY_ACCEPTED);

        AcceptQuestCsReq req = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp first = AcceptQuestScRsp.parseFrom(questService.handleAcceptQuest(playerId, req).payload());
        log.info("[测试准备] 场景=重复接取日常任务 | firstRetcode={} | status={} | progress={}",
                first.getRetcode(), first.getQuest().getStatus(), first.getQuest().getProgress());
        assertThat(first.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(first.getQuest().getStatus()).isEqualTo(QuestService.STATUS_ACCEPTED);

        AcceptQuestScRsp second = AcceptQuestScRsp.parseFrom(questService.handleAcceptQuest(playerId, req).payload());
        log.info("[测试断言] 场景=重复接取日常任务 | retcode={} | status={} | progress={}",
                second.getRetcode(), second.getQuest().getStatus(), second.getQuest().getProgress());
        assertThat(second.getRetcode()).isEqualTo(RetCode.QUEST_ALREADY_ACCEPTED);
        assertThat(second.getQuest().getStatus()).isEqualTo(QuestService.STATUS_ACCEPTED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimQuestReward_invalidPlayer() throws Exception {
        long playerId = 0L;
        int questId = 1001;
        log.info("[测试开始] 场景=领奖未选角色 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.PLAYER_NOT_SELECTED);

        ClaimQuestRewardCsReq req = ClaimQuestRewardCsReq.newBuilder().setQuestId(questId).build();
        ClaimQuestRewardScRsp rsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId, req).payload());

        log.info("[测试断言] 场景=领奖未选角色 | retcode={} | questId={} | expGained={} | goldGained={}",
                rsp.getRetcode(), rsp.getQuestId(), rsp.getExpGained(), rsp.getGoldGained());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getExpGained()).isZero();
        assertThat(rsp.getGoldGained()).isZero();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimQuestReward_questNotFound() throws Exception {
        long playerId = 40L;
        int questId = 8888;
        log.info("[测试开始] 场景=领奖任务不存在 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.QUEST_NOT_FOUND);

        ClaimQuestRewardCsReq req = ClaimQuestRewardCsReq.newBuilder().setQuestId(questId).build();
        ClaimQuestRewardScRsp rsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId, req).payload());

        log.info("[测试断言] 场景=领奖任务不存在 | retcode={} | questId={}",
                rsp.getRetcode(), rsp.getQuestId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.QUEST_NOT_FOUND);
        assertThat(rsp.getQuestId()).isEqualTo(questId);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimQuestReward_notCompleted() throws Exception {
        long playerId = 41L;
        int questId = 1002;
        log.info("[测试开始] 场景=领奖任务未完成 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.QUEST_NOT_COMPLETED);

        AcceptQuestCsReq acceptReq = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp acceptRsp = AcceptQuestScRsp.parseFrom(
                questService.handleAcceptQuest(playerId, acceptReq).payload());
        log.info("[测试准备] 场景=领奖任务未完成 | acceptRetcode={} | status={} | progress={} | target={}",
                acceptRsp.getRetcode(), acceptRsp.getQuest().getStatus(),
                acceptRsp.getQuest().getProgress(), acceptRsp.getQuest().getTarget());

        ClaimQuestRewardCsReq claimReq = ClaimQuestRewardCsReq.newBuilder().setQuestId(questId).build();
        ClaimQuestRewardScRsp claimRsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId, claimReq).payload());

        log.info("[测试断言] 场景=领奖任务未完成 | retcode={} | questId={} | expGained={} | goldGained={}",
                claimRsp.getRetcode(), claimRsp.getQuestId(), claimRsp.getExpGained(), claimRsp.getGoldGained());
        assertThat(claimRsp.getRetcode()).isEqualTo(RetCode.QUEST_NOT_COMPLETED);
        verify(playerProgressPort, never()).addExp(any(), anyInt());
        verify(playerProgressPort, never()).addGold(any(), anyLong());
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleClaimQuestReward_successThenAlreadyClaimed() throws Exception {
        long playerId = 50L;
        int questId = 1001;
        Player player = buildPlayer(playerId);
        log.info("[测试开始] 场景=主线领奖成功 | playerId={} | questId={} | 期望retcode={} | 期望exp=100 | 期望gold=50",
                playerId, questId, RetCode.OK);

        AcceptQuestCsReq acceptReq = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp acceptRsp = AcceptQuestScRsp.parseFrom(
                questService.handleAcceptQuest(playerId, acceptReq).payload());
        log.info("[测试准备] 场景=主线领奖成功 | acceptRetcode={} | status={} | progress={}",
                acceptRsp.getRetcode(), acceptRsp.getQuest().getStatus(), acceptRsp.getQuest().getProgress());
        assertThat(acceptRsp.getQuest().getStatus()).isEqualTo(QuestService.STATUS_COMPLETED);

        when(playerCachePort.findById(playerId)).thenReturn(player);
        ClaimQuestRewardCsReq claimReq = ClaimQuestRewardCsReq.newBuilder().setQuestId(questId).build();
        ClaimQuestRewardScRsp okRsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId, claimReq).payload());

        log.info("[测试断言] 场景=主线领奖成功 | retcode={} | questId={} | expGained={} | goldGained={}",
                okRsp.getRetcode(), okRsp.getQuestId(), okRsp.getExpGained(), okRsp.getGoldGained());
        assertThat(okRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(okRsp.getExpGained()).isEqualTo(100);
        assertThat(okRsp.getGoldGained()).isEqualTo(50);
        verify(playerProgressPort).addExp(eq(player), eq(100));
        verify(playerProgressPort).addGold(eq(player), eq(50L));

        log.info("[测试开始] 场景=任务已领奖 | playerId={} | questId={} | 期望retcode={}",
                playerId, questId, RetCode.QUEST_ALREADY_CLAIMED);
        ClaimQuestRewardScRsp claimedRsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId, claimReq).payload());
        log.info("[测试断言] 场景=任务已领奖 | retcode={} | expGained={} | goldGained={}",
                claimedRsp.getRetcode(), claimedRsp.getExpGained(), claimedRsp.getGoldGained());
        assertThat(claimedRsp.getRetcode()).isEqualTo(RetCode.QUEST_ALREADY_CLAIMED);
        verify(playerProgressPort, times(1)).addExp(eq(player), eq(100));
        verify(playerProgressPort, times(1)).addGold(eq(player), eq(50L));
    }

    @Test
    @org.junit.jupiter.api.Test
    public void advanceProgress_completesDailyThenClaim() throws Exception {
        long playerId = 60L;
        int questId = 1002;
        int delta1 = 1;
        int delta2 = 2;
        Player player = buildPlayer(playerId);
        log.info("[测试开始] 场景=日常巡逻推进完成并领奖 | playerId={} | questId={} | delta1={} | delta2={} | target=3 | 期望exp=50 | 期望gold=20",
                playerId, questId, delta1, delta2);

        AcceptQuestCsReq acceptReq = AcceptQuestCsReq.newBuilder().setQuestId(questId).build();
        AcceptQuestScRsp acceptRsp = AcceptQuestScRsp.parseFrom(
                questService.handleAcceptQuest(playerId, acceptReq).payload());
        log.info("[测试准备] 场景=日常巡逻推进完成并领奖 | acceptStatus={} | progress={}",
                acceptRsp.getQuest().getStatus(), acceptRsp.getQuest().getProgress());

        questService.advanceProgress(playerId, questId, delta1);
        GetQuestListScRsp midList = GetQuestListScRsp.parseFrom(
                questService.handleGetQuestList(playerId, GetQuestListCsReq.newBuilder().build()).payload());
        var midQuest = midList.getQuestsList().stream().filter(q -> q.getQuestId() == questId).findFirst().orElseThrow();
        log.info("[测试准备] 场景=日常巡逻推进完成并领奖 | afterDelta1 status={} | progress={}",
                midQuest.getStatus(), midQuest.getProgress());
        assertThat(midQuest.getStatus()).isEqualTo(QuestService.STATUS_ACCEPTED);
        assertThat(midQuest.getProgress()).isEqualTo(1);

        questService.advanceProgress(playerId, questId, delta2);
        GetQuestListScRsp doneList = GetQuestListScRsp.parseFrom(
                questService.handleGetQuestList(playerId, GetQuestListCsReq.newBuilder().build()).payload());
        var doneQuest = doneList.getQuestsList().stream().filter(q -> q.getQuestId() == questId).findFirst().orElseThrow();
        log.info("[测试准备] 场景=日常巡逻推进完成并领奖 | afterDelta2 status={} | progress={}",
                doneQuest.getStatus(), doneQuest.getProgress());
        assertThat(doneQuest.getStatus()).isEqualTo(QuestService.STATUS_COMPLETED);
        assertThat(doneQuest.getProgress()).isEqualTo(3);

        when(playerCachePort.findById(playerId)).thenReturn(player);
        ClaimQuestRewardScRsp claimRsp = ClaimQuestRewardScRsp.parseFrom(
                questService.handleClaimQuestReward(playerId,
                        ClaimQuestRewardCsReq.newBuilder().setQuestId(questId).build()).payload());

        log.info("[测试断言] 场景=日常巡逻推进完成并领奖 | retcode={} | questId={} | expGained={} | goldGained={}",
                claimRsp.getRetcode(), claimRsp.getQuestId(), claimRsp.getExpGained(), claimRsp.getGoldGained());
        assertThat(claimRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(claimRsp.getExpGained()).isEqualTo(50);
        assertThat(claimRsp.getGoldGained()).isEqualTo(20);
        verify(playerProgressPort).addExp(eq(player), eq(50));
        verify(playerProgressPort).addGold(eq(player), eq(20L));
    }

    @Test
    @org.junit.jupiter.api.Test
    public void advanceProgress_ignoredWhenNotAccepted() throws Exception {
        long playerId = 70L;
        int questId = 2001;
        int delta = 1;
        log.info("[测试开始] 场景=未接取时推进无效 | playerId={} | questId={} | delta={} | 期望status={}",
                playerId, questId, delta, QuestService.STATUS_AVAILABLE);

        questService.advanceProgress(playerId, questId, delta);
        GetQuestListScRsp list = GetQuestListScRsp.parseFrom(
                questService.handleGetQuestList(playerId, GetQuestListCsReq.newBuilder().build()).payload());
        var quest = list.getQuestsList().stream().filter(q -> q.getQuestId() == questId).findFirst().orElseThrow();

        log.info("[测试断言] 场景=未接取时推进无效 | questId={} | status={} | progress={}",
                quest.getQuestId(), quest.getStatus(), quest.getProgress());
        assertThat(quest.getStatus()).isEqualTo(QuestService.STATUS_AVAILABLE);
        assertThat(quest.getProgress()).isZero();
    }

    private static Player buildPlayer(long id) {
        Player player = new Player();
        player.setId(id);
        player.setName("quest-player-" + id);
        player.setLevel(5);
        player.setGold(0L);
        return player;
    }
}
