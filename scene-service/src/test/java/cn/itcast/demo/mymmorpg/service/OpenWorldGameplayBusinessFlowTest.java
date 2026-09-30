package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.world.sideplay.ExtractionMissionService;
import cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4 二游大世界完整业务流程（经 Runtime + Internal API）：
 * 探索奖励 → 换角开箱 → 奇观攀爬 → 清营改世界 → 剧情偶遇 →
 * 环境解谜 → 惊喜彩蛋 → 家园/捉宠/休闲 → 搜打撤撤离。
 */
public class OpenWorldGameplayBusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 42_001L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void statusExposesGameplayOverview() {
        Map<String, Object> status = openWorld.status();
        assertThat(status.get("ok")).isEqualTo(true);
        assertThat(status.get("gameplay")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> gp = (Map<String, Object>) status.get("gameplay");
        assertThat(gp.get("explorationPoints")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.INTEGER)
                .isGreaterThanOrEqualTo(3);
        assertThat(api.gameplayStatus(null, null).get("ok")).isEqualTo(true);
    }

    @Test
    public void fullAdventureJourney_exploreWonderRegionStoryExtract() {
        // 1) 景观打卡 → 核心成长资源
        Map<String, Object> vista = api.exploreDiscover(Map.of(
                "playerId", PLAYER, "pointId", "vista-sky-ruin",
                "x", 200f, "y", 40f, "z", 120f));
        assertThat(vista.get("ok")).isEqualTo(true);
        assertThat(vista.get("grantPlans")).asList().isNotEmpty();
        assertThat(String.valueOf(vista.get("idempotencyKey"))).startsWith("explore:");

        // 2) 无探索技能无法开隐藏宝箱 → 切七七感知 → 开箱得外观
        assertThat(api.exploreDiscover(Map.of(
                "playerId", PLAYER, "pointId", "chest-hidden-glyph",
                "x", 310f, "y", 2f, "z", 88f)).get("error"))
                .isEqualTo("missing_explore_skill");
        assertThat(api.switchExploreCharacter(PLAYER, "char_qiqi").get("ok")).isEqualTo(true);
        assertThat(api.senseHidden(PLAYER, Map.of("pointIds", List.of("chest-hidden-glyph")))
                .get("ok")).isEqualTo(true);
        Map<String, Object> chest = api.exploreDiscover(Map.of(
                "playerId", PLAYER, "pointId", "chest-hidden-glyph",
                "x", 310f, "y", 2f, "z", 88f));
        assertThat(chest.get("ok")).isEqualTo(true);
        assertThat(chest.get("grantPlans").toString()).contains("skin_cape_explorer");

        Map<String, Object> progress = api.exploreProgress(PLAYER);
        assertThat(progress.get("discoveredCount")).isEqualTo(2);

        // 3) 解锁移动手段 → 进入悬浮遗迹立体迷宫 → 通关写解谜位 + 解锁传送
        api.unlockTraverse(PLAYER, "HOOK");
        api.unlockTraverse(PLAYER, "GLIDE");
        api.unlockTraverse(PLAYER, "WIND_FIELD");
        assertThat(api.landmarkEnter(PLAYER, "floating-ruin").get("ok")).isEqualTo(true);
        api.landmarkAdvance(PLAYER, "floating-ruin");
        api.landmarkAdvance(PLAYER, "floating-ruin");
        Map<String, Object> wonderClear = api.landmarkAdvance(PLAYER, "floating-ruin");
        assertThat(wonderClear.get("cleared")).isEqualTo(true);
        assertThat(wonderClear.get("unlockIds")).asList().contains("portal:ruin-peak");
        assertThat(openWorld.worldState().getPuzzleBit(1, PLAYER, 17)).isTrue();

        // 4) 清怪营改变区域世界状态
        api.clearCamp("wolf-camp-valley", PLAYER);
        api.clearCamp("wolf-camp-valley", PLAYER);
        Map<String, Object> region = api.clearCamp("wolf-camp-valley", PLAYER);
        assertThat(region.get("safety")).isEqualTo("SAFE");
        assertThat(region.get("unlocked")).asList().contains("npc:rescued-merchant");
        assertThat(api.regionStatus("wolf-camp-valley").get("safety")).isEqualTo("SAFE");

        // 5) 剧情分支 + 世界偶遇
        api.storyStart(PLAYER, "story-open-1");
        Map<String, Object> story = api.storyChoose(PLAYER, "help");
        assertThat(story.get("nodeId")).isEqualTo("story-help-path");
        Map<String, Object> enc = api.encounterForce(PLAYER, "meet-ayaka-scenic", 1);
        assertThat(enc.get("triggered")).isEqualTo(true);

        // 6) 火烧藤蔓障碍 + 惊喜洞穴
        Map<String, Object> burn = api.envInteract(Map.of(
                "objectId", "vine-barrier-1", "skill", "FIRE", "playerId", PLAYER,
                "hourOfDay", 12));
        assertThat(burn.get("obstacleCleared")).isEqualTo(true);
        assertThat(burn.get("ruleTrigger")).isInstanceOf(Map.class);
        Map<String, Object> surprise = api.surpriseTry(Map.of(
                "playerId", PLAYER, "x", 90f, "y", 1f, "z", 20f, "hourOfDay", 12));
        assertThat(surprise.get("discovered")).isEqualTo(true);

        // 7) 家园建造 + 捉宠 + 钓鱼
        assertThat(api.homesteadClaim(Map.of(
                "playerId", PLAYER, "plotId", "plot-seaside-1", "x", 50f, "z", 50f))
                .get("ok")).isEqualTo(true);
        openWorld.gameplay().homestead().gatherMaterial(PLAYER, "wood", 10);
        assertThat(openWorld.gameplay().homestead()
                .craft(PLAYER, "bench", Map.of("wood", 3), "wood_bench").get("ok"))
                .isEqualTo(true);

        Map<String, Object> pet = openWorld.gameplay().creatures()
                .forceCatch(PLAYER, "wild-slime-1", System.currentTimeMillis());
        assertThat(pet.get("caught")).isEqualTo(true);

        assertThat(api.leisurePlay(Map.of(
                "playerId", PLAYER, "zoneId", "fish-lake-1",
                "x", 80f, "z", 40f, "inputScore", 40)).get("ok")).isEqualTo(true);

        // 8) 搜打撤：侦察 → 进攻 → 撤离发奖
        assertThat(api.extractStart(PLAYER, "extract-ruin-cache").get("phase"))
                .isEqualTo(ExtractionMissionService.Phase.SCOUT.name());
        assertThat(api.extractScout(Map.of("playerId", PLAYER, "x", 600f, "z", 600f))
                .get("phase")).isEqualTo(ExtractionMissionService.Phase.ASSAULT.name());
        assertThat(api.extractAssault(Map.of(
                "playerId", PLAYER, "x", 650f, "z", 650f, "lootGained", 88))
                .get("lootValue")).isEqualTo(88);
        Map<String, Object> extracted = api.extractFinish(Map.of(
                "playerId", PLAYER, "x", 580f, "z", 580f));
        assertThat(extracted.get("phase")).isEqualTo(ExtractionMissionService.Phase.COMPLETED.name());
        assertThat(extracted.get("grantPlans")).asList().isNotEmpty();
        assertThat(String.valueOf(extracted.get("idempotencyKey"))).startsWith("extract:");
    }

