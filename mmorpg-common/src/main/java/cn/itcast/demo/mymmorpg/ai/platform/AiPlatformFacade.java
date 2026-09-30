package cn.itcast.demo.mymmorpg.ai.platform;

import cn.itcast.demo.mymmorpg.ai.analyst.CombatAnalystService;
import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.content.ActivityParamOptimizer;
import cn.itcast.demo.mymmorpg.ai.content.ContentQualityValidator;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceGateway;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceRequest;
import cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService;
import cn.itcast.demo.mymmorpg.ai.npc.NpcEmotionVector;
import cn.itcast.demo.mymmorpg.ai.npc.NpcLongTermMemory;
import cn.itcast.demo.mymmorpg.ai.ops.RetentionInterventionEngine;
import cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine;
import cn.itcast.demo.mymmorpg.ai.recommend.RecommendationEngine;
import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.ai.support.FaqRagService;
import cn.itcast.demo.mymmorpg.ai.support.LlmDailyQuota;
import cn.itcast.demo.mymmorpg.ai.tactical.TacticalAdvisorEngine;
import cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator;
import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;
import cn.itcast.demo.mymmorpg.ml.ModelDriftMonitor;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import cn.itcast.demo.mymmorpg.world.puzzle.RuleTriggerService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 平台统一门面：单体嵌入与 ai-service 微服务共用同一套业务逻辑。
 */
public final class AiPlatformFacade {

    private final AiInferenceGateway gateway;
    private final RecommendationEngine recommendation;
    private final NpcLongTermMemory npcMemory;
    private final FaqRagService faqRag;
    private final AiContentGuard contentGuard;
    private final LlmDailyQuota llmQuota;
    private final RetentionInterventionEngine retention;
    private final TacticalAdvisorEngine tactical;
    private final ContentQualityValidator contentValidator;
    private final ActivityParamOptimizer activityOptimizer;
    private final ModelDriftMonitor driftMonitor;
    private final DynamicNarrativeService narrative;
    private final CompanionBotService companion;
    private final CombatAnalystService combatAnalyst;
    private final ScreenWaypointTranslator screenWaypoint;
    private final DynamicPersonaEngine persona;
    private final BattleReplayService battleReplays;

    public AiPlatformFacade(AiInferenceGateway gateway,
                              RecommendationEngine recommendation,
                              NpcLongTermMemory npcMemory,
                              FaqRagService faqRag,
                              AiContentGuard contentGuard,
                              LlmDailyQuota llmQuota,
                              RetentionInterventionEngine retention,
                              TacticalAdvisorEngine tactical,
                              ContentQualityValidator contentValidator,
                              ActivityParamOptimizer activityOptimizer,
                              ModelDriftMonitor driftMonitor,
                              DynamicNarrativeService narrative,
                              CompanionBotService companion,
                              CombatAnalystService combatAnalyst,
                              ScreenWaypointTranslator screenWaypoint,
                              DynamicPersonaEngine persona,
                              BattleReplayService battleReplays) {
        this.gateway = gateway;
        this.recommendation = recommendation;
        this.npcMemory = npcMemory;
        this.faqRag = faqRag;
        this.contentGuard = contentGuard;
        this.llmQuota = llmQuota;
        this.retention = retention;
        this.tactical = tactical;
        this.contentValidator = contentValidator;
        this.activityOptimizer = activityOptimizer;
        this.driftMonitor = driftMonitor;
        this.narrative = narrative == null ? new DynamicNarrativeService() : narrative;
        this.companion = companion == null ? new CompanionBotService(contentGuard) : companion;
        this.combatAnalyst = combatAnalyst == null ? new CombatAnalystService(contentGuard) : combatAnalyst;
        this.screenWaypoint = screenWaypoint == null ? new ScreenWaypointTranslator() : screenWaypoint;
        this.persona = persona == null ? new DynamicPersonaEngine() : persona;
        this.battleReplays = battleReplays == null ? new BattleReplayService() : battleReplays;
    }

