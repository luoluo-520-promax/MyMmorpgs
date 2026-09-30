package cn.itcast.demo.mymmorpg.aiservice;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.aiservice.config.AiPlatformConfiguration;
import cn.itcast.demo.mymmorpg.aiservice.web.InternalAiP15Controller;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ai-service P15 Internal API 全链路：Controller → Facade → 五项能力。
 */
public class AiP15BusinessFlowTest {

    private AnnotationConfigApplicationContext ctx;
    private InternalAiP15Controller controller;
    private AiPlatformFacade facade;

    @BeforeMethod
    public void setUp() {
        System.setProperty("spring.application.name", "ai-service");
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(AiPlatformConfiguration.class);
        ctx.refresh();
        facade = ctx.getBean(AiPlatformFacade.class);
        controller = new InternalAiP15Controller(facade);
    }

    @AfterMethod
    public void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
        System.clearProperty("spring.application.name");
    }

    @Test
    public void internalApis_coverFiveP15Capabilities() {
        long playerId = 15_100L;

        Map<String, Object> style = controller.personaStyle(Map.of(
                "playerId", playerId, "style", "详细"));
        assertThat(style.get("ok")).isEqualTo(true);
        assertThat(style.get("style")).isEqualTo("DETAILED");

        Map<String, Object> persona = controller.personaResolve(Map.of(
                "playerId", playerId,
                "handbookProgress", 0.5,
                "regionExplorePercent", 40,
                "abyssStars", 6,
                "friendAssistCount", 8,
                "chatMessages7d", 20));
        assertThat(persona.get("ok")).isEqualTo(true);
        assertThat(persona.get("cluster")).isIn("CASUAL", "HARDCORE", "SOCIAL");

        Map<String, Object> remember = controller.companionRemember(Map.of(
                "playerId", playerId, "type", "FLOWER", "summary", "采风车菊"));
        assertThat(((Number) remember.get("flowerPicks")).intValue()).isGreaterThanOrEqualTo(1);

        Map<String, Object> dialogue = controller.companionDialogue(Map.of(
                "playerId", playerId, "text", "今天天气不错", "mood", "CALM"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dialogue.get("dialogue");
        assertThat(bubble.get("msgId")).isEqualTo(MessageId.COMPANION_DIALOGUE_SC_NOTIFY);

        Map<String, Object> coach = controller.companionBattleCoach(Map.of(
                "playerId", playerId,
                "squadId", "none",
                "partyHpRatio", 0.9,
                "bossHpRatio", 0.2));
        assertThat(coach.get("ok")).isEqualTo(true);

        Map<String, Object> narrative = controller.narrativeGenerate(Map.of(
                "playerId", playerId,
                "npcId", "katherine",
                "triggerEventId", "STORM_FRONT",
                "weather", "STORM",
                "regionTide", "CHAOS"));
        assertThat(narrative.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) narrative.get("intent");
        assertThat(intent.get("intent")).isEqualTo("WARN");

        BattleReplayService replays = facade.battleReplays();
        String rid = String.valueOf(replays.start("api-p15", 1L, System.currentTimeMillis()).get("replayId"));
        replays.append(rid, 1, 100, "SKILL", Map.of("castDelaySec", 0.7));
        Map<String, Object> feedback = controller.analystFeedback(Map.of(
                "playerId", playerId, "replayId", rid, "applyEnhanceHints", true));
        assertThat(feedback.get("ok")).isEqualTo(true);
        assertThat(feedback.get("mistakeTimeline")).isNotNull();

        Map<String, Object> vision = controller.screenWaypoint(Map.of(
                "playerId", playerId,
                "regionId", "1",
                "visualTags", List.of("flag", "任务旗帜"),
                "x", 280, "y", 0, "z", 240,
                "nowMs", System.currentTimeMillis()));
        assertThat(vision.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> guide = (Map<String, Object>) vision.get("guide");
        assertThat(guide.get("description")).asString().contains("旗帜");

        Map<String, Object> memory = controller.companionMemory(playerId);
        assertThat(memory.get("playerId")).isEqualTo(playerId);
        assertThat(memory.get("redisKey")).asString().contains("companion:memory:");

        Map<String, Object> explore = controller.companionPassiveExplore(Map.of(
                "playerId", playerId, "regionId", "1"));
        assertThat(explore.get("ok")).isEqualTo(true);
    }

    @Test
    public void p15BeansRegisteredInAiServiceContext() {
        assertThat(ctx.getBeanNamesForType(cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService.class))
                .isNotEmpty();
        assertThat(ctx.getBeanNamesForType(cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService.class))
                .isNotEmpty();
        assertThat(ctx.getBeanNamesForType(cn.itcast.demo.mymmorpg.ai.analyst.CombatAnalystService.class))
                .isNotEmpty();
        assertThat(ctx.getBeanNamesForType(cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator.class))
                .isNotEmpty();
        assertThat(ctx.getBeanNamesForType(cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine.class))
                .isNotEmpty();
        assertThat(facade.health().get("p15")).isNotNull();
    }
}
