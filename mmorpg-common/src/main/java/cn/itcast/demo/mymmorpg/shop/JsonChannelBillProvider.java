package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 JSON 数组对账单加载渠道流水（字段：channelOrderId,merchantOrderId,amountFen,status,paidAtMs）。
 * 非 Spring Bean：由运维/对账任务按路径或内容构造。
 */
public class JsonChannelBillProvider implements ChannelBillProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String channelName;
    private final List<StoredLine> lines;

    public JsonChannelBillProvider(Path jsonPath) throws IOException {
        this(jsonPath, "JSON");
    }

    public JsonChannelBillProvider(Path jsonPath, String channelName) throws IOException {
        this(Files.readString(jsonPath, StandardCharsets.UTF_8), channelName);
    }

    public JsonChannelBillProvider(String jsonContent) {
        this(jsonContent, "JSON");
    }

    public JsonChannelBillProvider(String jsonContent, String channelName) {
        this.channelName = channelName == null || channelName.isBlank() ? "JSON" : channelName.trim();
        this.lines = List.copyOf(parse(jsonContent));
    }

    @Override
    public List<ChannelBillLine> fetchBills(String channel, long fromMs, long toMs) {
        List<ChannelBillLine> out = new ArrayList<>();
        for (StoredLine s : lines) {
            if (s.paidAtMs < fromMs || s.paidAtMs > toMs) {
                continue;
            }
            out.add(s.line);
        }
        return out;
    }

    @Override
    public String channel() {
        return channelName;
    }

    private static List<StoredLine> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("channel bill json must be an array");
            }
            List<StoredLine> rows = new ArrayList<>();
            for (JsonNode node : root) {
                String channelOrderId = text(node, "channelOrderId");
                if (channelOrderId.isBlank()) {
                    continue;
                }
                String merchantOrderId = text(node, "merchantOrderId");
                long amountFen = node.path("amountFen").asLong(0L);
                String status = text(node, "status");
                long paidAtMs = node.path("paidAtMs").asLong(0L);
                rows.add(new StoredLine(
                        new ChannelBillLine(channelOrderId, merchantOrderId, amountFen, status),
                        paidAtMs));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("parse channel bill json failed", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("").trim();
    }

    private record StoredLine(ChannelBillLine line, long paidAtMs) {
    }
}
