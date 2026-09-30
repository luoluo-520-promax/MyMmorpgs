package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.gm.GmCommandDispatcher;
import cn.itcast.demo.mymmorpg.service.OpenWorldRuntimeService;
import cn.itcast.demo.mymmorpg.world.lock.PartyEntityOwnership;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.migrate.GatewayRoutingTable;
import cn.itcast.demo.mymmorpg.world.portal.PortalConfig;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.state.HostWorldContext;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大世界核心运维/运行时 API：资源、天气、NPC、跨区握手、GM、混合压测。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene/open-world")
public class InternalOpenWorldController {

    private final OpenWorldRuntimeService openWorld;

    public InternalOpenWorldController(OpenWorldRuntimeService openWorld) {
        this.openWorld = openWorld;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return openWorld.status();
    }

    @PostMapping("/resource/register")
    public Map<String, Object> registerResource(@RequestBody Map<String, Object> body) {
        RespawnPoint point = new RespawnPoint(
                String.valueOf(body.get("pointId")),
                asInt(body.get("worldId"), 1),
                asInt(body.get("sceneId"), 1),
                RespawnPoint.RespawnKind.valueOf(String.valueOf(body.getOrDefault("kind", "GATHER"))),
                asFloat(body.get("x")),
                asFloat(body.get("y")),
                asFloat(body.get("z")),
                asInt(body.get("templateId"), 0),
                asInt(body.get("respawnSeconds"), 300),
                Boolean.TRUE.equals(body.get("oneShot")),
                RespawnPoint.SyncMode.valueOf(String.valueOf(body.getOrDefault("syncMode", "WORLD_SHARED"))));
        openWorld.resources().registerPoint(point);
        return Map.of("ok", true, "pointId", point.pointId(), "syncMode", point.syncMode().name());
    }

    @PostMapping("/resource/collect")
    public Map<String, Object> collect(
            @RequestParam String pointId,
            @RequestParam long playerId,
            @RequestParam(defaultValue = "0") long hostPlayerId,
            @RequestParam(defaultValue = "WORLD_SHARED") String lootMode,
            @RequestParam(required = false) List<Long> partyIds) {
        Set<Long> party = partyIds == null ? Set.of() : Set.copyOf(partyIds);
        return openWorld.collectWithOwnership(
                pointId, playerId, hostPlayerId, party,
                LootOwnershipPolicy.SyncMode.valueOf(lootMode));
    }

    @PostMapping("/mechanism/activate")
    public Map<String, Object> activateMechanism(
            @RequestParam String mechanismId,
            @RequestParam(defaultValue = "false") boolean oneShot,
            @RequestParam(defaultValue = "30000") long cooldownMs) {
        var r = openWorld.resources().activateMechanism(
                mechanismId, oneShot, cooldownMs, System.currentTimeMillis());
        return Map.of("ok", r.ok(), "from", r.from().name(), "to", r.to().name(), "reason", r.reason());
    }

    @GetMapping("/world-state")
    public Map<String, Object> worldState(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam(defaultValue = "0") long hostPlayerId) {
        return openWorld.worldState().snapshot(worldId, hostPlayerId);
    }

    @PostMapping("/world-state/puzzle-bit")
    public Map<String, Object> puzzleBit(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam(defaultValue = "0") long hostPlayerId,
            @RequestParam int bitIndex,
            @RequestParam(defaultValue = "true") boolean value) {
        return openWorld.worldState().setPuzzleBit(worldId, hostPlayerId, bitIndex, value);
    }

    @PostMapping("/world-state/host")
    public Map<String, Object> bindHostWorld(@RequestBody Map<String, Object> body) {
        long hostId = asLong(body.get("hostPlayerId"));
        @SuppressWarnings("unchecked")
        List<Long> members = body.get("members") instanceof List<?> list
                ? list.stream().map(v -> asLong(v)).collect(Collectors.toList())
                : List.of(hostId);
        HostWorldContext ctx = new HostWorldContext(
                hostId,
                asInt(body.get("worldId"), 1),
                asInt(body.get("worldLevel"), 0),
                asInt(body.get("adventureRank"), 1),
                members,
                !Boolean.FALSE.equals(body.get("suppressVisitorWorldLevel")));
        openWorld.worldState().bindHostWorld(ctx);
        openWorld.worldLevel().setWorldLevel(ctx.worldId(), ctx.hostPlayerId(), ctx.worldLevel());
        return ctx.toView();
    }

    @GetMapping("/world-level")
    public Map<String, Object> worldLevel(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam(defaultValue = "0") long hostPlayerId) {
        return openWorld.worldLevel().toView(worldId, hostPlayerId);
    }

    @PostMapping("/world-level")
    public Map<String, Object> setWorldLevel(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam(defaultValue = "0") long hostPlayerId,
            @RequestParam int worldLevel) {
        openWorld.worldLevel().setWorldLevel(worldId, hostPlayerId, worldLevel);
        return openWorld.worldLevel().toView(worldId, hostPlayerId);
    }

    @PostMapping("/boss/kill")
    public Map<String, Object> bossKill(
            @RequestParam String bossId,
            @RequestParam long killerPlayerId,
            @RequestParam(defaultValue = "1") int lineId,
            @RequestParam(defaultValue = "7200") long respawnSeconds) {
        Map<String, Object> r = openWorld.bossRespawn().markKilled(
                bossId, killerPlayerId, lineId, respawnSeconds, System.currentTimeMillis());
        if (Boolean.TRUE.equals(r.get("ok"))) {
            openWorld.metrics().setWorldBossAlive(0);
            openWorld.metrics().setBossRespawnAt(bossId, asLong(r.get("respawnAtMs")));
        }
        return r;
    }

    @GetMapping("/boss/status")
    public Map<String, Object> bossStatus(@RequestParam String bossId) {
        var state = openWorld.bossRespawn().peek(bossId, System.currentTimeMillis());
        if (state == null) {
            return Map.of("ok", true, "bossId", bossId, "status", "ALIVE", "canSpawn", true);
        }
        return Map.of("ok", true, "bossId", bossId, "status", state.status().name(),
                "respawnAtMs", state.respawnAtMs(),
                "canSpawn", openWorld.bossRespawn().canSpawn(bossId, System.currentTimeMillis()));
    }

