package cn.itcast.demo.mymmorpg.cli;

/**
 * MMORPG 命令行测试工具入口。
 * <p>
 * 用法示例：
 * <pre>
 *   java -jar MyMmorpg-cli.jar help
 *   java -jar MyMmorpg-cli.jar selftest
 *   java -jar MyMmorpg-cli.jar battle damage --attack 65 --defense 4 --action-type 1
 *   java -jar MyMmorpg-cli.jar battle heal --action-type 3 --item-id 1001
 *   java -jar MyMmorpg-cli.jar skill damage --level 5 --skill-id 1001
 *   java -jar MyMmorpg-cli.jar item --effect-params "{\"exp\":150,\"hp\":50}"
 *   java -jar MyMmorpg-cli.jar chat --content "大家好"
 *   java -jar MyMmorpg-cli.jar scene --map-id 1 --player-id 42
 *   java -jar MyMmorpg-cli.jar flow --name battle --attack 65 --defense 4
 * </pre>
 */
public final class MmorpgCliApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(0);
        }
        try {
            int exitCode = dispatch(args);
            System.exit(exitCode);
        } catch (NumberFormatException e) {
            System.err.println("参数格式错误: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int dispatch(String[] args) {
        String command = args[0].toLowerCase();
        String[] rest = sliceFrom(args, 1);

        return switch (command) {
            case "help", "-h", "--help" -> {
                printHelp();
                yield 0;
            }
            case "battle" -> CliCommands.runBattle(rest);
            case "skill" -> CliCommands.runSkill(rest);
            case "item" -> CliCommands.runItem(rest);
            case "chat" -> CliCommands.runChat(rest);
            case "scene" -> CliCommands.runScene(rest);
            case "flow" -> CliCommands.runFlow(rest);
            case "selftest" -> CliCommands.runSelfTest();
            default -> {
                System.err.println("未知命令: " + command);
                printHelp();
                yield 1;
            }
        };
    }

    private static void printHelp() {
        System.out.println("""
                MyMmorpg 命令行测试工具

                用法: java -jar MyMmorpg-cli.jar <命令> [选项]

                命令:
                  help                          显示帮助
                  selftest                      运行内置自检（对齐单元测试）
                  battle damage                 计算战斗伤害
                    --attack <n>                攻击力（默认 65）
                    --defense <n>               防御力（默认 4）
                    --action-type <n>           行动类型：1=普攻 2=技能 3=道具（默认 1）
                    --skill-id <n>              技能 ID（默认 0）
                  battle heal                   计算战斗治疗
                    --action-type <n>           行动类型（默认 3）
                    --item-id <n>               道具 ID（默认 0）
                  skill damage                  计算技能伤害
                    --level <n>                 玩家等级（默认 5）
                    --skill-id <n>              技能 ID（默认 1001）
                    --target-type <n>           目标类型（默认 1）
                  skill heal                    计算技能治疗
                    --level <n>                 玩家等级（默认 5）
                    --skill-id <n>              技能 ID（默认 1001）
                  item                          解析道具效果 JSON
                    --item-id <n>               道具 ID（默认 1001）
                    --effect-params <json>      效果参数 JSON
                  chat                          测试聊天敏感词过滤
                    --content <text>            聊天内容
                    --channel <n>               频道（默认 1）
                    --msg-type <n>              消息类型（默认 1）
                  scene                         测试进场景校验
                    --map-id <n>                地图 ID（默认 1）
                    --player-id <n>             玩家 ID（默认 42）
                  flow                          模拟完整业务流程（9 阶段）
                    --name full                 全流程（默认，battle 为别名）
                    --account testuser          登录账号（默认 testuser）
                    --password 123456           登录密码
                    --player-name 星穹列车员    角色名
                    --player-level 35           角色等级
                    --map-id 1                  地图 ID
                    --map-name 星穹月台         地图名
                    --player-id 1               玩家 ID
                    --lineup-id 1               阵容 ID
                    --enemy-id 200              敌人实体 ID
                    --skill-id 1                技能 ID
                    --item-id 1                 治疗道具 ID
                    --chat 战前聊天内容
                    --chat-after 战后聊天内容
                    --max-turns 20              最大回合数

                示例:
                  java -jar MyMmorpg-cli.jar selftest
                  java -jar MyMmorpg-cli.jar flow
                  java -jar MyMmorpg-cli.jar flow --player-level 10 --max-turns 15
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
