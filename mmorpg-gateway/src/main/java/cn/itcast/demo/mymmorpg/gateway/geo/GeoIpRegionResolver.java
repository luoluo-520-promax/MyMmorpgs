package cn.itcast.demo.mymmorpg.gateway.geo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 简易 GeoIP 区域解析：按客户端 IP 前缀/国家码映射到就近区域节点。
 * 生产可替换为 MaxMind GeoIP2 或边缘 GSLB 结果透传。
 */
public final class GeoIpRegionResolver {

    public record RegionNode(String region, String edgePop, String redisShard, double lat, double lon) {
    }

    private static final Map<String, RegionNode> REGION_NODES = Map.of(
            "cn-east", new RegionNode("cn-east", "pop-shanghai", "redis-cn-east", 31.23, 121.47),
            "cn-north", new RegionNode("cn-north", "pop-beijing", "redis-cn-north", 39.90, 116.40),
            "ap-se", new RegionNode("ap-se", "pop-singapore", "redis-ap-se", 1.35, 103.82),
            "eu-west", new RegionNode("eu-west", "pop-frankfurt", "redis-eu-west", 50.11, 8.68),
            "us-west", new RegionNode("us-west", "pop-oregon", "redis-us-west", 45.52, -122.68),
            "us-east", new RegionNode("us-east", "pop-virginia", "redis-us-east", 38.90, -77.04));

    private GeoIpRegionResolver() {
    }

    public static RegionNode resolveByIp(String clientIp) {
        String ip = clientIp == null ? "" : clientIp.trim();
        if (ip.startsWith("1.") || ip.startsWith("14.") || ip.startsWith("36.") || ip.startsWith("42.")
                || ip.startsWith("58.") || ip.startsWith("106.") || ip.startsWith("112.") || ip.startsWith("123.")) {
            return REGION_NODES.get("cn-east");
        }
        if (ip.startsWith("49.") || ip.startsWith("101.") || ip.startsWith("103.") || ip.startsWith("118.")) {
            return REGION_NODES.get("ap-se");
        }
        if (ip.startsWith("5.") || ip.startsWith("31.") || ip.startsWith("37.") || ip.startsWith("46.")) {
            return REGION_NODES.get("eu-west");
        }
        if (ip.startsWith("3.") || ip.startsWith("18.") || ip.startsWith("34.") || ip.startsWith("44.")
                || ip.startsWith("52.") || ip.startsWith("54.")) {
            return REGION_NODES.get("us-west");
        }
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.isBlank()) {
            return REGION_NODES.get("cn-east");
        }
        return REGION_NODES.get("cn-east");
    }

    public static RegionNode resolveByCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return REGION_NODES.get("cn-east");
        }
        String c = countryCode.trim().toLowerCase(Locale.ROOT);
        String region = switch (c) {
            case "cn", "hk", "tw", "mo" -> "cn-east";
            case "sg", "jp", "kr", "au", "my", "th" -> "ap-se";
            case "de", "fr", "gb", "nl", "it", "es" -> "eu-west";
            case "us", "ca", "mx" -> "us-west";
            default -> "cn-east";
        };
        return REGION_NODES.getOrDefault(region, REGION_NODES.get("cn-east"));
    }

    /** 按经纬度选最近节点（Haversine 近似）。 */
    public static RegionNode nearestByLatLon(double lat, double lon) {
        RegionNode best = REGION_NODES.get("cn-east");
        double bestDist = Double.MAX_VALUE;
        for (RegionNode n : REGION_NODES.values()) {
            double d = haversineKm(lat, lon, n.lat(), n.lon());
            if (d < bestDist) {
                bestDist = d;
                best = n;
            }
        }
        return best;
    }

    public static Map<String, Object> toView(RegionNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region", node.region());
        m.put("edgePop", node.edgePop());
        m.put("redisShard", node.redisShard());
        m.put("lat", node.lat());
        m.put("lon", node.lon());
        return m;
    }

    public static List<RegionNode> allNodes() {
        return List.copyOf(REGION_NODES.values());
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
