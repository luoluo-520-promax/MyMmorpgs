package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共建野外营地：4 人小队在 SAFE 区域放置烹饪锅/合成台/传送点，每日签到维护耐久。
 */
@Service
public class CoopCampService {

    public static final int MAX_PARTY_SIZE = 4;
    public static final int BASE_DURABILITY = 100;
    public static final int DAILY_SIGN_DURABILITY = 15;

    public record CampFacility(String facilityId, String type, float x, float y, float z) {
    }

    public record Camp(
            String campId,
            String regionId,
            long leaderId,
            List<Long> members,
            List<CampFacility> facilities,
            int durability,
            long lastSignDay) {
    }

    private final ConcurrentHashMap<String, Camp> camps = new ConcurrentHashMap<>();

    public Map<String, Object> establish(
            long leaderId, List<Long> partyMemberIds, String regionId,
            boolean regionSafe, float x, float y, float z, long nowMs) {
        if (!regionSafe) {
            return Map.of("ok", false, "error", "region_not_safe");
        }
        List<Long> members = new ArrayList<>();
        members.add(leaderId);
        if (partyMemberIds != null) {
            for (Long id : partyMemberIds) {
                if (id != null && !members.contains(id) && members.size() < MAX_PARTY_SIZE) {
                    members.add(id);
                }
            }
        }
        String campId = "camp-" + regionId + "-" + leaderId;
        List<CampFacility> facilities = List.of(
                new CampFacility("cook-" + campId, "COOKING_POT", x, y, z),
                new CampFacility("craft-" + campId, "CRAFT_BENCH", x + 2f, y, z),
                new CampFacility("tp-" + campId, "WAYPOINT", x - 2f, y, z));
        Camp camp = new Camp(campId, regionId, leaderId, List.copyOf(members),
                facilities, BASE_DURABILITY, nowMs / 86_400_000L);
        camps.put(campId, camp);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("campId", campId);
        body.put("regionId", regionId);
        body.put("members", members);
        body.put("facilities", facilities.stream().map(f -> Map.of(
                "facilityId", f.facilityId(), "type", f.type(),
                "x", f.x(), "y", f.y(), "z", f.z())).toList());
        body.put("durability", BASE_DURABILITY);
        return body;
    }

    public Map<String, Object> dailySign(long playerId, String campId, long nowMs) {
        Camp camp = camps.get(campId);
        if (camp == null) {
            return Map.of("ok", false, "error", "camp_not_found");
        }
        if (!camp.members().contains(playerId)) {
            return Map.of("ok", false, "error", "not_member");
        }
        long day = nowMs / 86_400_000L;
        int durability = camp.durability();
        if (camp.lastSignDay() < day) {
            durability = Math.min(BASE_DURABILITY, durability + DAILY_SIGN_DURABILITY);
        }
        Camp updated = new Camp(camp.campId(), camp.regionId(), camp.leaderId(),
                camp.members(), camp.facilities(), durability, day);
        camps.put(campId, updated);
        return Map.of("ok", true, "campId", campId, "durability", durability, "signedBy", playerId);
    }
}
