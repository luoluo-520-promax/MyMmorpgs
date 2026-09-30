package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置引用完整性校验：依赖图遍历，缺失 ID 列表阻断导入。
 */
@Service
public class ReferenceIntegrityValidator {

    public record ValidationIssue(String path, String refType, String refId, String message) {
    }

    public Map<String, Object> validate(
            List<Map<String, Object>> refs,
            Set<String> knownItemIds,
            Set<String> knownQuestIds,
            Set<String> knownShopProductIds) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> missingItemIds = new LinkedHashSet<>();
        Set<String> missingQuestIds = new LinkedHashSet<>();
        Set<String> missingShopProductIds = new LinkedHashSet<>();
        Set<String> missingMonsterIds = new LinkedHashSet<>();
        Set<String> missingSkillIds = new LinkedHashSet<>();

        if (refs != null) {
            for (int i = 0; i < refs.size(); i++) {
                Map<String, Object> ref = refs.get(i);
                if (ref == null) {
                    continue;
                }
                String type = String.valueOf(ref.getOrDefault("type", ""));
                String id = String.valueOf(ref.getOrDefault("id", ""));
                String path = String.valueOf(ref.getOrDefault("path", "refs[" + i + "]"));
                if (id.isBlank() || "null".equals(id)) {
                    continue;
                }
                if ("item".equalsIgnoreCase(type) || "equip".equalsIgnoreCase(type)
                        || "equipment".equalsIgnoreCase(type)) {
                    if (knownItemIds != null && !knownItemIds.contains(id)) {
                        issues.add(new ValidationIssue(path, type, id, "item_id_not_found"));
                        missingItemIds.add(id);
                    }
                } else if ("quest".equalsIgnoreCase(type)) {
                    if (knownQuestIds != null && !knownQuestIds.contains(id)) {
                        issues.add(new ValidationIssue(path, type, id, "quest_id_not_found"));
                        missingQuestIds.add(id);
                    }
                } else if ("shop_product".equalsIgnoreCase(type) || "product".equalsIgnoreCase(type)) {
                    if (knownShopProductIds != null && !knownShopProductIds.contains(id)) {
                        issues.add(new ValidationIssue(path, type, id, "shop_product_id_not_found"));
                        missingShopProductIds.add(id);
                    }
                } else if ("monster".equalsIgnoreCase(type) || "npc".equalsIgnoreCase(type)) {
                    // 目录未注入时仅记录，由调用方提供 known 集合后严格校验
                    Object known = ref.get("known");
                    if (Boolean.FALSE.equals(known)) {
                        issues.add(new ValidationIssue(path, type, id, "monster_id_not_found"));
                        missingMonsterIds.add(id);
                    }
                } else if ("skill".equalsIgnoreCase(type)) {
                    Object known = ref.get("known");
                    if (Boolean.FALSE.equals(known)) {
                        issues.add(new ValidationIssue(path, type, id, "skill_id_not_found"));
                        missingSkillIds.add(id);
                    }
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", issues.isEmpty());
        out.put("issueCount", issues.size());
        out.put("missingItemIds", List.copyOf(missingItemIds));
        out.put("missingQuestIds", List.copyOf(missingQuestIds));
        out.put("missingShopProductIds", List.copyOf(missingShopProductIds));
        out.put("missingMonsterIds", List.copyOf(missingMonsterIds));
        out.put("missingSkillIds", List.copyOf(missingSkillIds));
        List<Map<String, Object>> views = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            views.add(Map.of(
                    "path", issue.path(),
                    "refType", issue.refType(),
                    "refId", issue.refId(),
                    "message", issue.message()));
        }
        out.put("issues", views);
        return out;
    }

    /**
     * 从活动/任务 JSON 粗提取依赖边（reward itemId / questId / productId）。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractRefsFromJsonTree(Object node, String path) {
        List<Map<String, Object>> refs = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            maybeAdd(refs, m, "itemId", "item", path + ".itemId");
            maybeAdd(refs, m, "item_id", "item", path + ".item_id");
            maybeAdd(refs, m, "rewardItemId", "item", path + ".rewardItemId");
            maybeAdd(refs, m, "equipId", "equip", path + ".equipId");
            maybeAdd(refs, m, "questId", "quest", path + ".questId");
            maybeAdd(refs, m, "preQuestId", "quest", path + ".preQuestId");
            maybeAdd(refs, m, "productId", "shop_product", path + ".productId");
            maybeAdd(refs, m, "monsterId", "monster", path + ".monsterId");
            maybeAdd(refs, m, "skillId", "skill", path + ".skillId");
            for (Map.Entry<String, Object> e : m.entrySet()) {
                refs.addAll(extractRefsFromJsonTree(e.getValue(), path + "." + e.getKey()));
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                refs.addAll(extractRefsFromJsonTree(list.get(i), path + "[" + i + "]"));
            }
        }
        return refs;
    }

    private static void maybeAdd(List<Map<String, Object>> refs, Map<String, Object> m,
                                 String key, String type, String path) {
        if (!m.containsKey(key) || m.get(key) == null) {
            return;
        }
        refs.add(Map.of("type", type, "id", String.valueOf(m.get(key)), "path", path));
    }
}
