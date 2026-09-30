package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 社交货币「助战印记」：帮助他人完成突破本/解谜获得，兑换限定外观（非数值）。
 */
@Service
public class SocialTokenService {

    public static final int TOKEN_PER_ASSIST = 3;
    public static final int COSMETIC_COST = 50;

    private final ConcurrentHashMap<Long, AtomicInteger> balances = new ConcurrentHashMap<>();

    public Map<String, Object> grantAssist(long helperId, long helpedId, String reason) {
        int added = TOKEN_PER_ASSIST;
        int balance = balances.computeIfAbsent(helperId, id -> new AtomicInteger(0)).addAndGet(added);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("helperId", helperId);
        body.put("helpedId", helpedId);
        body.put("reason", reason == null ? "assist" : reason);
        body.put("tokensAdded", added);
        body.put("balance", balance);
        return body;
    }

    public Map<String, Object> redeemCosmetic(long playerId, String cosmeticId) {
        AtomicInteger bal = balances.computeIfAbsent(playerId, id -> new AtomicInteger(0));
        if (bal.get() < COSMETIC_COST) {
            return Map.of("ok", false, "error", "insufficient_tokens", "balance", bal.get());
        }
        int remain = bal.addAndGet(-COSMETIC_COST);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("cosmeticId", cosmeticId);
        body.put("tokensSpent", COSMETIC_COST);
        body.put("balance", remain);
        body.put("grantPlan", Map.of(
                "itemId", cosmeticId,
                "count", 1,
                "idempotencyKey", "social_cosmetic:" + playerId + ":" + cosmeticId));
        return body;
    }

    public int balanceOf(long playerId) {
        AtomicInteger bal = balances.get(playerId);
        return bal == null ? 0 : bal.get();
    }

    /**
     * 联机助力专属代币：可即时兑换房主世界特产采集物。
     */
    public Map<String, Object> grantCoopToken(long helperId, long hostPlayerId, String reason) {
        Map<String, Object> body = new LinkedHashMap<>(grantAssist(helperId, hostPlayerId, reason));
        body.put("coopExclusive", true);
        body.put("hostPlayerId", hostPlayerId);
        body.put("redeemHint", "可兑换房主世界区域特产");
        return body;
    }

    public static final int HOST_SPECIALTY_COST = 3;

    public Map<String, Object> redeemHostSpecialty(
            long guestPlayerId, long hostPlayerId, String specialtyItemId) {
        AtomicInteger bal = balances.computeIfAbsent(guestPlayerId, id -> new AtomicInteger(0));
        if (bal.get() < HOST_SPECIALTY_COST) {
            return Map.of("ok", false, "error", "insufficient_tokens", "balance", bal.get());
        }
        int remain = bal.addAndGet(-HOST_SPECIALTY_COST);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("guestPlayerId", guestPlayerId);
        body.put("hostPlayerId", hostPlayerId);
        body.put("specialtyItemId", specialtyItemId);
        body.put("tokensSpent", HOST_SPECIALTY_COST);
        body.put("balance", remain);
        body.put("grantPlan", Map.of(
                "itemId", specialtyItemId,
                "count", 1,
                "source", "HOST_WORLD_SPECIALTY",
                "idempotencyKey", "host_specialty:" + guestPlayerId + ":" + hostPlayerId + ":" + specialtyItemId));
        body.put("hint", "帮人就是帮己：联机代币兑换房主世界特产");
        return body;
    }
}
