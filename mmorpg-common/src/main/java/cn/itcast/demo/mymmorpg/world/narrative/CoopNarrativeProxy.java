package cn.itcast.demo.mymmorpg.world.narrative;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.social.SocialTokenService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 联机叙事代理：WORLD_SHIFT_SHIELD 升级为 HostMutexLock；SyncCutsceneState；
 * 助人之证升华为 SocialToken 可兑房主世界特产。
 */
@Service
public class CoopNarrativeProxy {

    public static final String WORLD_SHIFT_SHIELD = "WORLD_SHIFT_SHIELD";
    public static final String HOST_MUTEX_LOCK = "HOST_MUTEX_LOCK";
    public static final String BADGE_HELPER = "助人之证";
    public static final String GUEST_SPECTATE_HINT = "队友正在经历关键剧情...";

    private final ConcurrentHashMap<String, Long> roomHosts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> roomMembers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> assistCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Boolean>> badges =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> chaosHiddenForGuest = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> hostMutexLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> cutsceneSpectators = new ConcurrentHashMap<>();

    private PlayerChronicleService chronicle;
    private RegionImpactService regions;
    private SocialTokenService socialToken;

    public CoopNarrativeProxy() {
    }

    public CoopNarrativeProxy(PlayerChronicleService chronicle, RegionImpactService regions) {
        this.chronicle = chronicle;
        this.regions = regions;
    }

    public void bind(PlayerChronicleService chronicle, RegionImpactService regions) {
        this.chronicle = chronicle;
        this.regions = regions;
    }

    public void bindSocialToken(SocialTokenService socialToken) {
        this.socialToken = socialToken;
    }

    public Map<String, Object> registerRoom(String roomId, long hostPlayerId, long... guests) {
        String rid = roomId == null ? "" : roomId.trim();
        roomHosts.put(rid, hostPlayerId);
        ConcurrentHashMap<Long, Boolean> members = new ConcurrentHashMap<>();
        members.put(hostPlayerId, true);
        if (guests != null) {
            for (long g : guests) {
                if (g > 0 && g != hostPlayerId) {
                    members.put(g, true);
                }
            }
        }
        roomMembers.put(rid, members);
        return Map.of("ok", true, "roomId", rid, "hostPlayerId", hostPlayerId, "memberCount", members.size());
    }

