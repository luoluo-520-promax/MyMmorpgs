package cn.itcast.demo.mymmorpg.world.economy;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 制造树：多级合成 + 异步完成（邮件送达）+ 暴击产出。
 */
@Service
public class CraftingTreeService {

    public static final String EVENT_CRIT_CRAFT = "CRIT_CRAFT";

    public record RecipeNode(
            String recipeId,
            String outputItemId,
            int outputCount,
            Map<String, Integer> inputs,
            long craftDurationMs,
            double critChance,
            boolean critDouble,
            boolean critQualityUp) {
        public RecipeNode {
            recipeId = recipeId == null ? "" : recipeId.trim();
            outputItemId = outputItemId == null ? "" : outputItemId.trim();
            outputCount = Math.max(1, outputCount);
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
            craftDurationMs = craftDurationMs <= 0 ? 3_000L : craftDurationMs;
            critChance = Math.max(0, Math.min(1.0, critChance));
        }
    }

    private final ConcurrentHashMap<String, RecipeNode> recipes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Map<String, Object>>> mailbox = new ConcurrentHashMap<>();

    public void register(RecipeNode node) {
        if (node != null && !node.recipeId().isBlank()) {
            recipes.put(node.recipeId(), node);
        }
    }

    public Map<String, Object> startCraft(long playerId, String recipeId, long nowMs) {
        RecipeNode node = recipes.get(recipeId == null ? "" : recipeId.trim());
        if (node == null) {
            return Map.of("ok", false, "error", "recipe_not_found");
        }
        String jobId = "craft-" + UUID.randomUUID();
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("jobId", jobId);
        job.put("playerId", playerId);
        job.put("recipeId", node.recipeId());
        job.put("readyAt", nowMs + node.craftDurationMs());
        job.put("done", false);
        jobs.put(jobId, job);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("jobId", jobId);
        body.put("durationMs", node.craftDurationMs());
        body.put("async", true);
        body.put("offlineOk", true);
        return body;
    }

    public Map<String, Object> completeIfReady(String jobId, long nowMs) {
        Map<String, Object> job = jobs.get(jobId);
        if (job == null) {
            return Map.of("ok", false, "error", "job_not_found");
        }
        if (Boolean.TRUE.equals(job.get("done"))) {
            return Map.of("ok", true, "alreadyDone", true, "jobId", jobId);
        }
        long readyAt = job.get("readyAt") instanceof Number n ? n.longValue() : 0L;
        if (nowMs < readyAt) {
            return Map.of("ok", false, "error", "not_ready", "readyAt", readyAt);
        }
        RecipeNode node = recipes.get(String.valueOf(job.get("recipeId")));
        boolean crit = ThreadLocalRandom.current().nextDouble() < (node == null ? 0 : node.critChance());
        int count = node == null ? 1 : node.outputCount();
        String item = node == null ? "unknown" : node.outputItemId();
        String quality = "NORMAL";
        if (crit && node != null) {
            if (node.critDouble()) {
                count *= 2;
            }
            if (node.critQualityUp()) {
                quality = "HIGH";
                item = item + "_hq";
            }
        }
        long playerId = job.get("playerId") instanceof Number n ? n.longValue() : 0L;
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("mailId", "mail-" + jobId);
        mail.put("title", "制造完成");
        mail.put("itemId", item);
        mail.put("count", count);
        mail.put("quality", quality);
        mail.put("crit", crit);
        mail.put("source", "CraftingTreeService");
        mailbox.computeIfAbsent(playerId, id -> new ArrayList<>()).add(mail);
        job.put("done", true);
        job.put("mail", mail);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("jobId", jobId);
        body.put("crit", crit);
        if (crit) {
            body.put("event", EVENT_CRIT_CRAFT);
        }
        body.put("mail", mail);
        body.put("via", "MailService");
        return body;
    }

    public List<Map<String, Object>> claimMail(long playerId) {
        List<Map<String, Object>> list = mailbox.remove(playerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public Map<String, Object> loadRecipeTreeJson(Map<String, Object> tree) {
        if (tree == null) {
            return Map.of("ok", false, "error", "empty");
        }
        Object nodes = tree.get("nodes");
        int n = 0;
        if (nodes instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Integer> inputs = m.get("inputs") instanceof Map<?, ?> in
                        ? (Map<String, Integer>) in : Map.of();
                register(new RecipeNode(
                        String.valueOf(m.get("recipeId")),
                        String.valueOf(m.get("outputItemId")),
                        m.get("outputCount") instanceof Number c ? c.intValue() : 1,
                        inputs,
                        m.get("craftDurationMs") instanceof Number d ? d.longValue() : 3000L,
                        m.get("critChance") instanceof Number p ? p.doubleValue() : 0.1,
                        Boolean.TRUE.equals(m.get("critDouble")),
                        Boolean.TRUE.equals(m.get("critQualityUp"))));
                n++;
            }
        }
        return Map.of("ok", true, "loaded", n, "config", "recipe_tree_config");
    }
}
