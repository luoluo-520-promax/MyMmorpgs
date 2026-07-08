package cn.itcast.demo.mymmorpg.cli;

import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.support.SkillPolicy;

import java.util.Map;

/**
 * 各子命令的业务实现。
 */
final class CliCommands {

    private CliCommands() {
    }

    static int runBattle(String[] args) {
        if (args.length == 0) {
            System.err.println("用法: battle <damage|heal> [选项]");
            return 1;
        }
        return switch (args[0].toLowerCase()) {
            case "damage" -> runBattleDamage(args);
            case "heal" -> runBattleHeal(args);
            default -> {
                System.err.println("未知 battle 子命令: " + args[0]);
                yield 1;
            }
        };
    }

    private static int runBattleDamage(String[] args) {
        Map<String, String> p = CliArgs.parse(sliceFrom(args, 1));
        int attack = CliArgs.getInt(p, "attack", 65);
        int defense = CliArgs.getInt(p, "defense", 4);
        int actionType = CliArgs.getInt(p, "action-type", 1);
        int skillId = CliArgs.getInt(p, "skill-id", 0);

        BattlePolicy policy = PolicyHolder.BATTLE;
        int damage = policy.computeDamage(attack, defense, actionType, skillId);
        System.out.printf("[战斗伤害] 攻击=%d 防御=%d 行动类型=%d 技能ID=%d => 伤害=%d%n",
                attack, defense, actionType, skillId, damage);
        return 0;
    }

    private static int runBattleHeal(String[] args) {
        Map<String, String> p = CliArgs.parse(sliceFrom(args, 1));
        int actionType = CliArgs.getInt(p, "action-type", 3);
        int itemId = CliArgs.getInt(p, "item-id", 0);

        BattlePolicy policy = PolicyHolder.BATTLE;
        int heal = policy.computeHeal(actionType, itemId);
        System.out.printf("[战斗治疗] 行动类型=%d 道具ID=%d => 治疗量=%d%n", actionType, itemId, heal);
        return 0;
    }

    static int runSkill(String[] args) {
        if (args.length == 0) {
            System.err.println("用法: skill <damage|heal> [选项]");
            return 1;
        }
        Map<String, String> p = CliArgs.parse(sliceFrom(args, 1));
        int level = CliArgs.getInt(p, "level", 5);
        int skillId = CliArgs.getInt(p, "skill-id", 1001);
        SkillPolicy policy = PolicyHolder.SKILL;

        return switch (args[0].toLowerCase()) {
            case "damage" -> {
                int targetType = CliArgs.getInt(p, "target-type", 1);
                int damage = policy.computeDamage(level, skillId, targetType);
                System.out.printf("[技能伤害] 等级=%d 技能ID=%d 目标类型=%d => 伤害=%d%n",
                        level, skillId, targetType, damage);
                yield 0;
            }
            case "heal" -> {
                int heal = policy.computeHeal(level, skillId);
                System.out.printf("[技能治疗] 等级=%d 技能ID=%d => 治疗量=%d%n", level, skillId, heal);
                yield 0;
            }
            default -> {
                System.err.println("未知 skill 子命令: " + args[0]);
                yield 1;
            }
        };
    }

    static int runItem(String[] args) {
        Map<String, String> p = CliArgs.parse(args);
        String effectParams = CliArgs.getString(p, "effect-params", "{\"exp\":150,\"hp\":50,\"mp\":80}");
        ItemConfig config = new ItemConfig();
        config.setId(CliArgs.getInt(p, "item-id", 1001));
        config.setEffectParams(effectParams);

        ItemPolicy policy = PolicyHolder.ITEM;
        int exp = policy.parseExpReward(config);
        int hp = policy.parseHpRestore(config);
        int mp = policy.parseMpRestore(config);
        System.out.printf("[道具解析] itemId=%d effectParams=%s => exp=%d hp=%d mp=%d%n",
                config.getId(), effectParams, exp, hp, mp);
        return 0;
    }