    /**
     * 房主开启剧情：SyncCutsceneState — 访客旁观、移动封锁、选项置灰；HostMutexLock + UI_BLOCK_LAYER。
     */
    public Map<String, Object> onStoryInstanceStart(
            String roomId, long triggerPlayerId, String storyId, long nowMs) {
        String rid = roomId == null ? "" : roomId.trim();
        Long host = roomHosts.get(rid);
        ConcurrentHashMap<Long, Boolean> members = roomMembers.get(rid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("storyId", storyId);
        body.put("triggerPlayerId", triggerPlayerId);
        body.put("atMs", nowMs);

        if (host == null || members == null || members.size() <= 1) {
            body.put("coopMode", false);
            body.put("loadDialogueUi", true);
            body.put("msgId", MessageId.STORY_INSTANCE_START_SC_NOTIFY);
            return body;
        }

        boolean isHost = triggerPlayerId == host;
        body.put("coopMode", true);
        body.put("hostPlayerId", host);
        body.put("syncCutsceneState", true);

        if (isHost) {
            hostMutexLocks.put(rid, true);
            body.put("role", "HOST");
            body.put("loadDialogueUi", true);
            body.put("optionsEnabled", true);
            body.put("msgId", MessageId.STORY_INSTANCE_START_SC_NOTIFY);
            body.put("event", "STORY_INSTANCE_START");
            body.put("hostMutexLock", HOST_MUTEX_LOCK);
            body.put("msgIdUiBlock", MessageId.UI_BLOCK_LAYER_SC_NOTIFY);

            List<Map<String, Object>> guestNotifies = new ArrayList<>();
            for (Long mid : members.keySet()) {
                if (mid.equals(host)) {
                    continue;
                }
                cutsceneSpectators.put(mid, rid);
                chaosHiddenForGuest.put(mid, true);
                Map<String, Object> gn = new LinkedHashMap<>();
                gn.put("playerId", mid);
                gn.put("role", "GUEST");
                gn.put("loadDialogueUi", true);
                gn.put("optionsEnabled", false);
                gn.put("optionsGreyed", true);
                gn.put("canReadText", true);
                gn.put("moveBlocked", true);
                gn.put("popup", GUEST_SPECTATE_HINT);
                gn.put("effect", WORLD_SHIFT_SHIELD);
                gn.put("hostMutexLock", HOST_MUTEX_LOCK);
                gn.put("uiBlockLayer", true);
                gn.put("msgId", MessageId.UI_BLOCK_LAYER_SC_NOTIFY);
                gn.put("syncMsgId", MessageId.SYNC_CUTSCENE_STATE_SC_NOTIFY);
                guestNotifies.add(gn);
            }
            body.put("guestNotifies", guestNotifies);
        } else {
            cutsceneSpectators.put(triggerPlayerId, rid);
            chaosHiddenForGuest.put(triggerPlayerId, true);
            body.put("role", "GUEST");
            // 兼容 P16：访客不加载房主对话 UI，改播隔膜；P17 旁观增强另见 guestNotifies / syncCutscene
            body.put("loadDialogueUi", false);
            body.put("optionsEnabled", false);
            body.put("optionsGreyed", true);
            body.put("canReadText", true);
            body.put("moveBlocked", true);
            body.put("popup", GUEST_SPECTATE_HINT);
            body.put("effect", WORLD_SHIFT_SHIELD);
            body.put("hostMutexLock", HOST_MUTEX_LOCK);
            body.put("uiBlockLayer", true);
            body.put("msgId", MessageId.WORLD_SHIFT_SHIELD_SC_NOTIFY);
            body.put("syncMsgId", MessageId.SYNC_CUTSCENE_STATE_SC_NOTIFY);
            body.put("event", WORLD_SHIFT_SHIELD);
            body.put("chaosLayerHidden", true);
            if (regions != null) {
                body.put("regionChaosHidden", true);
            }
        }
        return body;
    }

    public boolean isHostMutexLocked(String roomId) {
        return Boolean.TRUE.equals(hostMutexLocks.get(roomId == null ? "" : roomId.trim()));
    }

    public boolean isMoveBlocked(long playerId) {
        return cutsceneSpectators.containsKey(playerId);
    }

    public Map<String, Object> releaseHostMutex(String roomId) {
        String rid = roomId == null ? "" : roomId.trim();
        hostMutexLocks.remove(rid);
        cutsceneSpectators.entrySet().removeIf(e -> rid.equals(e.getValue()));
        return Map.of("ok", true, "roomId", rid, "hostMutexReleased", true);
    }

    public Map<String, Object> chooseDialogueOption(String roomId, long playerId, String optionId) {
        String rid = roomId == null ? "" : roomId.trim();
        Long host = roomHosts.get(rid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", rid);
        body.put("playerId", playerId);
        body.put("optionId", optionId);
        if (host == null || playerId != host) {
            body.put("ok", false);
            body.put("error", "host_option_only");
            body.put("optionsEnabled", false);
            body.put("hint", "访客选项按钮置灰，仅可看文本");
            return body;
        }
        body.put("ok", true);
        body.put("optionsEnabled", true);
        body.put("applied", true);
        return body;
    }

    public boolean isChaosHiddenFor(long playerId) {
        return Boolean.TRUE.equals(chaosHiddenForGuest.get(playerId));
    }

    public void clearChaosHide(long playerId) {
        chaosHiddenForGuest.remove(playerId);
    }

    /**
     * 访客助力 → 助人之证 + SocialToken 联机专属代币（可兑房主世界特产）。
     */
    public Map<String, Object> recordCoopAssist(
            String roomId, long guestPlayerId, String storyBossId, long nowMs) {
        String rid = roomId == null ? "" : roomId.trim();
        Long host = roomHosts.get(rid);
        if (host == null || guestPlayerId == host) {
            return Map.of("ok", false, "error", "guest_required");
        }
        int count = assistCounts.computeIfAbsent(guestPlayerId, id -> new AtomicInteger(0))
                .incrementAndGet();
        badges.computeIfAbsent(guestPlayerId, id -> new ConcurrentHashMap<>())
                .put(BADGE_HELPER, true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("guestPlayerId", guestPlayerId);
        body.put("hostPlayerId", host);
        body.put("storyBossId", storyBossId);
        body.put("assistCount", count);
        body.put("badge", BADGE_HELPER);
        body.put("titleRedeemable", "限时称号·助人之证");
        body.put("atMs", nowMs);
        if (chronicle != null) {
            chronicle.register(new PlayerChronicleService.ChoiceNode(
                    "coop-assist:" + storyBossId, 90 + (int) (guestPlayerId % 20),
                    "SOLO", "HELPER"));
            Map<String, Object> choice = chronicle.choose(
                    guestPlayerId, "coop-assist:" + storyBossId, "HELPER");
            body.put("chronicle", choice);
            body.put("globalFlagHook", "联机助力");
        }
        if (socialToken != null) {
            Map<String, Object> token = socialToken.grantCoopToken(
                    guestPlayerId, host, "coop_assist:" + storyBossId);
            body.put("socialToken", token);
            body.put("hostWorldSpecialtyRedeemable", true);
        }
        return body;
    }

    public int assistCount(long playerId) {
        return assistCounts.getOrDefault(playerId, new AtomicInteger(0)).get();
    }
}