    public static AiPlatformFacade createDefault() {
        AiInferenceGateway gateway = new AiInferenceGateway();
        RecommendationEngine recommendation = new RecommendationEngine();
        recommendation.upsertItem(new RecommendationEngine.CatalogItem(
                "relic_gladiator", "equip", Map.of("combat", 0.9), List.of("combat", "dps")));
        recommendation.upsertItem(new RecommendationEngine.CatalogItem(
                "pack_weekend", "shop", Map.of("shop", 0.8), List.of("shop", "gacha")));
        recommendation.upsertItem(new RecommendationEngine.CatalogItem(
                "quest_abyss_weekly", "quest", Map.of("combat", 0.7), List.of("combat", "abyss")));
        recommendation.upsertItem(new RecommendationEngine.CatalogItem(
                "team_fire_resonance", "team", Map.of("combat", 0.85), List.of("combat", "team")));
        FaqRagService faq = new FaqRagService();
        faq.upsertDoc("faq_level", "如何升级更快", "优先完成主线与日常委托，合理分配体力可更快升级。");
        faq.upsertDoc("faq_abyss", "深渊怎么打", "深渊建议配置元素共鸣队伍，优先破盾再输出。");
        faq.upsertDoc("faq_lag", "游戏卡顿怎么办", "尝试切换画质、关闭后台应用，并检查网络延迟；仍卡顿可提交工单。");
        faq.upsertDoc("faq_ban", "误封申诉", "请准备账号与违规时间截图，通过智能客服转人工工单处理。");
        faq.putFaqCache("怎么升级快", "优先完成主线与日常委托，合理分配体力可更快升级。");
        ModelDriftMonitor drift = new ModelDriftMonitor(0.05);
        drift.setBaseline("recommend_cf", 0.82);
        drift.setBaseline("churn_xgb", 0.78);
        AiContentGuard guard = new AiContentGuard();
        RuleTriggerService rules = new RuleTriggerService();
        DynamicNarrativeService narrative = new DynamicNarrativeService(guard, rules);
        CompanionBotService companion = new CompanionBotService(guard);
        companion.bindExploration(new cn.itcast.demo.mymmorpg.world.explore.ExplorationVitalityService(),
                new cn.itcast.demo.mymmorpg.world.explore.MapMarkerService());
        companion.bindSquad(new cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService());
        CombatAnalystService analyst = new CombatAnalystService(guard);
        ScreenWaypointTranslator vision = new ScreenWaypointTranslator();
        DynamicPersonaEngine persona = new DynamicPersonaEngine();
        BattleReplayService replays = new BattleReplayService();
        return new AiPlatformFacade(
                gateway,
                recommendation,
                new NpcLongTermMemory(),
                faq,
                guard,
                new LlmDailyQuota(),
                new RetentionInterventionEngine(),
                new TacticalAdvisorEngine(),
                new ContentQualityValidator(
                        Set.of("item_exp_book", "item_mora", "relic_gladiator"),
                        Set.of("quest_main_01", "quest_abyss_weekly")),
                new ActivityParamOptimizer(),
                drift,
                narrative,
                companion,
                analyst,
                vision,
                persona,
                replays);
    }

    public Map<String, Object> infer(String modelName, String task, long playerId, Map<String, Object> features) {
        return gateway.infer(AiInferenceRequest.builder()
                .modelName(modelName)
                .task(task)
                .playerId(playerId)
                .features(features)
                .build()).toMap();
    }

    public Map<String, Object> recommend(long playerId, String category, int topN) {
        Map<String, Object> out = recommendation.recommendPayload(playerId, category, topN);
        driftMonitor.record("recommend_cf", !((List<?>) out.get("items")).isEmpty());
        return out;
    }

    public Map<String, Object> recordBehavior(long playerId, String itemId, String category) {
        recommendation.recordInteraction(playerId, itemId, category);
        return Map.of("ok", true, "profile", recommendation.profiles().toMap(playerId));
    }

    public Map<String, Object> profile(long playerId) {
        return recommendation.profiles().toMap(playerId);
    }

    public Map<String, Object> npcBond(long playerId, String npcId) {
        return npcMemory.toMap(playerId, npcId);
    }