    @PostMapping("/portal/register")
    public Map<String, Object> registerPortal(@RequestBody Map<String, Object> body) {
        PortalConfig portal = new PortalConfig(
                String.valueOf(body.get("portalId")),
                asInt(body.get("fromSceneId"), 1),
                asInt(body.get("toSceneId"), 2),
                asInt(body.get("toLineId"), 1),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("triggerRadius", 8f)),
                asFloat(body.getOrDefault("preloadRadius", 48f)),
                asFloat(body.get("entryX")), asFloat(body.get("entryY")), asFloat(body.get("entryZ")),
                body.get("targetNodeHint") == null ? null : String.valueOf(body.get("targetNodeHint")));
        openWorld.portals().register(portal);
        return Map.of("ok", true, "portalId", portal.portalId());
    }

    @PostMapping("/portal/preload")
    public Map<String, Object> portalPreload(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = openWorld.portals().onPlayerMove(
                asLong(body.get("playerId")),
                asInt(body.get("fromSceneId"), 1),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.get("velocityX")), asFloat(body.get("velocityZ")),
                asFloat(body.get("facingYaw")),
                System.currentTimeMillis());
        if (Boolean.TRUE.equals(r.get("ok")) && r.containsKey("leaseId")) {
            openWorld.metrics().incPortalPreload();
        }
        return r;
    }

    @PostMapping("/ownership/claim")
    public Map<String, Object> claimOwnership(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Set<Long> party = body.get("partyIds") instanceof List<?> list
                ? list.stream().map(v -> asLong(v)).collect(Collectors.toSet())
                : Set.of(asLong(body.get("playerId")));
        return openWorld.partyOwnership().tryClaim(
                asInt(body.get("sceneId"), 1),
                asLong(body.get("entityId")),
                asLong(body.get("playerId")),
                asLong(body.get("hostPlayerId")),
                party,
                PartyEntityOwnership.ThreatPriority.valueOf(
                        String.valueOf(body.getOrDefault("threatPriority", "ATTACKER"))),
                Duration.ofSeconds(asInt(body.get("ttlSeconds"), 120)));
    }

    @GetMapping("/metrics/business")
    public Map<String, Object> businessMetrics() {
        return openWorld.metrics().snapshot();
    }

    @GetMapping("/metrics/performance")
    public Map<String, Object> performanceMetrics() {
        return openWorld.performanceMetrics();
    }

    @PostMapping("/world-level/upgrade")
    public Map<String, Object> upgradeWorldLevel(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam(defaultValue = "0") long hostPlayerId,
            @RequestParam int cost,
            @RequestParam int targetLevel) {
        return openWorld.worldLevel().tryUpgradeWorldLevel(worldId, hostPlayerId, cost, targetLevel);
    }

    // ── 二游探索 / 叙事 / 移动 / 副玩法 ──

    @GetMapping("/gameplay/status")
    public Map<String, Object> gameplayStatus(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) String regionId) {
        if (playerId != null) {
            return openWorld.gameplay().gameplayStatusWithRegion(playerId, regionId);
        }
        return openWorld.gameplay().statusOverview();
    }

    // ── P5 解谜引擎 / 物理层 / 模板 ──

    @PostMapping("/rule/fire")
    public Map<String, Object> ruleFire(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = body.get("context") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        return openWorld.gameplay().rules().fire(
                String.valueOf(body.getOrDefault("eventType", "INTERACT")), ctx);
    }

    @GetMapping("/rule/list")
    public Map<String, Object> ruleList() {
        return Map.of("ok", true, "rules", openWorld.gameplay().rules().listRules());
    }

    @PostMapping("/physics/apply")
    public Map<String, Object> physicsApply(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().physics().apply(
                asInt(body.get("worldId"), 1),
                asFloat(body.get("x")), asFloat(body.get("z")),
                cn.itcast.demo.mymmorpg.world.puzzle.PhysicsLayerService.PhysicsKind
                        .valueOf(String.valueOf(body.getOrDefault("kind", "FIRE"))),
                asInt(body.get("intensity"), 2),
                asLong(body.getOrDefault("ttlMs", 10_000L)),
                System.currentTimeMillis());
    }

    @GetMapping("/physics/aoi")
    public Map<String, Object> physicsAoi(
            @RequestParam(defaultValue = "1") int worldId,
            @RequestParam float x,
            @RequestParam float z,
            @RequestParam(defaultValue = "3") int radiusCells) {
        return openWorld.gameplay().physics().queryAoi(
                worldId, x, z, radiusCells, System.currentTimeMillis());
    }

    @PostMapping("/physics/zone/tick")
    public Map<String, Object> physicsZoneTick(@RequestBody Map<String, Object> body) {
        int worldId = asInt(body.get("worldId"), 1);
        Map<Long, float[]> positions = new java.util.LinkedHashMap<>();
        if (body.get("entities") instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m) {
                    long id = asLong(m.get("entityId"));
                    positions.put(id, new float[]{asFloat(m.get("x")), asFloat(m.get("z"))});
                }
            }
        }
        return openWorld.gameplay().physics().tickZones(worldId, positions, System.currentTimeMillis());
    }

    @PostMapping("/move/admit")
    public Map<String, Object> moveAdmit(@RequestBody Map<String, Object> body) {
        int moveFlags = asInt(body.get("moveFlags"), 0);
        cn.itcast.demo.mymmorpg.sync.SceneMoveCmd cmd = new cn.itcast.demo.mymmorpg.sync.SceneMoveCmd(
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("speed", 8f)),
                asLong(body.getOrDefault("timestamp", System.currentTimeMillis())),
                cn.itcast.demo.mymmorpg.sync.MovementType.fromName(
                        String.valueOf(body.getOrDefault("movementType", "WALK"))),
                body.get("climbableMeshId") == null ? "" : String.valueOf(body.get("climbableMeshId")),
                moveFlags,
                body.get("mountCreatureUid") == null ? "" : String.valueOf(body.get("mountCreatureUid")),
                body.get("grappleNodeId") == null ? "" : String.valueOf(body.get("grappleNodeId")),
                asFloat(body.getOrDefault("wallNormalX", 0f)),
                asFloat(body.getOrDefault("wallNormalY", 0f)),
                asFloat(body.getOrDefault("wallNormalZ", 0f)),
                asFloat(body.getOrDefault("inheritedVelocityX", 0f)),
                asFloat(body.getOrDefault("inheritedVelocityY", 0f)),
                asFloat(body.getOrDefault("inheritedVelocityZ", 0f)),
                body.get("physicsStateHash") == null ? "" : String.valueOf(body.get("physicsStateHash")),
                cn.itcast.demo.mymmorpg.sync.ActionState.fromName(
                        String.valueOf(body.getOrDefault("actionState", "GROUND_IDLE"))),
                cn.itcast.demo.mymmorpg.sync.MoveIntent.fromName(
                        String.valueOf(body.getOrDefault("moveIntent", "FORWARD"))),
                asFloat(body.getOrDefault("cameraYaw", 0f)),
                asInt(body.get("clientDeltaMs"), 16),
                cn.itcast.demo.mymmorpg.sync.SurfaceType.fromName(
                        String.valueOf(body.getOrDefault("surfaceType", "PLAIN"))));
        String regionId = body.get("regionId") == null ? null : String.valueOf(body.get("regionId"));
        long playerId = asLong(body.get("playerId"));
        long nowMs = System.currentTimeMillis();
        Map<String, Object> result = openWorld.gameplay().movementAdmission().admit(
                playerId, cmd,
                asLong(body.getOrDefault("durationMs", 200L)),
                nowMs,
                asFloat(body.getOrDefault("fromX", 0f)),
                asFloat(body.getOrDefault("fromY", 0f)),
                asFloat(body.getOrDefault("fromZ", 0f)),
                regionId,
                asInt(body.get("terrainCellX"), 0),
                asInt(body.get("terrainCellY"), 0),
                asLong(body.getOrDefault("terrainTtlMs", 60_000L)),
                String.valueOf(body.getOrDefault("deviceType", "PC")));
        if (Boolean.TRUE.equals(result.get("ok"))) {
            Map<String, Object> physical = openWorld.gameplay().physicalDetail().onPlayerMove(
                    playerId, cmd, regionId == null ? "default" : regionId,
                    asFloat(body.getOrDefault("fromX", 0f)),
                    asFloat(body.getOrDefault("fromY", 0f)),
                    asFloat(body.getOrDefault("fromZ", 0f)),
                    nowMs);
            result.put("physicalDetail", physical);
            if (physical.get("aoiBroadcast") != null) {
                result.put("aoiBroadcast", physical.get("aoiBroadcast"));
            }
        }
        return result;
    }

    @PostMapping("/grapple/admit")
    public Map<String, Object> grappleAdmit(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().grappleNodes().admitGrapple(
                asLong(body.get("playerId")),
                String.valueOf(body.get("nodeId")),
                asFloat(body.get("fromX")), asFloat(body.get("fromY")), asFloat(body.get("fromZ")),
                asFloat(body.get("toX")), asFloat(body.get("toY")), asFloat(body.get("toZ")),
                System.currentTimeMillis());
    }

    @PostMapping("/grapple/predict")
    public Map<String, Object> grapplePredict(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().grappleNodes().predictGrapple(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("nodeId", "")),
                String.valueOf(body.getOrDefault("targetPointHash", "")),
                asFloat(body.getOrDefault("velX", 0f)),
                asFloat(body.getOrDefault("velY", 0f)),
                asFloat(body.getOrDefault("velZ", 0f)),
                System.currentTimeMillis());
    }

    @PostMapping("/grapple/audit")
    public Map<String, Object> grappleAudit(@RequestBody Map<String, Object> body) {
        long now = System.currentTimeMillis();
        if (body.containsKey("predictedX")) {
            openWorld.gameplay().physicsAuthority().scheduleGrappleAudit(
                    asLong(body.get("playerId")),
                    String.valueOf(body.get("auditToken")),
                    asFloat(body.get("predictedX")), asFloat(body.get("predictedY")), asFloat(body.get("predictedZ")),
                    asFloat(body.get("actualX")), asFloat(body.get("actualY")), asFloat(body.get("actualZ")),
                    now);
        }
        return openWorld.gameplay().physicsAuthority().runGrappleAudit(
                String.valueOf(body.get("auditToken")), now);
    }

    @PostMapping("/shadow/heartbeat")
    public Map<String, Object> shadowHeartbeat(@RequestParam long playerId) {
        return openWorld.gameplay().serverShadow().heartbeat(playerId, System.currentTimeMillis());
    }

    @PostMapping("/input/buffer/enqueue")
    public Map<String, Object> inputBufferEnqueueAlias(@RequestBody Map<String, Object> body) {
        return inputBufferEnqueueLegacy(body);
    }

    @PostMapping("/combat/cancel/try")
    public Map<String, Object> tryCombatCancel(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().reactions().tryCancel(
                asLong(body.get("playerId")),
                cn.itcast.demo.mymmorpg.world.battle.CancelAction.fromName(
                        String.valueOf(body.getOrDefault("expectedCancelAction", "DODGE"))),
                openWorld.gameplay().poise(),
                System.currentTimeMillis());
    }

    @PostMapping("/collectible/start")
    public Map<String, Object> collectibleStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().collectibles().startCollect(
                asLong(body.get("playerId")),
                String.valueOf(body.get("collectibleId")),
                System.currentTimeMillis());
    }

    @PostMapping("/collectible/tick")
    public Map<String, Object> collectibleTick(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().collectibles().tickCollect(
                String.valueOf(body.get("progressId")),
                asLong(body.getOrDefault("tickMs", 200L)),
                System.currentTimeMillis());
    }

    @PostMapping("/collectible/pause")
    public Map<String, Object> collectiblePause(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().collectibles().pauseCollect(
                asLong(body.get("playerId")),
                String.valueOf(body.get("collectibleId")),
                System.currentTimeMillis());
    }

    @PostMapping("/vehicle/board")
    public Map<String, Object> vehicleBoard(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().vehicles().board(
                asLong(body.get("playerId")),
                String.valueOf(body.get("vehicleId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @PostMapping("/vehicle/sync")
    public Map<String, Object> vehicleSync(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().vehicles().sync(
                asLong(body.get("playerId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("yawDeg", 0f)),
                asFloat(body.getOrDefault("speed", 0f)),
                asFloat(body.getOrDefault("accel", 0f)),
                System.currentTimeMillis());
    }

    @PostMapping("/mutability/damage")
    public Map<String, Object> mutabilityDamage(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().mutability().applyDamage(
                String.valueOf(body.get("id")),
                cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService.DamageType.valueOf(
                        String.valueOf(body.getOrDefault("damageType", "HEAVY_ATTACK"))),
                asInt(body.get("damage"), 0),
                System.currentTimeMillis(),
                asFloat(body.getOrDefault("radiusM", 300f)));
    }

    @PostMapping("/mutability/respawn-scan")
    public Map<String, Object> mutabilityRespawnScan(
            @RequestParam(defaultValue = "100") int limit) {
        return openWorld.gameplay().mutability().scanRespawn(System.currentTimeMillis(), limit);
    }

    @PostMapping("/coop-puzzle/press")
    public Map<String, Object> coopPuzzlePress(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().coopPuzzles().pressPlate(
                String.valueOf(body.get("puzzleId")),
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("zoneId", "")),
                System.currentTimeMillis());
    }

    @PostMapping("/chronicle/choose")
    public Map<String, Object> chronicleChoose(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().chronicle().choose(
                asLong(body.get("playerId")),
                String.valueOf(body.get("nodeId")),
                String.valueOf(body.get("choiceId")));
    }

    @GetMapping("/chronicle/global-flag")
    public Map<String, Object> chronicleGlobalFlag(
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "0.8") double threshold,
            @RequestParam(defaultValue = "0.5") double bossSpawnBoost) {
        return openWorld.gameplay().chronicle().evaluateGlobalFlag(nodeId, threshold, bossSpawnBoost);
    }

    @PostMapping("/homeland/plant")
    public Map<String, Object> homelandPlant(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        String plotId = String.valueOf(body.get("plotId"));
        openWorld.gameplay().homeland().claimLand(playerId, plotId);
        return openWorld.gameplay().homeland().plant(
                playerId, plotId,
                String.valueOf(body.getOrDefault("seedId", "wheat")),
                asLong(body.getOrDefault("growMs", 3_600_000L)),
                System.currentTimeMillis());
    }

    @PostMapping("/homeland/harvest")
    public Map<String, Object> homelandHarvest(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().homeland().harvest(
                asLong(body.get("playerId")),
                String.valueOf(body.get("cropId")),
                System.currentTimeMillis());
    }

    @PostMapping("/homeland/cook")
    public Map<String, Object> homelandCook(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().homeland().cookWithCrop(
                asLong(body.get("playerId")),
                String.valueOf(body.get("recipeId")),
                String.valueOf(body.get("cropItemId")),
                System.currentTimeMillis());
    }

    @PostMapping("/ascension/enter")
    public Map<String, Object> ascensionEnter(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ascension().enter(
                asLong(body.get("playerId")),
                String.valueOf(body.get("questId")),
                asInt(body.get("worldId"), 1),
                Boolean.TRUE.equals(body.get("expOverflow"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("expOverflow"))),
                Boolean.TRUE.equals(body.get("prerequisiteDone"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("prerequisiteDone"))),
                System.currentTimeMillis());
    }

    @PostMapping("/ascension/settle")
    public Map<String, Object> ascensionSettle(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ascension().settle(
                String.valueOf(body.get("runId")),
                Boolean.TRUE.equals(body.get("victory"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("victory"))),
                asInt(body.get("worldId"), 1),
                System.currentTimeMillis());
    }

    @PostMapping("/reaction/open-window")
    public Map<String, Object> reactionOpenWindow(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().reactions().openAttackWindow(
                String.valueOf(body.get("attackId")),
                asLong(body.get("attackerEntityId")),
                System.currentTimeMillis(),
                asInt(body.get("dodgeWindowMs"), 200),
                asInt(body.get("parryWindowMs"), 180),
                asInt(body.get("playerRttMs"), 0));
    }

    @PostMapping("/reaction/validate")
    public Map<String, Object> reactionValidate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().reactions().validate(
                cn.itcast.demo.mymmorpg.world.battle.ReactionValidator.ReactionKind.valueOf(
                        String.valueOf(body.getOrDefault("kind", "PERFECT_DODGE"))),
                String.valueOf(body.getOrDefault("battleId", "local")),
                asLong(body.get("playerId")),
                String.valueOf(body.get("attackId")),
                asLong(body.getOrDefault("clientTs", System.currentTimeMillis())),
                System.currentTimeMillis());
    }

    @PostMapping("/replay/start")
    public Map<String, Object> replayStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().replays().start(
                body.get("battleId") == null ? null : String.valueOf(body.get("battleId")),
                asLong(body.getOrDefault("seed", 1L)),
                System.currentTimeMillis());
    }

    @PostMapping("/replay/append")
    public Map<String, Object> replayAppend(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return openWorld.gameplay().replays().append(
                String.valueOf(body.get("replayId")),
                asInt(body.get("tick"), 0),
                asLong(body.getOrDefault("tsMs", System.currentTimeMillis())),
                String.valueOf(body.getOrDefault("actionType", "ACTION")),
                payload);
    }

    @PostMapping("/replay/playback")
    public Map<String, Object> replayPlayback(@RequestBody Map<String, Object> body) {
        Double min = body.get("expectedDamageMin") == null ? null
                : asFloat(body.get("expectedDamageMin")) * 1.0;
        Double max = body.get("expectedDamageMax") == null ? null
                : asFloat(body.get("expectedDamageMax")) * 1.0;
        return openWorld.gameplay().replays().playback(
                String.valueOf(body.get("replayId")),
                asFloat(body.getOrDefault("speedMul", 1f)),
                min, max);
    }

    @GetMapping("/stamina")
    public Map<String, Object> stamina(@RequestParam long playerId) {
        return openWorld.gameplay().stamina().snapshot(playerId);
    }

    @PostMapping("/guidance/start")
    public Map<String, Object> guidanceStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().guidanceChains().triggerStart(
                asLong(body.get("playerId")),
                String.valueOf(body.get("chainId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @PostMapping("/guidance/advance")
    public Map<String, Object> guidanceAdvance(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().guidanceChains().advance(
                asLong(body.get("playerId")),
                String.valueOf(body.get("chainId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @PostMapping("/guidance/particles")
    public Map<String, Object> guidanceParticles(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().guidanceChains().refreshParticles(
                asLong(body.get("playerId")),
                String.valueOf(body.get("chainId")),
                System.currentTimeMillis());
    }

    @PostMapping("/region/affliction/tick")
    public Map<String, Object> regionAfflictionTick(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regions().tickPlayerAffliction(
                String.valueOf(body.get("regionId")),
                asLong(body.get("playerId")),
                System.currentTimeMillis());
    }

    @PostMapping("/region/cleanse")
    public Map<String, Object> regionCleanse(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regions().interactCleanseAnchor(
                asLong(body.get("playerId")),
                String.valueOf(body.get("anchorId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @GetMapping("/puzzle/templates")
    public Map<String, Object> puzzleTemplates() {
        return Map.of("ok", true, "templates", openWorld.gameplay().puzzles().listTemplates());
    }

    @PostMapping("/puzzle/instantiate")
    public Map<String, Object> puzzleInstantiate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().puzzles().instantiateFromJson(body);
    }

    @PostMapping("/puzzle/advance")
    public Map<String, Object> puzzleAdvance(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> input = body.get("input") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> r = openWorld.gameplay().puzzles().advance(
                asLong(body.get("playerId")),
                String.valueOf(body.get("puzzleId")),
                input);
        if (Boolean.TRUE.equals(r.get("solved"))) {
            openWorld.gameplay().landmarks()
                    .markPuzzleSolved(asLong(body.get("playerId")), String.valueOf(body.get("puzzleId")));
            openWorld.gameplay().regionProgress().markPuzzle(
                    asLong(body.get("playerId")),
                    String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                    String.valueOf(body.get("puzzleId")));
        }
        return r;
    }

    // ── 收集物 / 区域探索度 / 奇观 LOD ──

    @PostMapping("/collectible/collect")
    public Map<String, Object> collectibleCollect(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        String collectibleId = String.valueOf(body.get("collectibleId"));
        float x = asFloat(body.get("x"));
        float y = asFloat(body.get("y"));
        float z = asFloat(body.get("z"));
        String coopRoomId = body.get("coopRoomId") == null ? null : String.valueOf(body.get("coopRoomId"));
        if (coopRoomId != null && (coopRoomId.isBlank() || "null".equals(coopRoomId))) {
            coopRoomId = null;
        }
        Map<String, Object> r;
        if (body.containsKey("worldLevel") || body.containsKey("regionExplorationRate")) {
            r = openWorld.gameplay().collectWithExplorationFeedback(
                    playerId,
                    String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                    collectibleId, x, y, z,
                    asInt(body.get("worldLevel"), 0),
                    body.get("regionExplorationRate") instanceof Number n
                            ? n.floatValue() : asFloat(body.getOrDefault("regionExplorationRate", 0f)),
                    coopRoomId);
        } else {
            r = openWorld.gameplay().collectWithExplorationFeedback(
                    playerId,
                    String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                    collectibleId, x, y, z, 0, 0f, coopRoomId);
        }
        if (Boolean.TRUE.equals(r.get("ok")) && !r.containsKey("exploration_impacts")) {
            openWorld.gameplay().regionProgress().markCollectible(
                    playerId,
                    String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                    collectibleId);
        }
        return r;
    }

    @GetMapping("/collectible/progress")
    public Map<String, Object> collectibleProgress(@RequestParam long playerId) {
        return openWorld.gameplay().collectibles().progress(playerId);
    }

    @GetMapping("/region/progress")
    public Map<String, Object> regionProgress(
            @RequestParam long playerId, @RequestParam String regionId) {
        return openWorld.gameplay().regionProgress().status(playerId, regionId);
    }

    @GetMapping("/landmark/lod")
    public Map<String, Object> landmarkLod(
            @RequestParam float x,
            @RequestParam float y,
            @RequestParam float z,
            @RequestParam(defaultValue = "400") float viewDistance) {
        return openWorld.gameplay().landmarks().lodMarkers(x, y, z, viewDistance);
    }

    // ── 角色世界技 / 烹饪 ──

    @PostMapping("/world-skill/unlock-mode")
    public Map<String, Object> unlockModeBound(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Set<String> items = body.get("ownedItems") instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.toSet())
                : Set.of();
        return openWorld.gameplay().worldSkills().unlockModeBound(
                asLong(body.get("playerId")),
                cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService.Mode
                        .valueOf(String.valueOf(body.get("mode"))),
                String.valueOf(body.get("characterId")),
                items,
                openWorld.gameplay().traverse());
    }

    @PostMapping("/env/gather-talent")
    public Map<String, Object> gatherTalent(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().environment().gatherWithTalent(
                asLong(body.get("playerId")),
                String.valueOf(body.get("itemId")),
                asInt(body.get("baseCount"), 1),
                Boolean.TRUE.equals(body.get("isFoodIngredient")));
    }

    @PostMapping("/cook")
    public Map<String, Object> cook(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().cooking().cook(
                asLong(body.get("playerId")),
                String.valueOf(body.get("campfireId")),
                String.valueOf(body.get("recipeId")),
                asFloat(body.get("x")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    // ── 区域潮汐 / 事件链 ──

    @PostMapping("/region/anchor")
    public Map<String, Object> regionAnchor(
            @RequestParam String regionId, @RequestParam long playerId) {
        return openWorld.gameplay().regions()
                .activateAnchor(regionId, playerId, System.currentTimeMillis());
    }

    @PostMapping("/region/tick-decay")
    public Map<String, Object> regionTickDecay(@RequestParam String regionId) {
        return openWorld.gameplay().regions().tickDecay(regionId, System.currentTimeMillis());
    }

    @GetMapping("/region/boss-link")
    public Map<String, Object> regionBossLink(@RequestParam String regionId) {
        return openWorld.gameplay().regions().bossLinkStatus(regionId);
    }

    @PostMapping("/region/event-chain/advance")
    public Map<String, Object> regionChainAdvance(
            @RequestParam String regionId,
            @RequestParam(defaultValue = "continue") String action) {
        return openWorld.gameplay().regions()
                .advanceEventChain(regionId, action, System.currentTimeMillis());
    }

    // ── 异步社交 ──

    @PostMapping("/mark/place")
    public Map<String, Object> markPlace(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().publicMarks().place(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                String.valueOf(body.getOrDefault("kind", "HELP")),
                body.get("message") == null ? "" : String.valueOf(body.get("message")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                Boolean.TRUE.equals(body.get("requestPublic")),
                System.currentTimeMillis());
    }

    @PostMapping("/mark/thank")
    public Map<String, Object> markThank(
            @RequestParam long markId,
            @RequestParam long fromPlayerId,
            @RequestParam(defaultValue = "5") int staminaGift) {
        return openWorld.gameplay().publicMarks().thank(markId, fromPlayerId, staminaGift);
    }

    @PostMapping("/phantom/leave")
    public Map<String, Object> phantomLeave(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> frames = body.get("emoteFrames") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("WAVE");
        return openWorld.gameplay().encounters().leavePhantom(
                asLong(body.get("playerId")),
                body.get("ownerName") == null ? null : String.valueOf(body.get("ownerName")),
                asInt(body.get("sceneId"), 1),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                String.valueOf(body.getOrDefault("pose", "JUMP_OFF_CLIFF")),
                frames,
                System.currentTimeMillis());
    }

    @PostMapping("/phantom/play")
    public Map<String, Object> phantomPlay(@RequestParam String phantomId) {
        return openWorld.gameplay().encounters().playPhantom(phantomId);
    }

    @PostMapping("/phantom/puzzle-fail")
    public Map<String, Object> phantomPuzzleFail(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().encounters().recordPuzzleFail(
                asLong(body.get("playerId")), String.valueOf(body.get("puzzleId")));
    }

    @PostMapping("/constellation/unlock")
    public Map<String, Object> constellationUnlock(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().constellation().unlock(
                asLong(body.get("playerId")),
                String.valueOf(body.get("characterId")),
                asInt(body.get("matCost"), 1));
    }

    @PostMapping("/skill/cast-resolve")
    public Map<String, Object> skillCastResolve(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().skillCast().resolveCast(
                asLong(body.get("playerId")),
                String.valueOf(body.get("characterId")),
                String.valueOf(body.get("skillId")),
                System.currentTimeMillis());
    }

    @PostMapping("/fall-attack/validate")
    public Map<String, Object> fallAttackValidate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().fallAttack().validateFallHeavy(
                asLong(body.get("playerId")),
                asFloat(body.get("fallDistance")),
                asFloat(body.get("velocityY")),
                Boolean.TRUE.equals(body.get("hitEnemy")),
                asInt(body.get("baseDamage"), 100),
                asInt(body.get("worldId"), 1),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @PostMapping("/underwater/enter")
    public Map<String, Object> underwaterEnter(
            @RequestParam long playerId, @RequestParam(defaultValue = "1") int regionId) {
        openWorld.gameplay().underwater().enterUnderwater(playerId, regionId);
        return Map.of("ok", true, "biome",
                cn.itcast.demo.mymmorpg.world.traverse.UnderwaterPhysicsService.BIOME_UNDERWATER,
                "playerId", playerId, "regionId", regionId);
    }

    @PostMapping("/underwater/leave")
    public Map<String, Object> underwaterLeave(@RequestParam long playerId) {
        openWorld.gameplay().underwater().leaveUnderwater(playerId);
        return Map.of("ok", true, "playerId", playerId);
    }

    @PostMapping("/rare-elite/kill")
    public Map<String, Object> rareEliteKill(
            @RequestParam String gridCell,
            @RequestParam(defaultValue = "0") long nowMs) {
        long ts = nowMs > 0 ? nowMs : System.currentTimeMillis();
        return openWorld.gameplay().rareElites().recordKill(gridCell, ts);
    }

    @PostMapping("/creature/follow-harvest")
    public Map<String, Object> creatureFollowHarvest(
            @RequestParam long playerId, @RequestParam String petInstanceId) {
        return openWorld.gameplay().creatureUtility().enableFollowHarvest(playerId, petInstanceId);
    }

    @PostMapping("/homeland/guard")
    public Map<String, Object> homelandGuard(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().homelandGuard().installGuard(
                asLong(body.get("ownerId")),
                String.valueOf(body.get("plotId")),
                asInt(body.get("coinCost"), 10));
    }

    @PostMapping("/homeland/steal")
    public Map<String, Object> homelandSteal(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().homelandGuard().steal(
                asLong(body.get("friendId")),
                String.valueOf(body.get("plotId")),
                String.valueOf(body.get("cropId")),
                System.currentTimeMillis());
    }

    @PostMapping("/poise/hit")
    public Map<String, Object> poiseHit(@RequestBody Map<String, Object> body) {
        long targetId = asLong(body.get("targetId"));
        if (openWorld.gameplay().poise().get(targetId) == null) {
            float max = asFloat(body.get("poiseMax"));
            openWorld.gameplay().poise().initEntity(targetId, max > 0 ? max : 100f, 5);
        }
        return openWorld.gameplay().poise().applyPoiseDamage(
                asLong(body.get("attackerId")),
                targetId,
                body.get("attackPoiseDamage") instanceof Number n ? n.doubleValue() : 30d,
                System.currentTimeMillis());
    }

    @PostMapping("/input-buffer/arm")
    public Map<String, Object> inputBufferArm(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().inputBuffer().armAfterDodgeWindow(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("attackId", "atk-buf")),
                System.currentTimeMillis(),
                asInt(body.get("dodgeWindowMs"), 200),
                asInt(body.get("clientDeltaMs"), 16));
    }

    @PostMapping("/input-buffer/enqueue")
    public Map<String, Object> inputBufferEnqueueLegacy(@RequestBody Map<String, Object> body) {
        if (body.containsKey("expectedCancelAction")) {
            return openWorld.gameplay().inputBuffer().enqueue(
                    asLong(body.get("playerId")),
                    String.valueOf(body.getOrDefault("action", "HEAVY")),
                    cn.itcast.demo.mymmorpg.world.battle.CancelAction.fromName(
                            String.valueOf(body.getOrDefault("expectedCancelAction", "NONE"))),
                    System.currentTimeMillis(),
                    asInt(body.get("clientDeltaMs"), 16));
        }
        return openWorld.gameplay().inputBuffer().enqueue(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("action", "HEAVY")),
                System.currentTimeMillis());
    }

    @PostMapping("/region/awaken")
    public Map<String, Object> regionAwaken(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regionAwakening().checkAndAwaken(
                asLong(body.get("playerId")),
                body.get("playerName") == null ? null : String.valueOf(body.get("playerName")),
                String.valueOf(body.get("regionId")),
                System.currentTimeMillis());
    }

    @PostMapping("/world-core/create")
    public Map<String, Object> worldCoreCreate(@RequestParam long leaderId) {
        return openWorld.gameplay().worldCore().createCoopRoom(leaderId, System.currentTimeMillis());
    }

    @GetMapping("/world-core/status")
    public Map<String, Object> worldCoreStatus(@RequestParam long playerId) {
        return openWorld.gameplay().worldCore().unlockStatus(playerId);
    }

    // ── P10：生态 / 钩锁载具 / 地貌 / 纪元 / 攻城 / 制造交易 / 手感 / 集群 AI ──

    @PostMapping("/ecosystem/tick")
    public Map<String, Object> ecosystemTick(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ecosystem().tick(
                String.valueOf(body.get("creatureUid")),
                asInt(body.get("hourOfDay"), 12),
                System.currentTimeMillis(),
                Boolean.TRUE.equals(body.get("inCombat")));
    }

    @PostMapping("/ecosystem/proximity")
    public Map<String, Object> ecosystemProximity(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ecosystem().onPlayerProximity(
                String.valueOf(body.get("creatureUid")),
                asFloat(body.get("playerDistM")),
                Boolean.TRUE.equals(body.get("inCombat")));
    }

    @PostMapping("/affinity/feed")
    public Map<String, Object> affinityFeed(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().affinity().feed(
                asLong(body.get("playerId")),
                String.valueOf(body.get("creatureUid")),
                String.valueOf(body.getOrDefault("itemId", "FOOD_ITEM")),
                asInt(body.get("amount"), 1));
    }

    @PostMapping("/affinity/treasure-hint")
    public Map<String, Object> affinityTreasure(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().affinity().tryTreasureHint(
                asLong(body.get("playerId")),
                String.valueOf(body.get("creatureUid")),
                asFloat(body.get("x")), asFloat(body.get("z")),
                System.currentTimeMillis(),
                asLong(body.getOrDefault("intervalMs", 0L)));
    }

    @PostMapping("/affinity/alert")
    public Map<String, Object> affinityAlert(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().affinity().pushCreatureAlert(
                asLong(body.get("playerId")),
                String.valueOf(body.get("creatureUid")),
                String.valueOf(body.get("eliteId")),
                String.valueOf(body.get("gridCell")));
    }

    @PostMapping("/grapple/pull-enemy")
    public Map<String, Object> grapplePullEnemy(@RequestBody Map<String, Object> body) {
        String mass = String.valueOf(body.getOrDefault("mass", "LIGHT"));
        return openWorld.gameplay().grapplePhysics().pullEnemy(
                asLong(body.get("playerId")),
                asLong(body.get("enemyId")),
                "HEAVY".equalsIgnoreCase(mass)
                        ? cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService.BodyMass.HEAVY
                        : cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService.BodyMass.LIGHT,
                body.get("attackPower") instanceof Number n ? n.doubleValue() : 20d,
                System.currentTimeMillis());
    }

    @PostMapping("/grapple/swing-kick")
    public Map<String, Object> grappleSwingKick(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        openWorld.gameplay().grapplePhysics().recordSwingSpeed(
                playerId, body.get("speed") instanceof Number n ? n.doubleValue() : 12d);
        return openWorld.gameplay().grapplePhysics().swingKick(
                playerId, asInt(body.get("baseDamage"), 100), Boolean.TRUE.equals(body.get("atApex")));
    }

    @PostMapping("/vehicle/cast-skill")
    public Map<String, Object> vehicleCastSkill(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        openWorld.gameplay().vehicleCombat().board(playerId, String.valueOf(body.get("vehicleId")));
        return openWorld.gameplay().vehicleCombat().castSkill(playerId, String.valueOf(body.get("skillId")));
    }

    @PostMapping("/vehicle/poise-hit")
    public Map<String, Object> vehiclePoiseHit(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        openWorld.gameplay().vehicleCombat().board(playerId, String.valueOf(body.get("vehicleId")));
        return openWorld.gameplay().vehicleCombat().applyPoiseDamage(
                playerId,
                asFloat(body.get("damage")),
                asFloat(body.get("horizontalSpeed")));
    }

    @PostMapping("/climb/attack")
    public Map<String, Object> climbAttack(@RequestBody Map<String, Object> body) {
        long playerId = asLong(body.get("playerId"));
        openWorld.gameplay().climbAttack().setClimbing(playerId, true);
        return openWorld.gameplay().climbAttack().climbAttack(
                playerId, asInt(body.get("baseDamage"), 40),
                Boolean.TRUE.equals(body.get("targetSuperArmor")));
    }

    @PostMapping("/terrain/overload-water")
    public Map<String, Object> terrainOverload(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().terrainMutation().applyOverloadOnWater(
                String.valueOf(body.get("regionId")),
                asInt(body.get("gx"), 10), asInt(body.get("gz"), 10),
                System.currentTimeMillis());
    }

    @PostMapping("/terrain/freeze-water")
    public Map<String, Object> terrainFreeze(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().terrainMutation().applyFreezeOnWater(
                String.valueOf(body.get("regionId")),
                asInt(body.get("gx"), 10), asInt(body.get("gz"), 10),
                System.currentTimeMillis());
    }

    @PostMapping("/projectile/swirl")
    public Map<String, Object> projectileSwirl(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().projectileCurve().applySwirl(
                String.valueOf(body.get("projectileId")),
                String.valueOf(body.getOrDefault("element", "PYRO")),
                asFloat(body.get("x")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("radiusM", 8f)));
    }

    @PostMapping("/epoch/bump-flag")
    public Map<String, Object> epochBump(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().serverEpoch().bumpFlag(
                String.valueOf(body.get("flag")), asInt(body.get("delta"), 1));
    }

    @PostMapping("/epoch/advance")
    public Map<String, Object> epochAdvance(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = body.get("worldStatePatch") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of("weather", "THUNDERSTORM");
        return openWorld.gameplay().serverEpoch().tryAdvanceEpoch(
                String.valueOf(body.get("flag")),
                asInt(body.get("threshold"), 100),
                patch);
    }

    @PostMapping("/faction/donate")
    public Map<String, Object> factionDonate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().tugOfWar().donate(
                asLong(body.get("playerId")),
                String.valueOf(body.get("regionId")),
                String.valueOf(body.get("faction")),
                asInt(body.get("amount"), 1));
    }

    @PostMapping("/faction/weekly-settle")
    public Map<String, Object> factionSettle(@RequestParam String regionId) {
        return openWorld.gameplay().tugOfWar().weeklySettle(regionId);
    }

    @PostMapping("/cutscene/evaluate")
    public Map<String, Object> cutsceneEvaluate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().cutsceneTrigger().evaluate(
                String.valueOf(body.getOrDefault("choiceA", "A")),
                String.valueOf(body.getOrDefault("choiceB", "B")),
                String.valueOf(body.getOrDefault("cutsceneId", "epoch-dragon")));
    }

    @PostMapping("/siege/create")
    public Map<String, Object> siegeCreate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().siegeWar().createSiegeRoom(
                String.valueOf(body.get("roomId")), asLong(body.get("leaderId")));
    }

    @PostMapping("/siege/break-part")
    public Map<String, Object> siegeBreakPart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().siegeWar().breakPart(
                String.valueOf(body.get("roomId")),
                cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService.BossPart.valueOf(
                        String.valueOf(body.getOrDefault("part", "LEFT_LEG"))));
    }

    @PostMapping("/asymmetric/hide-seek/start")
    public Map<String, Object> hideSeekStart(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> hiders = body.get("hiders") instanceof List<?> l
                ? l.stream().map(o -> ((Number) o).longValue()).toList() : List.of();
        @SuppressWarnings("unchecked")
        List<Long> seekers = body.get("seekers") instanceof List<?> l
                ? l.stream().map(o -> ((Number) o).longValue()).toList() : List.of();
        return openWorld.gameplay().asymmetricPlay().startHideSeek(
                String.valueOf(body.get("matchId")), hiders, seekers);
    }

    @PostMapping("/asymmetric/scanner")
    public Map<String, Object> asymmetricScanner(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().asymmetricPlay().scannerSkill(
                asLong(body.get("seekerId")),
                asFloat(body.get("x")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("radiusM", 15f)));
    }

    @PostMapping("/craft/start")
    public Map<String, Object> craftStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().crafting().startCraft(
                asLong(body.get("playerId")),
                String.valueOf(body.get("recipeId")),
                System.currentTimeMillis());
    }

    @PostMapping("/craft/complete")
    public Map<String, Object> craftComplete(@RequestParam String jobId) {
        return openWorld.gameplay().crafting().completeIfReady(jobId, System.currentTimeMillis());
    }

    @PostMapping("/auction/list")
    public Map<String, Object> auctionList(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().auctionHouse().listBuyout(
                asLong(body.get("sellerId")),
                String.valueOf(body.get("itemId")),
                asInt(body.get("count"), 1),
                asLong(body.getOrDefault("price", 100L)),
                System.currentTimeMillis());
    }

    @PostMapping("/auction/buyout")
    public Map<String, Object> auctionBuyout(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().auctionHouse().buyout(
                asLong(body.get("buyerId")), String.valueOf(body.get("listingId")));
    }

    @PostMapping("/hit-feedback")
    public Map<String, Object> hitFeedback(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().hitFeedback().buildFeedback(
                asLong(body.get("attackerId")),
                asLong(body.get("targetId")),
                body.get("poiseDamage") instanceof Number n ? n.doubleValue() : 20d,
                Boolean.TRUE.equals(body.get("heavyOrFall")),
                body.get("poiseRemainRatio") instanceof Number n ? n.floatValue() : 0.5f);
    }

    @PostMapping("/pre-playback/start")
    public Map<String, Object> prePlaybackStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().prePlayback().onActionStart(
                asLong(body.get("playerId")),
                String.valueOf(body.get("actionId")),
                System.currentTimeMillis());
    }

    @PostMapping("/pre-playback/confirm")
    public Map<String, Object> prePlaybackConfirm(@RequestBody Map<String, Object> body) {
        long now = System.currentTimeMillis();
        return openWorld.gameplay().prePlayback().confirmOrRollback(
                asLong(body.get("playerId")),
                String.valueOf(body.get("actionId")),
                asLong(body.getOrDefault("actionStartMs", now)),
                now,
                !Boolean.FALSE.equals(body.get("hitValid")));
    }

    @PostMapping("/squad/command")
    public Map<String, Object> squadCommand(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().squadCommander().issueCommand(
                String.valueOf(body.get("squadId")),
                String.valueOf(body.get("command")));
    }

    @PostMapping("/squad/quick-mark")
    public Map<String, Object> squadQuickMark(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().squadCommander().quickMark(
                String.valueOf(body.get("squadId")),
                asLong(body.get("leaderId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                String.valueOf(body.getOrDefault("bossPartId", "LEFT_LEG")),
                !Boolean.FALSE.equals(body.get("focusFire")),
                body.get("siegeRoomId") == null ? "" : String.valueOf(body.get("siegeRoomId")),
                System.currentTimeMillis());
    }

    @PostMapping("/team/resonance/refresh")
    public Map<String, Object> teamResonanceRefresh(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> elements = body.get("elements") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return openWorld.gameplay().teamComposition().refresh(
                asLong(body.get("playerId")), elements, System.currentTimeMillis());
    }

    @PostMapping("/story/instance/start")
    public Map<String, Object> storyInstanceStart(@RequestBody Map<String, Object> body) {
        String roomId = body.get("coopRoomId") == null ? "" : String.valueOf(body.get("coopRoomId"));
        if (!roomId.isBlank()) {
            return openWorld.gameplay().storyStateMachine().startInstanceWithCoop(
                    asLong(body.get("playerId")),
                    String.valueOf(body.get("storyId")),
                    roomId,
                    System.currentTimeMillis());
        }
        return openWorld.gameplay().storyStateMachine().startInstance(
                asLong(body.get("playerId")),
                String.valueOf(body.get("storyId")),
                System.currentTimeMillis());
    }

    @PostMapping("/story/instance/choose")
    public Map<String, Object> storyInstanceChoose(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().storyStateMachine().chooseDialogue(
                asLong(body.get("playerId")),
                String.valueOf(body.get("storyId")),
                String.valueOf(body.get("optionId")),
                System.currentTimeMillis());
    }

    @PostMapping("/story/instance/advance")
    public Map<String, Object> storyInstanceAdvance(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().storyStateMachine().advance(
                asLong(body.get("playerId")),
                String.valueOf(body.get("storyId")),
                cn.itcast.demo.mymmorpg.world.narrative.StoryStateMachine.State.valueOf(
                        String.valueOf(body.getOrDefault("state", "COMBAT"))));
    }

    @PostMapping("/story/instance/complete")
    public Map<String, Object> storyInstanceComplete(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().storyStateMachine().complete(
                asLong(body.get("playerId")),
                String.valueOf(body.get("storyId")),
                System.currentTimeMillis());
    }

    @PostMapping("/handbook/discover")
    public Map<String, Object> handbookDiscover(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().handbook().discover(
                asLong(body.get("playerId")),
                cn.itcast.demo.mymmorpg.world.sideplay.HandbookService.EntryKind.valueOf(
                        String.valueOf(body.getOrDefault("kind", "CREATURE"))),
                String.valueOf(body.get("entryId")),
                System.currentTimeMillis(),
                Boolean.TRUE.equals(body.get("perfectCook")));
    }

    @GetMapping("/handbook/progress")
    public Map<String, Object> handbookProgress(@RequestParam long playerId) {
        return openWorld.gameplay().handbook().progress(playerId);
    }

    @PostMapping("/handbook/gather")
    public Map<String, Object> handbookGather(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().handbook().recordGather(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                String.valueOf(body.getOrDefault("resourceType", "herb")),
                System.currentTimeMillis());
    }

    @PostMapping("/rogue/start")
    public Map<String, Object> rogueStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().rogueFate().startWithRegion(
                asLong(body.get("playerId")),
                asInt(body.get("rogueId"), 1),
                String.valueOf(body.get("region_id") == null ? body.get("regionId") : body.get("region_id")),
                System.currentTimeMillis());
    }

    @PostMapping("/rogue/settle")
    public Map<String, Object> rogueSettle(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().rogueFate().settleClear(
                asLong(body.get("playerId")),
                !Boolean.FALSE.equals(body.get("cleared")),
                System.currentTimeMillis());
    }

    @PostMapping("/env-ai/pickup")
    public Map<String, Object> envAiPickup(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().envUtilAi().tryPickup(
                asLong(body.get("aiEntityId")),
                String.valueOf(body.get("objectId")),
                asFloat(body.get("x")), asFloat(body.get("z")));
    }

    @PostMapping("/channel/broadcast-elite")
    public Map<String, Object> channelBroadcast(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regionChannel().broadcastEliteKill(
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                asLong(body.get("killerPlayerId")),
                String.valueOf(body.getOrDefault("eliteName", "稀有精英")),
                System.currentTimeMillis());
    }

    @PostMapping("/channel/broadcast-mvp")
    public Map<String, Object> channelBroadcastMvp(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regionChannel().broadcastBossMvp(
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                asLong(body.get("mvpPlayerId")),
                asLong(body.getOrDefault("topDamage", 0L)),
                String.valueOf(body.getOrDefault("bossName", "WorldBoss")),
                System.currentTimeMillis());
    }

    @PostMapping("/channel/join-battle")
    public Map<String, Object> channelJoinBattle(
            @RequestParam long playerId,
            @RequestParam long battleId,
            @RequestParam String token) {
        return openWorld.gameplay().regionChannel().joinBattle(playerId, battleId, token);
    }

    // ── 内容量产 ──

    @PostMapping("/placement/fill")
    public Map<String, Object> placementFill(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().placement().fillGatherables(
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                List.of(),
                asInt(body.get("targetCount"), 100),
                asFloat(body.getOrDefault("minDensityPerSqKm", 30f)),
                asFloat(body.getOrDefault("areaSqKm", 2f)));
    }

    @PostMapping("/config/patch")
    public Map<String, Object> configPatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return openWorld.gameplay().configPatch().upsert(
                String.valueOf(body.get("gridCell")),
                String.valueOf(body.getOrDefault("kind", "GENERIC")),
                payload);
    }

    @PostMapping("/config/reload")
    public Map<String, Object> configReload(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> cells = body.get("gridCells") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return openWorld.gameplay().configPatch().reloadCells(cells, System.currentTimeMillis());
    }

    @PostMapping("/explore/discover")
    public Map<String, Object> exploreDiscover(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().discoverExploration(
                asLong(body.get("playerId")),
                String.valueOf(body.get("pointId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @GetMapping("/explore/progress")
    public Map<String, Object> exploreProgress(@RequestParam long playerId) {
        return openWorld.gameplay().exploration().progress(playerId);
    }

    // ── P13：探索便利 / 移动体验 / 生态沉浸 / 战斗辅助 / 长线减负 ──

    @GetMapping("/compass/status")
    public Map<String, Object> compassStatus(
            @RequestParam long playerId, @RequestParam String regionId) {
        return openWorld.gameplay().explorationCompass().unlockStatus(playerId, regionId);
    }

    @PostMapping("/compass/echo-probe")
    public Map<String, Object> compassEchoProbe(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().explorationCompass().echoProbe(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("radius", 120f)),
                System.currentTimeMillis());
    }

    @PostMapping("/region/heat/visit")
    public Map<String, Object> regionHeatVisit(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regionHeat().recordVisit(
                String.valueOf(body.get("regionId")),
                asInt(body.get("gridX"), 0),
                asInt(body.get("gridZ"), 0),
                asLong(body.get("playerId")),
                System.currentTimeMillis());
    }

    @GetMapping("/region/heat/status")
    public Map<String, Object> regionHeatStatus(
            @RequestParam String regionId,
            @RequestParam(defaultValue = "0") int gridX,
            @RequestParam(defaultValue = "0") int gridZ) {
        return openWorld.gameplay().regionHeat().heatStatus(regionId, gridX, gridZ, System.currentTimeMillis());
    }

    @PostMapping("/zone/leave")
    public Map<String, Object> zoneLeave(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().zones().onEntityLeaveZone(
                asLong(body.get("entityId")),
                String.valueOf(body.get("zoneId")),
                System.currentTimeMillis());
    }

    @PostMapping("/fight/contribution/damage")
    public Map<String, Object> fightContributionDamage(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().fightContribution().recordDamage(
                asLong(body.get("battleId")),
                asLong(body.get("playerId")),
                asLong(body.get("damage")));
    }

    @PostMapping("/fight/contribution/settle")
    public Map<String, Object> fightContributionSettle(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().fightContribution().settleBattle(
                asLong(body.get("battleId")),
                asLong(body.get("killerPlayerId")));
    }

    @PostMapping("/fight/contribution/open")
    public Map<String, Object> fightContributionOpen(@RequestBody Map<String, Object> body) {
        long battleId = openWorld.gameplay().fightContribution().openBattle(
                String.valueOf(body.getOrDefault("bossName", "world-boss")),
                asLong(body.getOrDefault("maxHp", 1_000_000L)),
                asLong(body.getOrDefault("hostPlayerId", 0L)));
        return Map.of("ok", true, "battleId", battleId);
    }

    @PostMapping("/fight/instance-loot/settle")
    public Map<String, Object> fightInstanceLootSettle(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().fightContribution().settleInstanceLoot(
                asLong(body.get("battleId")));
    }

    @PostMapping("/phantom/mirror/start")
    public Map<String, Object> phantomMirrorStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().phantomBorrow().startRealtimeMirror(
                asLong(body.get("viewerId")),
                asLong(body.get("friendPlayerId")),
                asInt(body.get("socialTokens"), 0),
                asFloat(body.get("friendX")),
                asFloat(body.get("friendY")),
                asFloat(body.get("friendZ")),
                asFloat(body.getOrDefault("headingDeg", 0f)));
    }

    @PostMapping("/visual/degrade")
    public Map<String, Object> visualDegrade(@RequestBody Map<String, Object> body) {
        long viewerId = asLong(body.get("viewerId"));
        float vx = asFloat(body.get("viewerX"));
        float vz = asFloat(body.get("viewerZ"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = body.get("entities") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        List<cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler.EntitySignificance> entities =
                new java.util.ArrayList<>();
        for (Map<String, Object> row : raw) {
            entities.add(new cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler.EntitySignificance(
                    asLong(row.get("entityId")),
                    asFloat(row.getOrDefault("distance", 0f)),
                    Boolean.TRUE.equals(row.get("inCombat")),
                    asFloat(row.getOrDefault("hpPercent", 100f)),
                    Boolean.TRUE.equals(row.get("locked")),
                    0f));
        }
        return openWorld.gameplay().visualSignificance().computeDegrade(viewerId, vx, vz, entities);
    }

    @GetMapping("/terrain/ice-physics")
    public Map<String, Object> icePhysicsStatus(@RequestParam String curveId) {
        return openWorld.gameplay().terrainMutation().icePhysicsStatus(curveId, System.currentTimeMillis());
    }

    @PostMapping("/compass/probe")
    public Map<String, Object> compassProbe(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().explorationCompass().probe(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("radius", 120f)),
                System.currentTimeMillis());
    }

    @GetMapping("/map/markers")
    public Map<String, Object> mapMarkers(
            @RequestParam long playerId, @RequestParam String regionId) {
        return openWorld.gameplay().mapMarkers().markersForRegion(playerId, regionId);
    }

    @GetMapping("/explore/world-impact")
    public Map<String, Object> exploreWorldImpact(
            @RequestParam long playerId, @RequestParam String regionId) {
        return openWorld.gameplay().explorationImpacts().evaluate(playerId, regionId);
    }

    @PostMapping("/traverse/regional/use")
    public Map<String, Object> regionalTraverseUse(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().regionalTraverse().use(
                asLong(body.get("playerId")),
                String.valueOf(body.get("facilityId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @GetMapping("/traverse/regional/list")
    public Map<String, Object> regionalTraverseList(@RequestParam String regionId) {
        return openWorld.gameplay().regionalTraverse().listRegion(regionId);
    }

    @PostMapping("/traverse/universal/grant-kit")
    public Map<String, Object> grantUniversalKit(@RequestParam long playerId) {
        return openWorld.gameplay().universalTraversal().ensureCoreExplorationKit(playerId);
    }

    @GetMapping("/traverse/universal/status")
    public Map<String, Object> universalTraverseStatus(@RequestParam long playerId) {
        return openWorld.gameplay().universalTraversal().snapshot(playerId);
    }

    @PostMapping("/eco/narrative/evaluate")
    public Map<String, Object> ecoNarrativeEvaluate(
            @RequestParam long playerId, @RequestParam String creatureUid) {
        return openWorld.gameplay().ecoNarrative().evaluate(playerId, creatureUid);
    }

    @PostMapping("/env/story/inspect")
    public Map<String, Object> envStoryInspect(
            @RequestParam long playerId, @RequestParam String propId) {
        return openWorld.gameplay().environmentalStory().inspect(playerId, propId);
    }

    @PostMapping("/explore/feedback")
    public Map<String, Object> explorationFeedback(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().explorationFeedback().recordAction(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                cn.itcast.demo.mymmorpg.world.ecosystem.WorldExplorationFeedbackService.ActionKind
                        .valueOf(String.valueOf(body.getOrDefault("action", "DISCOVER_VISTA"))));
    }

    @PostMapping("/combat/assist/mode")
    public Map<String, Object> combatAssistMode(
            @RequestParam long playerId,
            @RequestParam(defaultValue = "SIMPLIFIED") String mode) {
        return openWorld.gameplay().combatAssist().setMode(
                playerId,
                cn.itcast.demo.mymmorpg.world.battle.CombatAssistService.AssistMode.valueOf(mode));
    }

    @PostMapping("/combat/assist/resolve")
    public Map<String, Object> combatAssistResolve(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().combatAssist().resolveAttack(
                asLong(body.get("playerId")),
                asLong(body.get("attackerId")),
                asLong(body.get("targetId")),
                body.get("incomingDamage") instanceof Number n ? n.doubleValue() : 0d,
                body.get("poiseDamage") instanceof Number p ? p.doubleValue() : 0d,
                Boolean.TRUE.equals(body.get("heavyOrFall")),
                asFloat(body.getOrDefault("targetPoiseRemainRatio", 0.5f)),
                asFloat(body.getOrDefault("playerHpRatio", 1f)));
    }

    @PostMapping("/automation/build")
    public Map<String, Object> automationBuild(
            @RequestParam long playerId, @RequestParam String facilityId) {
        return openWorld.gameplay().resourceAutomation().build(playerId, facilityId, System.currentTimeMillis());
    }

    @PostMapping("/automation/claim-all")
    public Map<String, Object> automationClaimAll(@RequestParam long playerId) {
        return openWorld.gameplay().resourceAutomation().claimAll(playerId, System.currentTimeMillis());
    }

    @GetMapping("/automation/status")
    public Map<String, Object> automationStatus(@RequestParam long playerId) {
        return openWorld.gameplay().resourceAutomation().status(playerId);
    }

    @PostMapping("/daily/report")
    public Map<String, Object> dailyReport(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().flexibleDaily().reportProgress(
                asLong(body.get("playerId")),
                cn.itcast.demo.mymmorpg.world.progression.FlexibleDailyQuestService.ProgressKind
                        .valueOf(String.valueOf(body.get("kind"))),
                asInt(body.get("amount"), 1));
    }

    @PostMapping("/daily/claim-all")
    public Map<String, Object> dailyClaimAll(@RequestParam long playerId) {
        return openWorld.gameplay().flexibleDaily().claimAll(playerId);
    }

    @GetMapping("/daily/list")
    public Map<String, Object> dailyList(@RequestParam long playerId) {
        return openWorld.gameplay().flexibleDaily().list(playerId);
    }

    @GetMapping("/build/recommend")
    public Map<String, Object> buildRecommend(
            @RequestParam long playerId, @RequestParam String characterId) {
        return openWorld.gameplay().buildRecommend().recommend(playerId, characterId);
    }

    @PostMapping("/build/apply")
    public Map<String, Object> buildApply(
            @RequestParam long playerId, @RequestParam String presetId) {
        return openWorld.gameplay().buildRecommend().applyPreset(playerId, presetId);
    }

    @PostMapping("/predict/action/start")
    public Map<String, Object> predictActionStart(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().clientPredict().predictStart(
                asLong(body.get("playerId")),
                String.valueOf(body.get("actionId")),
                cn.itcast.demo.mymmorpg.world.battle.ClientPredictedActionService.PredictedAction
                        .valueOf(String.valueOf(body.getOrDefault("action", "DODGE"))),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("speed", 8f)),
                asLong(body.getOrDefault("clientTs", System.currentTimeMillis())));
    }

    @PostMapping("/predict/action/reconcile")
    public Map<String, Object> predictActionReconcile(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().clientPredict().reconcile(
                asLong(body.get("playerId")),
                String.valueOf(body.get("actionId")),
                asLong(body.getOrDefault("clientTs", System.currentTimeMillis())),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("speed", 8f)),
                !Boolean.FALSE.equals(body.get("hitValid")),
                !Boolean.FALSE.equals(body.get("cooldownValid")));
    }

    @PostMapping("/combat/assist/device")
    public Map<String, Object> combatAssistDevice(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().combatAssist().registerDevice(
                asLong(body.get("playerId")),
                cn.itcast.demo.mymmorpg.world.battle.CombatAssistService.DeviceType
                        .valueOf(String.valueOf(body.getOrDefault("deviceType", "PC"))));
    }

    @PostMapping("/combat/assist/soft-lock")
    public Map<String, Object> combatAssistSoftLock(@RequestBody Map<String, Object> body) {
        if (body.containsKey("cameraYawDeg")) {
            return openWorld.gameplay().combatAssist().resolveSoftLock(
                    asLong(body.get("playerId")),
                    asFloat(body.get("cameraYawDeg")),
                    asFloat(body.get("aimYawDeg")),
                    asFloat(body.get("nearestEnemyYawDeg")),
                    asFloat(body.getOrDefault("distanceM", 10f)));
        }
        return openWorld.gameplay().combatAssist().resolveSoftLock(
                asLong(body.get("playerId")),
                asFloat(body.get("aimYawDeg")),
                asFloat(body.get("nearestEnemyYawDeg")),
                asFloat(body.getOrDefault("distanceM", 10f)));
    }

    @PostMapping("/combat/assist/aim")
    public Map<String, Object> combatAssistAim(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().combatAssist().predictiveAimAssist(
                asLong(body.get("playerId")),
                asFloat(body.get("aimYawDeg")),
                asFloat(body.get("predictedEnemyYawDeg")),
                asFloat(body.getOrDefault("enemyVx", 0f)),
                asFloat(body.getOrDefault("enemyVz", 0f)),
                asFloat(body.getOrDefault("distanceM", 10f)));
    }

    @PostMapping("/build/macro/save")
    public Map<String, Object> buildMacroSave(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> seq = body.get("skillSequence") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return openWorld.gameplay().buildRecommend().saveMacroCombo(
                asLong(body.get("playerId")),
                asInt(body.get("slotIndex"), 0),
                seq,
                System.currentTimeMillis());
    }

    @PostMapping("/build/macro/trigger")
    public Map<String, Object> buildMacroTrigger(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().buildRecommend().triggerMacro(
                asLong(body.get("playerId")),
                asInt(body.get("slotIndex"), 0),
                System.currentTimeMillis());
    }

    @PostMapping("/mutability/deterministic")
    public Map<String, Object> mutabilityDeterministic(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().deterministicMutation().issueLocalPlayback(
                String.valueOf(body.get("destroyableId")),
                System.currentTimeMillis(),
                asLong(body.getOrDefault("respawnCooldownMs", 300_000L)));
    }

    @GetMapping("/explore/vitality/daily")
    public Map<String, Object> exploreVitalityDaily(
            @RequestParam long playerId, @RequestParam String regionId) {
        return openWorld.gameplay().explorationVitality().refreshDaily(
                playerId, regionId, System.currentTimeMillis());
    }

    @PostMapping("/explore/vitality/complete")
    public Map<String, Object> exploreVitalityComplete(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().explorationVitality().completeSurvey(
                asLong(body.get("playerId")),
                String.valueOf(body.get("pointId")),
                System.currentTimeMillis());
    }

    @GetMapping("/explore/oculi/resonance")
    public Map<String, Object> oculiResonance(
            @RequestParam long playerId, @RequestParam int regionId) {
        return openWorld.gameplay().oculiResonance().evaluateResonance(
                playerId, regionId, System.currentTimeMillis());
    }

    @PostMapping("/camp/establish")
    public Map<String, Object> campEstablish(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> members = body.get("partyMemberIds") instanceof List<?> list
                ? list.stream().map(v -> asLong(v)).toList() : List.of();
        return openWorld.gameplay().coopCamp().establish(
                asLong(body.get("leaderId")), members,
                String.valueOf(body.get("regionId")),
                Boolean.TRUE.equals(body.getOrDefault("regionSafe", true)),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                System.currentTimeMillis());
    }

    @PostMapping("/camp/sign")
    public Map<String, Object> campSign(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().coopCamp().dailySign(
                asLong(body.get("playerId")),
                String.valueOf(body.get("campId")),
                System.currentTimeMillis());
    }

    @PostMapping("/phantom/borrow")
    public Map<String, Object> phantomBorrow(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().phantomBorrow().borrowPhantom(
                asLong(body.get("playerId")),
                String.valueOf(body.get("puzzleId")),
                asLong(body.get("friendPlayerId")));
    }

    @PostMapping("/social/token/grant")
    public Map<String, Object> socialTokenGrant(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().socialToken().grantAssist(
                asLong(body.get("helperId")),
                asLong(body.get("helpedId")),
                String.valueOf(body.getOrDefault("reason", "assist")));
    }

    @GetMapping("/rogue/weekly-affix")
    public Map<String, Object> rogueWeeklyAffix(@RequestParam(defaultValue = "1") long serverId) {
        return openWorld.gameplay().affixShuffle().weeklyAffixList(serverId, System.currentTimeMillis());
    }

    @PostMapping("/rogue/fate-echo/exchange")
    public Map<String, Object> rogueFateEchoExchange(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().rogueFate().exchangeFateEcho(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("mainStat", "CRIT_RATE")),
                asInt(body.getOrDefault("cost", 30), 30));
    }

    @PostMapping("/landmark/enter")
    public Map<String, Object> landmarkEnter(
            @RequestParam long playerId, @RequestParam String landmarkId) {
        return openWorld.gameplay().enterLandmark(playerId, landmarkId);
    }

    @PostMapping("/landmark/advance")
    public Map<String, Object> landmarkAdvance(
            @RequestParam long playerId, @RequestParam String landmarkId) {
        Map<String, Object> r = openWorld.gameplay().landmarks().advanceLayer(playerId, landmarkId);
        if (Boolean.TRUE.equals(r.get("cleared")) && r.get("puzzleBitIndex") instanceof Number bit) {
            openWorld.worldState().setPuzzleBit(1, playerId, bit.intValue(), true);
        }
        return r;
    }

    @PostMapping("/region/clear-camp")
    public Map<String, Object> clearCamp(
            @RequestParam String regionId, @RequestParam long playerId) {
        return openWorld.gameplay().regions().clearMonsterCamp(regionId, playerId);
    }

    @GetMapping("/region/status")
    public Map<String, Object> regionStatus(@RequestParam String regionId) {
        return openWorld.gameplay().regions().snapshot(regionId);
    }

    @PostMapping("/story/start")
    public Map<String, Object> storyStart(
            @RequestParam long playerId, @RequestParam String nodeId) {
        return openWorld.gameplay().story().start(playerId, nodeId);
    }

    @PostMapping("/story/choose")
    public Map<String, Object> storyChoose(
            @RequestParam long playerId, @RequestParam String choiceId) {
        return openWorld.gameplay().story().choose(playerId, choiceId);
    }

    @PostMapping("/encounter/try")
    public Map<String, Object> encounterTry(
            @RequestParam long playerId, @RequestParam(defaultValue = "1") int sceneId) {
        return openWorld.gameplay().encounters().tryTrigger(playerId, sceneId, System.currentTimeMillis());
    }

    @PostMapping("/encounter/force")
    public Map<String, Object> encounterForce(
            @RequestParam long playerId,
            @RequestParam String encounterId,
            @RequestParam(defaultValue = "1") int sceneId) {
        return openWorld.gameplay().encounters()
                .forceTrigger(playerId, encounterId, sceneId, System.currentTimeMillis());
    }

    @PostMapping("/explore-skill/switch")
    public Map<String, Object> switchExploreCharacter(
            @RequestParam long playerId, @RequestParam String characterId) {
        return openWorld.gameplay().exploreSkills().switchCharacter(playerId, characterId);
    }

    @PostMapping("/explore-skill/sense")
    public Map<String, Object> senseHidden(
            @RequestParam long playerId,
            @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> candidates = body != null && body.get("pointIds") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("chest-hidden-glyph");
        return openWorld.gameplay().exploreSkills().senseHidden(playerId, candidates);
    }

    @PostMapping("/traverse/unlock")
    public Map<String, Object> unlockTraverse(
            @RequestParam long playerId, @RequestParam String mode) {
        return openWorld.gameplay().traverse().unlock(
                playerId, cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService.Mode.valueOf(mode));
    }

    @PostMapping("/env/interact")
    public Map<String, Object> envInteract(@RequestBody Map<String, Object> body) {
        int hour = asInt(body.get("hourOfDay"), -1);
        if (hour < 0) {
            hour = openWorld.time().current(System.currentTimeMillis()).hourOfDay();
        }
        return openWorld.gameplay().envInteractWithRules(
                String.valueOf(body.get("objectId")),
                cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService.SkillElement
                        .valueOf(String.valueOf(body.getOrDefault("skill", "FIRE"))),
                asLong(body.get("playerId")),
                hour,
                System.currentTimeMillis());
    }

    @PostMapping("/surprise/try")
    public Map<String, Object> surpriseTry(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().surprises().tryDiscover(
                asLong(body.get("playerId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                body.get("action") == null ? null : String.valueOf(body.get("action")),
                asInt(body.get("hourOfDay"), 12),
                System.currentTimeMillis());
    }

    @PostMapping("/homestead/claim")
    public Map<String, Object> homesteadClaim(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().homestead().claimPlot(
                asLong(body.get("playerId")),
                String.valueOf(body.get("plotId")),
                asFloat(body.get("x")), asFloat(body.get("z")));
    }

    @PostMapping("/creature/catch")
    public Map<String, Object> creatureCatch(
            @RequestParam long playerId,
            @RequestParam String creatureId,
            @RequestParam(defaultValue = "20") int ballPower,
            @RequestParam(defaultValue = "item_catch_ball") String itemId,
            @RequestParam(defaultValue = "0") int worldLevel) {
        return openWorld.gameplay().creatures()
                .catchCreature(playerId, creatureId, itemId, ballPower, worldLevel,
                        System.currentTimeMillis());
    }

    @PostMapping("/creature/mount")
    public Map<String, Object> creatureMount(
            @RequestParam long playerId, @RequestParam String instanceId) {
        return openWorld.gameplay().creatures().mount(playerId, instanceId);
    }

    @PostMapping("/creature/tame")
    public Map<String, Object> creatureTame(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().creatures().tameAndName(
                asLong(body.get("playerId")),
                String.valueOf(body.get("instanceId")),
                String.valueOf(body.getOrDefault("displayName", "")));
    }

    @PostMapping("/leisure/play")
    public Map<String, Object> leisurePlay(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().leisure().play(
                asLong(body.get("playerId")),
                String.valueOf(body.get("zoneId")),
                asFloat(body.get("x")), asFloat(body.get("z")),
                asInt(body.get("inputScore"), 0));
    }

    @PostMapping("/extract/start")
    public Map<String, Object> extractStart(
            @RequestParam long playerId, @RequestParam String missionId) {
        return openWorld.gameplay().extraction()
                .start(playerId, missionId, System.currentTimeMillis());
    }

    @PostMapping("/extract/scout")
    public Map<String, Object> extractScout(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().extraction().scout(
                asLong(body.get("playerId")), asFloat(body.get("x")), asFloat(body.get("z")));
    }

    @PostMapping("/extract/assault")
    public Map<String, Object> extractAssault(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().extraction().assault(
                asLong(body.get("playerId")), asFloat(body.get("x")), asFloat(body.get("z")),
                asInt(body.get("lootGained"), 0));
    }

    @PostMapping("/extract/extract")
    public Map<String, Object> extractFinish(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().extraction().extract(
                asLong(body.get("playerId")), asFloat(body.get("x")), asFloat(body.get("z")));
    }

    @GetMapping("/time")
    public Map<String, Object> time() {
        return openWorld.time().toView(System.currentTimeMillis());
    }

    @PostMapping("/time/weather")
    public Map<String, Object> forceWeather(@RequestParam String weather) {
        openWorld.time().forceWeather(WorldTimeService.Weather.valueOf(weather));
        return openWorld.time().toView(System.currentTimeMillis());
    }

    @GetMapping("/npc")
    public Map<String, Object> npcs() {
        return Map.of("ok", true, "npcs", openWorld.npcs().snapshot());
    }

    @PostMapping("/route/upsert")
    public Map<String, Object> upsertRoute(@RequestBody Map<String, Object> body) {
        long now = System.currentTimeMillis();
        openWorld.routingTable().upsert(new GatewayRoutingTable.SceneNodeRoute(
                String.valueOf(body.get("nodeId")),
                String.valueOf(body.getOrDefault("host", "127.0.0.1")),
                asInt(body.get("port"), 8082),
                asInt(body.get("worldId"), 1),
                asInt(body.get("zoneId"), 1),
                asInt(body.get("cellMinX"), 0),
                asInt(body.get("cellMaxX"), 7),
                asInt(body.get("cellMinZ"), 0),
                asInt(body.get("cellMaxZ"), 7),
                now));
        return Map.of("ok", true);
    }

    @PostMapping("/migrate/prepare")
    public Map<String, Object> migratePrepare(@RequestBody Map<String, Object> body) {
        float x = asFloat(body.get("x"));
        float z = asFloat(body.get("z"));
        int worldId = asInt(body.get("worldId"), 1);
        int gridSize = asInt(body.get("gridSize"), 100);
        String fromNode = String.valueOf(body.getOrDefault("fromNodeId", "scene-local"));
        var lookup = openWorld.routingTable().lookup(worldId, x, z, gridSize, fromNode);
        return openWorld.handshake().prepare(
                asLong(body.get("playerId")),
                fromNode,
                lookup,
                asInt(body.get("sceneId"), worldId),
                asInt(body.get("lineId"), 1),
                x, asFloat(body.get("y")), z,
                asFloat(body.get("velocityX")),
                asFloat(body.get("velocityZ")),
                asFloat(body.get("facingYaw")));
    }

    @PostMapping("/migrate/commit")
    public Map<String, Object> migrateCommit(@RequestParam String migrationId) {
        return openWorld.handshake().commit(migrationId);
    }

    @PostMapping("/migrate/abort")
    public Map<String, Object> migrateAbort(@RequestParam String migrationId) {
        return openWorld.handshake().abort(migrationId);
    }

    @GetMapping("/gm/catalog")
    public Map<String, Object> gmCatalog() {
        return openWorld.gm().catalog();
    }

    @PostMapping("/gm/exec")
    public Map<String, Object> gmExec(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("command", ""));
        GmCommandDispatcher.Level level = GmCommandDispatcher.Level.valueOf(
                String.valueOf(body.getOrDefault("level", "ADMIN")));
        @SuppressWarnings("unchecked")
        Map<String, Object> args = body.get("args") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return openWorld.gm().dispatch(name, level, args);
    }

    @PostMapping("/load/mixed")
    public Map<String, Object> mixedLoad(
            @RequestParam(defaultValue = "500") int players,
            @RequestParam(defaultValue = "1000") int gatherOps,
            @RequestParam(defaultValue = "500") int combatLocks,
            @RequestParam(defaultValue = "5000") int moveQueries) {
        return openWorld.mixedLoadBenchmark(players, gatherOps, combatLocks, moveQueries);
    }

    // -------- P16 权威物理 / 生态图 / 探索热力 / 联机叙事 / 伙伴幽灵 / 断线快进 / 拍卖稳定 / 配置双缓冲 --------

    @PostMapping("/physics/hash/validate")
    public Map<String, Object> physicsHashValidate(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().physicsAuthority().validateHashAsync(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("physicsStateHash", "")),
                asFloat(body.getOrDefault("vx", 0f)),
                asFloat(body.getOrDefault("vy", 0f)),
                asFloat(body.getOrDefault("vz", 0f)),
                asFloat(body.getOrDefault("gravityScale", 1f)),
                asFloat(body.getOrDefault("nx", 0f)),
                asFloat(body.getOrDefault("ny", 1f)),
                asFloat(body.getOrDefault("nz", 0f)),
                Boolean.TRUE.equals(body.get("onIceSurface")));
    }

    @PostMapping("/physics/hash/validate-async")
    public Map<String, Object> physicsHashValidateAsync(@RequestBody Map<String, Object> body) {
        var future = openWorld.gameplay().physicsAuthority().validateHashAsyncFuture(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("physicsStateHash", "")),
                asFloat(body.getOrDefault("vx", 0f)),
                asFloat(body.getOrDefault("vy", 0f)),
                asFloat(body.getOrDefault("vz", 0f)),
                asFloat(body.getOrDefault("gravityScale", 1f)),
                asFloat(body.getOrDefault("nx", 0f)),
                asFloat(body.getOrDefault("ny", 1f)),
                asFloat(body.getOrDefault("nz", 0f)),
                Boolean.TRUE.equals(body.get("onIceSurface")));
        Map<String, Object> rsp = new LinkedHashMap<>();
        rsp.put("ok", true);
        rsp.put("submitted", true);
        rsp.put("async", true);
        future.whenComplete((result, err) -> {
            if (err != null) {
                org.slf4j.LoggerFactory.getLogger(InternalOpenWorldController.class)
                        .warn("[hyc] physics async validate failed: {}", err.getMessage());
            }
        });
        return rsp;
    }

    @PostMapping("/terrain/destroy-cliff")
    public Map<String, Object> terrainDestroyCliff(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().terrainTopology().validateDestroyCliff(
                asLong(body.get("playerId")),
                String.valueOf(body.get("cliffId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("aimDirX", 0f)),
                asFloat(body.getOrDefault("aimDirY", 0f)),
                asFloat(body.getOrDefault("aimDirZ", 1f)),
                cn.itcast.demo.mymmorpg.world.puzzle.TerrainTopologyGraph.DestroyTool.valueOf(
                        String.valueOf(body.getOrDefault("tool", "HEAVY_ATTACK"))),
                asInt(body.get("staminaCost"), 20),
                System.currentTimeMillis());
    }

    @PostMapping("/ecosystem/imbalance/ripple")
    public Map<String, Object> ecosystemImbalanceRipple(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ecoGraph().rippleImbalance(
                String.valueOf(body.get("regionId")),
                String.valueOf(body.get("species")),
                System.currentTimeMillis());
    }

    @PostMapping("/explore/balancer/tick")
    public Map<String, Object> exploreBalancerTick(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = body.get("cells") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        return openWorld.gameplay().explorationBalancer().balance(cells, System.currentTimeMillis());
    }

    @GetMapping("/explore/loot/heat-coeff")
    public Map<String, Object> exploreLootHeatCoeff(
            @RequestParam String regionId,
            @RequestParam int gridX,
            @RequestParam int gridZ) {
        return openWorld.gameplay().explorationBalancer().heatDensityCoefficient(
                regionId, gridX, gridZ, System.currentTimeMillis());
    }

    @PostMapping("/coop/narrative/room")
    public Map<String, Object> coopNarrativeRoom(@RequestBody Map<String, Object> body) {
        long host = asLong(body.get("hostPlayerId"));
        @SuppressWarnings("unchecked")
        List<Long> guests = body.get("guests") instanceof List<?> list
                ? list.stream().map(this::asLongSafe).toList() : List.of();
        long[] arr = guests.stream().mapToLong(Long::longValue).toArray();
        return openWorld.gameplay().coopNarrative().registerRoom(
                String.valueOf(body.get("roomId")), host, arr);
    }

    @PostMapping("/coop/narrative/assist")
    public Map<String, Object> coopNarrativeAssist(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().coopNarrative().recordCoopAssist(
                String.valueOf(body.get("roomId")),
                asLong(body.get("guestPlayerId")),
                String.valueOf(body.get("storyBossId")),
                System.currentTimeMillis());
    }

    @PostMapping("/coop/ownership/bind")
    public Map<String, Object> coopOwnershipBind(@RequestBody Map<String, Object> body) {
        long host = asLong(body.get("hostPlayerId"));
        @SuppressWarnings("unchecked")
        List<Long> members = body.get("members") instanceof List<?> list
                ? list.stream().map(this::asLongSafe).toList() : List.of();
        long[] arr = members.stream().mapToLong(Long::longValue).toArray();
        Map<String, Object> bind = openWorld.gameplay().worldOwnership().bindRoom(
                String.valueOf(body.get("roomId")), host, arr);
        openWorld.gameplay().coopElection().registerHost(String.valueOf(body.get("roomId")), host);
        return bind;
    }

    @PostMapping("/coop/host/disconnect")
    public Map<String, Object> coopHostDisconnect(@RequestBody Map<String, Object> body) {
        String roomId = String.valueOf(body.get("roomId"));
        long host = asLong(body.get("hostPlayerId"));
        long now = System.currentTimeMillis();
        openWorld.gameplay().serverShadow().saveRoomSnapshot(
                roomId, host,
                asLong(body.getOrDefault("bossHpRemain", 100_000L)),
                asLong(body.getOrDefault("bossHpMax", 100_000L)),
                String.valueOf(body.getOrDefault("gadgetBitmap", "0")),
                String.valueOf(body.getOrDefault("tideState", "IDLE")),
                now);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candMaps = body.get("candidates") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        List<cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService.Candidate> cands = candMaps.stream()
                .map(c -> new cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService.Candidate(
                        asLong(c.get("playerId")),
                        asLong(c.getOrDefault("pingMs", 50L)),
                        asInt(c.get("sceneActorLoad"), 1)))
                .toList();
        openWorld.gameplay().coopElection().updateCandidates(roomId, cands);
        return openWorld.gameplay().coopElection().onHostDisconnect(roomId, host, now);
    }

    @PostMapping("/coop/host/elect")
    public Map<String, Object> coopHostElect(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().coopElection().tryElect(
                String.valueOf(body.get("roomId")),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @GetMapping("/terrain/tsv")
    public Map<String, Object> terrainTsv(@RequestParam String regionId) {
        return openWorld.gameplay().terrainStateVector().snapshotZone(regionId);
    }

    // -------- P19：微观物理 / 文明时态 / 质量动量 / 感官 / 生态纪录片 --------

    @GetMapping("/feel/terrain-detail")
    public Map<String, Object> terrainDetailSnapshot(
            @RequestParam String zone,
            @RequestParam(defaultValue = "0") long nowMs) {
        long ts = nowMs > 0 ? nowMs : System.currentTimeMillis();
        return openWorld.gameplay().physicalDetail().syncAoiEnter(zone, ts);
    }

    @PostMapping("/feel/projectile-hit")
    public Map<String, Object> projectileHitDebris(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().physicalDetail().onProjectileHit(
                asLong(body.get("ownerId")),
                String.valueOf(body.getOrDefault("zone", "default")),
                String.valueOf(body.getOrDefault("debrisType", "ARROW")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("normalX", 0f)),
                asFloat(body.getOrDefault("normalY", 1f)),
                asFloat(body.getOrDefault("normalZ", 0f)),
                asFloat(body.getOrDefault("yaw", 0f)),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @GetMapping("/civilization/npc-state")
    public Map<String, Object> npcScheduleState(
            @RequestParam String npcId,
            @RequestParam(defaultValue = "0") long nowMs) {
        return openWorld.gameplay().civilizationSchedule().resolveNpcState(
                npcId, nowMs > 0 ? nowMs : System.currentTimeMillis());
    }

    @PostMapping("/civilization/shop-access")
    public Map<String, Object> shopAccessCheck(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().civilizationSchedule().checkShopAccess(
                String.valueOf(body.get("npcId")),
                asLong(body.get("playerId")),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @GetMapping("/time/game-clock")
    public Map<String, Object> gameClock(@RequestParam(defaultValue = "0") long nowMs) {
        return openWorld.gameplay().gameTimeKeeper().view(
                nowMs > 0 ? nowMs : System.currentTimeMillis());
    }

    @PostMapping("/physics/knockback")
    public Map<String, Object> physicsKnockBack(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().massMomentum().computeKnockBack(
                asLong(body.get("attackerId")),
                asLong(body.get("targetId")),
                body.get("attackerForce") instanceof Number n ? n.doubleValue() : 40d,
                body.get("groundFriction") instanceof Number f ? f.doubleValue() : 0.2,
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @PostMapping("/physics/grab")
    public Map<String, Object> grabEntity(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().grabThrow().tryGrab(
                asLong(body.get("playerId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @PostMapping("/physics/throw")
    public Map<String, Object> throwEntity(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().grabThrow().throwHeld(
                asLong(body.get("playerId")),
                asFloat(body.getOrDefault("dirX", 0f)),
                asFloat(body.getOrDefault("dirY", 0.5f)),
                asFloat(body.getOrDefault("dirZ", 1f)),
                asFloat(body.getOrDefault("throwSpeed", 12f)),
                asFloat(body.get("targetX")), asFloat(body.get("targetY")), asFloat(body.get("targetZ")),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @GetMapping("/perception/modifiers")
    public Map<String, Object> perceptionModifiers(
            @RequestParam long playerId,
            @RequestParam(defaultValue = "") String regionId,
            @RequestParam(defaultValue = "CLEAR") String weather,
            @RequestParam(defaultValue = "OPEN") String biome,
            @RequestParam(defaultValue = "0") int echoDelayMs,
            @RequestParam(defaultValue = "false") boolean enteringDark) {
        return openWorld.gameplay().perceptionModifier().resolveForPlayer(
                playerId, regionId,
                WorldTimeService.Weather.valueOf(weather),
                biome, echoDelayMs, enteringDark, System.currentTimeMillis());
    }

    @PostMapping("/eco/tableau/scan")
    public Map<String, Object> ecoTableauScan(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ecologicalTableau().scanPredatorPrey(
                String.valueOf(body.getOrDefault("regionId", "wolf-camp-valley")),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @PostMapping("/eco/tableau/pass-tree")
    public Map<String, Object> ecoPassTree(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().ecologicalTableau().onPassLandmarkTree(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("treeId", "ancient-tree-1")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asInt(body.get("hourOfDay"), 15),
                asInt(body.get("dayOfYear"), 1),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @PostMapping("/coop/terrain-snapshot/save")
    public Map<String, Object> saveCoopTerrainSnapshot(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> uids = body.get("destroyedStaticMeshUids") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String roomId = String.valueOf(body.get("roomId"));
        long revision = asLong(body.getOrDefault("revision", 0L));
        long nowMs = asLong(body.getOrDefault("nowMs", System.currentTimeMillis()));
        openWorld.gameplay().coopTerrainSnapshot().save(roomId, uids, revision, nowMs);
        openWorld.gameplay().terrainStateVector().setDestroyedMeshes(
                roomId, uids);
        return openWorld.gameplay().coopTerrainSnapshot().loadView(roomId, nowMs);
    }

    @GetMapping("/coop/terrain-snapshot/recompose")
    public Map<String, Object> coopWorldRecompose(@RequestParam String roomId) {
        return openWorld.gameplay().coopTerrainSnapshot().awaitWorldRecompose(
                roomId, System.currentTimeMillis());
    }

    @PostMapping("/predict/heartbeat-env")
    public Map<String, Object> predictHeartbeatEnv(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().clientPredict().heartbeatWithEnvironment(
                asLong(body.get("playerId")),
                String.valueOf(body.getOrDefault("biome", "OPEN")),
                asInt(body.get("echoDelayMs"), 0),
                asLong(body.getOrDefault("nowMs", System.currentTimeMillis())));
    }

    @PostMapping("/move/admit-tsv")
    public Map<String, Object> moveAdmitTsv(@RequestBody Map<String, Object> body) {
        cn.itcast.demo.mymmorpg.sync.SceneMoveCmd cmd = cn.itcast.demo.mymmorpg.sync.SceneMoveCmd.walk(
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")),
                asFloat(body.getOrDefault("speed", 5f)),
                asLong(body.getOrDefault("timestamp", System.currentTimeMillis())));
        return openWorld.gameplay().movementAdmission().admitWithTerrainRevision(
                asLong(body.get("playerId")), cmd,
                asLong(body.getOrDefault("durationMs", 100L)),
                System.currentTimeMillis(),
                asFloat(body.get("fromX")), asFloat(body.get("fromY")), asFloat(body.get("fromZ")),
                String.valueOf(body.getOrDefault("regionId", "")),
                asInt(body.get("terrainCellX"), 0),
                asInt(body.get("terrainCellY"), 0),
                asLong(body.getOrDefault("terrainTtlMs", 60_000L)),
                asLong(body.getOrDefault("clientTerrainRevision", 0L)));
    }

    @PostMapping("/bullet-time/local")
    public Map<String, Object> bulletTimeLocal(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.Set<Long> ignore = body.get("ignoredEntityIds") instanceof List<?> list
                ? list.stream().map(this::asLongSafe).collect(java.util.stream.Collectors.toSet())
                : java.util.Set.of();
        Map<String, Object> start = openWorld.gameplay().bulletTime().startLocal(
                String.valueOf(body.getOrDefault("battleId", "local")),
                asLong(body.get("playerId")),
                asLong(body.get("attackerEntityId")),
                asLong(body.get("lockedBossEntityId")),
                System.currentTimeMillis(),
                asLong(body.getOrDefault("durationMs", 1500L)),
                body.get("timeScale") instanceof Number n ? n.doubleValue() : 0.1d,
                ignore);
        Map<String, Object> observer = openWorld.gameplay().bulletTime().notifyObserver(
                String.valueOf(body.getOrDefault("battleId", "local")),
                asLong(body.getOrDefault("observerPlayerId", body.get("playerId"))),
                System.currentTimeMillis());
        Map<String, Object> out = new java.util.LinkedHashMap<>(start);
        out.put("observerNotify", observer);
        return out;
    }

    @PostMapping("/fight/instance-loot")
    public Map<String, Object> fightInstanceLoot(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().fightContribution().grantInstanceLoot(
                asLong(body.get("battleId")),
                asLong(body.get("playerId")),
                String.valueOf(body.get("itemId")),
                asInt(body.get("count"), 1));
    }

    @PostMapping("/social/token/redeem-specialty")
    public Map<String, Object> redeemHostSpecialty(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().socialToken().redeemHostSpecialty(
                asLong(body.get("guestPlayerId")),
                asLong(body.get("hostPlayerId")),
                String.valueOf(body.get("specialtyItemId")));
    }

    @PostMapping("/companion/spawn")
    public Map<String, Object> companionSpawn(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().companionGhost().spawn(
                asLong(body.get("ownerPlayerId")),
                asLong(body.get("companionEntityId")),
                asFloat(body.get("x")), asFloat(body.get("y")), asFloat(body.get("z")));
    }

    @PostMapping("/companion/follow-path")
    public Map<String, Object> companionFollowPath(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().companionGhost().issueFollowPath(
                asLong(body.get("companionEntityId")),
                asFloat(body.get("ownerX")), asFloat(body.get("ownerY")), asFloat(body.get("ownerZ")),
                System.currentTimeMillis());
    }

    @PostMapping("/shadow/recover")
    public Map<String, Object> shadowRecover(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frames = body.get("clientQueue") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        List<cn.itcast.demo.mymmorpg.sync.ServerShadowService.PredictedActionFrame> queue = frames.stream()
                .map(f -> new cn.itcast.demo.mymmorpg.sync.ServerShadowService.PredictedActionFrame(
                        String.valueOf(f.getOrDefault("actionId", "")),
                        String.valueOf(f.getOrDefault("action", "DODGE")),
                        asFloat(f.get("x")), asFloat(f.get("y")), asFloat(f.get("z")),
                        asFloat(f.getOrDefault("speed", 8f)),
                        asLong(f.getOrDefault("clientTs", System.currentTimeMillis()))))
                .toList();
        long playerId = asLong(body.get("playerId"));
        if (Boolean.TRUE.equals(body.get("markDisconnect"))) {
            openWorld.gameplay().serverShadow().markDisconnect(
                    playerId, asLong(body.getOrDefault("disconnectAtMs",
                            System.currentTimeMillis() - 35_000L)));
        }
        return openWorld.gameplay().serverShadow().recoverWithFastForward(
                playerId, System.currentTimeMillis(), queue);
    }

    @PostMapping("/auction/list-stable")
    public Map<String, Object> auctionListStable(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().auctionHouse().listBuyout(
                asLong(body.get("sellerId")),
                String.valueOf(body.get("itemId")),
                asInt(body.get("count"), 1),
                asLong(body.get("price")),
                System.currentTimeMillis(),
                String.valueOf(body.getOrDefault("regionShardId", "local")),
                Boolean.TRUE.equals(body.get("crossShard")),
                body.get("priceReason") == null ? null : String.valueOf(body.get("priceReason")));
    }

    @PostMapping("/config/stage")
    public Map<String, Object> configStage(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return openWorld.gameplay().configPatch().stagePatch(
                String.valueOf(body.get("gridCell")),
                String.valueOf(body.getOrDefault("kind", "GENERIC")),
                payload,
                String.valueOf(body.get("gitCommitSha")),
                body.get("configVersion") == null ? null : String.valueOf(body.get("configVersion")));
    }

    @PostMapping("/config/publish")
    public Map<String, Object> configPublish() {
        return openWorld.gameplay().configPatch().publishStaging(System.currentTimeMillis());
    }

    @PostMapping("/config/gray-publish")
    public Map<String, Object> configGrayPublish(@RequestBody Map<String, Object> body) {
        cn.itcast.demo.mymmorpg.world.content.GrayConditions cond =
                cn.itcast.demo.mymmorpg.world.content.GrayConditions.fromMap(body);
        return openWorld.gameplay().configPatch().publishStaging(cond, System.currentTimeMillis());
    }

    @PostMapping("/config/gray-rollback")
    public Map<String, Object> configGrayRollback(@RequestBody(required = false) Map<String, Object> body) {
        String label = body == null || body.get("grayLabel") == null
                ? null : String.valueOf(body.get("grayLabel"));
        return openWorld.gameplay().configPatch().rollbackGray(label);
    }

    @PostMapping("/config/snapshot-instance")
    public Map<String, Object> configSnapshotInstance(@RequestBody Map<String, Object> body) {
        return openWorld.gameplay().configPatch().snapshotForInstance(
                String.valueOf(body.get("sceneInstanceId")),
                System.currentTimeMillis());
    }

    @PostMapping("/hit/verdict")
    public Map<String, Object> hitVerdict(@RequestBody Map<String, Object> body) {
        var verdict = cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService.PredictionVerdict
                .valueOf(String.valueOf(body.getOrDefault("verdict", "CONFIRMED_HIT")));
        float scale = openWorld.gameplay().combatAssist().hitboxScale(asLong(body.get("playerId")));
        Map<String, Object> fb = openWorld.gameplay().hitFeedback().buildFeedback(
                asLong(body.get("attackerId")),
                asLong(body.get("targetId")),
                asFloat(body.getOrDefault("poiseDamage", 10f)),
                Boolean.TRUE.equals(body.get("heavyOrFall")),
                asFloat(body.getOrDefault("targetPoiseRemainRatio", 0.5f)),
                verdict,
                body.get("compensateEffectId") == null ? null : String.valueOf(body.get("compensateEffectId")),
                scale);
        if (verdict == cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService.PredictionVerdict.SOFT_ROLLBACK) {
            openWorld.gameplay().moveTrajectory().armElasticBuffer(
                    asLong(body.get("playerId")), System.currentTimeMillis());
            fb.put("elasticBufferMs", 1_000L);
        }
        return fb;
    }

    private long asLongSafe(Object v) {
        return asLong(v);
    }

    @PostMapping("/aoi/octree-stress")
    public Map<String, Object> octreeStress(
            @RequestParam(defaultValue = "2000") int entityCount,
            @RequestParam(defaultValue = "5000") int queryCount,
            @RequestParam(defaultValue = "300") float radius) {
        return openWorld.aoi3dCompare(entityCount, queryCount, radius);
    }

    private static int asInt(Object v, int dft) {
        if (v == null) {
            return dft;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static float asFloat(Object v) {
        if (v instanceof Number n) {
            return n.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (Exception e) {
            return 0f;
        }
    }
}
