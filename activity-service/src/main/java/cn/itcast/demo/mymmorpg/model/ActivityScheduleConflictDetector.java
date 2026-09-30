package cn.itcast.demo.mymmorpg.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 活动时间轴冲突检测：同一时间区间内互斥类型不可同时开启。
 */
public final class ActivityScheduleConflictDetector {

    /** 互斥组：组内类型不可时间重叠。 */
    private static final List<Set<Integer>> MUTEX_GROUPS = List.of(
            Set.of(10, 11), // 双倍掉落 vs 限时挑战（示例类型码）
            Set.of(20, 21)  // 全服狂欢 vs 限时副本
    );

    private ActivityScheduleConflictDetector() {
    }

    public static List<String> detect(List<ActivityImportDocument> docs) {
        List<String> errors = new ArrayList<>();
        if (docs == null || docs.size() < 2) {
            return errors;
        }
        for (int i = 0; i < docs.size(); i++) {
            ActivityImportDocument a = docs.get(i);
            if (a == null || a.type == null) {
                continue;
            }
            for (int j = i + 1; j < docs.size(); j++) {
                ActivityImportDocument b = docs.get(j);
                if (b == null || b.type == null) {
                    continue;
                }
                if (!overlaps(a.startTime, a.endTime, b.startTime, b.endTime)) {
                    continue;
                }
                if (mutex(a.type, b.type)) {
                    errors.add("时间轴冲突: documents[" + i + "](type=" + a.type
                            + ") 与 documents[" + j + "](type=" + b.type
                            + ") 互斥且时间重叠 [" + a.startTime + "," + a.endTime
                            + "] ∩ [" + b.startTime + "," + b.endTime + "]");
                }
            }
        }
        return errors;
    }

    static boolean overlaps(long s1, long e1, long s2, long e2) {
        return s1 <= e2 && s2 <= e1;
    }

    static boolean mutex(int typeA, int typeB) {
        if (typeA == typeB) {
            return false;
        }
        for (Set<Integer> group : MUTEX_GROUPS) {
            if (group.contains(typeA) && group.contains(typeB)) {
                return true;
            }
        }
        return false;
    }

    /** 预发布环境验证钩子：冲突 + 基础规则。 */
    public static Map<String, Object> stagingValidate(List<ActivityImportDocument> docs) {
        ActivityImportValidator.ValidationOutcome base = ActivityImportValidator.validate(docs);
        List<String> conflicts = detect(docs);
        List<String> errors = new ArrayList<>(base.errors());
        errors.addAll(conflicts);
        Map<String, Object> out = new HashMap<>();
        out.put("ok", errors.isEmpty());
        out.put("errors", errors);
        out.put("warnings", base.warnings());
        out.put("conflictCount", conflicts.size());
        out.put("staging", true);
        return out;
    }
}