    static int runChat(String[] args) {
        Map<String, String> p = CliArgs.parse(args);
        String content = CliArgs.getString(p, "content", "大家好，欢迎来到星穹月台！");
        int channel = CliArgs.getInt(p, "channel", 1);
        int msgType = CliArgs.getInt(p, "msg-type", 1);

        ChatPolicy policy = PolicyHolder.CHAT;
        String filtered = policy.filterContent(channel, msgType, content);
        if (filtered == null) {
            System.out.printf("[聊天过滤] 内容被拒绝: \"%s\"%n", content);
        } else {
            System.out.printf("[聊天过滤] 通过: \"%s\"%n", filtered);
        }
        return 0;
    }

    static int runScene(String[] args) {
        Map<String, String> p = CliArgs.parse(args);
        int mapId = CliArgs.getInt(p, "map-id", 1);
        long playerId = CliArgs.getLong(p, "player-id", 42L);

        ScenePolicy policy = PolicyHolder.SCENE;
        boolean allowed = policy.allowEnterScene(mapId, playerId);
        System.out.printf("[进场景] mapId=%d playerId=%d => %s%n",
                mapId, playerId, allowed ? "允许进入" : "拒绝进入");
        return allowed ? 0 : 1;
    }

    static int runFlow(String[] args) {
        Map<String, String> p = CliArgs.parse(args);
        return GameFlowSimulator.run(p);
    }

    static int runSelfTest() {
        System.out.println("========== 内置自检（对齐单元测试用例） ==========");
        int failed = 0;

        BattlePolicy battle = PolicyHolder.BATTLE;
        failed += assertEqual("普攻伤害", battle.computeDamage(65, 4, 1, 0), 61);
        failed += assertEqual("技能伤害", battle.computeDamage(65, 4, 2, 1001), 91);
        failed += assertEqual("最低伤害", battle.computeDamage(3, 10, 1, 0), 1);
        failed += assertEqual("道具治疗", battle.computeHeal(3, 1001), 150);
        failed += assertEqual("默认治疗", battle.computeHeal(3, 0), 100);
        failed += assertEqual("非道具不治疗", battle.computeHeal(1, 1001), 0);

        SkillPolicy skill = PolicyHolder.SKILL;
        failed += assertEqual("技能伤害公式", skill.computeDamage(5, 1001, 1), 80 + 5 * 12 + 1001 % 50);
        failed += assertEqual("技能治疗公式", skill.computeHeal(5, 1001), 60 + 5 * 8);

        ItemConfig item = new ItemConfig();
        item.setEffectParams("{\"exp\":150,\"hp\":320,\"mp\":80}");
        ItemPolicy itemPolicy = PolicyHolder.ITEM;
        failed += assertEqual("道具经验", itemPolicy.parseExpReward(item), 150);
        failed += assertEqual("道具HP", itemPolicy.parseHpRestore(item), 320);
        failed += assertEqual("道具MP", itemPolicy.parseMpRestore(item), 80);

        ChatPolicy chat = PolicyHolder.CHAT;
        failed += assertTrue("正常聊天", chat.filterContent(1, 1, "hello") != null);
        failed += assertTrue("敏感词拦截", chat.filterContent(1, 1, "赌博广告") == null);

        ScenePolicy scene = PolicyHolder.SCENE;
        failed += assertTrue("合法进场景", scene.allowEnterScene(1, 42L));
        failed += assertTrue("非法进场景", !scene.allowEnterScene(0, 42L));

        int[] stats = GameFlowSimulator.playerCombatStats(35);
        failed += assertEqual("Lv35玩家HP", stats[0], 100 + 35 * 50);
        failed += assertEqual("Lv35玩家攻击", stats[2], 50 + 35 * 5);

        System.out.println("========================================");
        if (failed == 0) {
            System.out.println("全部通过 ✓");
            return 0;
        }
        System.out.printf("失败 %d 项 ✗%n", failed);
        return 1;
    }

    private static int assertEqual(String name, int actual, int expected) {
        if (actual == expected) {
            System.out.printf("[PASS] %s => %d%n", name, actual);
            return 0;
        }
        System.out.printf("[FAIL] %s => 实际=%d 期望=%d%n", name, actual, expected);
        return 1;
    }

    private static int assertTrue(String name, boolean condition) {
        if (condition) {
            System.out.printf("[PASS] %s%n", name);
            return 0;
        }
        System.out.printf("[FAIL] %s%n", name);
        return 1;
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
