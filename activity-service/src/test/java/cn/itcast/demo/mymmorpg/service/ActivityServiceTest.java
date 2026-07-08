/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/ActivityServiceTest.java
 * 2) 所属模块：activity-service / test
 * 3) 主要职责：ActivityService 单元测试，覆盖列表/详情/领奖核心路径与异常路径
 * 4) 变更建议：新增业务分支时同步补充用例与参数化日志
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.ActivityConfigPayload;
import cn.itcast.demo.mymmorpg.model.ActivityTypes;
import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload;
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.ActivityRetCode;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.ActivityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ActivityService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class ActivityServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ActivityServiceTest.class);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private ActivityPlayerProgressStore progressStore;
    @Mock
    private ActivityItemGrantPort activityItemGrantPort;
    @Mock
    private ActivityPolicy activityPolicy;
    @Mock
    private ActivityEventPublisher activityEventPublisher;
    @Mock
    private PlayerNotificationPort playerNotificationPort;
    @Mock
    private PlayerDataLoadPort playerDataLoadPort;

    private AutoCloseable mocks;
    private ActivityService activityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        activityService = new ActivityService(
                activityRepository,
                playerRepository,
                progressStore,
                activityItemGrantPort,
                objectMapper,
                activityPolicy,
                activityEventPublisher,
                playerNotificationPort,
                playerDataLoadPort,
                false);
        log.info("[测试前置] ActivityService 已初始化 | preloadEnabled=false");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void handleGetActivityList_preloadNotReady_returnsLoading() throws Exception {
        long playerId = 100L;
        ActivityService preloadService = new ActivityService(
                activityRepository, playerRepository, progressStore, activityItemGrantPort,
                objectMapper, activityPolicy, activityEventPublisher, playerNotificationPort,
                playerDataLoadPort, true);
        when(playerDataLoadPort.isReady(playerId, PlayerDataLoadPort.DataType.ACTIVITY)).thenReturn(false);

        log.info("[测试开始] 场景=预加载未就绪 | playerId={} | preloadEnabled=true | dataType=ACTIVITY",
                playerId);

        ProtocolMessage msg = preloadService.handleGetActivityList(
                playerId, GetActivityListCsReq.getDefaultInstance());
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=预加载未就绪 | msgId={} | retcode={} | loading={} | activityCount={}",
                msg.msgId(), rsp.getRetcode(), rsp.getLoading(), rsp.getActivitiesCount());

        assertThat(msg.msgId()).isEqualTo(MessageId.GET_ACTIVITY_LIST_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(rsp.getLoading()).isTrue();
        verify(activityRepository, never()).findByOpenedTrue();
    }

    @Test
    public void loadActivityListNow_invalidPlayerId() throws Exception {
        long playerId = 0L;
        log.info("[测试开始] 场景=未选角色 | playerId={} | 期望retcode={}", playerId, RetCode.PLAYER_NOT_SELECTED);

        ProtocolMessage msg = activityService.loadActivityListNow(playerId);
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=未选角色 | retcode={} | loading={} | activityCount={}",
                rsp.getRetcode(), rsp.getLoading(), rsp.getActivitiesCount());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getActivitiesCount()).isZero();
    }

    @Test
    public void loadActivityListNow_playerNotFound() throws Exception {
        long playerId = 999L;
        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());

        log.info("[测试开始] 场景=玩家不存在 | playerId={} | 期望retcode={}", playerId, RetCode.PLAYER_NOT_FOUND);

        ProtocolMessage msg = activityService.loadActivityListNow(playerId);
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=玩家不存在 | retcode={} | activityCount={}",
                rsp.getRetcode(), rsp.getActivitiesCount());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_FOUND);
    }

    @Test
    public void loadActivityListNow_returnsOpenedActivities() throws Exception {
        long playerId = 10L;
        long now = System.currentTimeMillis();
        long startTime = now - 86_400_000L;
        long endTime = now + 86_400_000L;

        Player player = new Player();
        player.setId(playerId);
        player.setName("tester");
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 1001;
        tier.count = 10;
        tier.targetRecharge = 100;

        Activity activity = buildActivity(1L, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "首充活动", startTime, endTime, List.of(tier));
        when(activityRepository.findByOpenedTrue()).thenReturn(List.of(activity));

        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.rechargeAmount = 50L;
        when(progressStore.loadOrCreate(playerId, 1L)).thenReturn(progress);

        log.info("[测试开始] 场景=列表成功 | playerId={} | activityId={} | type={} | startTime={} | endTime={} | rechargeAmount={}",
                playerId, activity.getId(), activity.getType(), startTime, endTime, progress.rechargeAmount);

        ProtocolMessage msg = activityService.loadActivityListNow(playerId);
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=列表成功 | retcode={} | activityCount={} | firstActivityId={} | firstStatus={} | firstName={}",
                rsp.getRetcode(), rsp.getActivitiesCount(),
                rsp.getActivitiesCount() > 0 ? rsp.getActivities(0).getActivityId() : -1,
                rsp.getActivitiesCount() > 0 ? rsp.getActivities(0).getStatus() : -1,
                rsp.getActivitiesCount() > 0 ? rsp.getActivities(0).getName() : "");

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(rsp.getActivitiesCount()).isEqualTo(1);
        assertThat(rsp.getActivities(0).getActivityId()).isEqualTo(1L);
        assertThat(rsp.getActivities(0).getStatus()).isEqualTo(ActivityService.STATUS_IN_PROGRESS);
        assertThat(rsp.getActivities(0).getName()).isEqualTo("首充活动");
    }

    @Test
    public void handleGetActivityDetail_activityNotFound() throws Exception {
        long playerId = 10L;
        long activityId = 404L;
        stubPlayerExists(playerId);

        when(activityRepository.findById(activityId)).thenReturn(Optional.empty());

        GetActivityDetailCsReq req = GetActivityDetailCsReq.newBuilder().setActivityId(activityId).build();
        log.info("[测试开始] 场景=活动不存在 | playerId={} | activityId={} | 期望retcode={}",
                playerId, activityId, ActivityRetCode.ACTIVITY_NOT_FOUND);

        ProtocolMessage msg = activityService.handleGetActivityDetail(playerId, req);
        GetActivityDetailScRsp rsp = GetActivityDetailScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=活动不存在 | retcode={} | activityId={} | rewardStatusCount={}",
                rsp.getRetcode(), rsp.getActivityId(), rsp.getRewardStatusCount());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.ACTIVITY_NOT_FOUND);
        assertThat(rsp.getActivityId()).isEqualTo(activityId);
    }

    @Test
    public void handleGetActivityDetail_activityClosed() throws Exception {
        long playerId = 10L;
        long activityId = 2L;
        stubPlayerExists(playerId);

        Activity activity = buildActivity(activityId, ActivityTypes.SUMMER_SIGN_IN.getCode(), false,
                "签到", System.currentTimeMillis(), System.currentTimeMillis() + 86_400_000L, List.of());
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        GetActivityDetailCsReq req = GetActivityDetailCsReq.newBuilder().setActivityId(activityId).build();
        log.info("[测试开始] 场景=活动已关闭 | playerId={} | activityId={} | opened={} | 期望retcode={}",
                playerId, activityId, activity.getOpened(), ActivityRetCode.ACTIVITY_CLOSED);

        ProtocolMessage msg = activityService.handleGetActivityDetail(playerId, req);
        GetActivityDetailScRsp rsp = GetActivityDetailScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=活动已关闭 | retcode={} | type={}", rsp.getRetcode(), rsp.getType());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.ACTIVITY_CLOSED);
        assertThat(rsp.getType()).isEqualTo(ActivityTypes.SUMMER_SIGN_IN.getCode());
    }

    @Test
    public void handleGetActivityDetail_successWithRewardStatus() throws Exception {
        long playerId = 10L;
        long activityId = 3L;
        long now = System.currentTimeMillis();
        long startTime = now - 86_400_000L;
        long endTime = now + 86_400_000L;

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 2001;
        tier.count = 5;
        tier.targetRecharge = 100;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "首充详情", startTime, endTime, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.rechargeAmount = 150L;
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(progress);
        when(activityPolicy.allowClaimReward(anyInt(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(true);

        GetActivityDetailCsReq req = GetActivityDetailCsReq.newBuilder().setActivityId(activityId).build();
        log.info("[测试开始] 场景=详情成功 | playerId={} | activityId={} | rechargeAmount={} | targetRecharge={} | tierIndex={}",
                playerId, activityId, progress.rechargeAmount, tier.targetRecharge, tier.index);

        ProtocolMessage msg = activityService.handleGetActivityDetail(playerId, req);
        GetActivityDetailScRsp rsp = GetActivityDetailScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=详情成功 | retcode={} | status={} | detailData={} | rewardStatusCount={} | canClaim={} | claimed={}",
                rsp.getRetcode(), rsp.getStatus(), rsp.getDetailData(),
                rsp.getRewardStatusCount(),
                rsp.getRewardStatusCount() > 0 ? rsp.getRewardStatus(0).getCanClaim() : false,
                rsp.getRewardStatusCount() > 0 ? rsp.getRewardStatus(0).getClaimed() : false);

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(rsp.getStatus()).isEqualTo(ActivityService.STATUS_IN_PROGRESS);
        assertThat(rsp.getDetailData()).contains("\"recharge_amount\":150");
        assertThat(rsp.getRewardStatusCount()).isEqualTo(1);
        assertThat(rsp.getRewardStatus(0).getCanClaim()).isTrue();
        assertThat(rsp.getRewardStatus(0).getClaimed()).isFalse();
    }

    @Test
    public void handleClaimActivityReward_notStarted() throws Exception {
        long playerId = 10L;
        long activityId = 5L;
        long startTime = System.currentTimeMillis() + 86_400_000L;
        long endTime = startTime + 86_400_000L;

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 3001;
        tier.count = 1;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "未开始", startTime, endTime, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(new PlayerActivityProgress());

        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(1)
                .build();

        log.info("[测试开始] 场景=活动未开始不可领 | playerId={} | activityId={} | rewardIndex={} | startTime={} | endTime={} | 期望retcode={}",
                playerId, activityId, req.getRewardIndex(), startTime, endTime, ActivityRetCode.CONDITION_NOT_MET);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=活动未开始不可领 | retcode={} | claimedCount={} | rewardItemCount={}",
                rsp.getRetcode(), rsp.getClaimedCount(), rsp.getRewardItemsCount());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.CONDITION_NOT_MET);
        verify(activityItemGrantPort, never()).grantItemsForActivity(anyLong(), anyList());
    }

    @Test
    public void handleClaimActivityReward_invalidRewardIndex() throws Exception {
        long playerId = 10L;
        long activityId = 6L;
        long now = System.currentTimeMillis();

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 4001;
        tier.count = 1;
        tier.targetRecharge = 0;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "无效档位", now - 1000, now + 86_400_000L, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(new PlayerActivityProgress());

        int rewardIndex = 99;
        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(rewardIndex)
                .build();

        log.info("[测试开始] 场景=无效奖励档位 | playerId={} | activityId={} | rewardIndex={} | 有效档位={} | 期望retcode={}",
                playerId, activityId, rewardIndex, tier.index, ActivityRetCode.INVALID_REWARD_INDEX);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=无效奖励档位 | retcode={} | rewardIndex={}",
                rsp.getRetcode(), rsp.getRewardIndex());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.INVALID_REWARD_INDEX);
    }

    @Test
    public void handleClaimActivityReward_alreadyClaimed() throws Exception {
        long playerId = 10L;
        long activityId = 7L;
        long now = System.currentTimeMillis();

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 5001;
        tier.count = 1;
        tier.targetRecharge = 0;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "重复领取", now - 1000, now + 86_400_000L, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.claimed.add(tier.index);
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(progress);

        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(tier.index)
                .build();

        log.info("[测试开始] 场景=奖励已领取 | playerId={} | activityId={} | rewardIndex={} | claimedSet={} | 期望retcode={}",
                playerId, activityId, tier.index, progress.claimed, ActivityRetCode.REWARD_ALREADY_CLAIMED);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=奖励已领取 | retcode={} | rewardIndex={}",
                rsp.getRetcode(), rsp.getRewardIndex());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.REWARD_ALREADY_CLAIMED);
    }

    @Test
    public void handleClaimActivityReward_bagFull() throws Exception {
        long playerId = 10L;
        long activityId = 8L;
        long now = System.currentTimeMillis();

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 6001;
        tier.count = 3;
        tier.targetRecharge = 0;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "背包满", now - 1000, now + 86_400_000L, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(new PlayerActivityProgress());
        when(activityPolicy.allowClaimReward(anyInt(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(true);
        when(activityItemGrantPort.grantItemsForActivity(eq(playerId), anyList()))
                .thenReturn(BagRetCode.BAG_FULL);

        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(tier.index)
                .build();

        log.info("[测试开始] 场景=背包已满 | playerId={} | activityId={} | rewardIndex={} | itemId={} | count={} | bagRetcode={} | 期望retcode={}",
                playerId, activityId, tier.index, tier.itemId, tier.count, BagRetCode.BAG_FULL, ActivityRetCode.BAG_FULL);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=背包已满 | retcode={} | rewardItemCount={}",
                rsp.getRetcode(), rsp.getRewardItemsCount());

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.BAG_FULL);
        verify(progressStore, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    public void handleClaimActivityReward_successSingleTier() throws Exception {
        long playerId = 10L;
        long activityId = 9L;
        long now = System.currentTimeMillis();

        stubPlayerExists(playerId);

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 7001;
        tier.count = 2;
        tier.targetRecharge = 0;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "单档领取", now - 1000, now + 86_400_000L, List.of(tier));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        PlayerActivityProgress progress = new PlayerActivityProgress();
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(progress);
        when(activityPolicy.allowClaimReward(anyInt(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(true);
        when(activityItemGrantPort.grantItemsForActivity(eq(playerId), anyList()))
                .thenReturn(BagRetCode.OK);

        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(tier.index)
                .build();

        log.info("[测试开始] 场景=单档领取成功 | playerId={} | activityId={} | rewardIndex={} | itemId={} | count={}",
                playerId, activityId, tier.index, tier.itemId, tier.count);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        ArgumentCaptor<PlayerActivityProgress> progressCaptor = ArgumentCaptor.forClass(PlayerActivityProgress.class);
        verify(progressStore).save(eq(playerId), eq(activityId), progressCaptor.capture());

        log.info("[测试断言] 场景=单档领取成功 | retcode={} | claimedCount={} | rewardItemCount={} | savedClaimedSet={}",
                rsp.getRetcode(), rsp.getClaimedCount(), rsp.getRewardItemsCount(),
                progressCaptor.getValue().claimed);

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(rsp.getClaimedCount()).isEqualTo(1);
        assertThat(rsp.getRewardItemsCount()).isEqualTo(1);
        assertThat(rsp.getRewardItems(0).getItemId()).isEqualTo(tier.itemId);
        assertThat(rsp.getRewardItems(0).getCount()).isEqualTo(tier.count);
        assertThat(progressCaptor.getValue().claimed).contains(tier.index);
        verify(activityEventPublisher).publishRewardClaimed(
                eq(playerId), eq(activityId), eq(activity.getType()), eq(tier.index), eq(1));
    }

    @Test
    public void handleClaimActivityReward_claimAllEligibleTiers() throws Exception {
        long playerId = 10L;
        long activityId = 10L;
        long now = System.currentTimeMillis();

        stubPlayerExists(playerId);

        RewardTierPayload tier1 = new RewardTierPayload();
        tier1.index = 1;
        tier1.itemId = 8001;
        tier1.count = 1;
        tier1.targetRecharge = 0;

        RewardTierPayload tier2 = new RewardTierPayload();
        tier2.index = 2;
        tier2.itemId = 8002;
        tier2.count = 2;
        tier2.targetRecharge = 0;

        Activity activity = buildActivity(activityId, ActivityTypes.FIRST_RECHARGE.getCode(), true,
                "一键领取", now - 1000, now + 86_400_000L, List.of(tier1, tier2));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(new PlayerActivityProgress());
        when(activityPolicy.allowClaimReward(anyInt(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(true);
        when(activityItemGrantPort.grantItemsForActivity(eq(playerId), anyList()))
                .thenReturn(BagRetCode.OK);

        ClaimActivityRewardCsReq req = ClaimActivityRewardCsReq.newBuilder()
                .setActivityId(activityId)
                .setRewardIndex(0)
                .build();

        log.info("[测试开始] 场景=一键领取 | playerId={} | activityId={} | rewardIndex=0 | tierCount={} | tierIndexes=[{}, {}]",
                playerId, activityId, 2, tier1.index, tier2.index);

        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, req);
        ClaimActivityRewardScRsp rsp = ClaimActivityRewardScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=一键领取 | retcode={} | claimedCount={} | rewardItemCount={} | rewardIndexes=[{}, {}]",
                rsp.getRetcode(), rsp.getClaimedCount(), rsp.getRewardItemsCount(),
                rsp.getRewardItemsCount() > 0 ? rsp.getRewardItems(0).getItemId() : -1,
                rsp.getRewardItemsCount() > 1 ? rsp.getRewardItems(1).getItemId() : -1);

        assertThat(rsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(rsp.getClaimedCount()).isEqualTo(2);
        assertThat(rsp.getRewardItemsCount()).isEqualTo(2);
        verify(activityEventPublisher).publishRewardClaimed(
                eq(playerId), eq(activityId), eq(activity.getType()), eq(0), eq(2));
    }

    @Test
    public void addRechargeProgress_accumulatesAndSaves() {
        long playerId = 10L;
        long activityId = 11L;
        long delta = 50L;

        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.rechargeAmount = 100L;
        when(progressStore.loadOrCreate(playerId, activityId)).thenReturn(progress);

        log.info("[测试开始] 场景=累加充值进度 | playerId={} | activityId={} | delta={} | beforeRecharge={}",
                playerId, activityId, delta, progress.rechargeAmount);

        activityService.addRechargeProgress(playerId, activityId, delta);

        ArgumentCaptor<PlayerActivityProgress> captor = ArgumentCaptor.forClass(PlayerActivityProgress.class);
        verify(progressStore).save(eq(playerId), eq(activityId), captor.capture());

        log.info("[测试断言] 场景=累加充值进度 | afterRecharge={} | expected={}",
                captor.getValue().rechargeAmount, 150L);

        assertThat(captor.getValue().rechargeAmount).isEqualTo(150L);
    }

    @Test
    public void broadcastActivityStatusNotify_sends807() throws Exception {
        long now = System.currentTimeMillis();
        long startTime = now - 1000;
        long endTime = now + 86_400_000L;

        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 9001;
        tier.count = 1;

        Activity activity = buildActivity(12L, ActivityTypes.SUMMER_SIGN_IN.getCode(), true,
                "广播测试", startTime, endTime, List.of(tier));
        ActivityConfigPayload cfg = parseConfig(activity);

        log.info("[测试开始] 场景=广播807 | activityId={} | type={} | startTime={} | endTime={} | msgId={}",
                activity.getId(), activity.getType(), startTime, endTime, MessageId.ACTIVITY_STATUS_SC_NOTIFY);

        activityService.broadcastActivityStatusNotify(activity, cfg);

        verify(playerNotificationPort).broadcastAllOnline(
                eq(MessageId.ACTIVITY_STATUS_SC_NOTIFY), any(byte[].class), eq(null));

        log.info("[测试断言] 场景=广播807 | activityId={} | 已调用 broadcastAllOnline", activity.getId());
    }

    private void stubPlayerExists(long playerId) {
        Player player = new Player();
        player.setId(playerId);
        player.setName("p" + playerId);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
    }

    private Activity buildActivity(long id, int type, boolean opened, String name,
                                   long startTime, long endTime, List<RewardTierPayload> tiers) throws Exception {
        ActivityConfigPayload cfg = new ActivityConfigPayload();
        cfg.name = name;
        cfg.startTime = startTime;
        cfg.endTime = endTime;
        cfg.rewardTiers = tiers;

        Activity activity = new Activity();
        activity.setId(id);
        activity.setType(type);
        activity.setOpened(opened);
        activity.setData(objectMapper.writeValueAsString(cfg));
        return activity;
    }

    private ActivityConfigPayload parseConfig(Activity activity) throws Exception {
        return objectMapper.readValue(activity.getData(), ActivityConfigPayload.class);
    }
}
