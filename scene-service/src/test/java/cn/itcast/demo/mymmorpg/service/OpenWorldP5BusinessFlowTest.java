package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5 大世界新功能完整业务流程（经 Runtime + Internal API）：
 * 解谜引擎 → 物理层 → 收集物分级 → 区域探索度/声望 → 奇观入口与 LOD →
 * 角色世界技与烹饪 → 区域潮汐/事件链/Boss 联动 → 异步社交 → 量产管线热更。
 */
public class OpenWorldP5BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 55_001L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void statusIncludesP5CapabilitiesAndRegionProgress() {
        Map<String, Object> overview = api.gameplayStatus(null, null);
        assertThat(overview.get("ok")).isEqualTo(true);
        assertThat(overview.get("puzzleTemplates")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.INTEGER).isGreaterThanOrEqualTo(10);
        assertThat(overview.get("collectibles")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.INTEGER).isGreaterThanOrEqualTo(3);
        assertThat(overview.get("ruleTriggers")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.INTEGER).isGreaterThanOrEqualTo(2);

        Map<String, Object> withRegion = api.gameplayStatus(PLAYER, "wolf-camp-valley");
        assertThat(withRegion.get("region_progress")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> rpWrap = (Map<String, Object>) withRegion.get("region_progress");
        assertThat(rpWrap.get("region_progress")).isInstanceOf(Map.class);
    }

