package cn.itcast.demo.mymmorpg.world.puzzle;

import cn.itcast.demo.mymmorpg.world.social.PhantomBorrowService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人大世界协同解谜：宽松态（前30s 幻影补位）→ 硬性态（动态降员/临时 Buff）+ Checkpoint 容错。
 */
@Service
public class CoopPuzzleService {

    public static final long LEAVE_TIMEOUT_MS = 5_000L;
    public static final long LOOSE_PHASE_MS = 30_000L;
    public static final float TEMP_BUFF_ATTR_BONUS = 0.20f;

    public enum Phase { LOOSE, STRICT }

    public record PuzzleDef(
            String puzzleId,
            String zoneId,
            int requiredMembers,
            String gadgetId) {
        public PuzzleDef {
            puzzleId = puzzleId == null ? "" : puzzleId.trim();
            zoneId = zoneId == null ? "" : zoneId.trim();
            requiredMembers = Math.max(2, requiredMembers);
            gadgetId = gadgetId == null ? "" : gadgetId.trim();
        }
    }

    public record MemberStamp(long playerId, String zoneId, long tsMs, boolean phantom) {
        public MemberStamp(long playerId, String zoneId, long tsMs) {
            this(playerId, zoneId, tsMs, false);
        }
    }

    public record Checkpoint(
            String puzzleId, int activatedCount, int required, List<Long> players, long atMs) {
    }

    private final ConcurrentHashMap<String, PuzzleDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, MemberStamp>> pending =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> gadgetStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionStartMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> effectiveRequired = new ConcurrentHashMap<>();
    private PhantomBorrowService phantomBorrow;

    public void bindPhantomBorrow(PhantomBorrowService phantomBorrow) {
        this.phantomBorrow = phantomBorrow;
    }

    public void register(PuzzleDef def) {
        if (def != null && !def.puzzleId().isBlank()) {
            defs.put(def.puzzleId(), def);
            gadgetStates.putIfAbsent(def.gadgetId().isBlank() ? def.puzzleId() : def.gadgetId(), "IDLE");
            effectiveRequired.put(def.puzzleId(), def.requiredMembers());
        }
    }

    public Phase phaseOf(String puzzleId, long nowMs) {
        Long start = sessionStartMs.get(puzzleId == null ? "" : puzzleId.trim());
        if (start == null) {
            return Phase.LOOSE;
        }
        return nowMs - start < LOOSE_PHASE_MS ? Phase.LOOSE : Phase.STRICT;
    }

