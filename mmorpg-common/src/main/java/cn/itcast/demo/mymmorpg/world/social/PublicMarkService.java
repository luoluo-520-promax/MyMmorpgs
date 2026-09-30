package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大地图公共标记：求救 / 炫耀宝箱 / 提醒 BOSS；好友可见或待审核。
 */
@Service
public class PublicMarkService {

    public enum MarkKind {
        HELP, SHOWCASE_CHEST, BOSS_ALERT, WAYPOINT_HINT
    }

    public enum Visibility {
        FRIENDS, PUBLIC_PENDING, PUBLIC_APPROVED
    }

    public record PublicMark(
            long markId,
            long ownerPlayerId,
            String regionId,
            MarkKind kind,
            String message,
            float x, float y, float z,
            Visibility visibility,
            long createdAtMs) {
    }

    private final AtomicLong seq = new AtomicLong(1);
    private final ConcurrentHashMap<Long, PublicMark> marks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> likes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> thanks = new ConcurrentHashMap<>();
    /** playerId → friendIds */
    private final ConcurrentHashMap<Long, Set<Long>> friends = new ConcurrentHashMap<>();

    public void addFriend(long playerId, long friendId) {
        friends.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(friendId);
        friends.computeIfAbsent(friendId, id -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public Map<String, Object> place(
            long ownerPlayerId, String regionId, String kind, String message,
            float x, float y, float z, boolean requestPublic, long nowMs) {
        MarkKind mk;
        try {
            mk = MarkKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Map.of("ok", false, "error", "invalid_kind");
        }
        Visibility vis = requestPublic ? Visibility.PUBLIC_PENDING : Visibility.FRIENDS;
        long id = seq.getAndIncrement();
        PublicMark mark = new PublicMark(id, ownerPlayerId, regionId, mk,
                message == null ? "" : message, x, y, z, vis, nowMs);
        marks.put(id, mark);
        likes.put(id, ConcurrentHashMap.newKeySet());
        thanks.put(id, ConcurrentHashMap.newKeySet());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mark", toView(mark));
        return body;
    }

    public Map<String, Object> moderate(long markId, boolean approve) {
        PublicMark old = marks.get(markId);
        if (old == null) {
            return Map.of("ok", false, "error", "mark_not_found");
        }
        PublicMark updated = new PublicMark(old.markId(), old.ownerPlayerId(), old.regionId(),
                old.kind(), old.message(), old.x(), old.y(), old.z(),
                approve ? Visibility.PUBLIC_APPROVED : Visibility.FRIENDS, old.createdAtMs());
        marks.put(markId, updated);
        return Map.of("ok", true, "mark", toView(updated));
    }

    public Map<String, Object> like(long markId, long fromPlayerId) {
        PublicMark mark = marks.get(markId);
        if (mark == null) {
            return Map.of("ok", false, "error", "mark_not_found");
        }
        likes.computeIfAbsent(markId, id -> ConcurrentHashMap.newKeySet()).add(fromPlayerId);
        return Map.of("ok", true, "markId", markId, "likes", likes.get(markId).size());
    }

    /** 点赞/感谢：回赠体力（异步社交粘性）。 */
    public Map<String, Object> thank(long markId, long fromPlayerId, int staminaGift) {
        PublicMark mark = marks.get(markId);
        if (mark == null) {
            return Map.of("ok", false, "error", "mark_not_found");
        }
        thanks.computeIfAbsent(markId, id -> ConcurrentHashMap.newKeySet()).add(fromPlayerId);
        int gift = Math.max(1, Math.min(10, staminaGift));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("markId", markId);
        body.put("toPlayerId", mark.ownerPlayerId());
        body.put("staminaGift", gift);
        body.put("grantPlans", List.of(Map.of("itemId", "stamina", "count", gift,
                "targetPlayerId", mark.ownerPlayerId())));
        body.put("thanksCount", thanks.get(markId).size());
        return body;
    }

    public List<Map<String, Object>> listVisible(long viewerId, String regionId) {
        Set<Long> friendSet = friends.getOrDefault(viewerId, Set.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (PublicMark m : marks.values()) {
            if (regionId != null && !regionId.isBlank() && !regionId.equals(m.regionId())) {
                continue;
            }
            boolean visible = m.visibility() == Visibility.PUBLIC_APPROVED
                    || m.ownerPlayerId() == viewerId
                    || (m.visibility() == Visibility.FRIENDS && friendSet.contains(m.ownerPlayerId()));
            if (visible) {
                Map<String, Object> row = toView(m);
                row.put("likes", likes.getOrDefault(m.markId(), Set.of()).size());
                row.put("thanks", thanks.getOrDefault(m.markId(), Set.of()).size());
                out.add(row);
            }
        }
        return out;
    }

    private Map<String, Object> toView(PublicMark m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("markId", m.markId());
        row.put("ownerPlayerId", m.ownerPlayerId());
        row.put("regionId", m.regionId());
        row.put("kind", m.kind().name());
        row.put("message", m.message());
        row.put("x", m.x());
        row.put("y", m.y());
        row.put("z", m.z());
        row.put("visibility", m.visibility().name());
        row.put("createdAtMs", m.createdAtMs());
        return row;
    }
}
