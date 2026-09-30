package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryStateMachine;
import cn.itcast.demo.mymmorpg.world.sideplay.HandbookService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11 完整业务流程（经 Runtime + Internal API）：
 * 队伍共鸣 → 传说锁区 → 图鉴/贫瘠 → 指挥集火 → 地形冷却 → 肉鸽命运卡闭环。
 */
public class OpenWorldP11BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 200_011L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP11ApiJourney_teamStoryHandbookMarkTerrainRogue() {
        // ── 1) 队伍共鸣刷新 ──
        Map<String, Object> resonance = api.teamResonanceRefresh(body(
                "playerId", PLAYER,
                "elements", List.of("PYRO", "PYRO", "CRYO", "ANEMO")));
        assertThat(resonance.get("ok")).isEqualTo(true);
        assertThat(resonance.get("resonance_id")).isEqualTo("resonance_pyro_2");
        assertThat(((Map<?, ?>) resonance.get("attribute_modifiers")).get("atkPct")).isEqualTo(0.25);
        assertThat(resonance.get("clientHint").toString()).contains("双火");

        // ── 2) 传说实例：开始 / 选项 / 推进 / 完成 ──
        Map<String, Object> storyStart = api.storyInstanceStart(body(
                "playerId", PLAYER,
                "storyId", "legend-ayaka-1"));
        assertThat(storyStart.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) storyStart.get("notify")).get("msgId"))
                .isEqualTo(MessageId.STORY_INSTANCE_START_SC_NOTIFY);
        assertThat(openWorld.gameplay().instanceRegionLock().isTideLocked("wolf-camp-valley"))
                .isTrue();

        Map<String, Object> choose = api.storyInstanceChoose(body(
                "playerId", PLAYER,
                "storyId", "legend-ayaka-1",
                "optionId", "accept"));
        assertThat(choose.get("ok")).isEqualTo(true);
        assertThat(choose.get("state")).isEqualTo(StoryStateMachine.State.ESCORT.name());

        Map<String, Object> advance = api.storyInstanceAdvance(body(
                "playerId", PLAYER,
                "storyId", "legend-ayaka-1",
                "state", "COMBAT"));
        assertThat(advance.get("ok")).isEqualTo(true);

        Map<String, Object> complete = api.storyInstanceComplete(body(
                "playerId", PLAYER,
                "storyId", "legend-ayaka-1"));
        assertThat(complete.get("completed")).isEqualTo(true);
        assertThat(openWorld.gameplay().instanceRegionLock().isTideLocked("wolf-camp-valley"))
                .isFalse();

        // ── 3) 图鉴发现 + 进度查询 + 过度采集 ──
        for (String id : List.of("crystal_fox", "anemo_slime", "boar", "crystal_butterfly")) {
            Map<String, Object> disc = api.handbookDiscover(body(
                    "playerId", PLAYER,
                    "kind", "CREATURE",
                    "entryId", id));
            assertThat(disc.get("ok")).isEqualTo(true);
        }
        Map<String, Object> progress = api.handbookProgress(PLAYER);
        assertThat(progress.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> creatureProg = (Map<String, Object>) progress.get("creature");
        assertThat(creatureProg.get("collectorReady")).isEqualTo(true);

        Map<String, Object> gather = null;
        for (int i = 0; i <= HandbookService.DAILY_GATHER_LIMIT; i++) {
            gather = api.handbookGather(body(
                    "playerId", PLAYER,
                    "regionId", "wolf-camp-valley",
                    "resourceType", "herb"));
        }
        assertThat(gather.get("overGather")).isEqualTo(true);
        assertThat(gather.get("debuff")).isEqualTo("RESOURCE_BARREN");

        // ── 4) 攻城 + 队长快速标记集火 ──
        Map<String, Object> siege = api.siegeCreate(body(
                "roomId", "api-siege-1",
                "leaderId", 9001L));
        assertThat(siege.get("ok")).isEqualTo(true);

        Map<String, Object> mark = api.squadQuickMark(body(
                "squadId", "squad-archer-1",
                "leaderId", 9001L,
                "x", 11f, "y", 2f, "z", 33f,
                "bossPartId", "LEFT_LEG",
                "focusFire", true,
                "siegeRoomId", "api-siege-1"));
        assertThat(mark.get("ok")).isEqualTo(true);
        assertThat(mark.get("partDamageBonus")).isEqualTo(SquadCommanderService.FOCUS_DAMAGE_BONUS);
        assertThat(((Map<?, ?>) mark.get("broadcast")).get("msgId"))
                .isEqualTo(MessageId.MARK_TARGET_SC_NOTIFY);

        Map<String, Object> brk = api.siegeBreakPart(body(
                "roomId", "api-siege-1",
                "part", "LEFT_LEG"));
        assertThat(brk.get("ok")).isEqualTo(true);
        // HTTP break-part 走无伤害参数重载；直接校验 focus 倍率仍生效于服务
        assertThat(openWorld.gameplay().siegeWar()
                .focusDamageMul("api-siege-1", "LEFT_LEG", System.currentTimeMillis()))
                .isEqualTo(1.0 + SquadCommanderService.FOCUS_DAMAGE_BONUS);

        // ── 5) 弹射蘑菇冷却：admit → TERRAIN_EXHAUSTED ──
        Map<String, Object> bounce1 = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "BOUNCE",
                "x", 1f, "y", 0f, "z", 1f,
                "regionId", "wolf-camp-valley",
                "terrainCellX", 9,
                "terrainCellY", 9,
                "terrainTtlMs", 60_000L));
        assertThat(bounce1.get("ok")).isEqualTo(true);

        Map<String, Object> bounce2 = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "BOUNCE",
                "x", 1f, "y", 0f, "z", 1f,
                "regionId", "wolf-camp-valley",
                "terrainCellX", 9,
                "terrainCellY", 9,
                "terrainTtlMs", 60_000L));
        assertThat(bounce2.get("ok")).isEqualTo(false);
        assertThat(bounce2.get("retcode")).isEqualTo(RetCode.TERRAIN_EXHAUSTED);
        assertThat(bounce2.get("witherAnim")).isEqualTo(true);
        assertThat(bounce2.get("consumeStamina")).isEqualTo(false);

        // ── 6) 肉鸽：准备探索/亲密度 → start(region_id) → settle ──
        for (int i = 0; i < 3; i++) {
            openWorld.gameplay().regionProgress()
                    .markWaypoint(PLAYER, "wolf-camp-valley", "api-wp-" + i);
            openWorld.gameplay().regionProgress()
                    .markCollectible(PLAYER, "wolf-camp-valley", "api-c-" + i);
            openWorld.gameplay().regionProgress()
                    .markPuzzle(PLAYER, "wolf-camp-valley", "api-p-" + i);
            openWorld.gameplay().regionProgress()
                    .markWorldQuest(PLAYER, "wolf-camp-valley", "api-q-" + i);
        }
        for (int i = 0; i < 20; i++) {
            openWorld.gameplay().affinity().feed(PLAYER, "eco-fox-1", AffinityService.FOOD_ITEM, 1);
        }
        openWorld.gameplay().regions().enterChaos("wolf-camp-valley", System.currentTimeMillis());

        Map<String, Object> rogueStart = api.rogueStart(body(
                "playerId", PLAYER,
                "rogueId", 701,
                "region_id", "wolf-camp-valley"));
        assertThat(rogueStart.get("ok")).isEqualTo(true);
        assertThat(rogueStart.get("mapLinked")).isEqualTo(true);
        assertThat(((List<?>) rogueStart.get("fateCards")).size()).isGreaterThanOrEqualTo(1);

        Map<String, Object> settle = api.rogueSettle(body(
                "playerId", PLAYER,
                "cleared", true));
        assertThat(settle.get("loopClosed")).isEqualTo(true);
        assertThat(((Map<?, ?>) settle.get("tidePurify")).get("tidePurify")).isEqualTo(25);
        assertThat(openWorld.gameplay().regions().snapshot("wolf-camp-valley").get("safety"))
                .isEqualTo(RegionImpactService.RegionSafety.CHAOS.name());
    }

    @Test
    public void apiNegativePaths_resonanceEmpty_storyBad_markNonLeader() {
        Map<String, Object> emptyTeam = api.teamResonanceRefresh(body(
                "playerId", PLAYER,
                "elements", List.of("ANEMO", "GEO", "DENDRO", "ELECTRO")));
        assertThat(emptyTeam.get("ok")).isEqualTo(true);
        assertThat(emptyTeam.get("resonance_id")).isEqualTo("");

        Map<String, Object> badStory = api.storyInstanceStart(body(
                "playerId", PLAYER,
                "storyId", "missing-story"));
        assertThat(badStory.get("ok")).isEqualTo(false);

        Map<String, Object> notLeader = api.squadQuickMark(body(
                "squadId", "squad-archer-1",
                "leaderId", PLAYER,
                "x", 0, "y", 0, "z", 0,
                "bossPartId", "CORE",
                "focusFire", false));
        assertThat(notLeader.get("error")).isEqualTo("not_squad_leader");

        Map<String, Object> rogueNoRegion = api.rogueStart(body(
                "playerId", PLAYER,
                "rogueId", 1,
                "region_id", ""));
        assertThat(rogueNoRegion.get("ok")).isEqualTo(false);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
