package cn.itcast.demo.mymmorpg.world.content;

import java.util.Map;

/**
 * 灰度发布条件：匹配失败则回退全局基线配置。
 */
public record GrayConditions(
        String label,
        Long zoneId,
        Integer accountModBase,
        Integer accountModRemainder,
        Boolean betaTester,
        Integer trafficPercent) {

    public static GrayConditions fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new GrayConditions(
                str(map.get("label")),
                longOrNull(map.get("zoneId")),
                intOrNull(map.get("accountModBase")),
                intOrNull(map.get("accountModRemainder")),
                boolOrNull(map.get("isBetaTester")),
                intOrNull(map.get("trafficPercent")));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private static Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
