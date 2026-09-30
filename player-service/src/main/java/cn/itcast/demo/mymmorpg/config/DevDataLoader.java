/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/DevDataLoader.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：非 test 环境下表为空时写入示例账号、地图、技能、背包与活动，对齐接口文档联调数据。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import cn.itcast.demo.mymmorpg.entity.Activity; // 活动主表，data 字段存 JSON 档位配置
import cn.itcast.demo.mymmorpg.entity.Account; // 游戏账号，与 AdminUser 后台账号分离
import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 静态配置，供 BuffFacade 与 battle 引用
import cn.itcast.demo.mymmorpg.entity.MapConfig; // 地图宽高、默认分线数
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // 怪物模板，scene 刷怪与 battle 引用
import cn.itcast.demo.mymmorpg.entity.ItemConfig; // 道具模板，effect_params 存 JSON 效果
import cn.itcast.demo.mymmorpg.entity.Player; // 玩家角色，绑定 accountId
import cn.itcast.demo.mymmorpg.entity.PlayerBagItem; // 背包槽位行
import cn.itcast.demo.mymmorpg.entity.PlayerSkill; // 玩家已学技能
import cn.itcast.demo.mymmorpg.entity.PlayerSkillId; // 复合主键 playerId + skillId
import cn.itcast.demo.mymmorpg.entity.SkillConfig; // 技能静态配置
import cn.itcast.demo.mymmorpg.repository.BuffConfigRepository; // JPA 仓储，读写 BuffConfig 相关表
import cn.itcast.demo.mymmorpg.repository.ItemConfigRepository; // JPA 仓储，读写 ItemConfig 相关表
import cn.itcast.demo.mymmorpg.repository.ActivityRepository; // JPA 仓储，读写 Activity 相关表
import cn.itcast.demo.mymmorpg.repository.AccountRepository; // JPA 仓储，读写 Account 相关表
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository; // JPA 仓储，读写 PlayerBagItem 相关表
import cn.itcast.demo.mymmorpg.repository.MapConfigRepository; // JPA 仓储，读写 MapConfig 相关表
import cn.itcast.demo.mymmorpg.repository.MonsterConfigRepository; // JPA 仓储，读写 MonsterConfig 相关表
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // JPA 仓储，读写 Player 相关表
import cn.itcast.demo.mymmorpg.repository.PlayerSkillRepository; // JPA 仓储，读写 PlayerSkill 相关表
import cn.itcast.demo.mymmorpg.repository.SkillConfigRepository; // JPA 仓储，读写 SkillConfig 相关表
import org.slf4j.Logger; // SLF4J 日志接口
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger，输出业务/运维日志
import org.springframework.boot.CommandLineRunner; // 容器就绪后执行 run，晚于 JPA ddl/schema
import org.springframework.context.annotation.Profile; // !test 排除单元测试污染 H2
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder; // BCrypt 哈希 testuser 密码
import org.springframework.stereotype.Component; // 注册 Spring Bean，供容器注入或启动扫描
import java.math.BigDecimal; // skill castTime 精度字段
import java.time.Instant; // player_skill.learn_time UTC
/**
 * 开发环境示例数据：testuser / 123456（仅当对应表 count=0 时插入）。
 */

@Component // 单例，启动时自动 run
@Profile("dev")