    public Map<String, Object> npcInteract(long playerId, String npcId, String kind, String summary, double intensity) {
        NpcEmotionVector.InteractionKind k;
        try {
            k = NpcEmotionVector.InteractionKind.valueOf(kind);
        } catch (Exception e) {
            k = NpcEmotionVector.inferFromText(summary);
        }
        npcMemory.interact(playerId, npcId, k, summary, intensity);
        return npcMemory.toMap(playerId, npcId);
    }

    public Map<String, Object> supportAsk(long playerId, String question, boolean allowTicket) {
        Map<String, Object> ans = new LinkedHashMap<>(faqRag.answer(question, allowTicket));
        AiContentGuard.GuardResult guard = contentGuard.check(String.valueOf(ans.get("answer")));
        if (!guard.allowed()) {
            ans.put("answer", guard.text());
            ans.put("guardReason", guard.reason());
            ans.put("source", "content_guard");
        }
        DynamicPersonaEngine.PersonaProfile pp = persona.resolve(playerId,
                new DynamicPersonaEngine.PersonaSignals(0.4, 35, 3, 1, 5));
        ans.put("answer", persona.adaptAdvice(pp, String.valueOf(ans.get("answer"))));
        ans.put("persona", persona.toMap(pp));
        ans.put("llmQuota", llmQuota.snapshot(playerId));
        ans.put("ok", true);
        return ans;
    }

    public Map<String, Object> retentionEvaluate(long playerId, Map<String, Object> features) {
        PlayerRiskScorer.Features f = new PlayerRiskScorer.Features(
                num(features, "loginDays7", 3),
                num(features, "questCompletionRate", 0.5),
                num(features, "onlineMinutes7", 120),
                num(features, "rechargeAmount30d", 0),
                num(features, "rechargeCount30d", 0),
                (long) num(features, "daysSinceLastLogin", 1));
        RetentionInterventionEngine.Intervention i = retention.evaluate(playerId, f);
        driftMonitor.record("churn_xgb", i.churnRisk() >= 0);
        return retention.toMap(playerId, i);
    }

    public Map<String, Object> tacticalAdvise(Map<String, Object> battlefield) {
        TacticalAdvisorEngine.Battlefield bf = new TacticalAdvisorEngine.Battlefield(
                num(battlefield, "bossHpRatio", 1.0),
                num(battlefield, "partyHpRatio", 1.0),
                num(battlefield, "partyEnergyRatio", 0.5),
                Boolean.TRUE.equals(battlefield.get("hasElementShield"))
                        || "true".equalsIgnoreCase(String.valueOf(battlefield.getOrDefault("hasElementShield", "false"))),
                String.valueOf(battlefield.getOrDefault("weakElement", "火")),
                (int) num(battlefield, "aliveAdds", 0),
                Boolean.TRUE.equals(battlefield.get("mechanismReady")),
                battlefield.get("mechanismHint") == null ? null : String.valueOf(battlefield.get("mechanismHint")));
        Map<String, Object> out = new LinkedHashMap<>(tactical.toMap(tactical.advise(bf)));
        long playerId = (long) num(battlefield, "playerId", 0);
        DynamicPersonaEngine.PersonaProfile pp = persona.resolve(playerId,
                new DynamicPersonaEngine.PersonaSignals(
                        num(battlefield, "handbookProgress", 0.4),
                        num(battlefield, "regionExplorePercent", 35),
                        (int) num(battlefield, "abyssStars", 3),
                        (int) num(battlefield, "friendAssistCount", 1),
                        (int) num(battlefield, "chatMessages7d", 5)));
        out.put("hint", persona.adaptAdvice(pp, String.valueOf(out.get("hint"))));
        out.put("personaCluster", pp.cluster().name());
        out.put("ok", true);
        return out;
    }

    public Map<String, Object> contentGenerate(String type, String theme, Map<String, Object> knobs) {
        Map<String, Object> draft = contentValidator.generateDraft(type, theme, knobs);
        ContentQualityValidator.ValidationResult vr = contentValidator.validate(draft);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", vr.pass());
        out.put("draft", draft);
        out.put("validation", vr.toMap());
        out.put("readyForAdminDraft", vr.pass());
        return out;
    }