    @Test
    public void negativePaths_outOfRange_wrongPhase_failExtract() {
        long p = 42_002L;

        // 距离不够
        assertThat(api.exploreDiscover(Map.of(
                "playerId", p, "pointId", "vista-sky-ruin",
                "x", 0f, "y", 0f, "z", 0f)).get("error")).isEqualTo("out_of_range");

        // 奇观未解锁移动
        assertThat(api.landmarkEnter(p, "floating-ruin").get("error"))
                .isEqualTo("missing_traverse_mode");

        // 搜打撤阶段错乱 / 阵亡掉落
        api.extractStart(p, "extract-ruin-cache");
        assertThat(api.extractAssault(Map.of(
                "playerId", p, "x", 650f, "z", 650f, "lootGained", 50))
                .get("error")).isEqualTo("wrong_phase");
        api.extractScout(Map.of("playerId", p, "x", 600f, "z", 600f));
        api.extractAssault(Map.of("playerId", p, "x", 650f, "z", 650f, "lootGained", 50));
        Map<String, Object> failed = openWorld.gameplay().extraction().fail(p, "downed");
        assertThat(failed.get("phase")).isEqualTo(ExtractionMissionService.Phase.FAILED.name());
        assertThat(failed.get("lootLost")).isEqualTo(50);
    }