public class DevDataLoader implements CommandLineRunner { // DevDataLoader 类型定义
    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class); // SLF4J Logger，记录 dev 种子写入进度
    private final AccountRepository accountRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final PlayerRepository playerRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final MapConfigRepository mapConfigRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final MonsterConfigRepository monsterConfigRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final SkillConfigRepository skillConfigRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final BuffConfigRepository buffConfigRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final PlayerSkillRepository playerSkillRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final ItemConfigRepository itemConfigRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final PlayerBagItemRepository playerBagItemRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final ActivityRepository activityRepository; // JPA 仓储，dev 种子写入与 count 判空
    private final PasswordEncoder passwordEncoder; // BCrypt 编码器，哈希 testuser 密码
    private final JdbcTemplate jdbcTemplate; // 固定 ID 皮肤卡种子（绕过 IDENTITY）
    public DevDataLoader( // Spring 构造注入各 JPA 仓储与 PasswordEncoder，供 run 写 dev 种子
            AccountRepository accountRepository, // 联调账号 testuser 写入与 count 判空
            PlayerRepository playerRepository, // dev 角色星穹列车员/无名侠 写入
            MapConfigRepository mapConfigRepository, // 星穹月台 map_config 写入
            MonsterConfigRepository monsterConfigRepository, // 流浪者/盗贼 monster_config 写入
            SkillConfigRepository skillConfigRepository, // 三示例技能 skill_config 写入
            BuffConfigRepository buffConfigRepository, // 力量祝福/中毒/急如风 buff_config 写入
            PlayerSkillRepository playerSkillRepository, // 首个角色预置 player_skill
            ItemConfigRepository itemConfigRepository, // 经验药水/铁剑 item_config 写入
            PlayerBagItemRepository playerBagItemRepository, // 首个角色背包槽 0/1 写入
            ActivityRepository activityRepository, // 首充/签到 activity 写入
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) { // BCrypt 哈希 testuser 密码 123456
        this.accountRepository = accountRepository; // 构造器注入 accountRepository
        this.playerRepository = playerRepository; // 构造器注入 playerRepository
        this.mapConfigRepository = mapConfigRepository; // 构造器注入 mapConfigRepository
        this.monsterConfigRepository = monsterConfigRepository; // 构造器注入 monsterConfigRepository
        this.skillConfigRepository = skillConfigRepository; // 构造器注入 skillConfigRepository
        this.buffConfigRepository = buffConfigRepository; // 构造器注入 buffConfigRepository
        this.playerSkillRepository = playerSkillRepository; // 构造器注入 playerSkillRepository
        this.itemConfigRepository = itemConfigRepository; // 构造器注入 itemConfigRepository
        this.playerBagItemRepository = playerBagItemRepository; // 构造器注入 playerBagItemRepository
        this.activityRepository = activityRepository; // 构造器注入 activityRepository
        this.passwordEncoder = passwordEncoder; // 构造器注入 passwordEncoder
        this.jdbcTemplate = jdbcTemplate;
    } // method 方法体结束

    @Override // 实现接口/父类方法
    public void run(String... args) { // 容器就绪后按表 count=0 写入 dev 种子
        // --- 账号与角色：供登录/选角接口文档示例 ---
        if (accountRepository.count() == 0) { // 已有数据则跳过，避免重复插入
            Account account = new Account(); // dev 种子：联调账号 testuser/123456 实体
            account.setAccountName("testuser"); // 与 Postman/接口文档一致
            account.setPassword(passwordEncoder.encode("123456")); // BCrypt，非明文入库
            accountRepository.save(account); // 持久化联调账号 testuser
            Player p1 = new Player(); // dev 种子：主角色星穹列车员 Lv35 VIP
            p1.setAccountId(account.getId()); // 外键 account.id
            p1.setName("星穹列车员"); // dev 主角色名，Lv35 VIP
            p1.setLevel(35); // 高等级便于测功能解锁与战斗
            p1.setVipRight(1); // VIP 标记，FunctionService 可扩展校验
            p1.setStrength(40);
            p1.setAgility(30);
            p1.setIntelligence(25);
            p1.setTalentPoints(5);
            p1.setGold(1000L);
            p1.recalcPowerScore();
            playerRepository.save(p1); // 持久化 dev 角色行
            Player p2 = new Player(); // dev 种子：副角色无名侠 Lv10 同账号
            p2.setAccountId(account.getId()); // 同账号多角色
            p2.setName("无名侠"); // dev 副角色名，同账号多角色
            p2.setLevel(10); // 低等级副角色，测等级门槛
            p2.setVipRight(0); // 非 VIP 副角色
            p2.setStrength(15);
            p2.setAgility(12);
            p2.setIntelligence(10);
            p2.setTalentPoints(2);
            p2.recalcPowerScore();
            playerRepository.save(p2); // 持久化 dev 角色行
            log.info("已写入示例账号 testuser / 123456，角色与接口文档示例一致"); // dev 种子：账号与双角色写入完成
        } // DevDataLoader 类体结束
        // --- 地图：SceneActorService enterScene 依赖 map_config ---
        if (mapConfigRepository.count() == 0) { // map_config 表空才写入星穹月台
            MapConfig map = new MapConfig(); // dev 种子：星穹月台 map 4096x4096 默认 2 分线
            map.setName("星穹月台"); // dev 示例地图名
            map.setWidth(4096); // 地图宽 4096，SceneActor AOI 边界
            map.setHeight(4096); // 地图高 4096
            map.setDefaultLines(2); // 默认 2 条分线
            mapConfigRepository.save(map); // 持久化星穹月台 map_config
            log.info("已写入示例地图 map_config id={}", map.getId()); // dev 种子：地图 id 供 enterScene 引用
        } // 编译单元结束
        // --- 怪物：battle start 与 scene 刷怪引用 monster_config ---
        if (monsterConfigRepository.count() == 0) { // monster_config 表空才写入示例怪物
            MonsterConfig m1 = new MonsterConfig(); // dev 种子：怪物流浪者 Lv10 model 1001
            m1.setName("流浪者"); // dev 怪物模板 id=1，Lv10
            m1.setModelId(1001); // 客户端模型 id
            m1.setLevel(10); // 流浪者等级 10，scene 刷怪与 battle 引用
            m1.setDescription("示例怪物"); // 怪物描述文案
            monsterConfigRepository.save(m1); // 持久化 dev 怪物模板
            MonsterConfig m2 = new MonsterConfig(); // dev 种子：怪物盗贼 Lv12 model 1002
            m2.setName("盗贼"); // dev 怪物模板 id=2，Lv12
            m2.setModelId(1002); // 客户端模型 id 1002
            m2.setLevel(12); // 盗贼等级 12
            monsterConfigRepository.save(m2); // 持久化 dev 怪物模板
            log.info("已写入示例怪物 monster_config"); // dev 种子：流浪者/盗贼模板写入完成
        } // 编译单元结束
        // --- 技能：与接口文档 SkillInfo 示例字段对齐 ---
        if (skillConfigRepository.count() == 0) { // skill_config 表空才写入三示例技能
            SkillConfig s1 = new SkillConfig(); // dev 种子：技能1 飞龙探云手 Lv1 单体
            s1.setName("飞龙探云手"); // dev 技能1：偷取类主动技能
            s1.setEffect("偷取敌人东西或金币"); // 技能效果描述
            s1.setNeedLevel(1); // 1 级可学
            s1.setCooldown(30); // 秒
            s1.setManaCost(20); // 消耗 20 法力
            s1.setCastTime(new BigDecimal("1.50")); // 施法前摇 1.5 秒
            s1.setSkillType(1); // 主动技能
            s1.setTargetType(1); // 单体
            s1.setRange(300); // 施法距离 300
            s1.setShape(1); // 圆形范围
            s1.setShapeParams("{\"radius\":200}"); // 圆形半径 200 JSON
            skillConfigRepository.save(s1); // 持久化 dev 技能静态配置
            SkillConfig s2 = new SkillConfig(); // dev 种子：技能2 逍遥神剑 Lv20 扇形
            s2.setName("逍遥神剑"); // dev 技能2：全体攻击 Lv20
            s2.setEffect("李逍遥自创的绝技，攻击敌方全体"); // 全体攻击效果描述
            s2.setNeedLevel(20); // 20 级可学
            s2.setCooldown(60); // 冷却 60 秒
            s2.setManaCost(50); // 消耗 50 法力
            s2.setCastTime(new BigDecimal("2.00")); // 施法前摇 2 秒
            s2.setSkillType(1); // 主动技能
            s2.setTargetType(1); // 单体目标
            s2.setRange(500); // 逍遥神剑施法距离 500
            s2.setShape(2); // 扇形
            s2.setShapeParams("{\"radius\":300,\"angle\":90}"); // 扇形半径300角度90 JSON
            skillConfigRepository.save(s2); // 持久化 dev 技能静态配置
            SkillConfig s3 = new SkillConfig(); // dev 种子：技能3 泰山压顶 Lv30 圆形
            s3.setName("泰山压顶"); // dev 技能3：土系 Lv30 法术
            s3.setEffect("土系高级法术"); // 土系法术效果描述
            s3.setNeedLevel(30); // 30 级可学
            s3.setCooldown(45); // 冷却 45 秒
            s3.setManaCost(80); // 消耗 80 法力
            s3.setCastTime(new BigDecimal("3.00")); // 施法前摇 3 秒
            s3.setSkillType(1); // 主动技能
            s3.setTargetType(1); // 单体目标
            s3.setRange(400); // 泰山压顶施法距离 400
            s3.setShape(1); // 圆形范围
            s3.setShapeParams("{\"radius\":250}"); // 圆形半径 250 JSON
            skillConfigRepository.save(s3); // 持久化 dev 技能静态配置
            log.info("已写入示例技能 skill_config（与接口文档示例一致）"); // dev 种子：三技能静态配置写入完成
        } // 编译单元结束
        // --- Buff：BuffFacade 与 battle buff 系统 ---
        if (buffConfigRepository.count() == 0) { // buff_config 表空才写入三示例 Buff
            BuffConfig b1 = new BuffConfig(); // dev 种子：Buff 力量祝福 +100 攻击
            b1.setName("力量祝福"); // dev Buff1：+100 攻击 300s
            b1.setDuration(300_000); // 毫秒
            b1.setPeriodicInterval(null); // 非周期 buff
            b1.setStackLimit(5); // 最多叠 5 层
            b1.setEffectType(1); // 属性加成
            b1.setEffectParams("{\"attack\":100}"); // +100 攻击力 JSON
            b1.setDescription("提升 100 点攻击力"); // Buff 描述文案
            buffConfigRepository.save(b1); // 持久化 dev Buff 静态配置
            BuffConfig b2 = new BuffConfig(); // dev 种子：Buff 中毒周期伤害
            b2.setName("中毒"); // dev Buff2：周期伤害 10s
            b2.setDuration(10_000); // 持续 10 秒
            b2.setPeriodicInterval(2000); // 每 2s tick
            b2.setStackLimit(5); // 最多叠 5 层
            b2.setEffectType(2); // 周期伤害
            b2.setEffectParams("{\"damage_per_tick\":50}"); // 每 tick 50 伤害 JSON
            b2.setDescription("每2秒受到50点伤害"); // 中毒描述文案
            buffConfigRepository.save(b2); // 持久化 dev Buff 静态配置
            BuffConfig b3 = new BuffConfig(); // dev 种子：Buff 急如风攻速加成
            b3.setName("急如风"); // dev Buff3：攻速 +30% 15s
            b3.setDuration(15_000); // 持续 15 秒
            b3.setPeriodicInterval(null); // 非周期 buff，无 tick
            b3.setStackLimit(1); // 急如风不可叠加
            b3.setEffectType(1); // 属性加成类 buff
            b3.setEffectParams("{\"attack_speed\":30}"); // 攻速 +30% JSON
            b3.setDescription("攻击速度提升 30%"); // 急如风描述文案
            buffConfigRepository.save(b3); // 持久化 dev Buff 静态配置
            log.info("已写入示例 buff_config（与 Buff 接口文档 BuffInfo 示例一致）"); // dev 种子：三 Buff 模板写入完成
        } // 编译单元结束
        // --- 玩家技能：首个角色预置 skill 1、2 ---
        if (playerSkillRepository.count() == 0 && playerRepository.count() > 0) { // 有角色且无 player_skill 时预置技能
            Player first = playerRepository.findAll().iterator().next(); // 取第一个角色（星穹列车员）
            long pid = first.getId(); // 首个 dev 角色 playerId，写入 player_skill 外键
            Instant now = Instant.now(); // player_skill.learn_time UTC 时间戳
            for (int sid : new int[]{1, 2}) { // 为首个角色预置 skill 1、2
                if (skillConfigRepository.existsById(sid)) { // 确保 skill_config 已有对应 id
                    PlayerSkill ps = new PlayerSkill(); // dev 种子：首个角色预置已学技能行
                    ps.setId(new PlayerSkillId(pid, sid)); // 复合主键
                    ps.setLearnTime(now); // 学习时间 UTC 当前时刻
                    playerSkillRepository.save(ps); // 持久化首个角色预置技能
                } // 编译单元结束
            } // 编译单元结束
            log.info("已为首个角色预置 player_skill（技能1、2）"); // dev 种子：星穹列车员已学技能 1、2
        } // 编译单元结束
        // --- 道具配置：背包使用、活动奖励引用 item_config ---
        if (itemConfigRepository.count() == 0) { // item_config 表空才写入经验药水与铁剑
            ItemConfig expPot = new ItemConfig(); // dev 种子：道具经验药水 +1000 exp
            expPot.setName("经验药水"); // dev 道具1：消耗品 +1000 exp
            expPot.setKind(1); // 消耗品
            expPot.setStackLimit(99); // 堆叠上限 99
            expPot.setLevelRequired(1); // 1 级可用
            expPot.setDescription("使用后获得1000点经验"); // 经验药水描述
            expPot.setPrice(10); // 商店价 10
            expPot.setSellPrice(1); // 出售价 1
            expPot.setEffectParams("{\"exp\":1000}"); // ItemPolicy.parseExpReward 解析
            itemConfigRepository.save(expPot); // 持久化 dev 道具模板
            ItemConfig sword = new ItemConfig(); // dev 种子：道具铁剑 Lv10 装备
            sword.setName("铁剑"); // dev 道具2：装备 Lv10
            sword.setKind(2); // 装备
            sword.setStackLimit(1); // 装备不可堆叠
            sword.setLevelRequired(10); // 10 级可装备
            sword.setDescription("一把普通的铁剑"); // 铁剑描述
            sword.setPrice(100); // 商店价 100
            sword.setSellPrice(50); // 出售价 50
            sword.setEffectParams("slot=1;atk=15;def=2"); // 武器槽1，攻击+15 防御+2
            itemConfigRepository.save(sword); // 持久化 dev 道具模板
            log.info("已写入示例 item_config（经验药水、铁剑，与接口文档示例一致）"); // dev 种子：两道具模板写入完成
        } // 编译单元结束
        // --- 皮肤解锁卡：固定 ID 对齐 SkinConfigs.json / 商城 520001 ---
        ensureSkinUnlockItem(71002, "旅人披风解锁卡", 1002);
        ensureSkinUnlockItem(71003, "星穹礼服解锁卡", 1003);
        // --- 背包：首个角色槽位 0/1 放药水与铁剑 ---
        if (playerBagItemRepository.count() == 0 && playerRepository.count() > 0) { // 有角色且无背包行时预置槽位
            Player first = playerRepository.findAll().iterator().next(); // 取首个 dev 角色（星穹列车员）
            long pid = first.getId(); // 首个 dev 角色 playerId，写入 player_skill 外键
            ItemConfig expCfg = itemConfigRepository.findAll().stream() // 查 item_config 取奖励道具 id
                    .filter(c -> c.getKind() != null && c.getKind() == 1) // kind=1 消耗品
                    .findFirst() // DevDataLoader 逻辑
                    .orElse(null); // DevDataLoader 逻辑
            ItemConfig swordCfg = itemConfigRepository.findAll().stream() // 查 item_config 取奖励道具 id
                    .filter(c -> c.getKind() != null && c.getKind() == 2) // kind=2 装备
                    .findFirst() // DevDataLoader 逻辑
                    .orElse(null); // DevDataLoader 逻辑
            if (expCfg != null) { // 经验药水 item_config 存在才写槽 0
                PlayerBagItem b1 = new PlayerBagItem(); // dev 种子：槽0 放 5 瓶经验药水
                b1.setPlayerId(pid); // 背包行绑定首个 dev 角色 id
                b1.setItemConfigId(expCfg.getId()); // 背包槽引用 item_config id
                b1.setCount(5); // 槽位数量 5
                b1.setBind(0); // 可交易
                b1.setSlotIndex(0); // 背包槽位 0
                playerBagItemRepository.save(b1); // 持久化首个角色示例背包
            } // 编译单元结束
            if (swordCfg != null) { // 铁剑 item_config 存在才写槽 1
                PlayerBagItem b2 = new PlayerBagItem(); // dev 种子：槽1 放 1 把绑定铁剑
                b2.setPlayerId(pid); // 背包行绑定首个 dev 角色 id
                b2.setItemConfigId(swordCfg.getId()); // 背包槽引用 item_config id
                b2.setCount(1); // 数量 1
                b2.setBind(1); // 绑定
                b2.setSlotIndex(1); // 背包槽位 1
                playerBagItemRepository.save(b2); // 持久化首个角色示例背包
            } // 编译单元结束
            if (expCfg != null || swordCfg != null) { // 至少写入一件背包 dev 数据才打日志
                log.info("已为首个角色预置 player_bag_item（示例背包）"); // dev 种子：槽 0 药水 + 槽 1 铁剑
            } // 编译单元结束
        } // 编译单元结束
        // --- 活动：首充与签到，data JSON 引用 item_config 第一条 ---
        if (activityRepository.count() == 0 && itemConfigRepository.count() > 0) { // 有道具模板且无活动时写入首充/签到
            int itemId = itemConfigRepository.findAll().iterator().next().getId(); // 奖励道具 id
            long now = System.currentTimeMillis(); // 活动起止时间毫秒戳
            long start = now - 86_400_000L * 2; // 2 天前开始
            long end = now + 86_400_000L * 30; // 30 天后结束
            String json1 = String.format( // 组装活动 rewardTiers JSON 字符串
                    "{\"startTime\":%d,\"endTime\":%d,\"name\":\"首充大礼包\",\"briefDesc\":\"首次充值即送限定皮肤\"," // DevDataLoader 逻辑
                            + "\"rewardTiers\":[" // DevDataLoader 逻辑
                            + "{\"index\":1,\"itemId\":%d,\"count\":10,\"targetRecharge\":60}," // DevDataLoader 逻辑
                            + "{\"index\":2,\"itemId\":%d,\"count\":5,\"targetRecharge\":0}" // DevDataLoader 逻辑
                            + "]}", // DevDataLoader 逻辑
                    start, end, itemId, itemId); // DevDataLoader 逻辑
            Activity firstRecharge = new Activity(); // dev 种子：首充大礼包活动 JSON
            firstRecharge.setType(1); // 首充类型
            firstRecharge.setOpened(true); // 活动开关开启
            firstRecharge.setData(json1); // 活动档位 JSON 写入 data 字段
            activityRepository.save(firstRecharge); // 持久化首充/签到活动
            String json2 = String.format( // 组装活动 rewardTiers JSON 字符串
                    "{\"startTime\":%d,\"endTime\":%d,\"name\":\"夏日签到\",\"briefDesc\":\"每日签到领好礼\"," // DevDataLoader 逻辑
                            + "\"rewardTiers\":[{\"index\":1,\"itemId\":%d,\"count\":3,\"signDay\":1}]}", // DevDataLoader 逻辑
                    start, end, itemId); // DevDataLoader 逻辑
            Activity signIn = new Activity(); // dev 种子：夏日签到活动 JSON
            signIn.setType(2); // 签到类型
            signIn.setOpened(true); // 活动开关开启
            signIn.setData(json2); // 活动档位 JSON 写入 data 字段
            activityRepository.save(signIn); // 持久化首充/签到活动
            log.info("已写入示例 activity（首充、签到），档位道具取自 item_config"); // 记录 dev 种子/路由注册/Netty 启停日志
        } // 编译单元结束
    } // 编译单元结束

    /** 写入固定 ID 的皮肤解锁卡（对齐 SkinConfig.itemId）。 */
    private void ensureSkinUnlockItem(int itemId, String name, int skinId) {
        if (itemConfigRepository.existsById(itemId)) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO item_config (id, name, kind, stack_limit, level_required, description, price, sell_price, effect_params) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                itemId, name, 3, 1, 1, "使用后解锁皮肤", 0, 0, "{\"skinId\":" + skinId + "}");
        log.info("已写入皮肤解锁卡 itemId={} skinId={}", itemId, skinId);
    }
} // 编译单元结束
