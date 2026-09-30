package cn.itcast.demo.mymmorpg.world.ai;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伙伴幽灵实体：不参与战斗碰撞，参与 AOI；路径节点下发 + 插值；独立体力池（玩家 50%）。
 */
@Service
public class CompanionGhostService {

    public static final int ENTITY_COMPANION_GHOST = 4;
    public static final float STAMINA_RATIO = 0.5f;
    public static final String ENCOURAGE_BUFF = "COMPANION_ENCOURAGE";

    public record PathNode(float x, float y, float z, long arriveAtMs) {
    }

    public record CompanionState(
            long companionEntityId,
            long ownerPlayerId,
            float x, float y, float z,
            float affinity,
            float stamina) {
    }

    private final ConcurrentHashMap<Long, CompanionState> companions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<PathNode>> pendingPaths = new ConcurrentHashMap<>();
    private StaminaConsumeService playerStamina;

    public void bindPlayerStamina(StaminaConsumeService stamina) {
        this.playerStamina = stamina;
    }

    public Map<String, Object> spawn(long ownerPlayerId, long companionEntityId, float x, float y, float z) {
        CompanionState s = new CompanionState(companionEntityId, ownerPlayerId, x, y, z, 0.5f, 100f);
        companions.put(companionEntityId, s);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("actorType", "COMPANION_GHOST");
        body.put("entityType", ENTITY_COMPANION_GHOST);
        body.put("companionEntityId", companionEntityId);
        body.put("ownerPlayerId", ownerPlayerId);
        body.put("blocksCombat", false);
        body.put("aoiBroadcast", true);
        body.put("x", x);
        body.put("y", y);
        body.put("z", z);
        return body;
    }

    /**
     * FollowPathSmoothing：服务端下发路径节点，客户端插值，避免海量 SceneMoveCmd。
     */
    public Map<String, Object> issueFollowPath(
            long companionEntityId, float ownerX, float ownerY, float ownerZ, long nowMs) {
        CompanionState s = companions.get(companionEntityId);
        if (s == null) {
            return Map.of("ok", false, "error", "companion_not_found");
        }
        List<PathNode> nodes = new ArrayList<>();
        float mx = (s.x() + ownerX) / 2f;
        float my = (s.y() + ownerY) / 2f;
        float mz = (s.z() + ownerZ) / 2f;
        nodes.add(new PathNode(mx, my, mz, nowMs + 200));
        nodes.add(new PathNode(ownerX - 1.2f, ownerY, ownerZ - 1.2f, nowMs + 450));
        pendingPaths.put(companionEntityId, nodes);
        companions.put(companionEntityId, new CompanionState(
                s.companionEntityId(), s.ownerPlayerId(),
                ownerX - 1.2f, ownerY, ownerZ - 1.2f, s.affinity(), s.stamina()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "CompanionPathNodes");
        body.put("msgId", MessageId.COMPANION_PATH_NODES_SC_NOTIFY);
        body.put("companionEntityId", companionEntityId);
        body.put("followPathSmoothing", true);
        body.put("nodes", nodes.stream().map(n -> Map.<String, Object>of(
                "x", n.x(), "y", n.y(), "z", n.z(), "arriveAtMs", n.arriveAtMs())).toList());
        body.put("clientInterpolate", true);
        body.put("skipSceneMoveCmdFlood", true);
        return body;
    }

    /**
     * 攀爬/游泳：伙伴消耗为玩家 50%；玩家耗尽时触发鼓励 Buff 短暂回体。
     */
    public Map<String, Object> consumeTraverse(
            long companionEntityId, MovementType type, float playerCost, long nowMs) {
        CompanionState s = companions.get(companionEntityId);
        if (s == null) {
            return Map.of("ok", false, "error", "companion_not_found");
        }
        float cost = Math.max(0f, playerCost) * STAMINA_RATIO;
        float after = Math.max(0f, s.stamina() - cost);
        float affinity = s.affinity();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("companionEntityId", companionEntityId);
        body.put("movementType", type == null ? MovementType.CLIMB.name() : type.name());
        body.put("consumed", cost);
        body.put("stamina", after);
        body.put("staminaRatio", STAMINA_RATIO);

        boolean encourage = false;
        if (playerStamina != null) {
            float playerRemain = playerStamina.current(s.ownerPlayerId());
            if (playerRemain <= 0.01f) {
                encourage = true;
                float recover = 15f + affinity * 10f;
                playerStamina.recover(s.ownerPlayerId(), recover);
                affinity = Math.min(1f, affinity + 0.02f);
                body.put("buff", ENCOURAGE_BUFF);
                body.put("encourageRecover", recover);
                body.put("affinityDelta", 0.02f);
                body.put("msgId", MessageId.COMPANION_DIALOGUE_SC_NOTIFY);
                body.put("bubble", "加油，我陪你！");
            }
        }
        companions.put(companionEntityId, new CompanionState(
                s.companionEntityId(), s.ownerPlayerId(), s.x(), s.y(), s.z(), affinity, after));
        body.put("encourage", encourage);
        body.put("affinity", affinity);
        return body;
    }

    public CompanionState stateOf(long companionEntityId) {
        return companions.get(companionEntityId);
    }
}
