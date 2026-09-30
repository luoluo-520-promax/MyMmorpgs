package cn.itcast.demo.mymmorpg.ai.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FAQ / RAG 简易检索：文档向量用词袋哈希近似，命中缓存则不调 LLM。
 */
public final class FaqRagService {

    public record Doc(String id, String title, String content, Map<String, Double> vector) {
        public Doc {
            vector = vector == null ? Map.of() : Map.copyOf(vector);
        }
    }

    public record Hit(String docId, String title, String snippet, double score) {
    }

    private final ConcurrentHashMap<String, Doc> docs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> faqCache = new ConcurrentHashMap<>();

    public void upsertDoc(String id, String title, String content) {
        if (id == null || id.isBlank()) {
            return;
        }
        String body = content == null ? "" : content;
        docs.put(id, new Doc(id, title == null ? id : title, body, bagOfWords(body)));
    }

    public void putFaqCache(String question, String answer) {
        if (question == null || answer == null) {
            return;
        }
        faqCache.put(normalizeKey(question), answer);
    }

    public String faqHit(String question) {
        if (question == null) {
            return null;
        }
        return faqCache.get(normalizeKey(question));
    }

    public List<Hit> retrieve(String query, int topK) {
        int k = Math.max(1, Math.min(10, topK));
        Map<String, Double> qv = bagOfWords(query == null ? "" : query);
        List<Hit> hits = new ArrayList<>();
        for (Doc d : docs.values()) {
            double score = cosine(qv, d.vector());
            if (score <= 0.05) {
                continue;
            }
            String snippet = d.content().length() <= 120 ? d.content() : d.content().substring(0, 120);
            hits.add(new Hit(d.id(), d.title(), snippet, round3(score)));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (hits.size() > k) {
            return List.copyOf(hits.subList(0, k));
        }
        return List.copyOf(hits);
    }

    /**
     * 智能客服：FAQ 缓存 → RAG 片段 → 规则拼接回答。
     */
    public Map<String, Object> answer(String question, boolean allowTicket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        String cached = faqHit(question);
        if (cached != null) {
            out.put("answer", cached);
            out.put("source", "faq_cache");
            out.put("needTicket", false);
            return out;
        }
        List<Hit> hits = retrieve(question, 3);
        if (!hits.isEmpty() && hits.get(0).score() >= 0.25) {
            Hit top = hits.get(0);
            out.put("answer", top.snippet());
            out.put("source", "rag:" + top.docId());
            out.put("hits", hits.stream().map(h -> Map.of(
                    "docId", h.docId(), "title", h.title(), "score", h.score())).toList());
            out.put("needTicket", false);
            return out;
        }
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean ticket = allowTicket && (lower.contains("误封") || lower.contains("充值")
                || lower.contains("申诉") || lower.contains("卡顿"));
        out.put("answer", ticket
                ? "已为你标记需人工处理，请补充账号与问题截图后提交工单。"
                : "暂未找到精确答案。你可以问升级、活动、深渊，或转人工客服。");
        out.put("source", ticket ? "ticket_handoff" : "fallback");
        out.put("needTicket", ticket);
        return out;
    }

    public Map<String, Object> stats() {
        return Map.of("docs", docs.size(), "faqCache", faqCache.size());
    }

    public static String normalizeKey(String q) {
        String n = q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(n.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(n.hashCode());
        }
    }

    private static Map<String, Double> bagOfWords(String text) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return m;
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}，。！？、；：]+");
        for (String p : parts) {
            if (p.length() < 2) {
                continue;
            }
            m.merge(p, 1.0, Double::sum);
        }
        // 中文 bigram
        String compact = text.replaceAll("\\s+", "");
        for (int i = 0; i + 1 < compact.length(); i++) {
            String bg = compact.substring(i, i + 2);
            m.merge(bg, 0.5, Double::sum);
        }
        return m;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Double bv = b.get(e.getKey());
            if (bv != null) {
                dot += e.getValue() * bv;
            }
        }
        for (double v : b.values()) {
            nb += v * v;
        }
        if (na <= 0 || nb <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
