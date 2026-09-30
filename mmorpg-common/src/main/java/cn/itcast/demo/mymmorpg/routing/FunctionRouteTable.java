package cn.itcast.demo.mymmorpg.routing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 按协议号段路由：移动走 Scene、背包走 Bag，减轻 player-service 中转瓶颈。
 * <p>
 * 号段约定与 {@code MessageId} 对齐：1xx Scene、2xx Battle、3xx Bag…
 */
public final class FunctionRouteTable {

    public enum TargetService {
        PLAYER, SCENE, BATTLE, BAG, HALL, QUEST, MATCH, SHOP, ACTIVITY, UPDATE, GACHA, CHAT, SKILL
    }

    public record Route(int fromInclusive, int toInclusive, TargetService target, String note) {
    }

    private final NavigableMap<Integer, Route> ranges = new TreeMap<>();

    public FunctionRouteTable() {
        // 默认号段
        register(1, 99, TargetService.PLAYER, "auth/character");
        register(100, 199, TargetService.SCENE, "scene/move/aoi");
        register(200, 299, TargetService.BATTLE, "battle");
        register(300, 399, TargetService.BAG, "bag/equip");
        register(400, 499, TargetService.SKILL, "skill");
        register(500, 599, TargetService.CHAT, "chat");
        register(600, 699, TargetService.HALL, "friend/mail/rank/coop");
        register(700, 799, TargetService.QUEST, "quest");
        register(800, 899, TargetService.ACTIVITY, "activity");
        register(900, 999, TargetService.MATCH, "matchmaking");
        register(1000, 1099, TargetService.SHOP, "shop/pass");
        register(1100, 1199, TargetService.UPDATE, "client update");
        register(1400, 1499, TargetService.GACHA, "gacha");
    }

    public void register(int fromInclusive, int toInclusive, TargetService target, String note) {
        ranges.put(fromInclusive, new Route(fromInclusive, toInclusive, target, note));
    }

    public TargetService resolve(int msgId) {
        Map.Entry<Integer, Route> e = ranges.floorEntry(msgId);
        if (e == null) {
            return TargetService.PLAYER;
        }
        Route r = e.getValue();
        if (msgId >= r.fromInclusive() && msgId <= r.toInclusive()) {
            return r.target();
        }
        return TargetService.PLAYER;
    }

    public boolean shouldBypassPlayerGateway(int msgId) {
        TargetService t = resolve(msgId);
        return t != TargetService.PLAYER;
    }

    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Route r : ranges.values()) {
            m.put(r.fromInclusive() + "-" + r.toInclusive(),
                    Map.of("target", r.target().name(), "note", r.note()));
        }
        return m;
    }
}