    public Map<String, Object> contentValidate(Map<String, Object> draft) {
        return contentValidator.validate(draft).toMap();
    }

    public Map<String, Object> activityFeedback(String activityId, String event, double cost) {
        switch (event == null ? "exposure" : event) {
            case "join" -> activityOptimizer.recordJoin(activityId);
            case "pay" -> activityOptimizer.recordPay(activityId, cost);
            default -> activityOptimizer.recordExposure(activityId);
        }
        return activityOptimizer.snapshot(activityId);
    }

    public Map<String, Object> activitySnapshot(String activityId) {
        return activityOptimizer.snapshot(activityId);
    }

    public Map<String, Object> driftSnapshot(String modelName) {
        return driftMonitor.snapshot(modelName);
    }

    // -------- P15 --------

    public Map<String, Object> narrativeGenerate(Map<String, Object> body) {
        if (!llmQuota.tryAcquire((long) num(body, "playerId", 0))) {
            return Map.of("ok", false, "error", "llm_daily_quota_exceeded", "degraded", true);
        }
        Map<String, Object> bond = npcMemory.toMap(
                (long) num(body, "playerId", 0),
                String.valueOf(body.getOrDefault("npcId", "guide")));
        Map<String, Object> req = new LinkedHashMap<>(body == null ? Map.of() : body);
        if (!req.containsKey("npcTrust") && bond.get("emotion") instanceof Map<?, ?> emo) {
            Object trust = emo.get("trust");
            Object fear = emo.get("fear");
            if (trust instanceof Number n) {
                req.put("npcTrust", n.doubleValue());
            }
            if (fear instanceof Number n) {
                req.put("npcFear", n.doubleValue());
            }
        }
        return narrative.generateFromRequest(req);
    }

    public Map<String, Object> companionDialogue(Map<String, Object> body) {
        long playerId = (long) num(body, "playerId", 0);
        String line = body == null || body.get("text") == null ? "" : String.valueOf(body.get("text"));
        String mood = body == null || body.get("mood") == null ? "" : String.valueOf(body.get("mood"));
        return companion.dialogue(playerId, line, mood);
    }

    public Map<String, Object> companionRemember(Map<String, Object> body) {
        long playerId = (long) num(body, "playerId", 0);
        String type = body == null ? "NOTE" : String.valueOf(body.getOrDefault("type", "NOTE"));
        String summary = body == null ? "" : String.valueOf(body.getOrDefault("summary", ""));
        return companion.remember(playerId, type, summary);
    }

    public Map<String, Object> companionBattleCoach(Map<String, Object> body) {
        long playerId = (long) num(body, "playerId", 0);
        String squadId = body == null ? "" : String.valueOf(body.getOrDefault("squadId", ""));
        return companion.battleCoach(playerId, squadId, body);
    }

    public Map<String, Object> companionPassiveExplore(Map<String, Object> body) {
        long playerId = (long) num(body, "playerId", 0);
        String regionId = body == null ? "1" : String.valueOf(body.getOrDefault("regionId", "1"));
        long nowMs = body != null && body.get("nowMs") instanceof Number n
                ? n.longValue() : System.currentTimeMillis();
        return companion.passiveExplore(playerId, regionId, nowMs);
    }

