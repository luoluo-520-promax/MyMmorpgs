package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战令 / 月卡 / 首充双倍：管理每日每周任务经验、免费/付费档位解锁与领取。
 */
@Service
public class PassService {

    public enum Tier { FREE, PAID }

    public record PassProgress(
            long playerId,
            int seasonId,
            int level,
            int xp,
            boolean paidUnlocked,
            boolean firstChargeDoubleUsed,
            boolean monthlyCardActive,
            long monthlyCardExpireMs,
            boolean autoRenewEnabled,
            List<Integer> claimedFree,
            List<Integer> claimedPaid) {
    }

    private static final int XP_PER_LEVEL = 1000;
    private static final int MAX_LEVEL = 50;
    private static final long MONTHLY_CARD_MS = 30L * 86_400_000L;

    private final ConcurrentHashMap<Long, PassProgress> progress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> compensationClaimed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> platformSyncReceipts = new ConcurrentHashMap<>();
    /** 购买等级二次确认：token -> targetLevel */
    private final ConcurrentHashMap<String, Integer> purchaseLevelTokens = new ConcurrentHashMap<>();
    private final ObjectProvider<TLogEventPublisher> tLogEventPublisher;
    private final ObjectProvider<MonthlyCardRedisStore> monthlyCardRedisStore;

