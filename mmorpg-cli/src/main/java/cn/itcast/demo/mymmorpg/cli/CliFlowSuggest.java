package cn.itcast.demo.mymmorpg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地生成可重复执行的 CLI 回归命令序列（无需连模型）。
 */
final class CliFlowSuggest {

    private CliFlowSuggest() {
    }

    static int run(String[] args) {
        Map<String, String> map = CliArgs.parse(args);
        String scenario = map.getOrDefault("scenario", "smoke").trim().toLowerCase(Locale.ROOT);
        List<String> commands = switch (scenario) {
            case "balance", "battle" -> balanceCommands(map);
            case "complaint", "ops" -> complaintCommands();
            case "activity" -> activityCommands();
            default -> smokeCommands(map);
        };
        StringBuilder out = new StringBuilder();
        out.append("# scenario=").append(scenario).append('\n');
        out.append("# 用法: 逐条执行，或复制到脚本\n");
        for (String cmd : commands) {
            out.append(cmd).append('\n');
        }
        System.out.print(out);
        if (map.containsKey("out")) {
            try {
                Files.writeString(Path.of(map.get("out")), out.toString(), StandardCharsets.UTF_8);
                System.out.println("已写入 " + map.get("out"));
            } catch (Exception e) {
                System.err.println("写入失败: " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }

    private static List<String> smokeCommands(Map<String, String> map) {
        String level = map.getOrDefault("player-level", "10");
        String turns = map.getOrDefault("max-turns", "15");
        List<String> list = new ArrayList<>();
        list.add("selftest");
        list.add("battle damage --attack 65 --defense 4 --action-type 1");
        list.add("battle heal --action-type 3 --item-id 1001");
        list.add("flow --name battle --player-level " + level + " --max-turns " + turns);
        list.add("chat --content \"回归冒烟\"");
        return list;
    }

    private static List<String> balanceCommands(Map<String, String> map) {
        List<String> list = new ArrayList<>();
        list.add("selftest");
        list.add("battle damage --attack 65 --defense 4 --action-type 1");
        list.add("battle damage --attack 65 --defense 4 --action-type 2 --skill-id 1001");
        list.add("battle damage --attack 80 --defense 10 --action-type 2 --skill-id 1001");
        list.add("flow --name battle --player-level 10 --max-turns 15");
        list.add("flow --name battle --player-level 35 --max-turns 20");
        list.add("# 再拉取周报: ai battle-report --admin-user-id <id> --secret <hmac>");
        return list;
    }

    private static List<String> complaintCommands() {
        return List.of(
                "# 投诉归类建议（只读）",
                "ai complaint-suggest --content \"充值没到账\" --admin-user-id <id> --api-key <key>",
                "ai complaint-suggest --complaint-id <id> --admin-user-id <id> --api-key <key>",
                "# 人工确认后走 admin handle，勿自动结案");
    }

    private static List<String> activityCommands() {
        return List.of(
                "ai activity-draft --prompt \"做一周限时签到活动\" --template checkin --admin-user-id <id> --api-key <key> --out draft.json",
                "# 确认 dryRun 通过后:",
                "ai activity-apply --confirm-token <token> --admin-user-id <id> --api-key <key>");
    }
}
