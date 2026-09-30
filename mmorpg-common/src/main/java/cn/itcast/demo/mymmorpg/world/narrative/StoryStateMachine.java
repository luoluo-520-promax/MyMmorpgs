package cn.itcast.demo.mymmorpg.world.narrative;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色传说任务状态机：Idle → Talking → Escort → Combat → Reward。
 * 对话选项回写玩家编年史位，不影响全服 flag。
 */
@Service
public class StoryStateMachine {

    public enum State {
        IDLE, TALKING, ESCORT, COMBAT, REWARD
    }

    public record DialogueOption(String optionId, String text, int affinityDelta, String nextHint) {
        public DialogueOption {
            optionId = optionId == null ? "" : optionId.trim();
            text = text == null ? "" : text;
            nextHint = nextHint == null ? "" : nextHint;
        }
    }

    public record StoryDef(
            String storyId,
            String characterId,
            String regionId,
            String title,
            List<DialogueOption> options,
            int chronicleBitIndex) {
        public StoryDef {
            storyId = storyId == null ? "" : storyId.trim();
            characterId = characterId == null ? "" : characterId.trim();
            regionId = regionId == null ? "" : regionId.trim();
            title = title == null ? storyId : title;
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Runtime(
            String storyId,
            State state,
            String chosenOptionId,
            int affinityDelta,
            long startedAtMs) {
    }

    private final ConcurrentHashMap<String, StoryDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Runtime> runtimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Integer>> npcAffinity =
            new ConcurrentHashMap<>();
    private final PlayerChronicleService chronicle;
    private InstanceRegionLockService regionLock;
    private CoopNarrativeProxy coopNarrative;

    public StoryStateMachine() {
        this(new PlayerChronicleService());
    }

    public StoryStateMachine(PlayerChronicleService chronicle) {
        this.chronicle = chronicle == null ? new PlayerChronicleService() : chronicle;
    }

    public void bindRegionLock(InstanceRegionLockService regionLock) {
        this.regionLock = regionLock;
    }

    public void bindCoopNarrative(CoopNarrativeProxy coopNarrative) {
        this.coopNarrative = coopNarrative;
    }

    public void register(StoryDef def) {
        if (def != null && !def.storyId().isBlank()) {
            defs.put(def.storyId(), def);
            // 个人编年史节点：仅用于该玩家对话库，不驱动全服过场
            String a = def.options().isEmpty() ? "A" : def.options().get(0).optionId();
            String b = def.options().size() < 2 ? "B" : def.options().get(1).optionId();
            chronicle.register(new PlayerChronicleService.ChoiceNode(
                    "story:" + def.storyId(), Math.max(0, def.chronicleBitIndex()), a, b));
        }
    }

    private static String key(long playerId, String storyId) {
        return playerId + ":" + (storyId == null ? "" : storyId.trim());
    }

    /**
     * 开启个人剧情实例：下发带选项对话 UI（MsgId 2450），并锁定区域潮汐。
     */
    public Map<String, Object> startInstance(long playerId, String storyId, long nowMs) {
        StoryDef def = defs.get(storyId == null ? "" : storyId.trim());
        if (def == null) {
            return Map.of("ok", false, "error", "story_not_found");
        }
        String k = key(playerId, def.storyId());
        Runtime prev = runtimes.get(k);
        if (prev != null && prev.state() != State.IDLE && prev.state() != State.REWARD) {
            return Map.of("ok", false, "error", "story_already_active", "state", prev.state().name());
        }
        runtimes.put(k, new Runtime(def.storyId(), State.TALKING, "", 0, nowMs));
        Map<String, Object> lockResult = Map.of("locked", false);
        if (regionLock != null && !def.regionId().isBlank()) {
            lockResult = regionLock.lockForStory(playerId, def.regionId(), def.storyId(), nowMs);
        }

        List<Map<String, Object>> opts = def.options().stream().map(o -> Map.<String, Object>of(
                "optionId", o.optionId(),
                "text", o.text(),
                "affinityDelta", o.affinityDelta(),
                "nextHint", o.nextHint()
        )).toList();

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("msgId", MessageId.STORY_INSTANCE_START_SC_NOTIFY);
        notify.put("event", "STORY_INSTANCE_START");
        notify.put("storyId", def.storyId());
        notify.put("characterId", def.characterId());
        notify.put("title", def.title());
        notify.put("mustPlay", true);
        notify.put("options", opts);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("storyId", def.storyId());
        body.put("state", State.TALKING.name());
        body.put("regionId", def.regionId());
        body.put("regionLock", lockResult);
        body.put("notify", notify);
        return body;
    }

    /**
     * 联机房主触发剧情：房主走原流程；访客经 CoopNarrativeProxy 播放 WORLD_SHIFT_SHIELD。
     */
    public Map<String, Object> startInstanceWithCoop(
            long playerId, String storyId, String coopRoomId, long nowMs) {
        Map<String, Object> base = startInstance(playerId, storyId, nowMs);
        if (!Boolean.TRUE.equals(base.get("ok")) || coopNarrative == null
                || coopRoomId == null || coopRoomId.isBlank()) {
            return base;
        }
        Map<String, Object> proxy = coopNarrative.onStoryInstanceStart(coopRoomId, playerId, storyId, nowMs);
        Map<String, Object> body = new LinkedHashMap<>(base);
        body.put("coopNarrative", proxy);
        if (Boolean.FALSE.equals(proxy.get("loadDialogueUi"))) {
            body.put("loadDialogueUi", false);
            body.put("notify", proxy);
        }
        return body;
    }

    public Map<String, Object> chooseDialogue(long playerId, String storyId, String optionId, long nowMs) {
        StoryDef def = defs.get(storyId == null ? "" : storyId.trim());
        Runtime rt = runtimes.get(key(playerId, storyId));
        if (def == null || rt == null) {
            return Map.of("ok", false, "error", "story_not_active");
        }
        if (rt.state() != State.TALKING) {
            return Map.of("ok", false, "error", "invalid_state", "state", rt.state().name());
        }
        DialogueOption chosen = null;
        for (DialogueOption o : def.options()) {
            if (o.optionId().equals(optionId)) {
                chosen = o;
                break;
            }
        }
        if (chosen == null) {
            return Map.of("ok", false, "error", "invalid_option");
        }
        // 回写个人编年史，不触发全服 global_flag
        chronicle.choose(playerId, "story:" + def.storyId(), optionId);
        npcAffinity.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .merge(def.characterId(), chosen.affinityDelta(), Integer::sum);
        runtimes.put(key(playerId, storyId),
                new Runtime(def.storyId(), State.ESCORT, chosen.optionId(), chosen.affinityDelta(), rt.startedAtMs()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("state", State.ESCORT.name());
        body.put("chosen", optionId);
        body.put("affinityDelta", chosen.affinityDelta());
        body.put("npcAffinity", npcAffinityOf(playerId, def.characterId()));
        body.put("chroniclePersonalOnly", true);
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> advance(long playerId, String storyId, State target) {
        Runtime rt = runtimes.get(key(playerId, storyId));
        StoryDef def = defs.get(storyId == null ? "" : storyId.trim());
        if (rt == null || def == null) {
            return Map.of("ok", false, "error", "story_not_active");
        }
        if (!isValidTransition(rt.state(), target)) {
            return Map.of("ok", false, "error", "invalid_transition",
                    "from", rt.state().name(), "to", target.name());
        }
        runtimes.put(key(playerId, storyId),
                new Runtime(def.storyId(), target, rt.chosenOptionId(), rt.affinityDelta(), rt.startedAtMs()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("state", target.name());
        body.put("storyId", def.storyId());
        if (target == State.REWARD && regionLock != null) {
            body.put("regionUnlock", regionLock.unlockForStory(playerId, def.storyId()));
        }
        return body;
    }

    public Map<String, Object> complete(long playerId, String storyId, long nowMs) {
        Map<String, Object> toReward = advance(playerId, storyId, State.REWARD);
        if (!Boolean.TRUE.equals(toReward.get("ok"))) {
            // 允许从 COMBAT 直接 complete
            Runtime rt = runtimes.get(key(playerId, storyId));
            if (rt != null && (rt.state() == State.COMBAT || rt.state() == State.ESCORT)) {
                runtimes.put(key(playerId, storyId),
                        new Runtime(rt.storyId(), State.REWARD, rt.chosenOptionId(),
                                rt.affinityDelta(), rt.startedAtMs()));
                StoryDef def = defs.get(storyId);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("state", State.REWARD.name());
                body.put("completed", true);
                body.put("grantPlans", List.of(Map.of("itemId", "story_token_" + storyId, "count", 1)));
                body.put("idempotencyKey", "story:" + playerId + ":" + storyId);
                if (regionLock != null && def != null) {
                    body.put("regionUnlock", regionLock.unlockForStory(playerId, def.storyId()));
                }
                body.put("atMs", nowMs);
                return body;
            }
            return toReward;
        }
        Map<String, Object> body = new LinkedHashMap<>(toReward);
        body.put("completed", true);
        body.put("grantPlans", List.of(Map.of("itemId", "story_token_" + storyId, "count", 1)));
        body.put("idempotencyKey", "story:" + playerId + ":" + storyId);
        body.put("atMs", nowMs);
        return body;
    }

    public State stateOf(long playerId, String storyId) {
        Runtime rt = runtimes.get(key(playerId, storyId));
        return rt == null ? State.IDLE : rt.state();
    }

    public int npcAffinityOf(long playerId, String characterId) {
        return npcAffinity.getOrDefault(playerId, new ConcurrentHashMap<>())
                .getOrDefault(characterId == null ? "" : characterId, 0);
    }

    public List<Map<String, Object>> dialogueLibrary(long playerId, String characterId) {
        int aff = npcAffinityOf(playerId, characterId);
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(Map.of("tier", "base", "text", "你好，旅行者。"));
        if (aff >= 10) {
            lines.add(Map.of("tier", "friendly", "text", "上次多亏了你……"));
        }
        if (aff >= 30) {
            lines.add(Map.of("tier", "bond", "text", "我有件事只想拜托你。"));
        }
        return lines;
    }

    private static boolean isValidTransition(State from, State to) {
        return switch (from) {
            case IDLE -> to == State.TALKING;
            case TALKING -> to == State.ESCORT || to == State.COMBAT;
            case ESCORT -> to == State.COMBAT || to == State.REWARD;
            case COMBAT -> to == State.REWARD;
            case REWARD -> to == State.IDLE;
        };
    }
}
