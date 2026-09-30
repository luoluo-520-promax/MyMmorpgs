package cn.itcast.demo.mymmorpg.world.loot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 联机采集 / 开箱物权规则。
 * <ul>
 *   <li>WORLD_SHARED — 世界共享，谁先采谁得，全图刷新 CD</li>
 *   <li>PER_PLAYER — 独立掉落，每人各采各的</li>
 *   <li>PARTY_SHARED — 触发者开箱，房内全员可拾取（材料按需分配）</li>
 *   <li>RANDOM_SHARE — 队伍共享池随机分配给一名成员</li>
 *   <li>TRIGGER_ONLY — 仅触发者可拾取</li>
 *   <li>HOST_ONLY — 仅房主可拾取（神瞳/关键宝箱/传说节点，防偷世界）</li>
 * </ul>
 */
public final class LootOwnershipPolicy {

    public enum SyncMode {
        WORLD_SHARED,
        PER_PLAYER,
        PARTY_SHARED,
        RANDOM_SHARE,
        TRIGGER_ONLY,
        HOST_ONLY
    }

    public record LootDecision(
            boolean allowed,
            SyncMode mode,
            long ownerPlayerId,
            List<Long> pickupEligibleIds,
            String reason) {
    }

    private LootOwnershipPolicy() {
    }

    public static LootDecision decide(
            SyncMode mode,
            long actorPlayerId,
            long hostPlayerId,
            Set<Long> partyMemberIds) {
        return decide(mode, actorPlayerId, hostPlayerId, partyMemberIds, System.nanoTime());
    }

    /**
     * @param seed 随机分配种子（测试可固定）
     */
    public static LootDecision decide(
            SyncMode mode,
            long actorPlayerId,
            long hostPlayerId,
            Set<Long> partyMemberIds,
            long seed) {
        Objects.requireNonNull(mode, "mode");
        Set<Long> party = partyMemberIds == null ? Set.of() : partyMemberIds;
        return switch (mode) {
            case WORLD_SHARED -> new LootDecision(true, mode, actorPlayerId,
                    List.of(actorPlayerId), "world_shared_first_claim");
            case PER_PLAYER -> new LootDecision(true, mode, actorPlayerId,
                    List.of(actorPlayerId), "per_player_independent");
            case PARTY_SHARED -> {
                if (!party.isEmpty() && !party.contains(actorPlayerId) && actorPlayerId != hostPlayerId) {
                    yield new LootDecision(false, mode, 0L, List.of(), "not_party_member");
                }
                List<Long> eligible = party.isEmpty()
                        ? List.of(actorPlayerId)
                        : party.stream().sorted().toList();
                yield new LootDecision(true, mode, actorPlayerId, eligible, "party_shared_pickup");
            }
            case RANDOM_SHARE -> {
                if (!party.isEmpty() && !party.contains(actorPlayerId) && actorPlayerId != hostPlayerId) {
                    yield new LootDecision(false, mode, 0L, List.of(), "not_party_member");
                }
                List<Long> pool = party.isEmpty()
                        ? List.of(actorPlayerId)
                        : party.stream().sorted().toList();
                long winner = pool.get(Math.floorMod(seed, pool.size()));
                yield new LootDecision(true, mode, winner, List.of(winner), "random_share_assigned");
            }
            case TRIGGER_ONLY -> new LootDecision(true, mode, actorPlayerId,
                    List.of(actorPlayerId), "trigger_only");
            case HOST_ONLY -> {
                if (actorPlayerId != hostPlayerId) {
                    yield new LootDecision(false, mode, hostPlayerId, List.of(), "host_only_denied");
                }
                yield new LootDecision(true, mode, hostPlayerId, List.of(hostPlayerId), "host_only");
            }
        };
    }

    public static boolean canPickup(LootDecision decision, long playerId) {
        return decision != null && decision.allowed() && decision.pickupEligibleIds().contains(playerId);
    }

    public static Map<String, Object> toView(LootDecision d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allowed", d.allowed());
        m.put("mode", d.mode().name());
        m.put("ownerPlayerId", d.ownerPlayerId());
        m.put("pickupEligibleIds", d.pickupEligibleIds());
        m.put("reason", d.reason());
        return m;
    }
}
