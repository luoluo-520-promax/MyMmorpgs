package cn.itcast.demo.mymmorpg.element;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素反应引擎：附着(Aura/Gauge) → 触发 → 反应 → 精通放大 → 残留。
 * 支持客户端预测伤害/反应类型，服务端权威结算并标记 rollback。
 */
@Service
public class ElementReactionEngine {

    /** 附着默认持续 12 秒 */
    public static final long AURA_TTL_MS = 12_000L;
    /** 预测伤害允许相对误差（含取整） */
    public static final double PREDICT_TOLERANCE = 0.05;
    /** 精通转化：每 100 精通 ≈ +10% 反应倍率 */
    public static final double MASTERY_PER_100 = 0.10;

    private final ConcurrentHashMap<String, ElementAura> auras = new ConcurrentHashMap<>();

    public ReactionResult resolve(long battleId, long targetEntityId, ElementType applied,
                                  int baseDamage, int clientPredictedDamage, int clientPredictedReaction) {
        return resolve(battleId, targetEntityId, applied, baseDamage,
                clientPredictedDamage, clientPredictedReaction, 1, 0);
    }

    /**
     * @param gaugeApplied 本次附着量 1/2/4
     * @param mastery      触发者元素精通
     */
    public ReactionResult resolve(long battleId, long targetEntityId, ElementType applied,
                                  int baseDamage, int clientPredictedDamage, int clientPredictedReaction,
                                  int gaugeApplied, int mastery) {
        if (baseDamage < 0) {
            baseDamage = 0;
        }
        int gauge = ElementAura.normalizeGauge(gaugeApplied);
        int em = Math.max(0, mastery);
        if (applied == null || applied == ElementType.NONE) {
            return validatePrediction(ReactionResult.noReaction(baseDamage),
                    clientPredictedDamage, clientPredictedReaction);
        }

        long now = System.currentTimeMillis();
        String key = auraKey(battleId, targetEntityId);
        ElementAura existing = auras.get(key);
        if (existing != null && existing.isExpired(now)) {
            auras.remove(key, existing);
            existing = null;
        }

        // 1) 附着：无 Aura 则只挂元素
        if (existing == null || existing.element() == ElementType.NONE) {
            auras.put(key, new ElementAura(applied, 1, now + AURA_TTL_MS, gauge));
            return validatePrediction(
                    new ReactionResult(baseDamage, baseDamage, ReactionType.NONE, applied, false, clientPredictedDamage),
                    clientPredictedDamage, clientPredictedReaction);
        }

        // 2) 触发：同元素叠层（元素量取较大）
        if (existing.element() == applied) {
            int stackedGauge = Math.min(4, existing.gaugeUnits() + gauge);
            ElementAura stacked = new ElementAura(applied, Math.min(3, existing.stacks() + 1),
                    now + AURA_TTL_MS, stackedGauge);
            auras.put(key, stacked);
            return validatePrediction(
                    new ReactionResult(baseDamage, baseDamage, ReactionType.NONE, applied, false, clientPredictedDamage),
                    clientPredictedDamage, clientPredictedReaction);
        }

        // 3) 反应 + 4) 倍率 × (1 + 精通转化)
        ReactionType reaction = matchReaction(existing.element(), applied);
        double masteryBonus = 1.0 + (em / 100.0) * MASTERY_PER_100;
        double mult = reaction.getMultiplier() * masteryBonus;
        int finalDamage = (int) Math.max(1, Math.round(baseDamage * mult));
        ElementAura residual = consumeAuraWithGauge(existing, applied, gauge, reaction, now);
        if (residual == null || residual.element() == ElementType.NONE || residual.gaugeUnits() <= 0) {
            auras.remove(key);
            residual = new ElementAura(ElementType.NONE, 0, now, 0);
        } else {
            auras.put(key, residual);
        }

        ReactionResult result = new ReactionResult(baseDamage, finalDamage, reaction, residual.element(),
                false, clientPredictedDamage);
        return validatePrediction(result, clientPredictedDamage, clientPredictedReaction);
    }

    public ElementType currentAura(long battleId, long targetEntityId) {
        ElementAura aura = auras.get(auraKey(battleId, targetEntityId));
        if (aura == null || aura.isExpired(System.currentTimeMillis())) {
            return ElementType.NONE;
        }
        return aura.element();
    }

    public ElementAura currentAuraStack(long battleId, long targetEntityId) {
        ElementAura aura = auras.get(auraKey(battleId, targetEntityId));
        if (aura == null || aura.isExpired(System.currentTimeMillis())) {
            return null;
        }
        return aura;
    }

