package cn.itcast.demo.mymmorpg.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 出站内容风控：敏感词过滤；违规则丢弃并回退安全话术。
 */
public final class AiContentGuard {

    public record GuardResult(boolean allowed, String text, String reason) {
    }

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final AtomicInteger blockedCount = new AtomicInteger();

    public AiContentGuard() {
        blocked.addAll(List.of("外挂", "代充", "刷钻", "色情", "赌博", "政治敏感占位"));
    }

    public void addBlocked(String word) {
        if (word != null && !word.isBlank()) {
            blocked.add(word.trim().toLowerCase(Locale.ROOT));
        }
    }

    public GuardResult check(String text) {
        if (text == null || text.isBlank()) {
            return new GuardResult(false, safeFallback(), "empty");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String w : blocked) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) {
                hits.add(w);
            }
        }
        if (!hits.isEmpty()) {
            blockedCount.incrementAndGet();
            return new GuardResult(false, safeFallback(), "blocked:" + String.join(",", hits));
        }
        return new GuardResult(true, text, "ok");
    }

    public int blockedCount() {
        return blockedCount.get();
    }

    public static String safeFallback() {
        return "抱歉，该回复无法展示。请换个问法，或联系人工客服。";
    }
}
