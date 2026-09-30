package cn.itcast.demo.mymmorpg.world.civilization;

import cn.itcast.demo.mymmorpg.world.time.GameTimeKeeper;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P19 文明时态引擎：NPC 按 ScheduleTemplate 作息，动态响应天气/昼夜。
 */
@Service
public class CivilizationScheduleService {

    public enum NpcAction {
        IDLE, PATROL, WORK, SLEEP, SEEK_SHELTER, TRADE
    }

    public record ScheduleSlot(
            int startMinuteOfDay, int endMinuteOfDay,
            float offsetX, float offsetY, float offsetZ,
            NpcAction action, String dialogueOverride) {
        public ScheduleSlot {
            action = action == null ? NpcAction.IDLE : action;
            dialogueOverride = dialogueOverride == null ? "" : dialogueOverride;
        }
    }

    public record ScheduleTemplate(String npcId, String regionId, List<ScheduleSlot> slots) {
        public ScheduleTemplate {
            npcId = npcId == null ? "" : npcId.trim();
            regionId = regionId == null ? "" : regionId.trim();
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    public record NpcBaseState(
            String npcId, String npcType, float baseX, float baseY, float baseZ,
            boolean isShopkeeper) {
        public NpcBaseState {
            npcId = npcId == null ? "" : npcId.trim();
            npcType = npcType == null ? "generic" : npcType.trim();
        }
    }

    private final ConcurrentHashMap<String, ScheduleTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NpcBaseState> npcs = new ConcurrentHashMap<>();
    private GameTimeKeeper gameTime;

    public void bindGameTimeKeeper(GameTimeKeeper keeper) {
        this.gameTime = keeper;
    }

    public void registerNpc(NpcBaseState npc, ScheduleTemplate template) {
        if (npc == null || npc.npcId().isBlank()) {
            return;
        }
        npcs.put(npc.npcId(), npc);
        if (template != null) {
            templates.put(npc.npcId(), template);
        }
    }

    public Map<String, Object> resolveNpcState(String npcId, long nowMs) {
        NpcBaseState npc = npcs.get(npcId == null ? "" : npcId.trim());
        if (npc == null) {
            return Map.of("ok", false, "error", "npc_not_found");
        }
        int hour = 12;
        int minute = 0;
        WorldTimeService.Weather weather = WorldTimeService.Weather.CLEAR;
        if (gameTime != null) {
            WorldTimeService.WorldClock c = gameTime.worldTime().current(nowMs);
            hour = c.hourOfDay();
            minute = c.minuteOfHour();
            weather = c.weather();
        }
        int minuteOfDay = hour * 60 + minute;
        ScheduleTemplate tpl = templates.get(npc.npcId());
        NpcAction action = NpcAction.IDLE;
        float ox = 0f, oy = 0f, oz = 0f;
        String dialogue = "";
        if (tpl != null) {
            for (ScheduleSlot slot : tpl.slots()) {
                if (minuteOfDay >= slot.startMinuteOfDay() && minuteOfDay < slot.endMinuteOfDay()) {
                    action = slot.action();
                    ox = slot.offsetX();
                    oy = slot.offsetY();
                    oz = slot.offsetZ();
                    dialogue = slot.dialogueOverride();
                    break;
                }
            }
        }
        if (weather == WorldTimeService.Weather.RAIN
                || weather == WorldTimeService.Weather.THUNDER) {
            action = NpcAction.SEEK_SHELTER;
            dialogue = dialogue.isBlank() ? "快躲雨！" : dialogue;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("npcId", npc.npcId());
        body.put("npcType", npc.npcType());
        body.put("x", npc.baseX() + ox);
        body.put("y", npc.baseY() + oy);
        body.put("z", npc.baseZ() + oz);
        body.put("action", action.name());
        body.put("dialogueOverride", dialogue);
        body.put("hourOfDay", hour);
        body.put("weather", weather.name());
        body.put("weatherOverride", action == NpcAction.SEEK_SHELTER);
        return body;
    }

    /**
     * 深夜拜访商店：返回 STORE_CLOSED，可触发潜行盗窃任务线。
     */
    public Map<String, Object> checkShopAccess(String npcId, long playerId, long nowMs) {
        NpcBaseState npc = npcs.get(npcId == null ? "" : npcId.trim());
        if (npc == null || !npc.isShopkeeper()) {
            return Map.of("ok", false, "error", "not_a_shopkeeper");
        }
        int hour = 12;
        if (gameTime != null) {
            hour = gameTime.worldTime().current(nowMs).hourOfDay();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("npcId", npcId);
        body.put("playerId", playerId);
        body.put("hourOfDay", hour);
        if (gameTime != null && !gameTime.isStoreHours(hour)) {
            body.put("storeOpen", false);
            body.put("error", "STORE_CLOSED");
            body.put("event", "STORE_CLOSED");
            body.put("stealthTheftQuestEligible", true);
            body.put("hint", "深夜可尝试潜行盗窃（ClientPredictedActionService 判定）");
        } else {
            body.put("storeOpen", true);
            body.put("event", "STORE_OPEN");
        }
        return body;
    }

    public List<Map<String, Object>> listRegionNpcStates(String regionId, long nowMs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NpcBaseState npc : npcs.values()) {
            ScheduleTemplate tpl = templates.get(npc.npcId());
            if (tpl != null && tpl.regionId().equals(regionId == null ? "" : regionId.trim())) {
                out.add(resolveNpcState(npc.npcId(), nowMs));
            }
        }
        return out;
    }
}
