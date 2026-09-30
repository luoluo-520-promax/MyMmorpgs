package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.NetworkEmulatorProxy;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainTopologyGraph;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P16 完整业务流程（经 Runtime + Internal API）：
 * 物理哈希软拉回 → 碎岩因果链 → 生态失衡涟漪 → 热力掉落/补刷 →
 * 联机叙事隔膜 → 伙伴跟随/鼓励 → 断线快进 → 拍卖稳定/跨服 →
 * 预测补偿特效 → 配置双缓冲快照。
 */
public class OpenWorldP16BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long HOST = 400_016L;
    private static final long GUEST = 400_017L;
    private static final String REGION = "wolf-camp-valley";
    private static final String ROOM = "coop-p16-1";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP16ApiJourney_authorityEcoSocialCompanionEconomyConfig() {
        long now = System.currentTimeMillis();

        // ── 1) 物理哈希：API 接线 + 篡改重力软拉回 ──
        Map<String, Object> physSeed = api.physicsHashValidate(body(
                "playerId", HOST,
                "physicsStateHash", "seed",
                "vx", 4f, "vy", 0f, "vz", 1f,
                "gravityScale", 1.0f,
                "nx", 0f, "ny", 1f, "nz", 0f));
        assertThat(physSeed.get("ok")).isEqualTo(true);

        PhysicsAuthorityService auth = openWorld.gameplay().physicsAuthority();
        auth.recordExpected(HOST, new PhysicsAuthorityService.ExpectedPhysics(
                4f, 0f, 1f, 1.0f, 0f, 1f, 0f, now));
        Map<String, Object> pullback = auth.validateHash(
                HOST, "deadbeef", 4f, 0f, 1f, 2.8f, 0f, 1f, 0f, now + 600);
        assertThat(pullback.get("softPullback")).isEqualTo(true);
        assertThat(pullback.get("correctGravity")).isEqualTo(1.0f);

        // ── 2) DESTROY_CLIFF 因果链：射线命中 + 扣弹 ──
        openWorld.gameplay().terrainTopology().registerCliff(
                new TerrainTopologyGraph.CliffNode("cliff-p16", 1, 10f, 0f, 10f, 2.5f));
        openWorld.gameplay().terrainTopology().grantBomb(HOST, 2);
        Map<String, Object> cliffMiss = api.terrainDestroyCliff(body(
                "playerId", HOST,
                "cliffId", "cliff-p16",
                "x", 0f, "y", 0f, "z", 0f,
                "aimDirX", 0f, "aimDirY", 0f, "aimDirZ", -1f,
                "tool", "BOMB"));
        assertThat(cliffMiss.get("ok")).isEqualTo(false);

        Map<String, Object> cliffHit = api.terrainDestroyCliff(body(
                "playerId", HOST,
                "cliffId", "cliff-p16",
                "x", 0f, "y", 0f, "z", 0f,
                "aimDirX", 1f, "aimDirY", 0f, "aimDirZ", 1f,
                "tool", "BOMB"));
        assertThat(cliffHit.get("ok")).isEqualTo(true);
        assertThat(cliffHit.get("causalChain")).isEqualTo(true);
        assertThat(cliffHit.get("mutationType")).isEqualTo("DESTROY_CLIFF");

        // ── 3) 生态失衡涟漪 → 拉锯贡献偏移 ──
        openWorld.gameplay().ecoGraph().setStock(REGION, "boar", 3);
        Map<String, Object> ripple = api.ecosystemImbalanceRipple(body(
                "regionId", REGION,
                "species", "boar"));
        assertThat(ripple.get("ok")).isEqualTo(true);
        assertThat(ripple.get("event")).isEqualTo("ECO_IMBALANCE_RIPPLE");
        assertThat(ripple.get("hungryPredators")).asList().contains("wolf");
        assertThat(ripple.get("tugOfWarSeed")).isNotNull();

        // ── 4) 冷门热力掉落杠杆 + 全局补刷 ──
        Map<String, Object> heat = api.exploreLootHeatCoeff(REGION, 9, 9);
        assertThat(heat.get("coldSpot")).isEqualTo(true);
        assertThat(heat.get("heatDensityCoefficient")).isEqualTo(1.5);
        assertThat(heat.get("rarityBoostLabel")).isEqualTo("稀有度提升");
        assertThat(heat.get("msgId")).isEqualTo(MessageId.EXPLORE_LOOT_RARITY_BOOST_SC_NOTIFY);

        Map<String, Object> bal = api.exploreBalancerTick(body(
                "cells", List.of(Map.of("regionId", REGION, "gridX", 9, "gridZ", 9))));
        assertThat(bal.get("ok")).isEqualTo(true);
        assertThat(((Number) bal.get("compensateCount")).intValue()).isGreaterThanOrEqualTo(1);

        // ── 5) 多端辅助瞄准 + HitBox 缩放 + 软回滚划痕特效 ──
        api.combatAssistDevice(body("playerId", HOST, "deviceType", "MOBILE"));
        Map<String, Object> aim = api.combatAssistAim(body(
                "playerId", HOST,
                "aimYawDeg", 0f,
                "predictedEnemyYawDeg", 25f,
                "enemyVx", 2f,
                "enemyVz", 0f,
                "distanceM", 12f));
        assertThat(aim.get("serverDoesNotForceAim")).isEqualTo(true);
        assertThat(aim.get("suggestedTargetAngle")).isNotNull();
        assertThat(openWorld.gameplay().combatAssist().hitboxScale(HOST))
                .isEqualTo(CombatAssistService.MOBILE_HITBOX_SCALE);

        Map<String, Object> verdict = api.hitVerdict(body(
                "playerId", HOST,
                "attackerId", HOST,
                "targetId", 9001L,
                "verdict", "SOFT_ROLLBACK",
                "poiseDamage", 12f));
        assertThat(verdict.get("predictionVerdict")).isEqualTo("SOFT_ROLLBACK");
        assertThat(verdict.get("compensateEffectId")).isEqualTo(HitFeedbackService.MISS_SCRAPE_EFFECT);
        assertThat(verdict.get("elasticBufferMs")).isEqualTo(1_000L);
        assertThat(openWorld.gameplay().moveTrajectory().inElasticBuffer(HOST, System.currentTimeMillis()))
                .isTrue();

        // ── 6) 联机叙事：房主对话 / 访客隔膜 / 助人之证 ──
        Map<String, Object> room = api.coopNarrativeRoom(body(
                "roomId", ROOM,
                "hostPlayerId", HOST,
                "guests", List.of(GUEST)));
        assertThat(room.get("memberCount")).isEqualTo(2);

        Map<String, Object> hostStory = api.storyInstanceStart(body(
                "playerId", HOST,
                "storyId", "legend-ayaka-1",
                "coopRoomId", ROOM));
        assertThat(hostStory.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> hostCoop = (Map<String, Object>) hostStory.get("coopNarrative");
        assertThat(hostCoop.get("loadDialogueUi")).isEqualTo(true);

        Map<String, Object> guestProxy = openWorld.gameplay().coopNarrative()
                .onStoryInstanceStart(ROOM, GUEST, "legend-ayaka-1", now);
        assertThat(guestProxy.get("effect")).isEqualTo("WORLD_SHIFT_SHIELD");
        assertThat(guestProxy.get("msgId")).isEqualTo(MessageId.WORLD_SHIFT_SHIELD_SC_NOTIFY);
        assertThat(guestProxy.get("loadDialogueUi")).isEqualTo(false);

        Map<String, Object> assist = api.coopNarrativeAssist(body(
                "roomId", ROOM,
                "guestPlayerId", GUEST,
                "storyBossId", "ayaka-legend-boss"));
        assertThat(assist.get("badge")).isEqualTo("助人之证");
        assertThat(assist.get("globalFlagHook")).isEqualTo("联机助力");

        // ── 7) 伙伴幽灵：路径节点 + 体力耗尽鼓励 ──
        Map<String, Object> spawn = api.companionSpawn(body(
                "ownerPlayerId", HOST,
                "companionEntityId", 8_800_016L,
                "x", 100f, "y", 0f, "z", 100f));
        assertThat(spawn.get("actorType")).isEqualTo("COMPANION_GHOST");
        assertThat(spawn.get("blocksCombat")).isEqualTo(false);

        Map<String, Object> path = api.companionFollowPath(body(
                "companionEntityId", 8_800_016L,
                "ownerX", 110f, "ownerY", 0f, "ownerZ", 110f));
        assertThat(path.get("followPathSmoothing")).isEqualTo(true);
        assertThat(path.get("msgId")).isEqualTo(MessageId.COMPANION_PATH_NODES_SC_NOTIFY);

        openWorld.gameplay().stamina().consumeAllowNegative(HOST, 200f);
        Map<String, Object> encourage = openWorld.gameplay().companionGhost().consumeTraverse(
                8_800_016L, MovementType.CLIMB, 20f, now);
        assertThat(encourage.get("encourage")).isEqualTo(true);
        assertThat(encourage.get("buff")).isEqualTo("COMPANION_ENCOURAGE");

        // ── 8) 弱网混沌 + 断线超 30s 快进和解 ──
        NetworkEmulatorProxy net = new NetworkEmulatorProxy();
        var drop = net.force(NetworkEmulatorProxy.FaultKind.DROP_ACK, "move-ack-1", Map.of("ok", true));
        assertThat(drop.delivered()).isFalse();
        var delay = net.force(NetworkEmulatorProxy.FaultKind.DELAY_ACK, "move-ack-2", Map.of("ok", true));
        assertThat(delay.delayMs()).isEqualTo(2_000L);

        Map<String, Object> recover = api.shadowRecover(body(
                "playerId", HOST,
                "markDisconnect", true,
                "disconnectAtMs", now - 40_000L,
                "clientQueue", List.of(Map.of(
                        "actionId", "ff-1",
                        "action", "DODGE",
                        "x", 111f, "y", 0f, "z", 111f,
                        "speed", 10f,
                        "clientTs", System.currentTimeMillis() - 100))));
        assertThat(recover.get("fastForwardApplied")).isEqualTo(true);
        assertThat(recover.get("kicked")).isEqualTo(false);
        assertThat(recover.get("mode")).isEqualTo("FAST_FORWARD_RECONCILE");

        // ── 9) 拍卖 PriceStability + 跨服货架 ──
        for (long p : List.of(100L, 105L, 98L, 110L, 102L)) {
            openWorld.gameplay().auctionHouse().recordTradePrice("relic_blank_p16", p);
        }
        Map<String, Object> reject = api.auctionListStable(body(
                "sellerId", HOST,
                "itemId", "relic_blank_p16",
                "count", 1,
                "price", 50_000L,
                "regionShardId", "shard-b",
                "crossShard", true));
        assertThat(reject.get("ok")).isEqualTo(false);
        assertThat(reject.get("error")).isEqualTo("price_reason_required");

        Map<String, Object> listed = api.auctionListStable(body(
                "sellerId", HOST,
                "itemId", "relic_blank_p16",
                "count", 1,
                "price", 50_000L,
                "regionShardId", "shard-b",
                "crossShard", true,
                "priceReason", "跨服稀有胚子平抑物价"));
        assertThat(listed.get("ok")).isEqualTo(true);
        assertThat(listed.get("feeRate")).isEqualTo(0.10);
        assertThat(listed.get("mailVia")).isEqualTo("hall-service-international-post");

        // ── 10) 配置双缓冲：实例快照不受全服发布影响 ──
        openWorld.gameplay().configPatch().upsert("abyss-cell-p16", "ABYSS", Map.of("floorHp", 1000));
        Map<String, Object> snap = api.configSnapshotInstance(body("sceneInstanceId", "scene-p16-1"));
        assertThat(snap.get("snapshotCache")).isEqualTo(true);

        Map<String, Object> staged = api.configStage(body(
                "gridCell", "abyss-cell-p16",
                "kind", "ABYSS",
                "payload", Map.of("floorHp", 2500),
                "gitCommitSha", "p16abcdef012345",
                "configVersion", "cfg-p16-abyss"));
        assertThat(staged.get("staged")).isEqualTo(true);
        assertThat(staged.get("published")).isEqualTo(false);

        Map<String, Object> published = api.configPublish();
        assertThat(published.get("published")).isEqualTo(true);

        Map<String, Object> fromSnap = openWorld.gameplay().configPatch()
                .resolveForInstance("scene-p16-1", "abyss-cell-p16");
        assertThat(fromSnap.get("fromSnapshot")).isEqualTo(true);
        assertThat(((Map<?, ?>) fromSnap.get("payload")).get("floorHp")).isEqualTo(1000);

        Map<String, Object> current = openWorld.gameplay().configPatch().getCell("abyss-cell-p16");
        assertThat(((Map<?, ?>) current.get("payload")).get("floorHp")).isEqualTo(2500);

        // ── 门面 p16 快照 ──
        Map<String, Object> overview = openWorld.gameplay().statusOverview();
        @SuppressWarnings("unchecked")
        Map<String, Object> p16 = (Map<String, Object>) overview.get("p16");
        assertThat(p16.get("physicsAuthority")).isEqualTo(true);
        assertThat(p16.get("ecoGraph")).isEqualTo(true);
        assertThat(p16.get("companionGhost")).isEqualTo(true);
        assertThat(p16.get("configSchemaVersioning")).isEqualTo(true);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
