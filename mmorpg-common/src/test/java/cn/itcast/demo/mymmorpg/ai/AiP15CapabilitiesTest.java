package cn.itcast.demo.mymmorpg.ai;

import cn.itcast.demo.mymmorpg.ai.analyst.CombatAnalystService;
import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService;
import cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine;
import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 五项能力单元与门面联通测试。
 */
public class AiP15CapabilitiesTest {

    @Test
    public void dynamicNarrative_bossDefeatProducesMournIntent() {
        DynamicNarrativeService svc = new DynamicNarrativeService();
        Map<String, Object> out = svc.generate(new DynamicNarrativeService.WorldSnapshot(
                1001L, "katherine", "CLEAR", "PEACE", "CALM", "BOSS_DRAGON_DOWN",
                List.of("BOSS_DEFEAT_DRAGON"), 0.8, 0.1, List.of(), Map.of()));
        assertThat(out.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) out.get("intent");
        assertThat(intent.get("intent")).isEqualTo("MOURN");
        assertThat(intent.get("emotion")).isEqualTo("SORROW");
        assertThat(String.valueOf(out.get("dialogue"))).contains("硫磺");
    }

    @Test
    public void companionDialogue_msgId2500AndMemory() {
        CompanionBotService bot = new CompanionBotService();
        bot.remember(7L, "FALL", "坠崖了");
        bot.remember(7L, "FLOWER", "采甜甜花");
        Map<String, Object> dlg = bot.dialogue(7L, "前面有花", "CALM");
        assertThat(dlg.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dlg.get("dialogue");
        assertThat(bubble.get("msgId")).isEqualTo(MessageId.COMPANION_DIALOGUE_SC_NOTIFY);
        assertThat(bubble.get("msgId")).isEqualTo(2500);
        assertThat(bubble.get("ttsEmotion")).isNotNull();
        Map<String, Object> mem = bot.toMap(bot.get(7L));
        assertThat(((Number) mem.get("fallCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) mem.get("flowerPicks")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void combatAnalyst_flagsSkillIdleAndEquipHints() {
        CombatAnalystService analyst = new CombatAnalystService();
        List<Map<String, Object>> frames = List.of(
                Map.of("tick", 10, "tsMs", 1000L, "actionType", "SKILL_BURST",
                        "payload", Map.of("castDelaySec", 0.8)),
                Map.of("tick", 20, "tsMs", 2000L, "actionType", "DODGE",
                        "payload", Map.of("avoidedHit", false, "inIFrameWindow", false)),
                Map.of("tick", 30, "tsMs", 3000L, "actionType", "ELEMENT_REACTION",
                        "payload", Map.of("reactionApplied", false)));
        Map<String, Object> report = analyst.feedback("r1",
                Map.of("ok", true, "frames", frames), "HARDCORE", true);
        assertThat(report.get("ok")).isEqualTo(true);
        assertThat(String.valueOf(report.get("advice"))).contains("充能");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) report.get("mistakeTimeline");
        assertThat(timeline).isNotEmpty();
        assertThat(report.get("enhanceDeepLink")).isNotNull();
    }

    @Test
    public void screenWaypoint_recognizesBoulderAndExpires() {
        ScreenWaypointTranslator vision = new ScreenWaypointTranslator();
        long now = 1_000_000L;
        Map<String, Object> help = vision.help(9L, "1", List.of("boulder", "巨石"),
                100f, 0f, 100f, now);
        assertThat(help.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> guide = (Map<String, Object>) help.get("guide");
        assertThat(guide.get("description")).asString().contains("巨石");
        assertThat(guide.get("arrow")).isNotNull();

        String sessionId = String.valueOf(((Map<?, ?>) help.get("upload")).get("sessionId"));
        Map<String, Object> expired = vision.translate(sessionId, 100f, 0f, 100f,
                now + ScreenWaypointTranslator.IMAGE_TTL_MS + 1);
        assertThat(expired.get("ok")).isEqualTo(false);
    }

    @Test
    public void personaEngine_hardcoreVsCasualPrompt() {
        DynamicPersonaEngine engine = new DynamicPersonaEngine();
        var hard = engine.resolve(1L, new DynamicPersonaEngine.PersonaSignals(0.9, 85, 12, 0, 1));
        assertThat(hard.cluster()).isEqualTo(DynamicPersonaEngine.Cluster.HARDCORE);
        assertThat(hard.systemPromptFragment()).contains("HARDCORE");

        engine.setStyle(2L, "感性");
        var casual = engine.resolve(2L, new DynamicPersonaEngine.PersonaSignals(0.2, 20, 1, 0, 0));
        assertThat(casual.cluster()).isEqualTo(DynamicPersonaEngine.Cluster.CASUAL);
        assertThat(casual.style()).isEqualTo(DynamicPersonaEngine.StyleSlider.EMOTIONAL);
        String adapted = engine.adaptAdvice(casual, "立刻冲刺提升 DPS");
        assertThat(adapted).contains("有空时");
        assertThat(adapted).contains("慢慢来");
    }

    @Test
    public void facade_p15EndpointsWire() {
        AiPlatformFacade facade = AiPlatformFacade.createDefault();
        Map<String, Object> narrative = facade.narrativeGenerate(Map.of(
                "playerId", 55L, "npcId", "guide", "triggerEventId", "STORM_FRONT",
                "weather", "STORM"));
        assertThat(narrative.get("ok")).isEqualTo(true);

        Map<String, Object> companion = facade.companionDialogue(Map.of(
                "playerId", 55L, "text", "你好", "mood", "CALM"));
        assertThat(companion.get("ok")).isEqualTo(true);

        BattleReplayService replays = facade.battleReplays();
        Map<String, Object> start = replays.start("p15-replay", 42L, System.currentTimeMillis());
        String rid = String.valueOf(start.get("replayId"));
        replays.append(rid, 1, 100, "SKILL_BURST", Map.of("castDelaySec", 0.9));
        Map<String, Object> feedback = facade.analystFeedback(Map.of(
                "playerId", 55L, "replayId", rid, "applyEnhanceHints", true));
        assertThat(feedback.get("ok")).isEqualTo(true);

        Map<String, Object> wp = facade.screenWaypointHelp(Map.of(
                "playerId", 55L, "regionId", "1", "visualTags", List.of("statue"),
                "x", 90, "y", 0, "z", 90));
        assertThat(wp.get("ok")).isEqualTo(true);

        Map<String, Object> persona = facade.personaResolve(Map.of(
                "playerId", 55L, "abyssStars", 12, "regionExplorePercent", 80,
                "handbookProgress", 0.9, "style", "简洁"));
        assertThat(persona.get("cluster")).isEqualTo("HARDCORE");
        assertThat(facade.health().get("p15")).isNotNull();
    }
}