    /**
     * 踩下压力板：兼容原满员同 Zone 逻辑；增强宽松/硬性态与 Checkpoint。
     */
    public Map<String, Object> pressPlate(String puzzleId, long playerId, String zoneId, long nowMs) {
        PuzzleDef def = defs.get(puzzleId == null ? "" : puzzleId.trim());
        if (def == null) {
            return Map.of("ok", false, "error", "puzzle_not_found");
        }
        String z = zoneId == null ? "" : zoneId.trim();
        sessionStartMs.putIfAbsent(def.puzzleId(), nowMs);
        ConcurrentHashMap<Long, MemberStamp> set =
                pending.computeIfAbsent(def.puzzleId(), id -> new ConcurrentHashMap<>());
        set.entrySet().removeIf(e -> !e.getValue().phantom()
                && nowMs - e.getValue().tsMs() > LEAVE_TIMEOUT_MS);
        set.put(playerId, new MemberStamp(playerId, z, nowMs, false));

        Phase phase = phaseOf(def.puzzleId(), nowMs);
        int need = effectiveRequired.getOrDefault(def.puzzleId(), def.requiredMembers());

        List<Long> sameZone = new ArrayList<>();
        List<Long> phantoms = new ArrayList<>();
        for (MemberStamp m : set.values()) {
            if (def.zoneId().equals(m.zoneId())) {
                sameZone.add(m.playerId());
                if (m.phantom()) {
                    phantoms.add(m.playerId());
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("puzzleId", def.puzzleId());
        body.put("redisKey", "coop_puzzle:" + def.puzzleId());
        body.put("phase", phase.name());
        body.put("members", sameZone);
        body.put("required", need);
        body.put("pending", set.size());

        if (phase == Phase.LOOSE && sameZone.size() < need && phantomBorrow != null) {
            int missing = need - sameZone.size();
            for (int i = 0; i < missing; i++) {
                long phantomId = -1_000_000L - i - (playerId % 1000);
                set.put(phantomId, new MemberStamp(phantomId, def.zoneId(), nowMs, true));
                sameZone.add(phantomId);
                phantoms.add(phantomId);
            }
            body.put("phantomBorrowed", true);
            body.put("phantoms", phantoms);
            body.put("members", sameZone);
            body.put("hint", "宽松态：幻影残影补位，进度可推进");
        }

        long realOnline = sameZone.stream().filter(id -> id > 0).count();
        if (phase == Phase.STRICT && realOnline < need && realOnline >= 2) {
            need = (int) realOnline;
            effectiveRequired.put(def.puzzleId(), need);
            body.put("dynamicDifficulty", true);
            body.put("required", need);
            body.put("downgradedFrom", def.requiredMembers());
            body.put("event", "DYNAMIC_DIFFICULTY_ADJUSTMENT");
            body.put("tempBuff", Map.of(
                    "type", "TEMPORARY_BUFF",
                    "attrBonus", TEMP_BUFF_ATTR_BONUS,
                    "targets", sameZone.stream().filter(id -> id > 0).toList()));
            body.put("hint", "硬性态：压力板降级并下发临时属性+20%");
        }

        Checkpoint cp = new Checkpoint(
                def.puzzleId(), sameZone.size(), need,
                List.copyOf(sameZone.stream().filter(id -> id > 0).toList()), nowMs);
        checkpoints.put(def.puzzleId(), cp);
        body.put("checkpoint", Map.of(
                "activated", cp.activatedCount(),
                "required", cp.required(),
                "players", cp.players(),
                "atMs", cp.atMs()));

        int countForStart = phase == Phase.LOOSE ? sameZone.size() : (int) realOnline;
        // 兼容旧用例：未绑幻影时仍按真实同 Zone 人数判定
        if (phantomBorrow == null && phase == Phase.LOOSE) {
            countForStart = (int) realOnline;
        }
        if (countForStart >= need) {
            String gid = def.gadgetId().isBlank() ? def.puzzleId() : def.gadgetId();
            gadgetStates.put(gid, "ACTIVE");
            pending.remove(def.puzzleId());
            sessionStartMs.remove(def.puzzleId());
            body.put("started", true);
            body.put("event", "CoopPuzzleStart");
            body.put("forceCutscene", true);
            body.put("gadgetState", "ACTIVE");
            body.put("party", sameZone.stream().filter(id -> id > 0).toList());
        } else {
            body.put("started", false);
            body.put("gadgetState", gadgetStates.getOrDefault(
                    def.gadgetId().isBlank() ? def.puzzleId() : def.gadgetId(), "IDLE"));
        }
        return body;
    }

    public Map<String, Object> resumeCheckpoint(String puzzleId) {
        Checkpoint cp = checkpoints.get(puzzleId == null ? "" : puzzleId.trim());
        if (cp == null) {
            return Map.of("ok", false, "error", "no_checkpoint");
        }
        return Map.of(
                "ok", true,
                "puzzleId", cp.puzzleId(),
                "activated", cp.activatedCount(),
                "required", cp.required(),
                "players", cp.players(),
                "atMs", cp.atMs(),
                "resumeScene", true,
                "reset", false);
    }

    /** 成员离开：不清空 Checkpoint（容错进度）。 */
    public Map<String, Object> tickLeave(String puzzleId, long nowMs) {
        PuzzleDef def = defs.get(puzzleId);
        if (def == null) {
            return Map.of("ok", false, "error", "puzzle_not_found");
        }
        ConcurrentHashMap<Long, MemberStamp> set = pending.get(def.puzzleId());
        if (set == null || set.isEmpty()) {
            return Map.of("ok", true, "cleared", false, "pending", 0,
                    "checkpointPreserved", checkpoints.containsKey(def.puzzleId()));
        }
        int before = set.size();
        set.entrySet().removeIf(e -> !e.getValue().phantom()
                && nowMs - e.getValue().tsMs() > LEAVE_TIMEOUT_MS);
        boolean emptied = set.values().stream().noneMatch(m -> !m.phantom());
        if (emptied) {
            pending.remove(def.puzzleId());
            if (!checkpoints.containsKey(def.puzzleId())) {
                String gid = def.gadgetId().isBlank() ? def.puzzleId() : def.gadgetId();
                gadgetStates.put(gid, "IDLE");
            }
        }
        return Map.of("ok", true, "cleared", emptied, "before", before, "pending", set.size(),
                "checkpointPreserved", checkpoints.containsKey(def.puzzleId()),
                "gadgetState", gadgetStates.getOrDefault(
                        def.gadgetId().isBlank() ? def.puzzleId() : def.gadgetId(), "IDLE"));
    }

    public Map<String, Object> snapshot(String puzzleId) {
        PuzzleDef def = defs.get(puzzleId);
        if (def == null) {
            return Map.of("ok", false, "error", "puzzle_not_found");
        }
        ConcurrentHashMap<Long, MemberStamp> set =
                pending.getOrDefault(puzzleId, new ConcurrentHashMap<>());
        Checkpoint cp = checkpoints.get(puzzleId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("puzzleId", puzzleId);
        body.put("zoneId", def.zoneId());
        body.put("requiredMembers", effectiveRequired.getOrDefault(puzzleId, def.requiredMembers()));
        body.put("pendingCount", set.size());
        body.put("gadgetState", gadgetStates.getOrDefault(
                def.gadgetId().isBlank() ? puzzleId : def.gadgetId(), "IDLE"));
        if (cp != null) {
            body.put("checkpoint", Map.of(
                    "activated", cp.activatedCount(),
                    "required", cp.required(),
                    "players", cp.players()));
        }
        return body;
    }
}