    @Test
    public void characterSwapExploreSkillsAndStoryAffinityFlags() {
        long p = 42_003L;
        api.switchExploreCharacter(p, "char_ayaka");
        Map<String, Object> translate = openWorld.gameplay().exploreSkills()
                .translate(p, "g1", "RUIN");
        assertThat(translate.get("plainText")).isEqualTo("NIUR");

        api.switchExploreCharacter(p, "char_venti");
        assertThat(api.senseHidden(p, Map.of("pointIds", List.of("a"))).get("ok")).isEqualTo(true);

        api.storyStart(p, "story-open-1");
        Map<String, Object> later = api.storyChoose(p, "later");
        assertThat(((Map<?, ?>) later.get("flags")).get("route")).isEqualTo("free");
        assertThat(((Map<?, ?>) later.get("flags")).get("affinity_ayaka")).isEqualTo(-2);
    }

    @Test
    public void environmentReactionsAndTraverseUnlockCatalog() {
        long p = 42_004L;
        for (String mode : List.of("HOOK", "GLIDE", "CLIMB", "SWIM", "VEHICLE", "WIND_FIELD")) {
            assertThat(api.unlockTraverse(p, mode).get("ok")).isEqualTo(true);
        }
        assertThat(openWorld.gameplay().traverse().modeNames(p))
                .contains(TraverseModeService.Mode.HOOK.name(),
                        TraverseModeService.Mode.VEHICLE.name());

        Map<String, Object> melt = api.envInteract(Map.of(
                "objectId", "oil-patch-1",
                "skill", EnvironmentInteractionService.SkillElement.FIRE.name(),
                "playerId", p));
        assertThat(melt.get("to")).isEqualTo("ASH");

        Map<String, Object> stone = openWorld.gameplay().environment()
                .throwStone("throw-stone-1", 11f, 22f, p);
        assertThat(stone.get("landedX")).isEqualTo(11f);
    }

    @Test
    public void encounterCooldownAndSurpriseEmoteTrigger() {
        long p = 42_005L;
        Map<String, Object> first = api.encounterForce(p, "meet-bennett-commission", 1);
        assertThat(first.get("triggered")).isEqualTo(true);

        // 随机偶遇在冷却池耗尽时 miss（强制刚触发后仍可能抽到其他）
        Map<String, Object> tryEnc = api.encounterTry(p, 1);
        assertThat(tryEnc.get("ok")).isEqualTo(true);

        Map<String, Object> miss = api.surpriseTry(Map.of(
                "playerId", p, "x", 300f, "y", 0f, "z", 300f,
                "action", "WAVE", "hourOfDay", 12));
        assertThat(miss.get("discovered")).isEqualTo(false);

        Map<String, Object> dance = api.surpriseTry(Map.of(
                "playerId", p, "x", 300f, "y", 0f, "z", 300f,
                "action", "DANCE", "hourOfDay", 12));
        assertThat(dance.get("discovered")).isEqualTo(true);
        assertThat(dance.get("grantPlans")).asList().isNotEmpty();
    }

    @Test
    public void leisureZonesAndHomesteadDuplicateClaimRejected() {
        long p = 42_006L;
        assertThat(api.homesteadClaim(Map.of(
                "playerId", p, "plotId", "plot-seaside-1", "x", 50f, "z", 50f)).get("ok"))
                .isEqualTo(true);
        assertThat(api.homesteadClaim(Map.of(
                "playerId", p, "plotId", "plot-seaside-1", "x", 50f, "z", 50f)).get("error"))
                .isEqualTo("already_owns_plot");

        assertThat(api.leisurePlay(Map.of(
                "playerId", p, "zoneId", "race-coast-1",
                "x", 400f, "z", 100f, "inputScore", 3500)).get("ok")).isEqualTo(true);
        assertThat(api.leisurePlay(Map.of(
                "playerId", p, "zoneId", "rhythm-plaza-1",
                "x", 150f, "z", 150f, "inputScore", 95000)).get("ok")).isEqualTo(true);
        assertThat(api.leisurePlay(Map.of(
                "playerId", p, "zoneId", "fish-lake-1",
                "x", 0f, "z", 0f, "inputScore", 10)).get("error")).isEqualTo("out_of_zone");
    }
}