    public Map<String, Object> analystFeedback(Map<String, Object> body) {
        String replayId = body == null ? "" : String.valueOf(body.getOrDefault("replayId", ""));
        boolean apply = body != null && Boolean.TRUE.equals(body.get("applyEnhanceHints"));
        long playerId = (long) num(body, "playerId", 0);
        DynamicPersonaEngine.PersonaProfile pp = persona.resolve(playerId,
                new DynamicPersonaEngine.PersonaSignals(
                        num(body, "handbookProgress", 0.4),
                        num(body, "regionExplorePercent", 35),
                        (int) num(body, "abyssStars", 3),
                        (int) num(body, "friendAssistCount", 1),
                        (int) num(body, "chatMessages7d", 5)));
        if (body != null && body.get("style") != null) {
            persona.setStyle(playerId, String.valueOf(body.get("style")));
            pp = persona.resolve(playerId,
                    new DynamicPersonaEngine.PersonaSignals(
                            num(body, "handbookProgress", 0.4),
                            num(body, "regionExplorePercent", 35),
                            (int) num(body, "abyssStars", 3),
                            (int) num(body, "friendAssistCount", 1),
                            (int) num(body, "chatMessages7d", 5)));
        }
        String tone = pp.cluster().name();
        Map<String, Object> report;
        if (body != null && body.get("frames") instanceof List<?> frames) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frameList = (List<Map<String, Object>>) frames;
            Map<String, Object> fakePlayback = new LinkedHashMap<>();
            fakePlayback.put("ok", true);
            fakePlayback.put("frames", frameList);
            report = combatAnalyst.feedback(replayId, fakePlayback, tone, apply);
        } else {
            report = combatAnalyst.feedbackFromReplay(battleReplays, replayId, tone, apply);
        }
        if (Boolean.TRUE.equals(report.get("ok"))) {
            Map<String, Object> mutable = new LinkedHashMap<>(report);
            mutable.put("advice", persona.adaptAdvice(pp, String.valueOf(report.get("advice"))));
            mutable.put("persona", persona.toMap(pp));
            return mutable;
        }
        return report;
    }

    public Map<String, Object> screenWaypointHelp(Map<String, Object> body) {
        long playerId = (long) num(body, "playerId", 0);
        String regionId = body == null ? "1" : String.valueOf(body.getOrDefault("regionId", "1"));
        @SuppressWarnings("unchecked")
        List<String> tags = body != null && body.get("visualTags") instanceof List<?> l
                ? (List<String>) l : List.of();
        float x = (float) num(body, "x", 0);
        float y = (float) num(body, "y", 0);
        float z = (float) num(body, "z", 0);
        long nowMs = body != null && body.get("nowMs") instanceof Number n
                ? n.longValue() : System.currentTimeMillis();
        return screenWaypoint.help(playerId, regionId, tags, x, y, z, nowMs);
    }

    public Map<String, Object> personaResolve(Map<String, Object> body) {
        return persona.resolveFromRequest(body);
    }

    public Map<String, Object> personaSetStyle(long playerId, String style) {
        persona.setStyle(playerId, style);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("style", persona.styleOf(playerId).name());
        return out;
    }

    public Map<String, Object> health() {
        Map<String, Object> h = gateway.health();
        h.put("ok", true);
        h.put("p15", Map.of(
                "narrative", true,
                "companion", true,
                "combatAnalyst", true,
                "screenWaypoint", true,
                "persona", true));
        return h;
    }

    public AiInferenceGateway gateway() {
        return gateway;
    }

    public RecommendationEngine recommendation() {
        return recommendation;
    }

    public NpcLongTermMemory npcMemory() {
        return npcMemory;
    }

    public FaqRagService faqRag() {
        return faqRag;
    }

    public AiContentGuard contentGuard() {
        return contentGuard;
    }

    public LlmDailyQuota llmQuota() {
        return llmQuota;
    }

    public RetentionInterventionEngine retention() {
        return retention;
    }

    public TacticalAdvisorEngine tactical() {
        return tactical;
    }

    public ContentQualityValidator contentValidator() {
        return contentValidator;
    }

    public ActivityParamOptimizer activityOptimizer() {
        return activityOptimizer;
    }

    public ModelDriftMonitor driftMonitor() {
        return driftMonitor;
    }

    public DynamicNarrativeService narrative() {
        return narrative;
    }

    public CompanionBotService companion() {
        return companion;
    }

    public CombatAnalystService combatAnalyst() {
        return combatAnalyst;
    }

    public ScreenWaypointTranslator screenWaypoint() {
        return screenWaypoint;
    }

    public DynamicPersonaEngine persona() {
        return persona;
    }

    public BattleReplayService battleReplays() {
        return battleReplays;
    }

    private static double num(Map<String, Object> m, String key, double def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }
}
