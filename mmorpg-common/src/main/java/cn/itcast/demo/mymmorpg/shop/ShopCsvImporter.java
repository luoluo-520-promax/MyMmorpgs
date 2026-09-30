package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商城商品 CSV 导入（列对齐文档 5.1）。
 */
public final class ShopCsvImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShopCsvImporter() {
    }

    public static List<ShopProductConfig> parse(String csv) throws IOException {
        List<Map<String, String>> rows = parseCsvRows(csv);
        List<ShopProductConfig> products = new ArrayList<>();
        for (Map<String, String> row : rows) {
            products.add(toProduct(row));
        }
        return products;
    }

    private static ShopProductConfig toProduct(Map<String, String> row) throws IOException {
        ShopProductConfig p = new ShopProductConfig();
        p.productId = parseInt(row.get("productId"));
        p.productType = nullToEmpty(row.get("productType"));
        p.name = nullToEmpty(row.get("name"));
        p.desc = nullToEmpty(row.get("desc"));
        p.icon = nullToEmpty(row.get("icon"));
        p.channelSku = nullToEmpty(row.get("channelSku"));
        p.currency = blankTo(row.get("currency"), "CNY");
        p.price = parseLong(row.get("price"));
        p.originalPrice = parseLong(blankTo(row.get("originalPrice"), row.get("price")));
        if (row.get("discountRate") != null && !row.get("discountRate").isBlank()) {
            p.discountRate = parseInt(row.get("discountRate"));
        }
        p.discountBegin = emptyToNull(row.get("discountBegin"));
        p.discountEnd = emptyToNull(row.get("discountEnd"));
        p.saleBegin = emptyToNull(row.get("saleBegin"));
        p.saleEnd = emptyToNull(row.get("saleEnd"));
        p.rewards = parseRewards(row.get("rewardsJson"));
        p.limitType = blankTo(row.get("limitType"), "NONE");
        p.limitCount = parseInt(row.get("limitCount"));
        p.tags = parseTags(row.get("tags"));
        p.tabId = nullToEmpty(row.get("tabId"));
        p.sort = parseInt(row.get("sort"));
        p.opened = parseBoolean(row.get("opened"), true);
        p.version = Math.max(1, parseInt(blankTo(row.get("version"), "1")));
        return p;
    }

    private static List<ShopRewardConfig> parseRewards(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return MAPPER.readValue(json, new TypeReference<List<ShopRewardConfig>>() {
        });
    }

    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String v = raw.trim();
        if (v.startsWith("[")) {
            try {
                return MAPPER.readValue(v, new TypeReference<List<String>>() {
                });
            } catch (IOException e) {
                // fall through
            }
        }
        return Arrays.stream(v.split("[|;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<Map<String, String>> parseCsvRows(String csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String[] headers = splitCsvLine(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
            return rows;
        }
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value.trim());
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static boolean parseBoolean(String value, boolean def) {
        if (value == null || value.isBlank()) {
            return def;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }
}
