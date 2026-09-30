package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 签到服务：七日循环 + 补签（钻石/补签卡）+ 动态奖励替换 + 月卡日领。
 */
@Service
public class SignInService {

    public static final int CYCLE_DAYS = 7;
    public static final String COST_DIAMOND = "DIAMOND";
    public static final String COST_CARD = "CARD";

    public record SignState(
            long playerId,
            int cycleDay,
            int totalSigned,
            String lastSignDate,
            boolean monthlyCard,
            /** 本周期已签到的 day 集合（1..7），用于补签。 */
            java.util.Set<Integer> signedDays) {
    }

    private final ConcurrentHashMap<Long, SignState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> claimed = new ConcurrentHashMap<>();
    /** day -> [itemId, count] 动态奖励覆盖。 */
    private final ConcurrentHashMap<Integer, int[]> dynamicRewards = new ConcurrentHashMap<>();

    public Map<String, Object> status(long playerId) {
        return toView(getOrCreate(playerId));
    }

    public Map<String, Object> signToday(long playerId) {
        SignState s = getOrCreate(playerId);
        String today = LocalDate.now().toString();
        if (today.equals(s.lastSignDate())) {
            return Map.of("ok", false, "error", "already_signed", "cycleDay", s.cycleDay());
        }
        int nextDay = s.cycleDay() >= CYCLE_DAYS ? 1 : s.cycleDay() + 1;
        if (s.lastSignDate() == null || s.lastSignDate().isBlank()) {
            nextDay = 1;
        }
        java.util.Set<Integer> days = new java.util.HashSet<>(s.signedDays());
        if (nextDay == 1 && !days.isEmpty() && s.cycleDay() >= CYCLE_DAYS) {
            days.clear();
        }
        days.add(nextDay);
        SignState next = new SignState(playerId, nextDay, s.totalSigned() + 1, today, s.monthlyCard(), days);
        states.put(playerId, next);
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        int[] reward = rewardForDay(nextDay);
        out.put("rewardItemId", reward[0]);
        out.put("rewardCount", reward[1]);
        return out;
    }

    /**
     * 补签：消耗钻石或补签卡，补本周期内未签的某一天。
     */
    public Map<String, Object> makeupSign(long playerId, int day, String costType) {
        if (day < 1 || day > CYCLE_DAYS) {
            return Map.of("ok", false, "error", "invalid_day");
        }
        String cost = costType == null ? COST_DIAMOND : costType.trim().toUpperCase();
        if (!COST_DIAMOND.equals(cost) && !COST_CARD.equals(cost)) {
            return Map.of("ok", false, "error", "invalid_cost_type");
        }
        SignState s = getOrCreate(playerId);
        if (s.signedDays().contains(day)) {
            return Map.of("ok", false, "error", "already_signed_day");
        }
        // 扣费由调用方/背包服对接；此处记录消耗意图并完成补签
        java.util.Set<Integer> days = new java.util.HashSet<>(s.signedDays());
        days.add(day);
        int cycleDay = Math.max(s.cycleDay(), day);
        SignState next = new SignState(playerId, cycleDay, s.totalSigned() + 1,
                s.lastSignDate(), s.monthlyCard(), days);
        states.put(playerId, next);
        Map<String, Object> out = new LinkedHashMap<>(toView(next));
        int[] reward = rewardForDay(day);
        out.put("makeupDay", day);
        out.put("costType", cost);
        out.put("costAmount", COST_DIAMOND.equals(cost) ? 50 : 1);
        out.put("rewardItemId", reward[0]);
        out.put("rewardCount", reward[1]);
        return out;
    }

    public Map<String, Object> setDynamicReward(int day, int itemId, int count) {
        if (day < 1 || day > CYCLE_DAYS || itemId <= 0 || count <= 0) {
            return Map.of("ok", false, "error", "invalid_reward");
        }
        dynamicRewards.put(day, new int[]{itemId, count});
        return Map.of("ok", true, "day", day, "itemId", itemId, "count", count);
    }

    public Map<String, Object> claimMonthlyCardDaily(long playerId) {
        SignState s = getOrCreate(playerId);
        if (!s.monthlyCard()) {
            return Map.of("ok", false, "error", "no_monthly_card");
        }
        String key = "mc:" + playerId + ":" + LocalDate.now();
        if (Boolean.TRUE.equals(claimed.putIfAbsent(key, Boolean.TRUE))) {
            return Map.of("ok", false, "error", "already_claimed");
        }
        Map<String, Object> out = new LinkedHashMap<>(toView(s));
        out.put("dailyGem", 90);
        return out;
    }

    public Map<String, Object> activateMonthlyCard(long playerId) {
        SignState s = getOrCreate(playerId);
        SignState next = new SignState(s.playerId(), s.cycleDay(), s.totalSigned(),
                s.lastSignDate(), true, s.signedDays());
        states.put(playerId, next);
        return toView(next);
    }

    int[] rewardForDay(int day) {
        int[] override = dynamicRewards.get(day);
        if (override != null) {
            return override;
        }
        return new int[]{4000 + day, day == CYCLE_DAYS ? 5 : 1};
    }

    private SignState getOrCreate(long playerId) {
        return states.computeIfAbsent(playerId,
                id -> new SignState(id, 0, 0, "", false, java.util.Set.of()));
    }

    private Map<String, Object> toView(SignState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("playerId", s.playerId());
        m.put("cycleDay", s.cycleDay());
        m.put("cycleDays", CYCLE_DAYS);
        m.put("totalSigned", s.totalSigned());
        m.put("lastSignDate", s.lastSignDate());
        m.put("monthlyCard", s.monthlyCard());
        m.put("signedToday", LocalDate.now().toString().equals(s.lastSignDate()));
        m.put("signedDays", s.signedDays());
        return m;
    }
}
