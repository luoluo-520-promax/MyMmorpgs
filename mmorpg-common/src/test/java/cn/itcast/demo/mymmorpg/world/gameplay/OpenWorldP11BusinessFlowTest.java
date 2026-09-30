package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryStateMachine;
import cn.itcast.demo.mymmorpg.world.sideplay.HandbookService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11 端到端业务流（门面直调）：
 * 配队共鸣 → 传说锁区 → 图鉴收藏 → 攻城指挥 → 弹射冷却 → 肉鸽治世界。
 */
public class OpenWorldP11BusinessFlowTest {

    private static final long PLAYER = 88_001L;

    @Test
    public void fullP11Journey_resonanceStoryHandbookMarkTerrainRogue() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 1_000_000L;

        // ── 1) 配队：双火+双水，激活两套共鸣并注入 WorldBuff ──
        Map<String, Object> team = g.teamComposition().refresh(
                PLAYER, List.of("PYRO", "FIRE", "HYDRO", "WATER"), t0);
        assertThat(team.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resonances = (List<Map<String, Object>>) team.get("resonances");
        assertThat(resonances).hasSize(2);
        assertThat(((Map<?, ?>) team.get("attribute_modifiers")).get("atkPct")).isEqualTo(0.25);
        assertThat(((Map<?, ?>) team.get("attribute_modifiers")).get("hpPct")).isEqualTo(0.25);
        assertThat(g.teamComposition().battleBuffs(PLAYER, t0)).isNotEmpty();
        assertThat(team.get("clientHint").toString()).contains("激活");

        // ── 2) 传说任务：对话 → 护送 → 战斗 → 领奖，期间潮汐锁定 ──
        Map<String, Object> storyStart = g.storyStateMachine()
                .startInstance(PLAYER, "legend-ayaka-1", t0 + 10);
        assertThat(storyStart.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) storyStart.get("notify")).get("mustPlay")).isEqualTo(true);
        assertThat(((Map<?, ?>) storyStart.get("notify")).get("msgId"))
                .isEqualTo(MessageId.STORY_INSTANCE_START_SC_NOTIFY);
        assertThat(g.instanceRegionLock().isTideLocked("wolf-camp-valley")).isTrue();
        g.regions().forceSafety("wolf-camp-valley", RegionImpactService.RegionSafety.SAFE);
        Map<String, Object> lockedDecay = g.regions().tickDecay("wolf-camp-valley", t0 + 99_999_999L);
        assertThat(lockedDecay.get("tideLocked")).isEqualTo(true);
        assertThat(lockedDecay.get("decayed")).isEqualTo(false);

        Map<String, Object> choose = g.storyStateMachine()
                .chooseDialogue(PLAYER, "legend-ayaka-1", "accept", t0 + 20);
        assertThat(choose.get("state")).isEqualTo(StoryStateMachine.State.ESCORT.name());
        assertThat(g.storyStateMachine().npcAffinityOf(PLAYER, "char_ayaka")).isEqualTo(15);

        assertThat(g.storyStateMachine()
                .advance(PLAYER, "legend-ayaka-1", StoryStateMachine.State.COMBAT).get("ok"))
                .isEqualTo(true);
        Map<String, Object> storyDone = g.storyStateMachine()
                .complete(PLAYER, "legend-ayaka-1", t0 + 30);
        assertThat(storyDone.get("completed")).isEqualTo(true);
        assertThat(storyDone.get("grantPlans")).isNotNull();
        assertThat(g.instanceRegionLock().isTideLocked("wolf-camp-valley")).isFalse();
        assertThat(g.storyStateMachine().dialogueLibrary(PLAYER, "char_ayaka").size())
                .isGreaterThanOrEqualTo(2);

        // ── 3) 生活图鉴：首次发现 → 达 80% 发名片 → 过度采集贫瘠 ──
        String[] creatures = {"crystal_fox", "anemo_slime", "boar", "crystal_butterfly", "snow_fox"};
        Map<String, Object> collector = null;
        for (int i = 0; i < creatures.length; i++) {
            Map<String, Object> d = g.handbook().discover(
                    PLAYER, HandbookService.EntryKind.CREATURE, creatures[i], t0 + 100 + i, false);
            assertThat(d.get("ok")).isEqualTo(true);
            if (i == 0) {
                assertThat(d.get("firstDiscover")).isEqualTo(true);
            }
            if (Boolean.TRUE.equals(((Map<?, ?>) d.get("progress")).get("collectorReady"))
                    && d.get("mailGrant") != null
                    && Boolean.TRUE.equals(((Map<?, ?>) d.get("mailGrant")).get("granted"))) {
                collector = d;
            }
        }
        assertThat(collector).isNotNull();
        assertThat(((Map<?, ?>) collector.get("mailGrant")).get("title")).isEqualTo("图鉴收集者");
        Map<String, Object> food = g.handbook().discover(
                PLAYER, HandbookService.EntryKind.FOOD, "food_sweet_madame", t0 + 200, true);
        assertThat(food.get("perfectCookCount")).isEqualTo(1);

        Map<String, Object> gather = null;
        for (int i = 0; i <= HandbookService.DAILY_GATHER_LIMIT; i++) {
            gather = g.handbook().recordGather(PLAYER, "wolf-camp-valley", "flower", t0 + 300);
        }
        assertThat(gather.get("overGather")).isEqualTo(true);
        assertThat(g.regions().gatherYieldMul("wolf-camp-valley", t0 + 300)).isEqualTo(0.5);

        // ── 4) 攻城指挥：队长标记左腿集火 → 部位破击吃增伤 ──
        g.siegeWar().createSiegeRoom("siege-p11", 9001L);
        g.siegeWar().join("siege-p11", PLAYER);
        Map<String, Object> mark = g.squadCommander().quickMark(
                "squad-archer-1", 9001L, 120f, 5f, 80f, "LEFT_LEG", true, "siege-p11", t0 + 400);
        assertThat(mark.get("ok")).isEqualTo(true);
        assertThat(mark.get("command")).isEqualTo(SquadCommanderService.CMD_FOCUS_FIRE);
        assertThat(((Map<?, ?>) mark.get("broadcast")).get("event")).isEqualTo("MARK_TARGET");
        assertThat(((Map<?, ?>) mark.get("siegeFocus")).get("ok")).isEqualTo(true);

        Map<String, Object> partBreak = g.siegeWar().breakPart(
                "siege-p11", SiegeWarService.BossPart.LEFT_LEG, 200, t0 + 450);
        assertThat(((Number) partBreak.get("damageMul")).doubleValue()).isEqualTo(1.15);
        assertThat(partBreak.get("effectiveDamage")).isEqualTo(230);
        assertThat(partBreak.get("moveSpeedMul")).isEqualTo(0.5);

        // ── 5) 弹射蘑菇：成功 → 枯萎拒绝且不扣体力 → TTL 后可再踩 ──
        SceneMoveCmd bounce = new SceneMoveCmd(
                10f, 0f, 10f, 12f, t0 + 500, MovementType.BOUNCE, "", 0, "", "", 0, 0, 0);
        float stamina0 = g.stamina().current(PLAYER);
        Map<String, Object> bounceOk = g.movementAdmission().admit(
                PLAYER, bounce, 200L, t0 + 500, 0, 0, 0, "wolf-camp-valley", 7, 8, 45_000L);
        assertThat(bounceOk.get("ok")).isEqualTo(true);
        assertThat(bounceOk.get("bounce")).isEqualTo(true);
        assertThat(g.stamina().current(PLAYER)).isEqualTo(stamina0);

        Map<String, Object> wither = g.movementAdmission().admit(
                PLAYER, bounce, 200L, t0 + 600, 0, 0, 0, "wolf-camp-valley", 7, 8, 45_000L);
        assertThat(wither.get("ok")).isEqualTo(false);
        assertThat(wither.get("retcode")).isEqualTo(RetCode.TERRAIN_EXHAUSTED);
        assertThat(wither.get("witherAnim")).isEqualTo(true);
        assertThat(wither.get("consumeStamina")).isEqualTo(false);
        assertThat(g.stamina().current(PLAYER)).isEqualTo(stamina0);

        Map<String, Object> refreshed = g.movementAdmission().admit(
                PLAYER, bounce, 200L, t0 + 500 + 45_000L + 1, 0, 0, 0,
                "wolf-camp-valley", 7, 8, 45_000L);
        assertThat(refreshed.get("ok")).isEqualTo(true);

        // ── 6) 肉鸽命运卡：高探索+亲密度 → 通关净化，打本治世界 ──
        for (int i = 0; i < 4; i++) {
            g.regionProgress().markWaypoint(PLAYER, "wolf-camp-valley", "biz-wp-" + i);
            g.regionProgress().markCollectible(PLAYER, "wolf-camp-valley", "biz-c-" + i);
            g.regionProgress().markPuzzle(PLAYER, "wolf-camp-valley", "biz-p-" + i);
            g.regionProgress().markWorldQuest(PLAYER, "wolf-camp-valley", "biz-q-" + i);
        }
        for (int i = 0; i < 25; i++) {
            g.affinity().feed(PLAYER, "eco-fox-1", AffinityService.FOOD_ITEM, 1);
        }
        g.regions().enterChaos("wolf-camp-valley", t0 + 700);
        assertThat(g.regions().snapshot("wolf-camp-valley").get("safety"))
                .isEqualTo(RegionImpactService.RegionSafety.CHAOS.name());

        Map<String, Object> rogueStart = g.rogueFate().startWithRegion(
                PLAYER, 501, "wolf-camp-valley", t0 + 800);
        assertThat(rogueStart.get("ok")).isEqualTo(true);
        assertThat(rogueStart.get("mapLinked")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) rogueStart.get("fateCards");
        assertThat(cards.size()).isGreaterThanOrEqualTo(2);
        assertThat(cards.stream().anyMatch(c -> "EXPLORATION".equals(c.get("type")))).isTrue();
        assertThat(cards.stream().anyMatch(c -> "AFFINITY".equals(c.get("type")))).isTrue();
        assertThat(g.rogueFate().elementResistOf(PLAYER)).containsKey("CRYO");

        // 多次通关累计净化至 SAFE
        g.rogueFate().settleClear(PLAYER, true, t0 + 900);
        for (int i = 0; i < 3; i++) {
            g.rogueFate().startWithRegion(PLAYER, 502 + i, "wolf-camp-valley", t0 + 910 + i);
            g.rogueFate().settleClear(PLAYER, true, t0 + 920 + i);
        }
        assertThat(g.regions().snapshot("wolf-camp-valley").get("safety"))
                .isEqualTo(RegionImpactService.RegionSafety.SAFE.name());
    }
}
