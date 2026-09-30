package cn.itcast.demo.mymmorpg.shop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从 CSV 对账单加载渠道流水（列：channelOrderId,merchantOrderId,amountFen,status,paidAtMs）。
 * 非 Spring Bean：由运维/对账任务按路径或内容构造。
 */
public class CsvChannelBillProvider implements ChannelBillProvider {

    private final String channelName;
    private final List<StoredLine> lines;

    public CsvChannelBillProvider(Path csvPath) throws IOException {
        this(csvPath, "CSV");
    }

    public CsvChannelBillProvider(Path csvPath, String channelName) throws IOException {
        this(Files.readString(csvPath, StandardCharsets.UTF_8), channelName);
    }

    public CsvChannelBillProvider(String csvContent) {
        this(csvContent, "CSV");
    }

    public CsvChannelBillProvider(String csvContent, String channelName) {
        this.channelName = channelName == null || channelName.isBlank() ? "CSV" : channelName.trim();
        this.lines = List.copyOf(parse(csvContent));
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

    private static List<StoredLine> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = indexHeaders(headers);
            List<StoredLine> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = splitCsvLine(line);
                String channelOrderId = cell(values, idx, "channelOrderId");
                String merchantOrderId = cell(values, idx, "merchantOrderId");
                long amountFen = parseLong(cell(values, idx, "amountFen"));
                String status = cell(values, idx, "status");
                long paidAtMs = parseLong(cell(values, idx, "paidAtMs"));
                if (channelOrderId.isBlank()) {
                    continue;
                }
                rows.add(new StoredLine(
                        new ChannelBillLine(channelOrderId, merchantOrderId, amountFen, status),
                        paidAtMs));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("parse channel bill csv failed", e);
        }
    }

    private static Map<String, Integer> indexHeaders(String[] headers) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            idx.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return idx;
    }

    private static String cell(String[] values, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name.toLowerCase(Locale.ROOT));
        if (i == null || i < 0 || i >= values.length) {
            return "";
        }
        return values[i] == null ? "" : values[i].trim();
    }

    private static long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        return Long.parseLong(raw.trim());
    }

    private static String[] splitCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    private record StoredLine(ChannelBillLine line, long paidAtMs) {
    }
}
