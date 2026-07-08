package cn.itcast.demo.mymmorpg.cli;

import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.BattleSceneFactory;
import cn.itcast.demo.mymmorpg.service.BattleService;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;

import java.util.Map;

/**
 * 完整游戏业务流程模拟器，对齐 player-service / battle-service 真实逻辑。
 */
final class GameFlowSimulator {

    private static final int ACTION_NORMAL = 1;
    private static final int ACTION_SKILL = 2;
    private static final int ACTION_ITEM = 3;

    private GameFlowSimulator() {
    }

    static int run(Map<String, String> p) {
        String flow = CliArgs.getString(p, "name", "full");
        return switch (flow.toLowerCase()) {
            case "full", "battle" -> simulateFullFlow(p);
            default -> {
                System.err.println("未知流程: " + flow + "（支持: full / battle）");
                yield 1;
            }
        };
    }

    /**
     * 模拟：登录 → 选角 → 预加载 → 进场景 → 聊天 → 开战 → 多回合战斗 → 结算 → 战后聊天。
     */
    private static int simulateFullFlow(Map<String, String> p) {
        String account = CliArgs.getString(p, "account", "testuser");
        String password = CliArgs.getString(p, "password", "123456");
        String playerName = CliArgs.getString(p, "player-name", "星穹列车员");
        int playerLevel = CliArgs.getInt(p, "player-level", 35);
        int mapId = CliArgs.getInt(p, "map-id", 1);
        String mapName = CliArgs.getString(p, "map-name", "星穹月台");
        long playerId = CliArgs.getLong(p, "player-id", 1L);
        long lineupId = CliArgs.getLong(p, "lineup-id", 1L);
        long enemyEntityId = CliArgs.getLong(p, "enemy-id", 200L);
        int skillId = CliArgs.getInt(p, "skill-id", 1);
        int healItemId = CliArgs.getInt(p, "item-id", 1);
        String chatBefore = CliArgs.getString(p, "chat", "大家好，准备打怪！");
        String chatAfter = CliArgs.getString(p, "chat-after", "战斗胜利，收工！");
        int maxTurns = CliArgs.getInt(p, "max-turns", 20);

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║           MMORPG 完整业务流程模拟（CLI）                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        // ── 阶段 1：账号登录 ──
        System.out.println("【阶段 1/9】账号登录  AccountLoginCsReq → AccountLoginScRsp");
        if (account.isBlank() || password.isBlank()) {
            System.out.println("  ✗ 账号或密码为空，登录失败 (retCode=ACCOUNT_NOT_FOUND)");
            return 1;
        }
        String token = "tok-" + account + "-" + System.currentTimeMillis();
        System.out.printf("  账号: %s  密码: %s%n", account, mask(password));
        System.out.printf("  ✓ 登录成功  token=%s...%n", token.substring(0, Math.min(20, token.length())));
        System.out.printf("  角色列表: [%s Lv.%d] [无名侠 Lv.10]%n", playerName, playerLevel);
        System.out.println();

        // ── 阶段 2：选择角色 ──
        System.out.println("【阶段 2/9】选择角色  SelectPlayerCsReq → SelectPlayerScRsp");
        System.out.printf("  选中角色: playerId=%d  name=%s  level=%d  vip=%d%n",
                playerId, playerName, playerLevel, 1);
        System.out.println("  ✓ 选角成功，会话绑定 playerId");
        System.out.println();

        // ── 阶段 3：数据预加载 ──
        System.out.println("【阶段 3/9】异步预加载  PlayerDataPreloadEvent");
        System.out.println("  预加载: [背包] [技能] [活动] ...");
        ItemConfig expPotion = devExpPotion();
        ItemPolicy itemPolicy = PolicyHolder.ITEM;
        int potionExp = itemPolicy.parseExpReward(expPotion);
        System.out.printf("  ✓ 背包就绪  槽0=经验药水×5 (useExp=%d)%n", potionExp);
        System.out.println("  ✓ 技能就绪  已学: [1=飞龙探云手] [2=逍遥神剑]");
        System.out.println("  ✓ 活动就绪  [首充大礼包] [夏日签到]");
        System.out.println();

        // ── 阶段 4：进入场景 ──
        System.out.println("【阶段 4/9】进入场景  EnterSceneCsReq → EnterSceneScRsp");
        if (!PolicyHolder.SCENE.allowEnterScene(mapId, playerId)) {
            System.out.printf("  ✗ 进场景被拒绝  mapId=%d playerId=%d%n", mapId, playerId);
            return 1;
        }
        System.out.printf("  地图: %s (mapId=%d)  分线: 1  坐标: (1024, 2048)%n", mapName, mapId);
        System.out.println("  场景实体: [NPC] [流浪者×3] [盗贼×2]");
        System.out.println("  ✓ 进场景成功");
        System.out.println();

        // ── 阶段 5：战前聊天 ──
        System.out.println("【阶段 5/9】发送聊天  SendChatMsgCsReq → SendChatMsgScRsp");
        ChatPolicy chatPolicy = PolicyHolder.CHAT;
        String filteredBefore = chatPolicy.filterContent(1, 1, chatBefore);
        if (filteredBefore == null) {
            System.out.printf("  ✗ 消息被拒绝 (retCode=SENSITIVE): \"%s\"%n", chatBefore);
            return 1;
        }
        System.out.printf("  频道=世界  内容=\"%s\"%n", filteredBefore);
        System.out.println("  ✓ 聊天广播成功 (603 ScNotify)");
        System.out.println();

        // ── 阶段 6：开始战斗 ──
        System.out.println("【阶段 6/9】开始战斗  BattleStartCsReq → BattleStartScRsp");
        MonsterConfig monster = devWandererMonster();
        Player player = new Player();
        player.setName(playerName);
        player.setLevel(playerLevel);

        int[] ps = playerCombatStats(playerLevel);
        BattleSceneFactory factory = new BattleSceneFactory();
        long battleId = 10_001L;
        BattleService.BattleRuntimeState state = factory.createState(
                battleId, mapId, playerId, lineupId, enemyEntityId,
                monster, player, monster.getName(), monster.getLevel(),
                monster.getExpReward() == null ? 150 : monster.getExpReward(), 1);

        state.playerHpMax = ps[0];
        state.playerHp = ps[0];
        state.playerMpMax = ps[1];
        state.playerMp = ps[1];
        state.playerAttack = ps[2];
        state.playerDefense = ps[3];
        state.enemyHpMax = monster.getHpMax() == null ? 100 : monster.getHpMax();
        state.enemyHp = state.enemyHpMax;
        state.enemyMpMax = monster.getMpMax() == null ? 0 : monster.getMpMax();
        state.enemyMp = state.enemyMpMax;
        state.enemyAttack = monster.getAttack() == null ? 10 : monster.getAttack();
        state.enemyDefense = monster.getDefense() == null ? 5 : monster.getDefense();
        state.nextActionId = 1L;
        state.ended = false;

        System.out.printf("  目标: enemyEntityId=%d  template=%s(Lv.%d)%n",
                enemyEntityId, monster.getName(), monster.getLevel());
        System.out.printf("  battleId=%d  lineupId=%d  sceneId=%d%n", battleId, lineupId, mapId);
        printCombatants(state);
        System.out.println("  ✓ 开战成功 (Redis: battle:state:" + battleId + ")");
        System.out.println();

        // ── 阶段 7：多回合战斗 ──
        System.out.println("【阶段 7/9】战斗回合  BattleActionCsReq → BattleActionScRsp + BattleSyncScNotify");
        BattlePolicy battlePolicy = PolicyHolder.BATTLE;
        int turn = 0;
        boolean usedHeal = false;

        while (!state.ended && turn < maxTurns) {
            turn++;
            int actionType;
            String actionDesc;
            if (state.enemyHp > state.enemyHpMax / 2 && turn <= 2) {
                actionType = ACTION_NORMAL;
                actionDesc = "普攻";
            } else if (!usedHeal && state.playerHp < state.playerHpMax * 0.6) {
                actionType = ACTION_ITEM;
                actionDesc = "道具治疗(itemId=" + healItemId + ")";
            } else {
                actionType = ACTION_SKILL;
                actionDesc = "技能(skillId=" + skillId + ")";
            }

            System.out.printf("  ── 回合 %d ── 玩家行动: %s%n", turn, actionDesc);
            long actionId = state.nextActionId++;
            int damageDealt = 0;
            int healDone = 0;

            if (actionType == ACTION_ITEM) {
                healDone = battlePolicy.computeHeal(actionType, healItemId);
                state.playerHp = Math.min(state.playerHpMax, state.playerHp + healDone);
                usedHeal = true;
                System.out.printf("    actionId=%d  治疗=%d  玩家HP %d → %d/%d%n",
                        actionId, healDone, state.playerHp - healDone, state.playerHp, state.playerHpMax);
            } else {
                damageDealt = battlePolicy.computeDamage(
                        state.playerAttack, state.enemyDefense, actionType,
                        actionType == ACTION_SKILL ? skillId : 0);
                int enemyHpBefore = state.enemyHp;
                state.enemyHp = Math.max(0, state.enemyHp - damageDealt);
                System.out.printf("    actionId=%d  伤害=%d  敌人HP %d → %d/%d%n",
                        actionId, damageDealt, enemyHpBefore, state.enemyHp, state.enemyHpMax);
            }

            if (state.enemyHp > 0) {
                int monsterDamage = battlePolicy.computeDamage(
                        state.enemyAttack, state.playerDefense, ACTION_NORMAL, 0);
                int playerHpBefore = state.playerHp;
                state.playerHp = Math.max(0, state.playerHp - monsterDamage);
                System.out.printf("    怪物反击  伤害=%d  玩家HP %d → %d/%d%n",
                        monsterDamage, playerHpBefore, state.playerHp, state.playerHpMax);
            }

            if (state.enemyHp <= 0) {
                state.ended = true;
                System.out.println("    ★ 敌人被击败，战斗结束 (syncType=BATTLE_END)");
            } else if (state.playerHp <= 0) {
                state.ended = true;
                System.out.println("    ★ 玩家倒下，战斗失败 (syncType=BATTLE_END)");
            } else {
                System.out.println("    同步推送 BattleSyncScNotify (syncType=ATTR)");
            }
            System.out.println();
        }

        if (!state.ended) {
            System.out.printf("  ⚠ 达到最大回合数 %d，战斗未分胜负%n%n", maxTurns);
            return 1;
        }

        // ── 阶段 8：战斗结算 ──
        System.out.println("【阶段 8/9】战斗结算  BattleEndCsReq → BattleEndScRsp");
        int clientResult = state.enemyHp <= 0 ? 1 : 0;
        if (!validateResult(state, clientResult)) {
            System.out.println("  ✗ 客户端结果与服务端状态不一致 (retCode=BATTLE_RESULT_MISMATCH)");
            return 1;
        }

        if (clientResult == 1) {
            int exp = state.expReward;
            System.out.printf("  结果: 胜利  经验+%d  金币+500  掉落=[itemId=1001 ×1]%n", exp);
            System.out.println("  场景: 移除怪物 entityId=" + enemyEntityId);
            System.out.println("  MQ: BattleEndedEvent 已发布");
        } else {
            System.out.println("  结果: 失败  无奖励");
        }
        System.out.println("  ✓ 结算完成，Redis 状态已清理");
        System.out.println();

        // ── 阶段 9：战后聊天 ──
        System.out.println("【阶段 9/9】战后聊天  SendChatMsgCsReq");
        String filteredAfter = chatPolicy.filterContent(1, 1, chatAfter);
        if (filteredAfter == null) {
            System.out.printf("  ✗ 消息被拒绝: \"%s\"%n", chatAfter);
        } else {
            System.out.printf("  内容=\"%s\"  ✓ 广播成功%n", filteredAfter);
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf("║  流程完成  共 %d 回合  最终: 玩家HP=%d/%d  敌人HP=%d/%d   ║%n",
                turn, state.playerHp, state.playerHpMax, state.enemyHp, state.enemyHpMax);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        return clientResult == 1 ? 0 : 1;
    }

    /** 对齐 BattleService.playerCombatStats */
    static int[] playerCombatStats(int level) {
        int lv = Math.max(1, level);
        return new int[]{
                100 + lv * 50,
                lv * 10,
                50 + lv * 5,
                20 + lv * 2
        };
    }

    /** 对齐 BattleService.validateResult */
    private static boolean validateResult(BattleService.BattleRuntimeState state, int clientResult) {
        return switch (clientResult) {
            case 0 -> state.playerHp <= 0;
            case 1 -> state.enemyHp <= 0;
            case 2 -> state.playerHp > 0 && state.enemyHp > 0;
            default -> false;
        };
    }

    /** dev 种子怪物：流浪者 Lv10 */
    private static MonsterConfig devWandererMonster() {
        MonsterConfig m = new MonsterConfig();
        m.setId(1);
        m.setName("流浪者");
        m.setModelId(1001);
        m.setLevel(10);
        m.setHpMax(320);
        m.setMpMax(50);
        m.setAttack(28);
        m.setDefense(8);
        m.setExpReward(150);
        m.setDescription("示例怪物");
        return m;
    }

    /** dev 种子道具：经验药水 */
    private static ItemConfig devExpPotion() {
        ItemConfig c = new ItemConfig();
        c.setId(1);
        c.setName("经验药水");
        c.setEffectParams("{\"exp\":1000}");
        return c;
    }

    private static void printCombatants(BattleService.BattleRuntimeState s) {
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.printf("  │ 玩家 %-12s Lv.%-3d  HP %4d/%-4d  MP %3d/%-3d │%n",
                s.playerName, s.playerLevel, s.playerHp, s.playerHpMax, s.playerMp, s.playerMpMax);
        System.out.printf("  │       攻击=%-4d  防御=%-4d                            │%n",
                s.playerAttack, s.playerDefense);
        System.out.printf("  │ 敌人 %-12s Lv.%-3d  HP %4d/%-4d  MP %3d/%-3d │%n",
                s.enemyName, s.enemyLevel, s.enemyHp, s.enemyHpMax, s.enemyMp, s.enemyMpMax);
        System.out.printf("  │       攻击=%-4d  防御=%-4d  经验奖励=%-4d            │%n",
                s.enemyAttack, s.enemyDefense, s.expReward);
        System.out.println("  └─────────────────────────────────────────────────────┘");
    }

    private static String mask(String s) {
        if (s.length() <= 2) {
            return "**";
        }
        return s.charAt(0) + "*".repeat(s.length() - 2) + s.charAt(s.length() - 1);
    }
}