    public PassService(ObjectProvider<TLogEventPublisher> tLogEventPublisher) {
        this(tLogEventPublisher, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PassService(ObjectProvider<TLogEventPublisher> tLogEventPublisher,
                       ObjectProvider<MonthlyCardRedisStore> monthlyCardRedisStore) {
        this.tLogEventPublisher = tLogEventPublisher;
        this.monthlyCardRedisStore = monthlyCardRedisStore;
    }

    public PassProgress getOrCreate(long playerId, int seasonId) {
        return progress.computeIfAbsent(playerId, id -> new PassProgress(
                id, seasonId, 1, 0, false, false, false, 0L, false, List.of(), List.of()));
    }

    public Map<String, Object> status(long playerId, int seasonId) {
        return toView(getOrCreate(playerId, seasonId));
    }

    public Map<String, Object> addDailyXp(long playerId, int seasonId, int xp) {
        PassProgress p = getOrCreate(playerId, seasonId);
        int add = Math.max(0, xp);
        int newXp = p.xp() + add;
        int level = p.level();
        while (newXp >= XP_PER_LEVEL && level < MAX_LEVEL) {
            newXp -= XP_PER_LEVEL;
            level++;
        }
        PassProgress next = copy(p, seasonId, level, newXp, p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        return toView(next);
    }

    public Map<String, Object> unlockPaid(long playerId, int seasonId) {
        PassProgress p = getOrCreate(playerId, seasonId);
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), true,
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("pass_unlock_paid", playerId, Map.of("seasonId", seasonId));
        return toView(next);
    }

    public Map<String, Object> activateMonthlyCard(long playerId, int seasonId, int days) {
        MonthlyCardRedisStore store = monthlyCardRedisStore == null ? null : monthlyCardRedisStore.getIfAvailable();
        if (store != null && !store.activateOrRenew(playerId, days)) {
            return Map.of("ok", false, "error", "monthly_card_cap_exceeded",
                    "maxRemaining", MonthlyCardRedisStore.MAX_REMAINING_FOR_RENEW,
                    "remaining", store.remainingDays(playerId));
        }
        PassProgress p = getOrCreate(playerId, seasonId);
        int effectiveDays = Math.max(1, days);
        if (store != null) {
            effectiveDays = store.remainingDays(playerId);
        }
        long expire = System.currentTimeMillis() + effectiveDays * 86_400_000L;
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), true, expire, p.autoRenewEnabled(),
                p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("monthly_card_activate", playerId, Map.of("days", days, "expireMs", expire));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        if (store != null) {
            out.put("remainingDays", store.remainingDays(playerId));
        }
        return out;
    }

    /**
     * 购买战令等级：第一步生成确认 token（需弹窗二次鉴权）。
     */
    public Map<String, Object> requestPurchaseLevel(long playerId, int seasonId, int targetLevel) {
        PassProgress p = getOrCreate(playerId, seasonId);
        if (targetLevel <= p.level() || targetLevel > MAX_LEVEL) {
            return Map.of("ok", false, "error", "invalid_target_level", "currentLevel", p.level());
        }
        String token = "PL-" + playerId + "-" + seasonId + "-" + targetLevel + "-"
                + Long.toHexString(System.currentTimeMillis());
        purchaseLevelTokens.put(token, targetLevel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("confirmToken", token);
        out.put("currentLevel", p.level());
        out.put("targetLevel", targetLevel);
        out.put("levelsToBuy", targetLevel - p.level());
        out.put("requireConfirm", true);
        out.put("message", "请弹窗二次确认后调用 confirmPurchaseLevel");
        return out;
    }

    /**
     * 购买战令等级：第二步校验 token 后升到目标等级。
     */
    public Map<String, Object> confirmPurchaseLevel(long playerId, int seasonId, String confirmToken) {
        if (confirmToken == null || confirmToken.isBlank()) {
            return Map.of("ok", false, "error", "confirm_token_required");
        }
        Integer target = purchaseLevelTokens.remove(confirmToken.trim());
        if (target == null) {
            return Map.of("ok", false, "error", "confirm_token_invalid_or_used");
        }
        if (!confirmToken.contains("-" + playerId + "-") || !confirmToken.contains("-" + seasonId + "-")) {
            return Map.of("ok", false, "error", "confirm_token_mismatch");
        }
        PassProgress p = getOrCreate(playerId, seasonId);
        int level = Math.min(MAX_LEVEL, Math.max(p.level(), target));
        PassProgress next = copy(p, seasonId, level, 0, p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("pass_purchase_level", playerId, Map.of("seasonId", seasonId, "level", level));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("purchased", true);
        out.put("targetLevel", target);
        return out;
    }

    /** 任务完成事件 → 战令 XP。 */
    public Map<String, Object> onQuestCompleted(long playerId, int seasonId, int questId, int xp) {
        return addDailyXp(playerId, seasonId, Math.max(0, xp));
    }

    /** Redis 月卡天数耗尽回调。 */
    public void onMonthlyCardDaysExhausted(long playerId) {
        PassProgress p = progress.get(playerId);
        if (p == null) {
            return;
        }
        PassProgress next = copy(p, p.seasonId(), p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), false, 0L, p.autoRenewEnabled(),
                p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("monthly_card_exhausted", playerId, Map.of("seasonId", p.seasonId()));
    }

    /** 首充双倍：同一账号仅一次。 */
    public Map<String, Object> applyFirstChargeDouble(long playerId, int seasonId, int baseAmount) {
        PassProgress p = getOrCreate(playerId, seasonId);
        if (p.firstChargeDoubleUsed()) {
            return Map.of("ok", false, "error", "already_used", "amount", baseAmount);
        }
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                true, p.monthlyCardActive(), p.monthlyCardExpireMs(), p.autoRenewEnabled(),
                p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        int doubled = Math.max(0, baseAmount) * 2;
        emit("first_charge_double", playerId, Map.of("base", baseAmount, "doubled", doubled));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("amount", doubled);
        return out;
    }

    public Map<String, Object> claim(long playerId, int seasonId, int level, Tier tier) {
        PassProgress p = getOrCreate(playerId, seasonId);
        if (level <= 0 || level > p.level()) {
            return Map.of("ok", false, "error", "level_locked");
        }
        if (tier == Tier.PAID && !p.paidUnlocked()) {
            return Map.of("ok", false, "error", "paid_locked");
        }
        List<Integer> claimed = new ArrayList<>(tier == Tier.PAID ? p.claimedPaid() : p.claimedFree());
        if (claimed.contains(level)) {
            return Map.of("ok", false, "error", "already_claimed");
        }
        claimed.add(level);
        PassProgress next = tier == Tier.PAID
                ? copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), p.claimedFree(), List.copyOf(claimed))
                : copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), List.copyOf(claimed), p.claimedPaid());
        progress.put(playerId, next);
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("claimedLevel", level);
        out.put("tier", tier.name());
        out.put("rewardItemId", tier == Tier.PAID ? 3000 + level : 2000 + level);
        out.put("rewardCount", tier == Tier.PAID ? 2 : 1);
        return out;
    }

    public Map<String, Object> claimMonthlyDaily(long playerId, int seasonId) {
        PassProgress p = getOrCreate(playerId, seasonId);
        if (!p.monthlyCardActive() || p.monthlyCardExpireMs() < System.currentTimeMillis()) {
            return Map.of("ok", false, "error", "monthly_inactive");
        }
        String dayKey = "mc:" + playerId + ":" + LocalDate.now();
        if (Boolean.TRUE.equals(dailyClaimed.putIfAbsent(dayKey, Boolean.TRUE))) {
            return Map.of("ok", false, "error", "already_claimed_today");
        }
        Map<String, Object> out = new LinkedHashMap<>(toView(p));
        out.put("dailyGem", 90);
        out.put("day", LocalDate.now().toString());
        return out;
    }

    public Map<String, Object> setAutoRenew(long playerId, int seasonId, boolean enabled) {
        PassProgress p = getOrCreate(playerId, seasonId);
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                enabled, p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("monthly_card_auto_renew", playerId, Map.of("seasonId", seasonId, "enabled", enabled));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("autoRenewEnabled", enabled);
        return out;
    }

    /**
     * 到期自动续费：需开启 autoRenew，且月卡已到期（或从未激活时拒绝）。
     */
    public Map<String, Object> renewMonthlyCardIfDue(long playerId, int seasonId) {
        PassProgress p = getOrCreate(playerId, seasonId);
        if (!p.autoRenewEnabled()) {
            return Map.of("ok", false, "error", "auto_renew_disabled");
        }
        MonthlyCardRedisStore store = monthlyCardRedisStore == null ? null : monthlyCardRedisStore.getIfAvailable();
        if (store != null) {
            int remaining = store.remainingDays(playerId);
            if (remaining > MonthlyCardRedisStore.MAX_REMAINING_FOR_RENEW) {
                return Map.of("ok", false, "error", "monthly_card_cap_exceeded",
                        "remaining", remaining, "maxRemaining", MonthlyCardRedisStore.MAX_REMAINING_FOR_RENEW);
            }
            if (remaining > 0) {
                Map<String, Object> notDue = new LinkedHashMap<>(toView(p));
                notDue.put("ok", false);
                notDue.put("error", "not_due");
                notDue.put("remainingDays", remaining);
                return notDue;
            }
            if (!store.activateOrRenew(playerId, 30)) {
                return Map.of("ok", false, "error", "monthly_card_cap_exceeded");
            }
            long expire = System.currentTimeMillis() + store.remainingDays(playerId) * 86_400_000L;
            PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                    p.firstChargeDoubleUsed(), true, expire, true, p.claimedFree(), p.claimedPaid());
            progress.put(playerId, next);
            Map<String, Object> out = new LinkedHashMap<>(toView(next));
            out.put("renewed", true);
            out.put("remainingDays", store.remainingDays(playerId));
            return out;
        }
        long now = System.currentTimeMillis();
        if (p.monthlyCardExpireMs() <= 0) {
            return Map.of("ok", false, "error", "no_monthly_card");
        }
        if (p.monthlyCardExpireMs() > now) {
            Map<String, Object> notDue = new LinkedHashMap<>(toView(p));
            notDue.put("ok", false);
            notDue.put("error", "not_due");
            return notDue;
        }
        long base = Math.max(p.monthlyCardExpireMs(), now);
        long expire = base + MONTHLY_CARD_MS;
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), true, expire, true, p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("monthly_card_renew", playerId, Map.of("seasonId", seasonId, "expireMs", expire));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("renewed", true);
        out.put("expireMs", expire);
        return out;
    }

    /**
     * 月卡已过期时，一键补领尚未领取的战令档位奖励（免费 + 已解锁付费档）。
     */
    public Map<String, Object> claimCompensation(long playerId, int seasonId) {
        PassProgress p = getOrCreate(playerId, seasonId);
        long now = System.currentTimeMillis();
        if (p.monthlyCardExpireMs() <= 0 || p.monthlyCardExpireMs() >= now) {
            return Map.of("ok", false, "error", "not_eligible");
        }
        String key = playerId + ":" + seasonId;
        if (Boolean.TRUE.equals(compensationClaimed.putIfAbsent(key, Boolean.TRUE))) {
            return Map.of("ok", false, "error", "already_claimed");
        }
        List<Integer> free = new ArrayList<>(p.claimedFree());
        List<Integer> paid = new ArrayList<>(p.claimedPaid());
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (int level = 1; level <= p.level(); level++) {
            if (!free.contains(level)) {
                free.add(level);
                rewards.add(Map.of("tier", "FREE", "level", level,
                        "rewardItemId", 2000 + level, "rewardCount", 1));
            }
            if (p.paidUnlocked() && !paid.contains(level)) {
                paid.add(level);
                rewards.add(Map.of("tier", "PAID", "level", level,
                        "rewardItemId", 3000 + level, "rewardCount", 2));
            }
        }
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), p.monthlyCardActive(), p.monthlyCardExpireMs(),
                p.autoRenewEnabled(), List.copyOf(free), List.copyOf(paid));
        progress.put(playerId, next);
        emit("pass_compensation_claim", playerId, Map.of("seasonId", seasonId, "rewardCount", rewards.size()));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("compensation", true);
        out.put("rewards", rewards);
        out.put("rewardCount", rewards.size());
        return out;
    }

    /**
     * 跨平台订阅同步 stub：校验平台与票据结构后标记同步，并可激活/续期月卡。
     */
    public Map<String, Object> syncFromPlatform(long playerId, int seasonId, String platform, String platformReceipt) {
        if (platform == null || platform.isBlank()) {
            return Map.of("ok", false, "error", "platform_required");
        }
        if (platformReceipt == null || platformReceipt.isBlank()) {
            return Map.of("ok", false, "error", "receipt_required");
        }
        String normalized = platform.trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPLE", "APP_STORE", "GOOGLE", "GOOGLE_PLAY", "ALIPAY", "WECHAT").contains(normalized)) {
            return Map.of("ok", false, "error", "unsupported_platform");
        }
        PassProgress p = getOrCreate(playerId, seasonId);
        String syncKey = playerId + ":" + seasonId + ":" + normalized;
        platformSyncReceipts.put(syncKey, platformReceipt.trim());
        long expire = Math.max(p.monthlyCardExpireMs(), System.currentTimeMillis()) + MONTHLY_CARD_MS;
        boolean autoRenew = p.autoRenewEnabled()
                || platformReceipt.toLowerCase(Locale.ROOT).contains("autorenew");
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), true, expire, autoRenew, p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
        emit("pass_platform_sync", playerId, Map.of(
                "seasonId", seasonId, "platform", normalized, "stub", true));
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        out.put("synced", true);
        out.put("platform", normalized);
        out.put("stub", true);
        out.put("message", "cross-platform sync stub; replace with store subscription API");
        return out;
    }

    private final ConcurrentHashMap<String, Boolean> dailyClaimed = new ConcurrentHashMap<>();

    /** package-visible：单测构造过期月卡。 */
    void setMonthlyCardExpireMs(long playerId, int seasonId, long expireMs) {
        PassProgress p = getOrCreate(playerId, seasonId);
        PassProgress next = copy(p, seasonId, p.level(), p.xp(), p.paidUnlocked(),
                p.firstChargeDoubleUsed(), true, expireMs, p.autoRenewEnabled(),
                p.claimedFree(), p.claimedPaid());
        progress.put(playerId, next);
    }

    private static PassProgress copy(PassProgress p, int seasonId, int level, int xp,
                                     boolean paidUnlocked, boolean firstChargeDoubleUsed,
                                     boolean monthlyCardActive, long monthlyCardExpireMs,
                                     boolean autoRenewEnabled,
                                     List<Integer> claimedFree, List<Integer> claimedPaid) {
        return new PassProgress(p.playerId(), seasonId, level, xp, paidUnlocked, firstChargeDoubleUsed,
                monthlyCardActive, monthlyCardExpireMs, autoRenewEnabled, claimedFree, claimedPaid);
    }

    private Map<String, Object> toView(PassProgress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("playerId", p.playerId());
        m.put("seasonId", p.seasonId());
        m.put("level", p.level());
        m.put("xp", p.xp());
        m.put("xpPerLevel", XP_PER_LEVEL);
        m.put("paidUnlocked", p.paidUnlocked());
        m.put("firstChargeDoubleUsed", p.firstChargeDoubleUsed());
        m.put("monthlyCardActive", p.monthlyCardActive() && p.monthlyCardExpireMs() >= System.currentTimeMillis());
        m.put("monthlyCardExpireMs", p.monthlyCardExpireMs());
        m.put("autoRenewEnabled", p.autoRenewEnabled());
        m.put("claimedFree", p.claimedFree());
        m.put("claimedPaid", p.claimedPaid());
        return m;
    }

    private void emit(String type, long playerId, Map<String, Object> fields) {
        TLogEventPublisher pub = tLogEventPublisher == null ? null : tLogEventPublisher.getIfAvailable();
        if (pub != null) {
            pub.emit(type, playerId, fields);
        }
    }
}
