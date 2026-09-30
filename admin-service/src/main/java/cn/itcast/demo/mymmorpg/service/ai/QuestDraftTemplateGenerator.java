package cn.itcast.demo.mymmorpg.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** 任务文本/场景细节本地模板（LLM 不可用时回退）。 */
public final class QuestDraftTemplateGenerator {

    private final ObjectMapper objectMapper;

    public QuestDraftTemplateGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(String prompt) {
        String theme = prompt == null || prompt.isBlank() ? "星轨遗迹调查" : prompt.trim();
        int questId = 80000 + ThreadLocalRandom.current().nextInt(1000);
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("questId", questId);
        doc.put("name", truncate(theme, 24));
        doc.put("type", theme.toLowerCase(Locale.ROOT).contains("日常") ? "DAILY" : "MAIN");
        doc.put("description", "【草案】" + theme + "。前往目标场景完成调查并回报向导 NPC。");
        doc.put("sceneHint", "ruins-east");
        doc.put("npcHint", "guide");
        ArrayNode objectives = doc.putArray("objectives");
        ObjectNode o1 = objectives.addObject();
        o1.put("type", "TALK_NPC");
        o1.put("target", "guide");
        o1.put("count", 1);
        o1.put("text", "与向导对话了解背景");
        ObjectNode o2 = objectives.addObject();
        o2.put("type", "REACH_SCENE");
        o2.put("target", "ruins-east");
        o2.put("count", 1);
        o2.put("text", "抵达东侧遗迹");
        ArrayNode rewards = doc.putArray("rewards");
        ObjectNode r = rewards.addObject();
        r.put("itemId", 21000);
        r.put("count", 5);
        doc.put("sceneDetail", "残破石柱与微光碎片散落，风中隐约可闻星轨共鸣。");
        doc.put("_draft", true);
        return doc;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
