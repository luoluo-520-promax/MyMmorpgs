package cn.itcast.demo.mymmorpg.aiservice.config;

import cn.itcast.demo.mymmorpg.ai.analyst.CombatAnalystService;
import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.content.ActivityParamOptimizer;
import cn.itcast.demo.mymmorpg.ai.content.ContentQualityValidator;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceGateway;
import cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService;
import cn.itcast.demo.mymmorpg.ai.npc.NpcLongTermMemory;
import cn.itcast.demo.mymmorpg.ai.ops.RetentionInterventionEngine;
import cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine;
import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.ai.recommend.RecommendationEngine;
import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.ai.support.FaqRagService;
import cn.itcast.demo.mymmorpg.ai.support.LlmDailyQuota;
import cn.itcast.demo.mymmorpg.ai.tactical.TacticalAdvisorEngine;
import cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator;
import cn.itcast.demo.mymmorpg.ml.ModelDriftMonitor;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class AiPlatformConfiguration {

    @Bean
    public AiPlatformFacade aiPlatformFacade() {
        return AiPlatformFacade.createDefault();
    }

    @Bean
    public AiInferenceGateway aiInferenceGateway(AiPlatformFacade facade) {
        return facade.gateway();
    }

    @Bean
    public RecommendationEngine recommendationEngine(AiPlatformFacade facade) {
        return facade.recommendation();
    }

    @Bean
    public NpcLongTermMemory npcLongTermMemory(AiPlatformFacade facade) {
        return facade.npcMemory();
    }

    @Bean
    public FaqRagService faqRagService(AiPlatformFacade facade) {
        return facade.faqRag();
    }

    @Bean
    public AiContentGuard aiContentGuard(AiPlatformFacade facade) {
        return facade.contentGuard();
    }

    @Bean
    public LlmDailyQuota llmDailyQuota(AiPlatformFacade facade) {
        return facade.llmQuota();
    }

    @Bean
    public RetentionInterventionEngine retentionInterventionEngine(AiPlatformFacade facade) {
        return facade.retention();
    }

    @Bean
    public TacticalAdvisorEngine tacticalAdvisorEngine(AiPlatformFacade facade) {
        return facade.tactical();
    }

    @Bean
    public ContentQualityValidator contentQualityValidator(AiPlatformFacade facade) {
        return facade.contentValidator();
    }

    @Bean
    public ActivityParamOptimizer activityParamOptimizer(AiPlatformFacade facade) {
        return facade.activityOptimizer();
    }

    @Bean
    public ModelDriftMonitor modelDriftMonitor(AiPlatformFacade facade) {
        return facade.driftMonitor();
    }

    @Bean
    public DynamicNarrativeService dynamicNarrativeService(AiPlatformFacade facade) {
        return facade.narrative();
    }

    @Bean
    public CompanionBotService companionBotService(AiPlatformFacade facade) {
        return facade.companion();
    }

    @Bean
    public CombatAnalystService combatAnalystService(AiPlatformFacade facade) {
        return facade.combatAnalyst();
    }

    @Bean
    public ScreenWaypointTranslator screenWaypointTranslator(AiPlatformFacade facade) {
        return facade.screenWaypoint();
    }

    @Bean
    public DynamicPersonaEngine dynamicPersonaEngine(AiPlatformFacade facade) {
        return facade.persona();
    }

    @Bean
    public BattleReplayService battleReplayService(AiPlatformFacade facade) {
        return facade.battleReplays();
    }
}
