package cn.itcast.demo.mymmorpg.ai;

import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine;
import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 端到端业务流程：单人探索 → 伙伴陪伴 → Boss 战后叙事 → 战斗复盘 → 截图寻路 → 人格化建议。
 */
public class AiP15BusinessFlowTest {

    private AiPlatformFacade facade;
    private long playerId;

    @BeforeMethod
    public void setUp() {
        facade = AiPlatformFacade.createDefault();
        playerId = 15_001L;
    }

    @Test
    public void fullSoloExploreJourney_companionNarrativeAnalystVisionPersona() {
        // 1) 设置 AI 交流风格 + 解析人格（休闲种田党）
        facade.personaSetStyle(playerId, "感性");
        Map<String, Object> persona = facade.personaResolve(Map.of(
                "playerId", playerId,
                "handbookProgress", 0.25,
                "regionExplorePercent", 22,
                "abyssStars", 2,
                "friendAssistCount", 0,
                "chatMessages7d", 1,
                "style", "感性"));
        assertThat(persona.get("ok")).isEqualTo(true);
        assertThat(persona.get("cluster")).isEqualTo("CASUAL");
        assertThat(persona.get("style")).isEqualTo("EMOTIONAL");
        assertThat(String.valueOf(persona.get("PLAYER_PERSONA"))).contains("CASUAL");

        // 2) 探索途中积累伙伴记忆（坠崖、采花）
        facade.companionRemember(Map.of("playerId", playerId, "type", "FALL", "summary", "又坠崖了"));
        facade.companionRemember(Map.of("playerId", playerId, "type", "FLOWER", "summary", "采甜甜花"));
        facade.companionRemember(Map.of("playerId", playerId, "type", "COMBAT", "summary", "CAUTIOUS 打法"));
        Map<String, Object> mem = facade.companion().toMap(facade.companion().get(playerId));
        assertThat(((Number) mem.get("fallCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) mem.get("flowerPicks")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(mem.get("combatStyle")).isEqualTo("CAUTIOUS");
        assertThat(mem.get("redisKey")).isEqualTo(CompanionBotService.REDIS_KEY_PREFIX + playerId);

        // 3) 伙伴气泡对话（MsgId 2500）
        Map<String, Object> dialogue = facade.companionDialogue(Map.of(
                "playerId", playerId, "text", "前面有花", "mood", "CALM"));
        assertThat(dialogue.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dialogue.get("dialogue");
        assertThat(bubble.get("msgId")).isEqualTo(MessageId.COMPANION_DIALOGUE_SC_NOTIFY);
        assertThat(bubble.get("ttsEmotion")).isNotNull();
        assertThat(Boolean.TRUE.equals(((Map<?, ?>) bubble.get("ui")).get("bubble"))).isTrue();

        // 4) 低血量战况 → 伙伴建议盾墙撤退
        SquadCommanderService squad = new SquadCommanderService();
        squad.register(new SquadCommanderService.SquadTemplate("squad-p15", playerId, List.of(playerId + 1)));
        facade.companion().bindSquad(squad);
        Map<String, Object> coach = facade.companionBattleCoach(Map.of(
                "playerId", playerId,
                "squadId", "squad-p15",
                "partyHpRatio", 0.2,
                "bossHpRatio", 0.7));
        assertThat(coach.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> inject = (Map<String, Object>) coach.get("inject");
        assertThat(inject.get("suggestedCommand")).isEqualTo(SquadCommanderService.CMD_SHIELD_WALL);
        @SuppressWarnings("unchecked")
        Map<String, Object> squadResult = (Map<String, Object>) inject.get("squadResult");
        assertThat(squadResult.get("ok")).isEqualTo(true);

        // 5) 刷好感到阈值 → 被动解锁调查点 / 有趣标记
        CompanionBotService bot = facade.companion();
        for (int i = 0; i < 40; i++) {
            bot.remember(playerId, "NOTE", "并肩探索 " + i);
        }
        Map<String, Object> explore = facade.companionPassiveExplore(Map.of(
                "playerId", playerId, "regionId", "1", "nowMs", System.currentTimeMillis()));
        assertThat(explore.get("surveyUnlocked")).isEqualTo(true);
        assertThat(explore.get("hiddenSurvey")).isNotNull();
        if (Boolean.TRUE.equals(explore.get("markerUnlocked"))) {
            assertThat(explore.get("interestingMarkerId")).asString().startsWith("companion-poi-");
        }

        // 6) Boss 击杀后世界情境叙事（MOURN + 情感标签）
        Map<String, Object> narrative = facade.narrativeGenerate(Map.of(
                "playerId", playerId,
                "npcId", "katherine",
                "triggerEventId", "BOSS_DRAGON_DOWN",
                "weather", "ASH",
                "epoch", "WAR",
                "regionTide", "CHAOS",
                "chronicleFlags", List.of("BOSS_DEFEAT_DRAGON"),
                "npcTrust", 0.85,
                "specialBagItems", List.of()));
        assertThat(narrative.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) narrative.get("intent");
        assertThat(intent.get("intent")).isEqualTo("MOURN");
        assertThat(narrative.get("emotionTag")).isEqualTo("SORROW");
        assertThat(narrative.get("pipeline")).isEqualTo("B");
        assertThat(String.valueOf(narrative.get("dialogue"))).isNotBlank();

        // 7) 战斗回放分析 → 失误时间轴 + 强化深链
        BattleReplayService replays = facade.battleReplays();
        Map<String, Object> start = replays.start("p15-biz-" + playerId, 99L, System.currentTimeMillis());
        String replayId = String.valueOf(start.get("replayId"));
        replays.append(replayId, 10, 1_000, "SKILL_BURST", Map.of("castDelaySec", 0.9));
        replays.append(replayId, 20, 2_000, "DODGE", Map.of("avoidedHit", false, "inIFrameWindow", false));
        replays.append(replayId, 30, 3_000, "ELEMENT_REACTION", Map.of("reactionApplied", false));
        replays.append(replayId, 40, 4_000, "HIT_FEEDBACK", Map.of("latencyMs", 150));

        Map<String, Object> report = facade.analystFeedback(Map.of(
                "playerId", playerId,
                "replayId", replayId,
                "applyEnhanceHints", true,
                "handbookProgress", 0.25,
                "regionExplorePercent", 22,
                "abyssStars", 2,
                "style", "感性"));
        assertThat(report.get("ok")).isEqualTo(true);
        assertThat(report.get("advice")).asString().isNotBlank();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) report.get("mistakeTimeline");
        assertThat(timeline).isNotEmpty();
        assertThat(timeline.stream().anyMatch(m -> Boolean.TRUE.equals(m.get("highlight")))).isTrue();
        assertThat(report.get("enhanceDeepLink")).isNotNull();
        assertThat(report.get("persona")).isNotNull();

        // 8) 截图求助 → 前方巨石路点
        Map<String, Object> waypoint = facade.screenWaypointHelp(Map.of(
                "playerId", playerId,
                "regionId", "1",
                "visualTags", List.of("boulder", "巨石"),
                "x", 100, "y", 0, "z", 100,
                "nowMs", System.currentTimeMillis()));
        assertThat(waypoint.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> guide = (Map<String, Object>) waypoint.get("guide");
        assertThat(guide.get("description")).asString().contains("巨石");
        assertThat(guide.get("arrow")).isNotNull();
        assertThat(guide.get("markerId")).isEqualTo("wp-boulder");

        // 9) 人格化战术建议（CASUAL 语气）
        Map<String, Object> tactical = facade.tacticalAdvise(Map.of(
                "playerId", playerId,
                "bossHpRatio", 0.5,
                "partyHpRatio", 0.8,
                "hasElementShield", true,
                "weakElement", "火",
                "handbookProgress", 0.25,
                "regionExplorePercent", 22,
                "abyssStars", 2));
        assertThat(tactical.get("ok")).isEqualTo(true);
        assertThat(tactical.get("personaCluster")).isEqualTo("CASUAL");
        assertThat(String.valueOf(tactical.get("hint"))).contains("慢慢来");

        // 10) 健康检查含 P15 能力位
        Map<String, Object> health = facade.health();
        assertThat(health.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> p15 = (Map<String, Object>) health.get("p15");
        assertThat(p15).containsKeys("narrative", "companion", "combatAnalyst", "screenWaypoint", "persona");
    }

    @Test
    public void hardcorePlayerJourney_preciseAnalystAndPersona() {
        long hardId = 15_002L;
        facade.personaSetStyle(hardId, "简洁");
        Map<String, Object> persona = facade.personaResolve(Map.of(
                "playerId", hardId,
                "handbookProgress", 0.95,
                "regionExplorePercent", 88,
                "abyssStars", 15,
                "friendAssistCount", 1,
                "chatMessages7d", 2));
        assertThat(persona.get("cluster")).isEqualTo("HARDCORE");

        Map<String, Object> report = facade.analystFeedback(Map.of(
                "playerId", hardId,
                "replayId", "inline",
                "applyEnhanceHints", true,
                "abyssStars", 15,
                "regionExplorePercent", 88,
                "handbookProgress", 0.95,
                "frames", List.of(
                        Map.of("tick", 1, "tsMs", 10L, "actionType", "SKILL_BURST",
                                "payload", Map.of("castDelaySec", 0.8, "wastedCharge", true)),
                        Map.of("tick", 2, "tsMs", 20L, "actionType", "ELEMENT_REACTION",
                                "payload", Map.of("reactionApplied", false)))));
        assertThat(report.get("ok")).isEqualTo(true);
        assertThat(String.valueOf(report.get("advice"))).containsAnyOf("充能", "idle", "【数据】", "帧数");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hints = (List<Map<String, Object>>) report.get("equipHints");
        assertThat(hints).isNotEmpty();
        assertThat(hints.get(0)).containsKeys("slot", "stat", "reason");
    }

    @Test
    public void bagItemTriggersNarrativeReactItem() {
        Map<String, Object> out = facade.narrativeGenerate(Map.of(
                "playerId", playerId,
                "npcId", "guide",
                "triggerEventId", "IDLE",
                "specialBagItems", List.of("龙血结晶"),
                "npcTrust", 0.4));
        assertThat(out.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) out.get("intent");
        assertThat(intent.get("intent")).isEqualTo("REACT_ITEM");
        assertThat(String.valueOf(out.get("dialogue"))).contains("龙血结晶");
    }
}
