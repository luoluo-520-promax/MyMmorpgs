package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 弹道改写：风系扩散（SWIRL）偏转敌方远程弹道，并广播元素残留。
 */
@Service
public class ProjectileCurveService {

    public static final String EVENT_GAUGE_REMNANT = "GAUGE_REMNANT";

    private final ConcurrentHashMap<String, Map<String, Object>> routeModifiers = new ConcurrentHashMap<>();

    /**
     * SWIRL：范围内敌方弹道偏转 60°~120°，附着元素转换。
     */
    public Map<String, Object> applySwirl(
            String projectileId, String attachedElement, float cx, float cz, float radiusM) {
        int deflect = 60 + ThreadLocalRandom.current().nextInt(61);
        List<Map<String, Object>> route = new ArrayList<>();
        double rad = Math.toRadians(deflect);
        for (int i = 0; i < 5; i++) {
            float t = i * 0.2f;
            route.add(Map.of(
                    "t", t,
                    "x", cx + (float) (Math.cos(rad) * radiusM * t),
                    "z", cz + (float) (Math.sin(rad) * radiusM * t)));
        }
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("projectileId", projectileId);
        mod.put("reaction", "SWIRL");
        mod.put("deflectDeg", deflect);
        mod.put("attachedElement", attachedElement == null ? "ANEMO" : attachedElement);
        mod.put("route", route);
        routeModifiers.put(projectileId == null ? "" : projectileId.trim(), mod);
        Map<String, Object> body = new LinkedHashMap<>(mod);
        body.put("ok", true);
        body.put("projectile_route_modifier", true);
        return body;
    }

    public Map<String, Object> gaugeRemnant(
            long targetId, String element, double remainingGauge, float x, float y, float z) {
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("event", EVENT_GAUGE_REMNANT);
        notify.put("targetId", targetId);
        notify.put("element", element);
        notify.put("remainingGauge", remainingGauge);
        notify.put("x", x);
        notify.put("y", y);
        notify.put("z", z);
        notify.put("ui", "overhead_element_decay_bar");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("aoiBroadcast", notify);
        return body;
    }

    public Map<String, Object> routeOf(String projectileId) {
        Map<String, Object> mod = routeModifiers.get(projectileId == null ? "" : projectileId.trim());
        if (mod == null) {
            return Map.of("ok", false, "error", "no_modifier");
        }
        Map<String, Object> body = new LinkedHashMap<>(mod);
        body.put("ok", true);
        return body;
    }
}
