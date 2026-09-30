package cn.itcast.demo.mymmorpg.ai.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 长期关系记忆：跨会话持久（进程内 + 可选 Redis 由调用方序列化）。
 */
public final class NpcLongTermMemory {

    public record MemoryEvent(
            String type,
            String summary,
            long atMs,
            Map<String, Object> payload) {

        public MemoryEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record Bond(
            long playerId,
            String npcId,
            NpcEmotionVector.Emotion emotion,
            List<MemoryEvent> history,
            int giftCount,
            int questHelps,
            int combatAssists,
            long updatedAtMs) {

        public Bond {
            history = history == null ? List.of() : List.copyOf(history);
            emotion = emotion == null ? NpcEmotionVector.Emotion.neutral() : emotion;
        }
    }

    private static final int MAX_HISTORY = 40;
    private final ConcurrentHashMap<String, MutableBond> bonds = new ConcurrentHashMap<>();

    public Bond get(long playerId, String npcId) {
        MutableBond b = bonds.get(key(playerId, npcId));
        if (b == null) {
            return new Bond(playerId, npcId == null ? "guide" : npcId,
                    NpcEmotionVector.Emotion.neutral(), List.of(), 0, 0, 0, System.currentTimeMillis());
        }
        synchronized (b) {
            return snapshot(b);
        }
    }

    public Bond interact(long playerId, String npcId, NpcEmotionVector.InteractionKind kind,
                         String summary, double intensity) {
        String k = key(playerId, npcId);
        MutableBond b = bonds.computeIfAbsent(k, id -> new MutableBond(playerId, npcId == null ? "guide" : npcId));
        synchronized (b) {
            b.emotion = NpcEmotionVector.apply(b.emotion, kind, intensity);
            if (kind == NpcEmotionVector.InteractionKind.GIFT) {
                b.giftCount++;
            } else if (kind == NpcEmotionVector.InteractionKind.QUEST_HELP) {
                b.questHelps++;
            } else if (kind == NpcEmotionVector.InteractionKind.COMBAT_ASSIST) {
                b.combatAssists++;
            }
            b.history.add(new MemoryEvent(
                    kind == null ? "UNKNOWN" : kind.name(),
                    summary == null ? "" : truncate(summary, 120),
                    System.currentTimeMillis(),
                    Map.of("intensity", intensity)));
            while (b.history.size() > MAX_HISTORY) {
                b.history.remove(0);
            }
            b.updatedAtMs = System.currentTimeMillis();
            return snapshot(b);
        }
    }

    public Map<String, Object> toMap(long playerId, String npcId) {
        Bond bond = get(playerId, npcId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playerId", bond.playerId());
        m.put("npcId", bond.npcId());
        m.put("emotion", bond.emotion().toMap());
        m.put("mood", bond.emotion().dominantMood());
        m.put("behaviorWeights", NpcEmotionVector.behaviorWeights(bond.emotion()));
        m.put("giftCount", bond.giftCount());
        m.put("questHelps", bond.questHelps());
        m.put("combatAssists", bond.combatAssists());
        m.put("historySize", bond.history().size());
        m.put("updatedAtMs", bond.updatedAtMs());
        List<Map<String, Object>> recent = new ArrayList<>();
        int from = Math.max(0, bond.history().size() - 5);
        for (int i = from; i < bond.history().size(); i++) {
            MemoryEvent e = bond.history().get(i);
            recent.add(Map.of("type", e.type(), "summary", e.summary(), "atMs", e.atMs()));
        }
        m.put("recentMemories", recent);
        return m;
    }

    public void importBond(Bond bond) {
        if (bond == null) {
            return;
        }
        MutableBond b = new MutableBond(bond.playerId(), bond.npcId());
        b.emotion = bond.emotion();
        b.history.addAll(bond.history());
        b.giftCount = bond.giftCount();
        b.questHelps = bond.questHelps();
        b.combatAssists = bond.combatAssists();
        b.updatedAtMs = bond.updatedAtMs();
        bonds.put(key(bond.playerId(), bond.npcId()), b);
    }

    private static Bond snapshot(MutableBond b) {
        return new Bond(b.playerId, b.npcId, b.emotion, new ArrayList<>(b.history),
                b.giftCount, b.questHelps, b.combatAssists, b.updatedAtMs);
    }

    private static String key(long playerId, String npcId) {
        return playerId + ":" + (npcId == null || npcId.isBlank() ? "guide" : npcId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static final class MutableBond {
        final long playerId;
        final String npcId;
        NpcEmotionVector.Emotion emotion = NpcEmotionVector.Emotion.neutral();
        final List<MemoryEvent> history = new ArrayList<>();
        int giftCount;
        int questHelps;
        int combatAssists;
        long updatedAtMs = System.currentTimeMillis();

        MutableBond(long playerId, String npcId) {
            this.playerId = playerId;
            this.npcId = npcId;
        }
    }
}