    @Test
    public void fullP5Journey_puzzlePhysicsCollectReputationSocialPipeline() {
        // ── 1) ECA 规则：夜间火烧藤蔓 → 隐藏路径 ──
        assertThat(api.ruleFire(Map.of(
                "eventType", "INTERACT",
                "context", Map.of("element", "FIRE", "target", "VINE", "time", "DAY")))
                .get("matchedCount")).isEqualTo(0);
        Map<String, Object> ruleHit = api.ruleFire(Map.of(
                "eventType", "INTERACT",
                "element", "FIRE", "target", "VINE", "time", "NIGHT"));
        assertThat(ruleHit.get("matchedCount")).isEqualTo(1);
        assertThat(api.ruleList().get("rules")).asList().isNotEmpty();

        // 弓箭命中悬浮靶 → 风场
        Map<String, Object> wind = api.ruleFire(Map.of(
                "eventType", "HIT",
                "weapon", "BOW", "target", "HOVER_TARGET"));
        assertThat(wind.get("matchedCount")).isEqualTo(1);
        assertThat(wind.get("matched").toString()).contains("SPAWN_WIND");

        // ── 2) 物理层：冻结水面 + AOI 查询 ──
        assertThat(api.physicsApply(Map.of(
                "worldId", 1, "x", 48f, "z", 48f, "kind", "FROZEN",
                "intensity", 3, "ttlMs", 20_000L)).get("ok")).isEqualTo(true);
        Map<String, Object> aoi = api.physicsAoi(1, 48f, 48f, 2);
        assertThat(aoi.get("count")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.INTEGER).isGreaterThanOrEqualTo(1);
        assertThat(aoi.get("physicsLayer").toString()).contains("FROZEN");

        // 火焰蔓延
        openWorld.gameplay().physics().apply(1, 64f, 64f,
                cn.itcast.demo.mymmorpg.world.puzzle.PhysicsLayerService.PhysicsKind.FIRE,
                2, 10_000L, System.currentTimeMillis());
        Map<String, Object> spread = openWorld.gameplay().physics().tickSpread(
                1, System.currentTimeMillis(), List.of("1:4:4", "1:3:4", "1:5:4", "1:4:3", "1:4:5"));
        assertThat(spread.get("ok")).isEqualTo(true);

        // ── 3) 解谜库：JSON 实例化 + 逐步破解写入口条件 ──
        assertThat(api.puzzleTemplates().get("templates")).asList().hasSizeGreaterThanOrEqualTo(10);
        Map<String, Object> created = api.puzzleInstantiate(Map.of(
                "puzzleId", "api-music-1",
                "type", "MUSIC_RUNE",
                "x", 10f, "y", 0f, "z", 10f,
                "config", Map.of("notes", List.of("Do", "Mi", "Sol")),
                "rewards", List.of(Map.of("itemId", "primogem", "count", 3))));
        assertThat(created.get("ok")).isEqualTo(true);

        api.puzzleAdvance(Map.of("playerId", PLAYER, "puzzleId", "puzzle-demo-obelisk",
                "regionId", "wolf-camp-valley", "input", Map.of("element", "FIRE")));
        api.puzzleAdvance(Map.of("playerId", PLAYER, "puzzleId", "puzzle-demo-obelisk",
                "regionId", "wolf-camp-valley"));
        Map<String, Object> puzzleDone = api.puzzleAdvance(Map.of(
                "playerId", PLAYER, "puzzleId", "puzzle-demo-obelisk",
                "regionId", "wolf-camp-valley"));
        assertThat(puzzleDone.get("solved")).isEqualTo(true);
        assertThat(puzzleDone.get("idempotencyKey").toString()).startsWith("puzzle:");

        // ── 4) 收集物分级：普通箱 → 神瞳提升探索技能/体力 ──
        assertThat(api.collectibleCollect(Map.of(
                "playerId", PLAYER, "collectibleId", "chest-common-1",
                "x", 100f, "y", 0f, "z", 100f, "regionId", "wolf-camp-valley"))
                .get("tier")).isEqualTo("COMMON_CHEST");
        Map<String, Object> oculus = api.collectibleCollect(Map.of(
                "playerId", PLAYER, "collectibleId", "oculus-anemo-1",
                "x", 180f, "y", 20f, "z", 160f, "regionId", "wolf-camp-valley"));
        assertThat(oculus.get("exploreSkillLevel")).isEqualTo(2);
        assertThat(oculus.get("staminaCapBonus")).isEqualTo(20);
        assertThat(api.collectibleProgress(PLAYER).get("oculus")).isInstanceOf(Map.class);

        // ── 5) 区域探索度凑阈值 → 声望奖励 ──
        openWorld.gameplay().regionProgress().markWaypoint(PLAYER, "wolf-camp-valley", "wp-a");
        openWorld.gameplay().regionProgress().markWaypoint(PLAYER, "wolf-camp-valley", "wp-b");
        openWorld.gameplay().regionProgress().markWaypoint(PLAYER, "wolf-camp-valley", "wp-c");
        openWorld.gameplay().regionProgress().markWorldQuest(PLAYER, "wolf-camp-valley", "wq-1");
        openWorld.gameplay().regionProgress().markWorldQuest(PLAYER, "wolf-camp-valley", "wq-2");
        openWorld.gameplay().regionProgress().markPuzzle(PLAYER, "wolf-camp-valley", "p-extra");
        Map<String, Object> regionProg = api.regionProgress(PLAYER, "wolf-camp-valley");
        @SuppressWarnings("unchecked")
        Map<String, Object> rp = (Map<String, Object>) regionProg.get("region_progress");
        assertThat((Integer) rp.get("percent")).isGreaterThanOrEqualTo(40);
        assertThat(regionProg.get("reputationUnlocked")).asList().isNotEmpty();

        // ── 6) 奇观：入口条件 + LOD 轮廓 ──
        Map<String, Object> lod = api.landmarkLod(500f, 80f, 500f, 400f);
        assertThat(lod.get("count")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.INTEGER).isGreaterThanOrEqualTo(2);
        assertThat(api.unlockTraverse(PLAYER, "CLIMB").get("ok")).isEqualTo(true);
        assertThat(api.landmarkEnter(PLAYER, "sealed-sanctum").get("ok")).isEqualTo(true);

        // ── 7) 角色绑定世界技 + 厨师天赋采集 + 篝火烹饪 ──
        Map<String, Object> bound = api.unlockModeBound(Map.of(
                "playerId", PLAYER, "mode", "GLIDE", "characterId", "char_venti"));
        assertThat(bound.get("ok")).isEqualTo(true);
        assertThat(bound.get("boundCharacter")).isEqualTo("char_venti");

        openWorld.gameplay().environment().setPartyTalents(PLAYER,
                java.util.Set.of(cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService.PartyTalent.CHEF));
        assertThat(api.gatherTalent(Map.of(
                "playerId", PLAYER, "itemId", "mint", "baseCount", 2, "isFoodIngredient", true))
                .get("count")).isEqualTo(4);

        openWorld.gameplay().cooking().grantIngredient(PLAYER, "fowl", 2);
        openWorld.gameplay().cooking().grantIngredient(PLAYER, "sweet_flower", 2);
        Map<String, Object> cook = api.cook(Map.of(
                "playerId", PLAYER, "campfireId", "camp-valley-1",
                "recipeId", "recipe-sweet-madame", "x", 110f, "z", 90f));
        assertThat(cook.get("ok")).isEqualTo(true);
        assertThat(cook.get("buff")).isInstanceOf(Map.class);

        // ── 8) 清营 → SAFE + Boss 可召唤 + 事件链 → 锚点防衰退 ──
        api.clearCamp("wolf-camp-valley", PLAYER);
        api.clearCamp("wolf-camp-valley", PLAYER);
        Map<String, Object> safe = api.clearCamp("wolf-camp-valley", PLAYER);
        assertThat(safe.get("safety")).isEqualTo("SAFE");
        assertThat(safe.get("bossSummonable")).isEqualTo(true);
        assertThat(api.regionBossLink("wolf-camp-valley").get("bossSummonable")).isEqualTo(true);
        assertThat(((Map<?, ?>) safe.get("eventChain")).get("started")).isEqualTo(true);

        api.regionChainAdvance("wolf-camp-valley", "escort");
        api.regionChainAdvance("wolf-camp-valley", "ambush");
        assertThat(api.regionChainAdvance("wolf-camp-valley", "unlock").get("completed"))
                .isEqualTo(true);

        assertThat(api.regionAnchor("wolf-camp-valley", PLAYER).get("anchorActive")).isEqualTo(true);
        assertThat(api.regionTickDecay("wolf-camp-valley").get("decayed")).isEqualTo(false);

        // ── 9) 异步社交：公共标记 / 幻影 / 区域频道蹭怪 ──
        Map<String, Object> mark = api.markPlace(Map.of(
                "playerId", PLAYER, "regionId", "wolf-camp-valley",
                "kind", "BOSS_ALERT", "message", "无相已刷新",
                "x", 1f, "y", 0f, "z", 1f, "requestPublic", true));
        @SuppressWarnings("unchecked")
        Map<String, Object> markView = (Map<String, Object>) mark.get("mark");
        long markId = ((Number) markView.get("markId")).longValue();
        openWorld.gameplay().publicMarks().moderate(markId, true);
        assertThat(api.markThank(markId, PLAYER + 1, 5).get("staminaGift")).isEqualTo(5);

        Map<String, Object> phantom = api.phantomLeave(Map.of(
                "playerId", PLAYER, "ownerName", "旅人P5",
                "sceneId", 1, "x", 10f, "y", 40f, "z", 10f,
                "pose", "JUMP_OFF_CLIFF",
                "emoteFrames", List.of("POSE1", "POSE2", "POSE3")));
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) phantom.get("phantom");
        assertThat(api.phantomPlay(String.valueOf(ph.get("phantomId"))).get("ok")).isEqualTo(true);
        assertThat(openWorld.gameplay().encounters()
                .listPhantomsNear(1, 10f, 10f, 20f)).isNotEmpty();

