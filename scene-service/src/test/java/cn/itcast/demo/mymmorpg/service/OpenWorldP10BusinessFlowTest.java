package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.economy.AuctionHouseService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoCarryingCapacity;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.narrative.RegionTugOfWarService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbAttackService;
import cn.itcast.demo.mymmorpg.world.traverse.VehicleCombatService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10 完整业务流程（经 Runtime + Internal API）：
 * 生态亲密度 → 钩锁/载具/攀爬毁崖 → 地貌元素 → 纪元拉锯过场 →
 * 攻城与不对称竞技 → 制造交易所 → 命中预表现 → 集群/环境 AI。
 */
public class OpenWorldP10BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 100_001L;
    private static final long FRIEND = 100_002L;
    private static final long ENEMY = 100_099L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP10Journey_ecoTraverseTerrainEpochEndgameEconomyFeelAi() {
        // ── 1) 生态：惧怕 → Tick 睡眠 → 喂食亲密度 → 寻宝/预警 ──
        Map<String, Object> prox = api.ecosystemProximity(body(
                "creatureUid", "eco-fox-1",
                "playerDistM", 4f,
                "inCombat", false));
        assertThat(prox.get("ok")).isEqualTo(true);
        assertThat(prox.get("state")).isEqualTo(EcosystemBehaviorService.EcoState.FLEE.name());

        Map<String, Object> ecoTick = api.ecosystemTick(body(
                "creatureUid", "eco-fox-1",
                "hourOfDay", 22,
                "inCombat", false));
        assertThat(ecoTick.get("ok")).isEqualTo(true);
        assertThat(ecoTick.get("behaviorTree")).isEqualTo("behavior_tree_eco.xml");

        Map<String, Object> combatSkip = api.ecosystemTick(body(
                "creatureUid", "eco-boar-1",
                "hourOfDay", 12,
                "inCombat", true));
        assertThat(combatSkip.get("skipped")).isEqualTo(true);

        for (int i = 0; i < 15; i++) {
            Map<String, Object> feed = api.affinityFeed(body(
                    "playerId", PLAYER,
                    "creatureUid", "pet-fox",
                    "itemId", AffinityService.FOOD_ITEM,
                    "amount", 1));
            assertThat(feed.get("ok")).isEqualTo(true);
        }
        assertThat(openWorld.gameplay().affinity().get(PLAYER, "pet-fox"))
                .isGreaterThanOrEqualTo(AffinityService.THRESHOLD_TREASURE);
        assertThat(openWorld.gameplay().collectibles().visibilityOf("chest-hidden-1"))
                .isEqualTo(CollectibleService.Visibility.HIDDEN);

        Map<String, Object> hint = api.affinityTreasure(body(
                "playerId", PLAYER,
                "creatureUid", "pet-fox",
                "x", 100f, "z", 100f,
                "intervalMs", 1L));
        assertThat(hint.get("hinted")).isEqualTo(true);
        assertThat(hint.get("assist")).isEqualTo("TREASURE_SENSE");

        for (int i = 0; i < 20; i++) {
            api.affinityFeed(body(
                    "playerId", PLAYER,
                    "creatureUid", "pet-fox",
                    "itemId", AffinityService.FOOD_ITEM,
                    "amount", 1));
        }
        Map<String, Object> alert = api.affinityAlert(body(
                "playerId", PLAYER,
                "creatureUid", "pet-fox",
                "eliteId", "rare-elite-p10",
                "gridCell", "3_4"));
        assertThat(alert.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) alert.get("notify")).get("msgId"))
                .isEqualTo(MessageId.CREATURE_ALERT_SC_NOTIFY);

        for (int i = 0; i < EcoCarryingCapacity.OVERKILL_THRESHOLD; i++) {
            openWorld.gameplay().placement().carrying().recordKill("wolf-camp-valley", "boar");
        }
        Map<String, Object> place = api.placementFill(body(
                "regionId", "wolf-camp-valley",
                "targetCount", 40,
                "minDensityPerSqKm", 1,
                "areaSqKm", 2f));
        assertThat(place.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> ecoCap = (Map<String, Object>) place.get("ecoCapacity");
        // placementFill 默认 species=wildlife，单独校验 boar 承载力
        assertThat(openWorld.gameplay().placement().carrying()
                .spawnRateMultiplier("wolf-camp-valley", "boar")).isLessThan(1.0);
        assertThat(openWorld.gameplay().placement().carrying()
                .cropYieldMultiplier("wolf-camp-valley")).isEqualTo(0.5);
        assertThat(ecoCap).isNotNull();

        // ── 2) 钩锁拉拽 / 摆荡踢 → 载具战斗弹射 → 攀爬攻击 → 毁崖新路径 ──
        Map<String, Object> pullLight = api.grapplePullEnemy(body(
                "playerId", PLAYER,
                "enemyId", ENEMY,
                "mass", "LIGHT",
                "attackPower", 40));
        assertThat(pullLight.get("outcome")).isEqualTo("ENEMY_PULLED_TO_PLAYER");
        assertThat(pullLight.get("msgId")).isEqualTo(MessageId.GRAPPLE_PULL_ENEMY_CS_REQ);

        Map<String, Object> pullHeavy = api.grapplePullEnemy(body(
                "playerId", PLAYER,
                "enemyId", ENEMY + 1,
                "mass", "HEAVY",
                "attackPower", 40));
        assertThat(pullHeavy.get("outcome")).isEqualTo("PLAYER_PULLED_TO_ENEMY");
        assertThat(((Number) pullHeavy.get("collisionDamage")).intValue()).isGreaterThan(0);

        Map<String, Object> kick = api.grappleSwingKick(body(
                "playerId", PLAYER,
                "speed", 24d,
                "baseDamage", 100,
                "atApex", true));
        assertThat(kick.get("ok")).isEqualTo(true);
        assertThat(((Number) kick.get("damage")).intValue()).isEqualTo(200);

        Map<String, Object> vCast = api.vehicleCastSkill(body(
                "playerId", PLAYER,
                "vehicleId", "cart-coast-1",
                "skillId", "vehicle_ram"));
        assertThat(vCast.get("ok")).isEqualTo(true);
        assertThat(vCast.get("msgId")).isEqualTo(MessageId.VEHICLE_CAST_SKILL_CS_REQ);

        Map<String, Object> eject = api.vehiclePoiseHit(body(
                "playerId", PLAYER,
                "vehicleId", "cart-coast-1",
                "damage", 200f,
                "horizontalSpeed", 18f));
        assertThat(eject.get("event")).isEqualTo(VehicleCombatService.EVENT_EJECT_GLIDE);
        assertThat(eject.get("inheritedHorizontalSpeed")).isEqualTo(18f);

        Map<String, Object> climbAtk = api.climbAttack(body(
                "playerId", PLAYER,
                "baseDamage", 50,
                "targetSuperArmor", false));
        assertThat(climbAtk.get("ok")).isEqualTo(true);
        assertThat(climbAtk.get("extraStaminaCost")).isEqualTo(ClimbAttackService.EXTRA_STAMINA);
        assertThat(climbAtk.get("interruptEnemy")).isEqualTo(true);

        Map<String, Object> cliff = api.mutabilityDamage(body(
                "id", "cliff-wall-1",
                "damageType", "HEAVY_ATTACK",
                "damage", 200,
                "radiusM", 300f));
        assertThat(cliff.get("mutated")).isEqualTo(true);
        assertThat(cliff.get("mutationType")).isEqualTo("DESTROY_CLIFF");
        assertThat(cliff.get("newClimbPath")).isEqualTo(true);

        // ── 3) 地貌：感电水池 → 冰封 → 风系偏转弹道 + 元素残留 ──
        Map<String, Object> overload = api.terrainOverload(body(
                "regionId", "wolf-camp-valley",
                "gx", 10, "gz", 10));
        assertThat(overload.get("zone")).isEqualTo(TerrainMutationService.ELECTRO_CHARGED_ZONE);
        assertThat(overload.get("chainRadiusM")).isEqualTo(5);

        openWorld.gameplay().terrainMutation().markWater("wolf-camp-valley", 10, 10);
        openWorld.gameplay().terrainMutation().markWater("wolf-camp-valley", 10, 11);
        Map<String, Object> freeze = api.terrainFreeze(body(
                "regionId", "wolf-camp-valley",
                "gx", 10, "gz", 10));
        assertThat(freeze.get("zone")).isEqualTo(TerrainMutationService.ICE_TERRAIN);
        assertThat(freeze.get("walkSpeedMul")).isEqualTo(0.7);
        assertThat(freeze.get("canBearHeavyWeight")).isEqualTo(true);

        Map<String, Object> swirl = api.projectileSwirl(body(
                "projectileId", "arrow-p10",
                "element", "PYRO",
                "x", 0f, "z", 0f,
                "radiusM", 8f));
        assertThat(swirl.get("reaction")).isEqualTo("SWIRL");
        assertThat(((Number) swirl.get("deflectDeg")).intValue()).isBetween(60, 120);
        Map<String, Object> remnant = openWorld.gameplay().projectileCurve()
                .gaugeRemnant(ENEMY, "PYRO", 0.4, 1, 0, 1);
        assertThat(((Map<?, ?>) remnant.get("aoiBroadcast")).get("event")).isEqualTo("GAUGE_REMNANT");

        // ── 4) 纪元投票 → 拉锯周结算 → 动态过场 ──
        for (int i = 0; i < 100; i++) {
            api.epochBump(body("flag", "slay_dragon", "delta", 1));
        }
        Map<String, Object> epoch = api.epochAdvance(body(
                "flag", "slay_dragon",
                "threshold", 100,
                "worldStatePatch", Map.of("statue", "dragon_slayer", "weather", "THUNDERSTORM")));
        assertThat(epoch.get("ok")).isEqualTo(true);
        assertThat(epoch.get("epoch_version")).isEqualTo(1);
        assertThat(epoch.get("syncAllLines")).isEqualTo(true);

        api.factionDonate(body(
                "playerId", PLAYER,
                "regionId", "wolf-camp-valley",
                "faction", RegionTugOfWarService.FACTION_KINGDOM,
                "amount", 80));
        api.factionDonate(body(
                "playerId", FRIEND,
                "regionId", "wolf-camp-valley",
                "faction", RegionTugOfWarService.FACTION_ABYSS,
                "amount", 20));
        Map<String, Object> war = api.factionSettle("wolf-camp-valley");
        assertThat(war.get("winner")).isEqualTo(RegionTugOfWarService.FACTION_KINGDOM);
        assertThat(((Map<?, ?>) war.get("winnerBuff")).get("buff"))
                .isEqualTo(RegionTugOfWarService.FACTION_BUFF);

        for (int i = 0; i < 3; i++) {
            openWorld.gameplay().cutsceneTrigger().recordChoice("A");
        }
        openWorld.gameplay().cutsceneTrigger().recordChoice("B");
        Map<String, Object> cut = api.cutsceneEvaluate(body(
                "choiceA", "A",
                "choiceB", "B",
                "cutsceneId", "epoch-dragon"));
        assertThat(cut.get("triggered")).isEqualTo(true);
        assertThat(((Map<?, ?>) cut.get("broadcast")).get("msgId"))
                .isEqualTo(MessageId.FORCED_CUTSCENE_START_SC_NOTIFY);

        // ── 5) 攻城部位破坏破盾 → 躲猫猫扫描 → 竞速排名 ──
        Map<String, Object> siege = api.siegeCreate(body("roomId", "siege-p10", "leaderId", PLAYER));
        assertThat(siege.get("maxPlayers")).isEqualTo(SiegeWarService.MAX_PLAYERS);
        openWorld.gameplay().siegeWar().join("siege-p10", FRIEND);
        api.siegeBreakPart(body("roomId", "siege-p10", "part", "LEFT_LEG"));
        api.siegeBreakPart(body("roomId", "siege-p10", "part", "CORE"));
        Map<String, Object> shield = openWorld.gameplay().siegeWar().breakInvincibleShield(
                "siege-p10", List.of("PYRO", "HYDRO", "CRYO", "ELECTRO"));
        assertThat(shield.get("invincibleBroken")).isEqualTo(true);
        Map<String, Object> settle = openWorld.gameplay().siegeWar().settle("siege-p10", 500);
        assertThat(settle.get("ok")).isEqualTo(true);
        assertThat(settle.get("grantPlans")).isNotNull();

        Map<String, Object> hide = api.hideSeekStart(body(
                "matchId", "hs-p10",
                "hiders", List.of(PLAYER),
                "seekers", List.of(FRIEND)));
        assertThat(hide.get("gameMode")).isEqualTo("HIDE_SEEK");
        Map<String, Object> scan = api.asymmetricScanner(body(
                "seekerId", FRIEND,
                "x", 0f, "z", 0f,
                "radiusM", 20f));
        assertThat(scan.get("skill")).isEqualTo("SCANNER_SKILL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) scan.get("hits");
        assertThat(hits).isNotEmpty();

        openWorld.gameplay().asymmetricPlay().startRace("race-p10", List.of("cp1", "finish"));
        openWorld.gameplay().asymmetricPlay().passCheckpoint("race-p10", PLAYER, "cp1", 1000L);
        Map<String, Object> finish = openWorld.gameplay().asymmetricPlay()
                .passCheckpoint("race-p10", PLAYER, "finish", 2500L);
        assertThat(finish.get("finished")).isEqualTo(true);
        assertThat(openWorld.gameplay().asymmetricPlay().raceRanking("race-p10").get("ok"))
                .isEqualTo(true);

        // ── 6) 制造异步邮件 → 交易所冻结成交 ──
        Map<String, Object> craft = api.craftStart(body(
                "playerId", PLAYER,
                "recipeId", "iron_ingot"));
        assertThat(craft.get("async")).isEqualTo(true);
        String jobId = String.valueOf(craft.get("jobId"));
        Map<String, Object> crafted = api.craftComplete(jobId);
        // 若刚启动未就绪，推进时间
        if (!Boolean.TRUE.equals(crafted.get("ok"))) {
            crafted = openWorld.gameplay().crafting().completeIfReady(jobId, System.currentTimeMillis() + 60_000L);
        }
        assertThat(crafted.get("ok")).isEqualTo(true);
        assertThat(crafted.get("via")).isEqualTo("MailService");
        assertThat(openWorld.gameplay().crafting().claimMail(PLAYER)).isNotEmpty();

        Map<String, Object> list = api.auctionList(body(
                "sellerId", PLAYER,
                "itemId", "iron_ingot",
                "count", 2,
                "price", 200L));
        assertThat(list.get("tradeServicePort")).isEqualTo(AuctionHouseService.PORT);
        assertThat(openWorld.gameplay().auctionHouse().frozenCount(PLAYER, "iron_ingot")).isEqualTo(2);
        Map<String, Object> buy = api.auctionBuyout(body(
                "buyerId", FRIEND,
                "listingId", list.get("listingId")));
        assertThat(buy.get("ok")).isEqualTo(true);
        assertThat(buy.get("fee")).isEqualTo(Math.round(200 * AuctionHouseService.FEE_RATE));

        // ── 7) 命中反馈 → 预表现确认 / 回滚 ──
        Map<String, Object> feedback = api.hitFeedback(body(
                "attackerId", PLAYER,
                "targetId", ENEMY,
                "poiseDamage", 50d,
                "heavyOrFall", true,
                "poiseRemainRatio", 0.1f));
        assertThat(feedback.get("msgId")).isEqualTo(MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        assertThat(((Number) feedback.get("hitStopDurationMs")).intValue()).isBetween(50, 150);
        assertThat(feedback.get("pause_gameplay_tick")).isEqualTo(true);

        long t0 = System.currentTimeMillis();
        Map<String, Object> pre = api.prePlaybackStart(body(
                "playerId", PLAYER,
                "actionId", "atk-p10-1"));
        assertThat(pre.get("event")).isEqualTo("PRE_IMPACT");
        Map<String, Object> confirm = openWorld.gameplay().prePlayback()
                .confirmOrRollback(PLAYER, "atk-p10-1", t0, t0 + 30, true);
        assertThat(confirm.get("event")).isEqualTo("IMPACT_CONFIRM");

        openWorld.gameplay().prePlayback().onActionStart(PLAYER, "atk-p10-2", t0);
        Map<String, Object> rollback = openWorld.gameplay().prePlayback()
                .confirmOrRollback(PLAYER, "atk-p10-2", t0, t0 + 80, true);
        assertThat(rollback.get("event")).isEqualTo("ROLLBACK");

        // ── 8) 集群盾墙 → 队长死亡狂暴 → 环境投掷独占 ──
        Map<String, Object> cmd = api.squadCommand(body(
                "squadId", "squad-archer-1",
                "command", SquadCommanderService.CMD_SHIELD_WALL));
        assertThat(cmd.get("blockRateBonus")).isEqualTo(0.6);
        assertThat(openWorld.gameplay().squadCommander().overrideOf(9002L))
                .isEqualTo(SquadCommanderService.CMD_SHIELD_WALL);

        Map<String, Object> berserk = openWorld.gameplay().squadCommander()
                .onLeaderDeath("squad-archer-1");
        assertThat(berserk.get("leaderDead")).isEqualTo(true);
        assertThat(openWorld.gameplay().squadCommander().isBerserk(9002L)).isTrue();

        Map<String, Object> pick = api.envAiPickup(body(
                "aiEntityId", 55L,
                "objectId", "boulder-1",
                "x", 200f, "z", 200f));
        assertThat(pick.get("ok")).isEqualTo(true);
        assertThat(api.envAiPickup(body(
                "aiEntityId", 56L,
                "objectId", "boulder-1",
                "x", 200f, "z", 200f)).get("error")).isEqualTo("already_held");
        Map<String, Object> thr = openWorld.gameplay().envUtilAi().throwAt(55L, "boulder-1", ENEMY, 1000);
        assertThat(thr.get("damage")).isEqualTo(200);
        assertThat(thr.get("knockback")).isEqualTo(true);

        // 门面总览含纪元与生态
        Map<String, Object> status = openWorld.gameplay().statusOverview();
        assertThat(((Number) status.get("epoch")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) status.get("ecoCreatures")).intValue()).isGreaterThanOrEqualTo(2);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
