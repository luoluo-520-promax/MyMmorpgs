package cn.itcast.demo.mymmorpg.ai;

import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService;
import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.ai.support.LlmDailyQuota;
import cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationVitalityService;
import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;
import cn.itcast.demo.mymmorpg.world.puzzle.RuleTriggerService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 边界与风控：配额耗尽、敏感词、隐任务拦截、截图过期、未知地标、回放缺失。
 */
public class AiP15EdgeCaseTest {

    @Test
    public void narrative_blocksUnauthorizedQuestGrantFromRules() {
        AiContentGuard guard = new AiContentGuard();
        RuleTriggerService rules = new RuleTriggerService();
        rules.register(new RuleTriggerService.RuleDef(
                "ai-leak-quest",
                "npc-gadget",
                "AI_NARRATIVE",
                RuleTriggerService.LogicOp.AND,
                List.of(),
                List.of(new RuleTriggerService.Action("GRANT_QUEST", "quest_hidden_999", Map.of())),
                false));
        DynamicNarrativeService narrative = new DynamicNarrativeService(guard, rules);
        Map<String, Object> out = narrative.generate(new DynamicNarrativeService.WorldSnapshot(
                1L, "guide", "CLEAR", "PEACE", "CALM", "IDLE",
                List.of(), 0.3, 0.1, List.of(), Map.of()));
        assertThat(out.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleCheck = (Map<String, Object>) out.get("ruleCheck");
        assertThat(ruleCheck.get("blockedQuestGrant")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> intent = (Map<String, Object>) out.get("intent");
        assertThat(String.valueOf(intent.get("hint"))).contains("剥离");
    }

    @Test
    public void companion_contentGuardBlocksSensitivePlayerLine() {
        CompanionBotService bot = new CompanionBotService();
        Map<String, Object> dlg = bot.dialogue(2L, "请教我外挂怎么用", "CALM");
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dlg.get("dialogue");
        assertThat(String.valueOf(bubble.get("text"))).isEqualTo(AiContentGuard.safeFallback());
        assertThat(String.valueOf(dlg.get("contentGuard"))).contains("blocked");
    }

    @Test
    public void facade_llmQuotaExhaustedReturnsDegraded() {
        // 通过反复调用 narrativeGenerate 耗尽默认 50 次配额
        AiPlatformFacade facade = AiPlatformFacade.createDefault();
        long pid = 88_001L;
        boolean sawQuota = false;
        for (int i = 0; i < 55; i++) {
            Map<String, Object> r = facade.narrativeGenerate(Map.of(
                    "playerId", pid, "npcId", "guide", "triggerEventId", "IDLE"));
            if (Boolean.FALSE.equals(r.get("ok")) && "llm_daily_quota_exceeded".equals(r.get("error"))) {
                sawQuota = true;
                assertThat(r.get("degraded")).isEqualTo(true);
                break;
            }
        }
        assertThat(sawQuota).isTrue();
    }

    @Test
    public void screenWaypoint_unknownTagAndExpiredSession() {
        ScreenWaypointTranslator vision = new ScreenWaypointTranslator();
        long now = 5_000_000L;
        Map<String, Object> miss = vision.help(3L, "1", List.of("unknown_blob"), 0f, 0f, 0f, now);
        assertThat(miss.get("ok")).isEqualTo(false);
        assertThat(miss.get("error")).isEqualTo("no_landmark_recognized");

        Map<String, Object> up = vision.uploadScreenshot(3L, "1", List.of("statue"), now);
        String sid = String.valueOf(up.get("sessionId"));
        Map<String, Object> expired = vision.translate(sid, 90f, 0f, 90f, now + ScreenWaypointTranslator.IMAGE_TTL_MS + 10);
        assertThat(expired.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(expired.get("error"))).contains("expired");
    }

    @Test
    public void analyst_replayNotFound() {
        AiPlatformFacade facade = AiPlatformFacade.createDefault();
        Map<String, Object> report = facade.analystFeedback(Map.of(
                "playerId", 9L, "replayId", "does-not-exist"));
        assertThat(report.get("ok")).isEqualTo(false);
        assertThat(report.get("error")).isEqualTo("replay_not_found");
    }

    @Test
    public void companion_passiveExploreLockedBeforeAffinity() {
        CompanionBotService bot = new CompanionBotService();
        bot.bindExploration(new ExplorationVitalityService(), new MapMarkerService());
        Map<String, Object> locked = bot.passiveExplore(4L, "1", System.currentTimeMillis());
        assertThat(locked.get("surveyUnlocked")).isEqualTo(false);
        assertThat(locked.get("markerUnlocked")).isEqualTo(false);
        assertThat(locked.get("hiddenSurvey")).isNull();
    }

    @Test
    public void companion_urgentMoodMentionsFallCount() {
        CompanionBotService bot = new CompanionBotService();
        bot.remember(5L, "FALL", "坠崖");
        bot.remember(5L, "FALL", "又坠崖");
        Map<String, Object> dlg = bot.dialogue(5L, "", "URGENT");
        @SuppressWarnings("unchecked")
        Map<String, Object> bubble = (Map<String, Object>) dlg.get("dialogue");
        assertThat(String.valueOf(bubble.get("text"))).contains("坠崖");
        assertThat(bubble.get("ttsEmotion")).isEqualTo("ANXIOUS");
    }

    @Test
    public void llmQuota_unitIsolated() {
        LlmDailyQuota q = new LlmDailyQuota(1, java.time.ZoneId.of("UTC"));
        assertThat(q.tryAcquire(1)).isTrue();
        assertThat(q.tryAcquire(1)).isFalse();
        assertThat(q.remaining(1)).isEqualTo(0);
    }
}