        openWorld.gameplay().regionChannel().joinChannel("wolf-camp-valley", PLAYER + 2, "server-b");
        Map<String, Object> bc = api.channelBroadcast(Map.of(
                "regionId", "wolf-camp-valley",
                "killerPlayerId", PLAYER,
                "eliteName", "狂风之核"));
        assertThat(bc.get("joinBattleButton")).isEqualTo(true);
        assertThat(api.channelJoinBattle(PLAYER + 2,
                ((Number) bc.get("battleId")).longValue(),
                String.valueOf(bc.get("joinBattleToken"))).get("rewardMode"))
                .isEqualTo("INDEPENDENT");

        // ── 10) 程序化洒点 + 配置段热更 ──
        Map<String, Object> fill = api.placementFill(Map.of(
                "regionId", "wolf-camp-valley",
                "targetCount", 80,
                "minDensityPerSqKm", 30f,
                "areaSqKm", 2f));
        assertThat(fill.get("placedCount")).isEqualTo(80);
        assertThat(fill.get("densityOk")).isEqualTo(true);

        assertThat(api.configPatch(Map.of(
                "gridCell", "1:9:9",
                "kind", "CHEST",
                "payload", Map.of("chestId", "chest-hot", "x", 99, "z", 99))).get("ok"))
                .isEqualTo(true);
        Map<String, Object> reload = api.configReload(Map.of("gridCells", List.of("1:9:9")));
        assertThat(reload.get("refreshed")).asList().contains("1:9:9");
    }

    @Test
    public void negativePaths_p5GatesAndIdempotency() {
        long p = 55_002L;

        // 解谜重复结算
        api.puzzleAdvance(Map.of("playerId", p, "puzzleId", "puzzle-demo-target"));
        api.puzzleAdvance(Map.of("playerId", p, "puzzleId", "puzzle-demo-target"));
        assertThat(api.puzzleAdvance(Map.of("playerId", p, "puzzleId", "puzzle-demo-target"))
                .get("solved")).isEqualTo(true);
        assertThat(api.puzzleAdvance(Map.of("playerId", p, "puzzleId", "puzzle-demo-target"))
                .get("error")).isEqualTo("already_solved");

        // 收集物距离 / 重复
        assertThat(api.collectibleCollect(Map.of(
                "playerId", p, "collectibleId", "chest-fine-1",
                "x", 0f, "y", 0f, "z", 0f)).get("error")).isEqualTo("out_of_range");
        assertThat(api.collectibleCollect(Map.of(
                "playerId", p, "collectibleId", "chest-fine-1",
                "x", 150f, "y", 0f, "z", 120f)).get("ok")).isEqualTo(true);
        assertThat(api.collectibleCollect(Map.of(
                "playerId", p, "collectibleId", "chest-fine-1",
                "x", 150f, "y", 0f, "z", 120f)).get("error")).isEqualTo("already_collected");

        // 奇观入口条件未满足
        api.unlockTraverse(p, "CLIMB");
        assertThat(api.landmarkEnter(p, "sealed-sanctum").get("error"))
                .isEqualTo("entry_condition_puzzles");

        // 角色无法解锁不匹配的移动手段；缺道具
        assertThat(api.unlockModeBound(Map.of(
                "playerId", p, "mode", "HOOK", "characterId", "char_zhongli"))
                .get("error")).isEqualTo("character_cannot_unlock_mode");
        assertThat(api.unlockModeBound(Map.of(
                "playerId", p, "mode", "CLIMB", "characterId", "char_zhongli"))
                .get("error")).isEqualTo("missing_item");
        assertThat(api.unlockModeBound(Map.of(
                "playerId", p, "mode", "CLIMB", "characterId", "char_zhongli",
                "ownedItems", List.of("item_geo_sigil"))).get("ok")).isEqualTo(true);

        // 烹饪：远离篝火 / 缺材料
        assertThat(api.cook(Map.of(
                "playerId", p, "campfireId", "camp-valley-1",
                "recipeId", "recipe-mint-jelly", "x", 0f, "z", 0f)).get("error"))
                .isEqualTo("too_far_from_campfire");
        assertThat(api.cook(Map.of(
                "playerId", p, "campfireId", "camp-valley-1",
                "recipeId", "recipe-mint-jelly", "x", 110f, "z", 90f)).get("error"))
                .isEqualTo("missing_ingredient");

        // 频道非法 token
        assertThat(api.channelJoinBattle(p, 999L, "bad-token").get("error"))
                .isEqualTo("invalid_token");

        // 配置热更缺失 cell
        Map<String, Object> miss = api.configReload(Map.of("gridCells", List.of("no-such-cell")));
        assertThat(miss.get("missing")).asList().contains("no-such-cell");
    }

    @Test
    public void regionChaosAndDecayWithoutAnchor() {
        long p = 55_003L;
        // 独立区域实例：直接操作 facade，避免与全局 SAFE 状态互相污染
        var regions = openWorld.gameplay().regions();
        regions.register(new cn.itcast.demo.mymmorpg.world.explore.RegionImpactService.RegionProfile(
                "chaos-test-zone", "混沌试炼谷", 1, 1,
                List.of("portal:chaos"), 5_000L, "boss-chaos"));
        regions.registerEventChain("chaos-test-zone", List.of(
                new cn.itcast.demo.mymmorpg.world.explore.RegionImpactService.EventChainStep(
                        "s1", "第一步", null, Map.of())));

        long t0 = 100_000L;
        Map<String, Object> safe = regions.clearMonsterCamp("chaos-test-zone", p, t0);
        assertThat(safe.get("safety")).isEqualTo("SAFE");

        Map<String, Object> chaos = regions.enterChaos("chaos-test-zone", t0 + 1);
        assertThat(chaos.get("safety")).isEqualTo("CHAOS");
        assertThat(regions.bossLinkStatus("chaos-test-zone").get("negativeWeather")).isEqualTo(true);

        // 重新清到 SAFE 后无锚点 → 潮汐退回
        regions.register(new cn.itcast.demo.mymmorpg.world.explore.RegionImpactService.RegionProfile(
                "decay-test-zone", "衰退谷", 1, 1,
                List.of(), 1_000L, null));
        regions.clearMonsterCamp("decay-test-zone", p, t0);
        Map<String, Object> decayed = regions.tickDecay("decay-test-zone", t0 + 1_000L);
        assertThat(decayed.get("decayed")).isEqualTo(true);
        assertThat(decayed.get("after")).isEqualTo("CONTESTED");
    }

    @Test
    public void minerTalentBonusOnStoneShatter() {
        long p = 55_004L;
        openWorld.gameplay().environment().setPartyTalents(p,
                java.util.Set.of(cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService.PartyTalent.MINER));
        // 重新注册一块石头避免被其他用例污染
        openWorld.gameplay().environment().register(
                new cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService.EnvObject(
                        "ore-rock-p5", cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService.EnvElement.STONE,
                        "IDLE", 1, 1f, 0f, 1f));
        Map<String, Object> shatter = api.envInteract(Map.of(
                "objectId", "ore-rock-p5", "skill", "GEO", "playerId", p));
        assertThat(shatter.get("to")).isEqualTo("SHATTERED");
        assertThat(shatter.get("lootBonus")).isInstanceOf(Map.class);
        assertThat(shatter.get("lootBonus").toString()).contains("MINER");
    }
}
