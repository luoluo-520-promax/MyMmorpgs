package cn.itcast.demo.mymmorpg.cli;

import cn.itcast.demo.mymmorpg.security.AdminApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.AdminApiSignUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 通过 admin-service HTTP API 导入活动/版本清单。
 */
final class CliImportCommands {

    private CliImportCommands() {
    }

    static int runImport(String[] args) {
        if (args.length == 0) {
            printHelp();
            return 1;
        }
        return switch (args[0].toLowerCase()) {
            case "activity" -> importActivity(sliceFrom(args, 1));
            case "manifest" -> importManifest(sliceFrom(args, 1));
            default -> {
                System.err.println("未知 import 子命令: " + args[0]);
                printHelp();
                yield 1;
            }
        };
    }

    private static int importActivity(String[] args) {
        MapHolder opts = parseCommonOptions(args);
        if (opts == null) {
            return 1;
        }
        String format = opts.map.getOrDefault("format", "json").toLowerCase();
        String contentType = "csv".equals(format) ? "text/csv" : "application/json";
        try {
            byte[] body = Files.readAllBytes(Path.of(opts.map.get("file")));
            HttpResponse<String> response = sendAdminRequest(
                    opts,
                    "/admin/import/activities",
                    "POST",
                    contentType,
                    body);
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("导入活动失败: " + e.getMessage());
            return 1;
        }
    }

    private static int importManifest(String[] args) {
        MapHolder opts = parseCommonOptions(args);
        if (opts == null) {
            return 1;
        }
        try {
            byte[] body = Files.readAllBytes(Path.of(opts.map.get("file")));
            HttpResponse<String> response = sendAdminRequest(
                    opts,
                    "/admin/import/manifests",
                    "POST",
                    "application/json",
                    body);
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("导入版本清单失败: " + e.getMessage());
            return 1;
        }
    }

    private static MapHolder parseCommonOptions(String[] args) {
        java.util.Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("file") || !map.containsKey("admin-user-id")) {
            System.err.println("缺少必填参数: --file --admin-user-id");
            printHelp();
            return null;
        }
        if (!map.containsKey("admin-url")) {
            map.put("admin-url", "http://127.0.0.1:8985");
        }
        if (!map.containsKey("secret") && !map.containsKey("api-key")) {
            System.err.println("缺少鉴权参数: --secret 或 --api-key");
            return null;
        }
        return new MapHolder(map);
    }

    private static HttpResponse<String> sendAdminRequest(
            MapHolder opts, String path, String method, String contentType, byte[] body) throws Exception {
        String baseUrl = opts.map.get("admin-url").replaceAll("/+$", "");
        long adminUserId = Long.parseLong(opts.map.get("admin-user-id"));
        long timestamp = System.currentTimeMillis();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header(AdminApiAuthHeaders.USER_ID, Long.toString(adminUserId))
                .header("Content-Type", contentType);

        if (opts.map.containsKey("api-key")) {
            builder.header(AdminApiAuthHeaders.API_KEY, opts.map.get("api-key"));
        } else {
            String signature = AdminApiSignUtil.sign(
                    opts.map.get("secret"), timestamp, method, path, adminUserId, body);
            builder.header(AdminApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
            builder.header(AdminApiAuthHeaders.SIGNATURE, signature);
        }

        builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        HttpClient client = HttpClient.newHttpClient();
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void printHelp() {
        System.out.println("""
                import 子命令:
                  import activity --file <path> --admin-user-id <id> [--format json|csv] \\
                      [--admin-url http://127.0.0.1:8985] (--secret <hmac> | --api-key <key>)
                  import manifest --file <path> --admin-user-id <id> \\
                      [--admin-url http://127.0.0.1:8985] (--secret <hmac> | --api-key <key>)
                """);
    }

    private static String[] sliceFrom(String[] args, int from) {
        if (from >= args.length) {
            return new String[0];
        }
        String[] rest = new String[args.length - from];
        System.arraycopy(args, from, rest, 0, rest.length);
        return rest;
    }

    private record MapHolder(java.util.Map<String, String> map) {
    }
}
