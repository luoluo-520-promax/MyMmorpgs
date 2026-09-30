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
import java.util.Map;

/**
 * AI 助手 CLI：活动草案、投诉建议、战斗周报、回归命令序列。
 */
final class CliAiCommands {

    private CliAiCommands() {
    }

    static int runAi(String[] args) {
        if (args.length == 0) {
            printHelp();
            return 1;
        }
        return switch (args[0].toLowerCase()) {
            case "activity-draft", "draft" -> activityDraft(sliceFrom(args, 1));
            case "activity-apply", "apply" -> activityApply(sliceFrom(args, 1));
            case "complaint-suggest" -> complaintSuggest(sliceFrom(args, 1));
            case "battle-report" -> battleReport(sliceFrom(args, 1));
            case "player-advise", "advise" -> playerAdvise(sliceFrom(args, 1));
            case "flow-suggest" -> CliFlowSuggest.run(sliceFrom(args, 1));
            default -> {
                System.err.println("未知 ai 子命令: " + args[0]);
                printHelp();
                yield 1;
            }
        };
    }

    private static int activityDraft(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("prompt") || !map.containsKey("admin-user-id")) {
            System.err.println("缺少必填参数: --prompt --admin-user-id");
            printHelp();
            return 1;
        }
        if (!ensureAuth(map)) {
            return 1;
        }
        String template = map.getOrDefault("template", "");
        boolean dryRun = !"false".equalsIgnoreCase(map.getOrDefault("dry-run", "true"));
        String body = "{\"prompt\":" + jsonString(map.get("prompt"))
                + ",\"template\":" + jsonString(template)
                + ",\"dryRun\":" + dryRun + "}";
        try {
            HttpResponse<String> response = sendAdminRequest(
                    map, "/admin/ai/activity/draft", "POST", "application/json",
                    body.getBytes(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            if (map.containsKey("out") && response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.writeString(Path.of(map.get("out")), response.body(), StandardCharsets.UTF_8);
                System.out.println("已写入 " + map.get("out"));
            }
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("AI draft 失败: " + e.getMessage());
            return 1;
        }
    }

    private static int activityApply(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("confirm-token") || !map.containsKey("admin-user-id")) {
            System.err.println("缺少必填参数: --confirm-token --admin-user-id");
            printHelp();
            return 1;
        }
        if (!ensureAuth(map)) {
            return 1;
        }
        String body = "{\"confirmToken\":" + jsonString(map.get("confirm-token")) + "}";
        try {
            HttpResponse<String> response = sendAdminRequest(
                    map, "/admin/ai/activity/apply", "POST", "application/json",
                    body.getBytes(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("AI apply 失败: " + e.getMessage());
            return 1;
        }
    }

    private static int complaintSuggest(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("admin-user-id")) {
            System.err.println("缺少必填参数: --admin-user-id");
            printHelp();
            return 1;
        }
        if (!map.containsKey("complaint-id") && !map.containsKey("content")) {
            System.err.println("缺少 --complaint-id 或 --content");
            return 1;
        }
        if (!ensureAuth(map)) {
            return 1;
        }
        StringBuilder body = new StringBuilder("{");
        boolean first = true;
        if (map.containsKey("complaint-id")) {
            body.append("\"complaintId\":").append(map.get("complaint-id"));
            first = false;
        }
        if (map.containsKey("content")) {
            if (!first) {
                body.append(',');
            }
            body.append("\"content\":").append(jsonString(map.get("content")));
        }
        body.append('}');
        try {
            HttpResponse<String> response = sendAdminRequest(
                    map, "/admin/ai/complaints/suggest", "POST", "application/json",
                    body.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("complaint-suggest 失败: " + e.getMessage());
            return 1;
        }
    }

    private static int battleReport(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("admin-user-id")) {
            System.err.println("缺少必填参数: --admin-user-id");
            printHelp();
            return 1;
        }
        if (!ensureAuth(map)) {
            return 1;
        }
        String focus = map.getOrDefault("focus", "");
        String body = "{\"focus\":" + jsonString(focus) + "}";
        try {
            HttpResponse<String> response = sendAdminRequest(
                    map, "/admin/ai/battle/weekly-report", "POST", "application/json",
                    body.getBytes(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            if (map.containsKey("out") && response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.writeString(Path.of(map.get("out")), response.body(), StandardCharsets.UTF_8);
                System.out.println("已写入 " + map.get("out"));
            }
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("battle-report 失败: " + e.getMessage());
            return 1;
        }
    }

    private static int playerAdvise(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        if (!map.containsKey("player-id") || !map.containsKey("question") || !map.containsKey("token")) {
            System.err.println("缺少必填参数: --player-id --question --token");
            printHelp();
            return 1;
        }
        map.putIfAbsent("player-url", "http://127.0.0.1:8989");
        String topic = map.getOrDefault("topic", "BATTLE");
        String body = "{\"playerId\":" + map.get("player-id")
                + ",\"question\":" + jsonString(map.get("question"))
                + ",\"topic\":" + jsonString(topic) + "}";
        try {
            String baseUrl = map.get("player-url").replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/player/ai/advise"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Token", map.get("token"))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
            if (map.containsKey("out") && response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.writeString(Path.of(map.get("out")), response.body(), StandardCharsets.UTF_8);
            }
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception e) {
            System.err.println("player-advise 失败: " + e.getMessage());
            return 1;
        }
    }

    private static boolean ensureAuth(Map<String, String> map) {
        map.putIfAbsent("admin-url", "http://127.0.0.1:8985");
        if (!map.containsKey("secret") && !map.containsKey("api-key")) {
            System.err.println("缺少鉴权参数: --secret 或 --api-key");
            return false;
        }
        return true;
    }

    private static HttpResponse<String> sendAdminRequest(
            Map<String, String> map, String path, String method, String contentType, byte[] body) throws Exception {
        String baseUrl = map.get("admin-url").replaceAll("/+$", "");
        long adminUserId = Long.parseLong(map.get("admin-user-id"));
        long timestamp = System.currentTimeMillis();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(90))
                .header(AdminApiAuthHeaders.USER_ID, Long.toString(adminUserId))
                .header("Content-Type", contentType);

        if (map.containsKey("api-key")) {
            builder.header(AdminApiAuthHeaders.API_KEY, map.get("api-key"));
        } else {
            String signature = AdminApiSignUtil.sign(
                    map.get("secret"), timestamp, method, path, adminUserId, body);
            builder.header(AdminApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
            builder.header(AdminApiAuthHeaders.SIGNATURE, signature);
        }

        builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static void printHelp() {
        System.out.println("""
                ai 子命令:
                  ai activity-draft --prompt <text> --admin-user-id <id> \\
                      [--template checkin|recharge|shop] [--dry-run true|false] [--out draft.json] \\
                      [--admin-url http://127.0.0.1:8985] (--secret <hmac> | --api-key <key>)
                  ai activity-apply --confirm-token <token> --admin-user-id <id> \\
                      [--admin-url http://127.0.0.1:8985] (--secret <hmac> | --api-key <key>)
                  ai complaint-suggest (--complaint-id <id> | --content <text>) --admin-user-id <id> \\
                      (--secret <hmac> | --api-key <key>)
                  ai battle-report --admin-user-id <id> [--focus <text>] [--out report.json] \\
                      (--secret <hmac> | --api-key <key>)
                  ai player-advise --player-id <id> --question <text> --token <loginToken> \\
                      [--topic BATTLE|BUILD|QUEST] [--player-url http://127.0.0.1:8989] [--out advice.json]
                  ai flow-suggest [--scenario smoke|balance|complaint|activity] [--out cmds.txt]
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
}
