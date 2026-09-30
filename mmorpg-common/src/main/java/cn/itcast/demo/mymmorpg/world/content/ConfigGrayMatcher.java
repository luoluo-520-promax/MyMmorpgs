package cn.itcast.demo.mymmorpg.world.content;

/**
 * 灰度条件匹配器。
 */
public final class ConfigGrayMatcher {

    private ConfigGrayMatcher() {
    }

    public static boolean matches(GrayConditions cond, PlayerConfigContext ctx) {
        if (cond == null || ctx == null) {
            return false;
        }
        if (cond.zoneId() != null && cond.zoneId() != ctx.zoneId()) {
            return false;
        }
        if (Boolean.TRUE.equals(cond.betaTester()) && !ctx.betaTester()) {
            return false;
        }
        if (cond.accountModBase() != null && cond.accountModRemainder() != null) {
            int base = Math.max(1, cond.accountModBase());
            int rem = cond.accountModRemainder();
            if (ctx.accountId() % base != rem) {
                return false;
            }
        } else if (cond.accountModRemainder() != null) {
            if (ctx.accountId() % 10 != cond.accountModRemainder()) {
                return false;
            }
        }
        if (cond.trafficPercent() != null) {
            int pct = Math.max(0, Math.min(100, cond.trafficPercent()));
            if (pct < 100 && Math.floorMod(ctx.accountId(), 100) >= pct) {
                return false;
            }
        }
        return true;
    }
}
