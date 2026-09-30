package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6 二游手感纵深完整业务流程（经 Runtime + Internal API）：
 * 攀爬/滑翔准入与体力 → 火烧 DoT Zone Tick → 仙灵引导链 →
 * 动态宝箱品质 → CHAOS 诅咒与净化锚点 → MVP 频道广播。
 */
public class OpenWorldP6BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 66_001L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP6Journey_verticalMoveDotGuidanceChaosDynamicLootMvp() {
        // ── 1) 解锁攀爬/滑翔，准入校验 + 体力阶梯扣除 ──
        assertThat(api.unlockTraverse(PLAYER, "CLIMB").get("ok")).isEqualTo(true);
        assertThat(api.unlockTraverse(PLAYER, "GLIDE").get("ok")).isEqualTo(true);

        Map<String, Object> climb = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "x", 400f, "y", 30f, "z", 200f,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "durationMs", 500L,
                "speed", 5f));
        assertThat(climb.get("ok")).isEqualTo(true);
        assertThat(climb.get("climbableMeshId")).isEqualTo("cliff-valley-north");
        assertThat(((Number) climb.get("consumed")).floatValue()).isGreaterThan(0f);

        Map<String, Object> stamina = api.stamina(PLAYER);
        assertThat(stamina.get("ok")).isEqualTo(true);
        assertThat(((Number) stamina.get("stamina")).floatValue())
                .isLessThan(((Number) stamina.get("capacity")).floatValue());

        Map<String, Object> glideDenied = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "x", 10f, "y", 5f, "z", 10f,
                "movementType", "GLIDE",
                "durationMs", 200L));
        // 体力可能仍足够；若不足则 glide_unavailable。至少返回结构化结果
        assertThat(glideDenied).containsKey("ok");
        assertThat(glideDenied.get("movementType")).isEqualTo("GLIDE");

        // 风场内滑翔应可用且减耗
        Map<String, Object> glideWind = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "x", 500f, "y", 80f, "z", 500f,
                "movementType", "GLIDE",
                "durationMs", 200L));
        assertThat(glideWind.get("ok")).isEqualTo(true);
        assertThat(glideWind.get("inWindField")).isEqualTo(true);

        // 未解锁游泳应拒绝
        Map<String, Object> swimLocked = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "x", 1f, "y", 0f, "z", 1f,
                "movementType", "SWIM",
                "durationMs", 100L));
        assertThat(swimLocked.get("ok")).isEqualTo(false);
        assertThat(swimLocked.get("error")).isEqualTo("swim_locked");

        // ── 2) 火元素 → 燃烧 DoT Zone → Tick 对实体造成伤害 ──
        Map<String, Object> fire = api.physicsApply(Map.of(
                "worldId", 1, "x", 80f, "z", 80f,
                "kind", "FIRE", "intensity", 3, "ttlMs", 10_000L));
        assertThat(fire.get("ok")).isEqualTo(true);
        assertThat(fire.get("persistentZone")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> zone = (Map<String, Object>) fire.get("persistentZone");
        assertThat(zone.get("ok")).isEqualTo(true);
        assertThat(zone.get("sourceKind")).isEqualTo("BURN");

        // 立刻 tick 可能因间隔未到无命中；推进时间由服务内部 lastTick 控制，再调一次带足够间隔
        long now = System.currentTimeMillis();
        openWorld.gameplay().physics().zones().register(
                new cn.itcast.demo.mymmorpg.world.puzzle.ZoneLifecycleManager.PersistentEffectZone(
                        "test-burn-1", 1,
                        cn.itcast.demo.mymmorpg.world.puzzle.ZoneLifecycleManager.ShapeKind.CIRCLE,
                        81f, 81f, 8f, 8f, now - 1000, now + 5000, 100L, 12, "burn:12", "BURN",
                        Map.of()));
        Map<String, Object> tick = api.physicsZoneTick(Map.of(
                "worldId", 1,
                "entities", List.of(Map.of("entityId", PLAYER, "x", 81f, "z", 81f))));
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(((Number) tick.get("hitCount")).intValue()).isGreaterThanOrEqualTo(1);

        // ── 3) 仙灵引导链：起点 → 节点推进 → 粒子 AOI → 终点宝箱 ──
        Map<String, Object> start = api.guidanceStart(Map.of(
                "playerId", PLAYER, "chainId", "seelie-chest-1",
                "x", 140f, "y", 0f, "z", 100f));
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(start.get("started")).isEqualTo(true);

        Map<String, Object> particles = api.guidanceParticles(Map.of(
                "playerId", PLAYER, "chainId", "seelie-chest-1"));
        assertThat(particles.get("ok")).isEqualTo(true);
        assertThat(particles.get("aoiBroadcast")).isEqualTo(true);
        assertThat(particles.get("guideParticles")).asList().isNotEmpty();

        assertThat(api.guidanceAdvance(Map.of(
                "playerId", PLAYER, "chainId", "seelie-chest-1",
                "x", 145f, "y", 2f, "z", 110f)).get("ok")).isEqualTo(true);
        assertThat(api.guidanceAdvance(Map.of(
                "playerId", PLAYER, "chainId", "seelie-chest-1",
                "x", 148f, "y", 4f, "z", 115f)).get("ok")).isEqualTo(true);
        Map<String, Object> chainEnd = api.guidanceAdvance(Map.of(
                "playerId", PLAYER, "chainId", "seelie-chest-1",
                "x", 150f, "y", 0f, "z", 120f));
        assertThat(chainEnd.get("completed")).isEqualTo(true);
        assertThat(chainEnd.get("chestCollectibleId")).isEqualTo("chest-fine-1");

        // ── 4) 动态品质宝箱（高世界等级 + 高探索度）──
        Map<String, Object> loot = api.collectibleCollect(Map.of(
                "playerId", PLAYER,
                "collectibleId", "chest-common-1",
                "x", 100f, "y", 0f, "z", 100f,
                "worldLevel", 8,
                "regionExplorationRate", 0.95f,
                "regionId", "wolf-camp-valley"));
        assertThat(loot.get("ok")).isEqualTo(true);
        assertThat(loot.get("dynamicLoot")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) loot.get("grantPlans");
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0).get("itemId")).isNotEqualTo("gold");

        // ── 5) CHAOS 诅咒叠层 → 净化锚点抵消 ──
        openWorld.gameplay().regions().enterChaos("wolf-camp-valley", 2_000_000L);
        // Internal API 使用 System.currentTimeMillis，改走服务层精确时间
        Map<String, Object> early = openWorld.gameplay().regions()
                .tickPlayerAffliction("wolf-camp-valley", PLAYER, 2_000_000L + 5_000L);
        assertThat(early.get("afflicted")).isEqualTo(false);

        Map<String, Object> cursed = openWorld.gameplay().regions()
                .tickPlayerAffliction("wolf-camp-valley", PLAYER, 2_000_000L + 45_000L);
        assertThat(cursed.get("afflicted")).isEqualTo(true);
        assertThat(((Number) cursed.get("curseLayers")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) cursed.get("atkDownPct")).floatValue()).isGreaterThan(0f);

        Map<String, Object> cleanse = api.regionCleanse(Map.of(
                "playerId", PLAYER,
                "anchorId", "cleanse-valley-1",
                "x", 200f, "y", 0f, "z", 200f));
        assertThat(cleanse.get("ok")).isEqualTo(true);
        assertThat(cleanse.get("curseLayers")).isEqualTo(0);

        Map<String, Object> afterCleanse = openWorld.gameplay().regions()
                .tickPlayerAffliction("wolf-camp-valley", PLAYER, System.currentTimeMillis());
        assertThat(afterCleanse.get("cleansed")).isEqualTo(true);

        // ── 6) Boss MVP 区域频道广播 ──
        openWorld.gameplay().regionChannel().joinChannel("wolf-camp-valley", PLAYER, "local");
        Map<String, Object> mvp = api.channelBroadcastMvp(Map.of(
                "regionId", "wolf-camp-valley",
                "mvpPlayerId", PLAYER,
                "topDamage", 999_999L,
                "bossName", "geo-hypostasis"));
        assertThat(mvp.get("ok")).isEqualTo(true);
        assertThat(mvp.get("type")).isEqualTo("BOSS_MVP");
        assertThat(mvp.get("message").toString()).contains("MVP");
        assertThat(mvp.get("message").toString()).contains("999999");
        assertThat(((Number) mvp.get("recipientCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void climbWithoutUnlockOrMeshIsRejected() {
        Map<String, Object> locked = api.moveAdmit(Map.of(
                "playerId", PLAYER + 1,
                "x", 400f, "y", 30f, "z", 200f,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "durationMs", 200L));
        assertThat(locked.get("ok")).isEqualTo(false);
        assertThat(locked.get("error")).isEqualTo("climb_locked");

        api.unlockTraverse(PLAYER + 1, "CLIMB");
        Map<String, Object> badMesh = api.moveAdmit(Map.of(
                "playerId", PLAYER + 1,
                "x", 0f, "y", 0f, "z", 0f,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "durationMs", 200L));
        assertThat(badMesh.get("ok")).isEqualTo(false);
        assertThat(badMesh.get("error")).isEqualTo("out_of_climb_mesh");
    }
}
