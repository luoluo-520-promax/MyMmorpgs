package cn.itcast.demo.mymmorpg.ai.npc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * NPC 多维情感向量：信任 / 好感 / 愤怒 / 恐惧，影响后续行为权重。
 */
public final class NpcEmotionVector {

    public record Emotion(double trust, double affection, double anger, double fear) {

        public Emotion {
            trust = clamp(trust);
            affection = clamp(affection);
            anger = clamp(anger);
            fear = clamp(fear);
        }

        public static Emotion neutral() {
            return new Emotion(0.3, 0.3, 0.0, 0.0);
        }

        public Map<String, Double> toMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("trust", trust);
            m.put("affection", affection);
            m.put("anger", anger);
            m.put("fear", fear);
            return m;
        }

        public String dominantMood() {
            if (fear >= 0.6) {
                return "FEARFUL";
            }
            if (anger >= 0.55) {
                return "ANGRY";
            }
            if (affection >= 0.65 && trust >= 0.5) {
                return "FRIENDLY";
            }
            if (trust >= 0.7) {
                return "TRUSTING";
            }
            return "NEUTRAL";
        }
    }

    public enum InteractionKind {
        DIALOGUE_POSITIVE, DIALOGUE_NEGATIVE, GIFT, QUEST_HELP, COMBAT_ASSIST, PLAYER_ATTACK, ENVIRONMENT_DANGER
    }

    public static Emotion apply(Emotion prev, InteractionKind kind, double intensity) {
        Emotion base = prev == null ? Emotion.neutral() : prev;
        double i = clamp(intensity <= 0 ? 0.2 : intensity);
        return switch (kind == null ? InteractionKind.DIALOGUE_POSITIVE : kind) {
            case DIALOGUE_POSITIVE -> new Emotion(base.trust() + 0.05 * i, base.affection() + 0.08 * i,
                    base.anger() - 0.03 * i, base.fear() - 0.02 * i);
            case DIALOGUE_NEGATIVE -> new Emotion(base.trust() - 0.06 * i, base.affection() - 0.05 * i,
                    base.anger() + 0.1 * i, base.fear());
            case GIFT -> new Emotion(base.trust() + 0.04 * i, base.affection() + 0.15 * i,
                    base.anger() - 0.05 * i, base.fear());
            case QUEST_HELP, COMBAT_ASSIST -> new Emotion(base.trust() + 0.12 * i, base.affection() + 0.06 * i,
                    base.anger() - 0.04 * i, base.fear() - 0.03 * i);
            case PLAYER_ATTACK -> new Emotion(base.trust() - 0.2 * i, base.affection() - 0.15 * i,
                    base.anger() + 0.25 * i, base.fear() + 0.15 * i);
            case ENVIRONMENT_DANGER -> new Emotion(base.trust(), base.affection(),
                    base.anger(), base.fear() + 0.2 * i);
        };
    }

    public static InteractionKind inferFromText(String message) {
        if (message == null || message.isBlank()) {
            return InteractionKind.DIALOGUE_POSITIVE;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("打") || lower.contains("杀") || lower.contains("攻击")) {
            return InteractionKind.PLAYER_ATTACK;
        }
        if (lower.contains("礼物") || lower.contains("送给") || lower.contains("赠")) {
            return InteractionKind.GIFT;
        }
        if (lower.contains("危险") || lower.contains("怪物") || lower.contains("boss")) {
            return InteractionKind.ENVIRONMENT_DANGER;
        }
        if (lower.contains("讨厌") || lower.contains("滚") || lower.contains("白痴")) {
            return InteractionKind.DIALOGUE_NEGATIVE;
        }
        return InteractionKind.DIALOGUE_POSITIVE;
    }

    /**
     * 行为树权重调整：高恐惧→逃跑，高信任→协助。
     */
    public static Map<String, Double> behaviorWeights(Emotion e) {
        Emotion emo = e == null ? Emotion.neutral() : e;
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("flee", round2(0.2 + emo.fear() * 0.7));
        w.put("assist", round2(0.2 + emo.trust() * 0.6 + emo.affection() * 0.2));
        w.put("attack", round2(0.15 + emo.anger() * 0.75));
        w.put("chat", round2(0.3 + emo.affection() * 0.4 - emo.anger() * 0.2));
        w.put("unlockHiddenQuest", emo.affection() >= 0.7 && emo.trust() >= 0.6 ? 1.0 : 0.0);
        return w;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
