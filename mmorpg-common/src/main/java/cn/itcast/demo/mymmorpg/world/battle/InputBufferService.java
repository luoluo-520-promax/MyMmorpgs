package cn.itcast.demo.mymmorpg.world.battle;



import org.springframework.stereotype.Service;



import java.util.ArrayDeque;

import java.util.LinkedHashMap;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;



/**

 * 预输入队列：dodgeWindowMs 结束后额外保留窗口；支持取消优先级与动态帧间隔缩放。

 */

@Service

public class InputBufferService {



    public static final int POST_DODGE_BUFFER_MS = 100;

    public static final String EVENT_INPUT_CLEAR = "INPUT_CLEAR";



    public record BufferedInput(String action, CancelAction expectedCancelAction, long queuedAtMs, int clientDeltaMs) {

    }



    private final ConcurrentHashMap<Long, ArrayDeque<BufferedInput>> queues =

            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Long> bufferDeadlineMs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Integer> lastClientDeltaMs = new ConcurrentHashMap<>();

    private final ReactionValidator reactions;

    private PoiseService poise;



    public InputBufferService() {

        this(new ReactionValidator());

    }



    public InputBufferService(ReactionValidator reactions) {

        this.reactions = reactions == null ? new ReactionValidator() : reactions;

    }



    public void bindPoise(PoiseService poise) {

        this.poise = poise;

    }



    /**

     * 闪避窗口开启时同步开启预输入保留截止时间 = openAt + dodgeWindow + effectiveWindow。

     */

    public Map<String, Object> armAfterDodgeWindow(

            long playerId, String attackId, long nowMs, int dodgeWindowMs) {

        return armAfterDodgeWindow(playerId, attackId, nowMs, dodgeWindowMs, 16);

    }



    public Map<String, Object> armAfterDodgeWindow(

            long playerId, String attackId, long nowMs, int dodgeWindowMs, int clientDeltaMs) {

        int dodge = dodgeWindowMs > 0 ? dodgeWindowMs : ReactionValidator.DEFAULT_DODGE_WINDOW_MS;

        int effective = effectiveWindowMs(clientDeltaMs);

        reactions.openAttackWindow(attackId, playerId, nowMs, dodge, ReactionValidator.DEFAULT_PARRY_WINDOW_MS);

        long deadline = nowMs + dodge + effective;

        bufferDeadlineMs.put(playerId, deadline);

        queues.put(playerId, new ArrayDeque<>());

        lastClientDeltaMs.put(playerId, Math.max(1, clientDeltaMs));

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("ok", true);

        body.put("playerId", playerId);

        body.put("attackId", attackId);

        body.put("dodgeWindowMs", dodge);

        body.put("postBufferMs", effective);

        body.put("effectiveWindowMs", effective);

        body.put("bufferDeadlineMs", deadline);

        return body;

    }



    public static int effectiveWindowMs(int clientDeltaMs) {

        int delta = Math.max(1, clientDeltaMs);

        return POST_DODGE_BUFFER_MS + delta * 2;

    }



    public Map<String, Object> enqueue(long playerId, String action, long nowMs) {

        return enqueue(playerId, action, CancelAction.NONE, nowMs, lastClientDeltaMs.getOrDefault(playerId, 16));

    }



    public Map<String, Object> enqueue(

            long playerId, String action, CancelAction expectedCancelAction, long nowMs, int clientDeltaMs) {

        Long deadline = bufferDeadlineMs.get(playerId);

        int effective = effectiveWindowMs(clientDeltaMs);

        if (deadline == null || nowMs > deadline) {

            Map<String, Object> cancel = reactions.tryCancel(playerId, expectedCancelAction, poise, nowMs);

            if (Boolean.TRUE.equals(cancel.get("cancelAllowed"))) {

                Map<String, Object> body = new LinkedHashMap<>(cancel);

                body.put("ok", true);

                body.put("queued", action);

                body.put("bypassBuffer", true);

                return body;

            }

            clear(playerId);

            return Map.of("ok", false, "error", "buffer_expired",

                    "event", EVENT_INPUT_CLEAR, "action", action);

        }

        ArrayDeque<BufferedInput> q = queues.computeIfAbsent(playerId, id -> new ArrayDeque<>());

        if (q.size() >= 4) {

            q.pollFirst();

        }

        q.addLast(new BufferedInput(action, expectedCancelAction, nowMs, clientDeltaMs));

        lastClientDeltaMs.put(playerId, Math.max(1, clientDeltaMs));

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("ok", true);

        body.put("queued", action);

        body.put("queueSize", q.size());

        body.put("bufferRemainMs", deadline - nowMs);

        body.put("effectiveWindowMs", effective);

        body.put("expectedCancelAction",

                expectedCancelAction == null ? CancelAction.NONE.name() : expectedCancelAction.name());

        return body;

    }



    public Map<String, Object> consume(long playerId, long nowMs) {

        Long deadline = bufferDeadlineMs.get(playerId);

        if (deadline == null || nowMs > deadline) {

            Map<String, Object> cleared = clear(playerId);

            cleared.put("consumed", false);

            return cleared;

        }

        ArrayDeque<BufferedInput> q = queues.get(playerId);

        if (q == null || q.isEmpty()) {

            return Map.of("ok", true, "consumed", false, "empty", true);

        }

        BufferedInput in = q.pollFirst();

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("ok", true);

        body.put("consumed", true);

        body.put("action", in.action());

        body.put("queuedAtMs", in.queuedAtMs());

        body.put("clientDeltaMs", in.clientDeltaMs());

        body.put("expectedCancelAction",

                in.expectedCancelAction() == null ? CancelAction.NONE.name() : in.expectedCancelAction().name());

        return body;

    }



    public Map<String, Object> clear(long playerId) {

        queues.remove(playerId);

        bufferDeadlineMs.remove(playerId);

        return Map.of("ok", true, "event", EVENT_INPUT_CLEAR, "playerId", playerId);

    }



    public Map<String, Object> tick(long playerId, long nowMs) {

        Long deadline = bufferDeadlineMs.get(playerId);

        if (deadline != null && nowMs > deadline) {

            return clear(playerId);

        }

        return Map.of("ok", true, "event", "KEEP", "bufferRemainMs",

                deadline == null ? 0 : deadline - nowMs);

    }

}


