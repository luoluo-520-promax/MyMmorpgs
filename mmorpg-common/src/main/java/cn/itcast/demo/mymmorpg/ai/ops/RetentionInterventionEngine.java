package cn.itcast.demo.mymmorpg.ai.ops;

import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流失预警与自动干预：风险超阈值触发召回礼包 / 登录奖励。
 */
public final class RetentionInterventionEngine {

    public record Intervention(
            boolean triggered,
            String action,
            String giftPackId,
            String channel,
            String reason,
            double churnRisk) {
    }

    private final PlayerRiskScorer scorer = new PlayerRiskScorer();
    private final ConcurrentHashMap<Long, Intervention> last = new ConcurrentHashMap<>();
    private final double churnThreshold;

    public RetentionInterventionEngine() {
        this(0.7);
    }

    public RetentionInterventionEngine(double churnThreshold) {
        this.churnThreshold = Math.max(0.1, Math.min(0.99, churnThreshold));
    }

    public Intervention evaluate(long playerId, PlayerRiskScorer.Features features) {
        PlayerRiskScorer.Prediction pred = scorer.predict(features);
        if (pred.churnRisk() < churnThreshold) {
            Intervention none = new Intervention(false, "none", null, null, "below_threshold", pred.churnRisk());
            last.put(playerId, none);
            return none;
        }
        String action = pred.suggestedAction();
        String pack;
        String channel;
        if ("push_limited_offer".equals(action)) {
            pack = "pack_comeback_premium";
            channel = "mail+push";
        } else if ("push_retention_quest".equals(action)) {
            pack = "pack_comeback_quest";
            channel = "in_game";
        } else {
            pack = "pack_login_bonus";
            channel = "mail";
            action = "push_login_bonus";
        }
        Intervention i = new Intervention(true, action, pack, channel,
                "churn>=" + churnThreshold, pred.churnRisk());
        last.put(playerId, i);
        return i;
    }

    public Map<String, Object> toMap(long playerId, Intervention i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("playerId", playerId);
        m.put("triggered", i.triggered());
        m.put("action", i.action());
        m.put("giftPackId", i.giftPackId());
        m.put("channel", i.channel());
        m.put("reason", i.reason());
        m.put("churnRisk", i.churnRisk());
        return m;
    }

    public Intervention lastOf(long playerId) {
        return last.get(playerId);
    }
}