    public void clearBattle(long battleId) {
        String prefix = battleId + ":";
        auras.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 仅供测试：直接写入 Aura。 */
    void putAuraForTest(long battleId, long targetEntityId, ElementAura aura) {
        auras.put(auraKey(battleId, targetEntityId), aura);
    }

    Map<String, ElementAura> auraSnapshotForTest() {
        return Map.copyOf(auras);
    }

    static ReactionType matchReaction(ElementType aura, ElementType trigger) {
        if (isPair(aura, trigger, ElementType.PYRO, ElementType.ELECTRO)) {
            return ReactionType.OVERLOAD;
        }
        if (isPair(aura, trigger, ElementType.CRYO, ElementType.HYDRO)) {
            return ReactionType.FREEZE;
        }
        if (isPair(aura, trigger, ElementType.CRYO, ElementType.ELECTRO)) {
            return ReactionType.SUPERCONDUCT;
        }
        if (isPair(aura, trigger, ElementType.PYRO, ElementType.HYDRO)) {
            return ReactionType.VAPORIZE;
        }
        if (isPair(aura, trigger, ElementType.PYRO, ElementType.CRYO)) {
            return ReactionType.MELT;
        }
        if (isPair(aura, trigger, ElementType.ELECTRO, ElementType.HYDRO)) {
            return ReactionType.ELECTRO_CHARGED;
        }
        if (trigger == ElementType.ANEMO || aura == ElementType.ANEMO) {
            return ReactionType.SWIRL;
        }
        if (trigger == ElementType.GEO || aura == ElementType.GEO) {
            return ReactionType.CRYSTALLIZE;
        }
        if (isPair(aura, trigger, ElementType.DENDRO, ElementType.HYDRO)) {
            return ReactionType.BLOOM;
        }
        return ReactionType.NONE;
    }

    private static boolean isPair(ElementType a, ElementType b, ElementType x, ElementType y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    /**
     * 元素量对撞残留：如蒸发火打水 → 水消失，火残留减半（若火 gauge &gt; 水）。
     */
    static ElementAura consumeAuraWithGauge(
            ElementAura existing, ElementType trigger, int triggerGauge,
            ReactionType reaction, long now) {
        if (reaction == ReactionType.NONE) {
            return existing;
        }
        return switch (reaction) {
            case SWIRL, CRYSTALLIZE -> existing;
            case OVERLOAD, FREEZE -> null;
            case VAPORIZE, MELT -> residualAfterAmplifying(existing, trigger, triggerGauge, now);
            default -> {
                int left = existing.gaugeUnits() - triggerGauge;
                if (left > 0) {
                    yield existing.withGauge(left);
                }
                yield new ElementAura(trigger, 1, now + AURA_TTL_MS,
                        Math.max(1, triggerGauge - existing.gaugeUnits()) / 2);
            }
        };
    }

    private static ElementAura residualAfterAmplifying(
            ElementAura existing, ElementType trigger, int triggerGauge, long now) {
        int auraG = existing.gaugeUnits();
        int trigG = ElementAura.normalizeGauge(triggerGauge);
        if (auraG > trigG) {
            // 原附着量更大：保留原元素，减去触发量
            return existing.withGauge(auraG - trigG);
        }
        if (trigG > auraG) {
            // 触发量更大：触发元素残留减半
            int left = Math.max(1, (trigG - auraG) / 2);
            return new ElementAura(trigger, 1, now + AURA_TTL_MS, left);
        }
        return null;
    }

    private static ReactionResult validatePrediction(ReactionResult result,
                                                     int clientPredictedDamage,
                                                     int clientPredictedReaction) {
        if (clientPredictedDamage <= 0 && clientPredictedReaction <= 0) {
            return result;
        }
        boolean damageMismatch = false;
        if (clientPredictedDamage > 0) {
            double tol = Math.max(1.0, result.finalDamage() * PREDICT_TOLERANCE);
            damageMismatch = Math.abs(clientPredictedDamage - result.finalDamage()) > tol;
        }
        boolean reactionMismatch = clientPredictedReaction > 0
                && clientPredictedReaction != result.reaction().getCode();
        if (damageMismatch || reactionMismatch) {
            return new ReactionResult(result.baseDamage(), result.finalDamage(), result.reaction(),
                    result.remainingAura(), true, clientPredictedDamage);
        }
        return result;
    }

    private static String auraKey(long battleId, long targetEntityId) {
        return battleId + ":" + targetEntityId;
    }
}
