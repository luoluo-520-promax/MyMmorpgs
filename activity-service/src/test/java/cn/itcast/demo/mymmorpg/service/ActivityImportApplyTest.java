package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.ActivityConfigPayload;
import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress;
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.ActivityRetCode;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.ActivityPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 JSON/CSV 导入的活动参数能被识别并在列表、详情、领奖流程中运用。
 */
public class ActivityImportApplyTest {

    private static final long PLAYER_ID = 100L;

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
    private ObjectMapper objectMapper;
    private ActivityImportService importService;
    private ActivityService activityService;
    private final Map<Long, Activity> activityStore = new HashMap<>();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        importService = new ActivityImportService(activityRepository, objectMapper);
        activityService = new ActivityService(
                activityRepository, playerRepository, progressStore, activityItemGrantPort,
                objectMapper, activityPolicy, activityEventPublisher, playerNotificationPort,
                playerDataLoadPort, false);
        activityStore.clear();

        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId((long) (9000 + activityStore.size() + 1));
            }
            activityStore.put(a.getId(), a);
            return a;
        });
        when(activityRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(activityStore.get(inv.getArgument(0))));
        when(activityRepository.findByOpenedTrue()).thenAnswer(inv ->
                activityStore.values().stream().filter(a -> Boolean.TRUE.equals(a.getOpened())).toList());
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(activityPolicy.allowClaimReward(anyInt(), anyLong(), anyLong(), anyInt(), any())).thenReturn(true);
        when(activityItemGrantPort.grantItemsForActivity(anyLong(), anyString(), anyList())).thenReturn(BagRetCode.OK);
        when(progressStore.loadOrCreate(anyLong(), anyLong())).thenAnswer(inv -> new PlayerActivityProgress());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void importJson_thenApplyInListDetailAndClaim() throws Exception {
        String json = readResource("import/activity_full.json");
        List<Activity> imported = importService.importFromJson(json);
        assertThat(imported).hasSize(1);

        Activity activity = imported.get(0);
        assertThat(activity.getId()).isEqualTo(9001L);
        assertThat(activity.getType()).isEqualTo(3);

        ActivityConfigPayload cfg = objectMapper.readValue(activity.getData(), ActivityConfigPayload.class);
        assertThat(cfg.name).isEqualTo("春节集字活动");
        assertThat(cfg.shopId).isEqualTo(8001L);
        assertThat(cfg.uiResources.bannerUrl).contains("spring_banner");
        assertThat(cfg.displayText.title).isEqualTo("春节集字");
        assertThat(cfg.rewardTiers).hasSize(2);

        // 列表：识别名称、时间、描述
        ProtocolMessage listMsg = activityService.loadActivityListNow(PLAYER_ID);
        GetActivityListScRsp listRsp = GetActivityListScRsp.parseFrom(listMsg.payload());
        assertThat(listRsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(listRsp.getActivitiesList()).anySatisfy(brief -> {
            assertThat(brief.getActivityId()).isEqualTo(9001L);
            assertThat(brief.getType()).isEqualTo(3);
            assertThat(brief.getName()).isEqualTo("春节集字活动");
            assertThat(brief.getBriefDesc()).isEqualTo("集字换好礼");
            assertThat(brief.getStartTime()).isEqualTo(1700000000000L);
            assertThat(brief.getEndTime()).isEqualTo(1800000000000L);
        });

        // 详情：识别玩法、规则、阶段、商店、UI、显示文本
        ProtocolMessage detailMsg = activityService.handleGetActivityDetail(PLAYER_ID,
                GetActivityDetailCsReq.newBuilder().setActivityId(9001L).build());
        GetActivityDetailScRsp detailRsp = GetActivityDetailScRsp.parseFrom(detailMsg.payload());
        assertThat(detailRsp.getRetcode()).isEqualTo(ActivityRetCode.OK);

        JsonNode detail = objectMapper.readTree(detailRsp.getDetailData());
        assertThat(detail.get("config_version").asLong()).isEqualTo(2);
        assertThat(detail.get("description").asText()).contains("福字");
        assertThat(detail.get("gameplay").asText()).contains("代币");
        assertThat(detail.get("rules").asText()).contains("仅可领取一次");
        assertThat(detail.get("shop_id").asLong()).isEqualTo(8001L);
        assertThat(detail.get("reward_method").asInt()).isEqualTo(1);
        assertThat(detail.get("stages")).hasSize(2);
        assertThat(detail.get("shop_products")).hasSize(1);
        assertThat(detail.get("ui_resources").get("uiPrefabPath").asText()).isEqualTo("prefabs/activity/SpringPanel");
        assertThat(detail.get("display_text").get("title").asText()).isEqualTo("春节集字");
        assertThat(detail.get("cost_limit").get("dailyLimit").asInt()).isEqualTo(3);

        // 领奖：运用阶段条件 + 代币奖励
        PlayerActivityProgress progress = new PlayerActivityProgress();
        progress.currentStage = 1;
        progress.tokenAmount = 15;
        when(progressStore.loadOrCreate(PLAYER_ID, 9001L)).thenReturn(progress);

        ProtocolMessage failMsg = activityService.handleClaimActivityReward(PLAYER_ID,
                ClaimActivityRewardCsReq.newBuilder().setActivityId(9001L).setRewardIndex(2).build());
        ClaimActivityRewardScRsp failRsp = ClaimActivityRewardScRsp.parseFrom(failMsg.payload());
        assertThat(failRsp.getRetcode()).isEqualTo(ActivityRetCode.CONDITION_NOT_MET);

        ProtocolMessage claimMsg = activityService.handleClaimActivityReward(PLAYER_ID,
                ClaimActivityRewardCsReq.newBuilder().setActivityId(9001L).setRewardIndex(1).build());
        ClaimActivityRewardScRsp claimRsp = ClaimActivityRewardScRsp.parseFrom(claimMsg.payload());
        assertThat(claimRsp.getRetcode()).isEqualTo(ActivityRetCode.OK);
        assertThat(claimRsp.getRewardItemsList()).hasSize(1);
        assertThat(claimRsp.getRewardItems(0).getItemId()).isEqualTo(501);

        ArgumentCaptor<PlayerActivityProgress> progressCaptor = ArgumentCaptor.forClass(PlayerActivityProgress.class);
        verify(progressStore).save(eq(PLAYER_ID), eq(9001L), progressCaptor.capture());
        assertThat(progressCaptor.getValue().tokenAmount).isEqualTo(35);
        assertThat(progressCaptor.getValue().claimed).contains(1);
    }

    @Test
    public void importCsv_thenApplyInDetailAndSignReward() throws Exception {
        String csv = readResource("import/activity_full.csv");
        List<Activity> imported = importService.importFromCsv(csv);
        assertThat(imported).hasSize(1);

        Activity activity = imported.get(0);
        assertThat(activity.getId()).isEqualTo(9002L);
        assertThat(activity.getType()).isEqualTo(4);

        ActivityConfigPayload cfg = objectMapper.readValue(activity.getData(), ActivityConfigPayload.class);
        assertThat(cfg.name).isEqualTo("夏日签到Excel");
        assertThat(cfg.configVersion).isEqualTo(3);
        assertThat(cfg.token.tokenName).isEqualTo("签到点");
        assertThat(cfg.shopProducts.get(0).tokenCost).isEqualTo(50);

        ProtocolMessage detailMsg = activityService.handleGetActivityDetail(PLAYER_ID,
                GetActivityDetailCsReq.newBuilder().setActivityId(9002L).build());
        GetActivityDetailScRsp detailRsp = GetActivityDetailScRsp.parseFrom(detailMsg.payload());
        JsonNode detail = objectMapper.readTree(detailRsp.getDetailData());
        assertThat(detail.get("description").asText()).contains("连续签到");
        assertThat(detail.get("token").get("tokenName").asText()).isEqualTo("签到点");
        assertThat(detail.get("display_text").get("buttonText").asText()).isEqualTo("签到");

        PlayerActivityProgress progress = new PlayerActivityProgress();
        when(progressStore.loadOrCreate(PLAYER_ID, 9002L)).thenReturn(progress);

        ProtocolMessage claimMsg = activityService.handleClaimActivityReward(PLAYER_ID,
                ClaimActivityRewardCsReq.newBuilder().setActivityId(9002L).setRewardIndex(1).build());
        ClaimActivityRewardScRsp claimRsp = ClaimActivityRewardScRsp.parseFrom(claimMsg.payload());
        assertThat(claimRsp.getRetcode()).isEqualTo(ActivityRetCode.OK);

        ArgumentCaptor<PlayerActivityProgress> progressCaptor = ArgumentCaptor.forClass(PlayerActivityProgress.class);
        verify(progressStore).save(eq(PLAYER_ID), eq(9002L), progressCaptor.capture());
        assertThat(progressCaptor.getValue().tokenAmount).isEqualTo(5);
        assertThat(progressCaptor.getValue().signDays).contains(1);
    }

    private static Player player(long id) {
        Player p = new Player();
        p.setId(id);
        p.setName("测试角色");
        return p;
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = ActivityImportApplyTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("测试资源 %s 应存在", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
