package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.ActionState;
import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.sync.MoveIntent;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.narrative.CoopNarrativeProxy;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainStateVector;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物理准入校验：攀爬网格 / 滑翔风场 / 钩锁 / 壁走法线 / 体力阈值 / 已解锁移动手段 / 地形冷却。
 */
@Service
public class MovementAdmissionService {

    public static final float WALL_RUN_MAX_ANGLE_DEG = 30f;
    public static final long AIR_COMBO_WINDOW_MS = 1_500L;
    public static final int AIR_COMBO_MAX_HITS = 3;
    public static final float CLIMB_HANG_STAMINA_THRESHOLD = 5f;
    public static final float CLIMB_HANG_SPEED_THRESHOLD = 0.5f;
    public static final long CLIMB_HANG_MAX_MS = 3_000L;
    public static final float VAULT_EDGE_DISTANCE_M = 0.5f;

    public record ClimbableMesh(String meshId, int worldId, float x, float y, float z,
                                float radius, float staminaCostPerSec) {
        public ClimbableMesh {
            meshId = meshId == null ? "" : meshId.trim();
            radius = radius <= 0f ? 6f : radius;
            staminaCostPerSec = staminaCostPerSec <= 0f ? 10f : staminaCostPerSec;
        }
    }

    public record WindField(String fieldId, int worldId, float x, float y, float z,
                            float radius, float minStaminaToEnter) {
        public WindField {
            fieldId = fieldId == null ? "" : fieldId.trim();
            radius = radius <= 0f ? 20f : radius;
            minStaminaToEnter = minStaminaToEnter <= 0f ? 15f : minStaminaToEnter;
        }
    }

    private final ConcurrentHashMap<String, ClimbableMesh> climbMeshes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindField> windFields = new ConcurrentHashMap<>();
    private final TraverseModeService traverseModes;
    private final StaminaConsumeService stamina;
    private final GrappleNodeService grappleNodes;
    private final UnderwaterPhysicsService underwater;
    private WorldMutabilityService.TerrainInteractionTracker terrainTracker;
    private ClimbRestPointService climbRestPoints;
    private UniversalTraversalService universalTraversal;
    private MoveTrajectoryValidator trajectoryValidator;
    private InputConfidenceAnalyzer inputConfidence;
    private TerrainStateVector terrainStateVector;
    private CoopNarrativeProxy coopNarrative;
    /** 空中连击：playerId → 轻击时间戳队列 */
    private final ConcurrentHashMap<Long, Deque<Long>> airComboHits = new ConcurrentHashMap<>();
    /** 挂边状态：playerId → 挂边开始时间 */
    private final ConcurrentHashMap<Long, Long> climbHangSince = new ConcurrentHashMap<>();

    public MovementAdmissionService() {
        this(new TraverseModeService(), new StaminaConsumeService(), new GrappleNodeService(),
                new UnderwaterPhysicsService());
    }

    public MovementAdmissionService(TraverseModeService traverseModes, StaminaConsumeService stamina) {
        this(traverseModes, stamina, new GrappleNodeService(), new UnderwaterPhysicsService());
    }

    public MovementAdmissionService(
            TraverseModeService traverseModes, StaminaConsumeService stamina, GrappleNodeService grappleNodes) {
        this(traverseModes, stamina, grappleNodes, new UnderwaterPhysicsService());
    }

    public MovementAdmissionService(
            TraverseModeService traverseModes, StaminaConsumeService stamina, GrappleNodeService grappleNodes,
            UnderwaterPhysicsService underwater) {
        this.traverseModes = traverseModes == null ? new TraverseModeService() : traverseModes;
        this.stamina = stamina == null ? new StaminaConsumeService() : stamina;
        this.grappleNodes = grappleNodes == null ? new GrappleNodeService() : grappleNodes;
        this.underwater = underwater == null ? new UnderwaterPhysicsService() : underwater;
    }

    public void bindTerrainTracker(WorldMutabilityService.TerrainInteractionTracker tracker) {
        this.terrainTracker = tracker;
    }

    public void bindClimbRestPoints(ClimbRestPointService restPoints) {
        this.climbRestPoints = restPoints;
    }

    public void bindUniversalTraversal(UniversalTraversalService universal) {
        this.universalTraversal = universal;
    }

    public void bindTrajectoryValidator(MoveTrajectoryValidator validator) {
        this.trajectoryValidator = validator;
    }

    public void bindInputConfidence(InputConfidenceAnalyzer analyzer) {
        this.inputConfidence = analyzer;
    }

    public void bindTerrainStateVector(TerrainStateVector tsv) {
        this.terrainStateVector = tsv;
    }

    public void bindCoopNarrative(CoopNarrativeProxy narrative) {
        this.coopNarrative = narrative;
    }

    /**
     * 移动准入（带地形 revision）：落后则 TERRAIN_STATE_MISMATCH 并软拉回。
     */
    public Map<String, Object> admitWithTerrainRevision(
            long playerId, SceneMoveCmd cmd, long durationMs, long nowMs,
            float fromX, float fromY, float fromZ,
            String regionId, int terrainCellX, int terrainCellY, long terrainTtlMs,
            long clientTerrainRevision) {
        if (coopNarrative != null && coopNarrative.isMoveBlocked(playerId)) {
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("ok", false);
            blocked.put("error", "cutscene_move_blocked");
            blocked.put("retcode", RetCode.CUTSCENE_MOVE_BLOCKED);
            blocked.put("hint", "队友正在经历关键剧情，移动输入已暂停");
            return blocked;
        }
        if (terrainStateVector != null && regionId != null && !regionId.isBlank()
                && (terrainCellX != 0 || terrainCellY != 0)) {
            Map<String, Object> rev = terrainStateVector.validateMoveRevision(
                    regionId, terrainCellX, terrainCellY, clientTerrainRevision);
            if (Boolean.TRUE.equals(rev.get("mismatch"))) {
                Map<String, Object> mismatch = new LinkedHashMap<>(rev);
                mismatch.put("ok", false);
                mismatch.put("retcode", RetCode.TERRAIN_STATE_MISMATCH);
                mismatch.put("softPullback", true);
                mismatch.put("tsv", terrainStateVector.snapshotZone(regionId));
                return mismatch;
            }
        }
        return admit(playerId, cmd, durationMs, nowMs, fromX, fromY, fromZ,
                regionId, terrainCellX, terrainCellY, terrainTtlMs);
    }

    private boolean hasTraverseMode(long playerId, TraverseModeService.Mode mode) {
        if (universalTraversal != null && universalTraversal.canUseMode(playerId, mode)) {
            return true;
        }
        return traverseModes.has(playerId, mode);
    }

    public WorldMutabilityService.TerrainInteractionTracker terrainTracker() {
        return terrainTracker;
    }

    public void registerClimbable(ClimbableMesh mesh) {
        if (mesh != null && !mesh.meshId().isBlank()) {
            climbMeshes.put(mesh.meshId(), mesh);
        }
    }

    public void registerWindField(WindField field) {
        if (field != null && !field.fieldId().isBlank()) {
            windFields.put(field.fieldId(), field);
        }
    }

    /**
     * 准入：解锁校验 → 攀爬网格/滑翔风场/钩锁/壁走 → 体力阶梯扣除。
     */
    public Map<String, Object> admit(long playerId, SceneMoveCmd cmd, long durationMs, long nowMs) {
        return admit(playerId, cmd, durationMs, nowMs, 0f, 0f, 0f);
    }

    public Map<String, Object> admit(
            long playerId, SceneMoveCmd cmd, long durationMs, long nowMs,
            float fromX, float fromY, float fromZ) {
        return admit(playerId, cmd, durationMs, nowMs, fromX, fromY, fromZ, null, 0, 0, 0);
    }

    /**
     * @param regionId 非空且 movement 为 BOUNCE/风场弹射时，校验地形冷却
     * @param terrainCellX / terrainCellY 格子坐标；均为 0 且 regionId 为空时跳过
     * @param terrainTtlMs 冷却 TTL，默认 60s
     */
    public Map<String, Object> admit(
            long playerId, SceneMoveCmd cmd, long durationMs, long nowMs,
            float fromX, float fromY, float fromZ,
            String regionId, int terrainCellX, int terrainCellY, long terrainTtlMs) {
        return admit(playerId, cmd, durationMs, nowMs, fromX, fromY, fromZ,
                regionId, terrainCellX, terrainCellY, terrainTtlMs, "PC");
    }

    public Map<String, Object> admit(
            long playerId, SceneMoveCmd cmd, long durationMs, long nowMs,
            float fromX, float fromY, float fromZ,
            String regionId, int terrainCellX, int terrainCellY, long terrainTtlMs,
            String deviceType) {
        if (cmd == null) {
            return Map.of("ok", false, "error", "cmd_required");
        }
        if (coopNarrative != null && coopNarrative.isMoveBlocked(playerId)) {
            return Map.of("ok", false, "error", "cutscene_move_blocked",
                    "retcode", RetCode.CUTSCENE_MOVE_BLOCKED);
        }
        Map<String, Object> inputConfidenceMeta = null;
        if (inputConfidence != null) {
            Map<String, Object> input = inputConfidence.analyze(
                    playerId, deviceType, fromX, fromZ, cmd.targetX(), cmd.targetZ(), nowMs,
                    cmd.clientDeltaMs());
            if (Boolean.TRUE.equals(input.get("inputDebounced"))) {
                Map<String, Object> debounced = new LinkedHashMap<>(input);
                debounced.put("ok", false);
                debounced.put("error", "input_debounced");
                return debounced;
            }
            inputConfidenceMeta = input;
        }
        Map<String, Object> actionCheck = validateActionState(playerId, cmd, nowMs);
        if (!Boolean.TRUE.equals(actionCheck.get("ok"))) {
            return actionCheck;
        }
        float[] remapped = remapByCamera(cmd, fromX, fromY, fromZ);
        float trajToX = remapped[0];
        float trajToY = remapped[1];
        float trajToZ = remapped[2];
        if (trajectoryValidator != null) {
            MoveTrajectoryValidator.ValidationResult traj = trajectoryValidator.validate(
                    playerId, fromX, fromY, fromZ,
                    trajToX, trajToY, trajToZ, cmd.speed(), nowMs, cmd.cameraYaw());
            if (!traj.accepted()) {
                return Map.of("ok", false, "error", "trajectory_rejected",
                        "reason", traj.reason(), "maxAngleDeg", traj.maxAngleDeg(),
                        "hardReject", traj.hardReject());
            }
        }
        MovementType type = cmd.movementType();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("movementType", type.name());
        body.put("moveFlags", cmd.moveFlags());
        body.put("actionState", cmd.actionState().name());
        if (inputConfidenceMeta != null) {
            body.put("effectiveWindowMs", inputConfidenceMeta.get("effectiveWindowMs"));
            body.put("clientDeltaMs", inputConfidenceMeta.get("clientDeltaMs"));
        }
        if (remapped[3] != 0f) {
            body.put("dashVariant", remapped[3] == -1f ? "DASH_BACKWARD" : "DASH_FORWARD");
        }
        body.putAll(actionCheck);

        if (type == MovementType.CLIMB_HANG) {
            return admitClimbHang(playerId, cmd, durationMs, nowMs, body);
        }
        if (type == MovementType.CLIMB_VAULT) {
            return admitClimbVault(playerId, cmd, fromX, fromY, fromZ, body);
        }

        boolean usesTerrain = type == MovementType.BOUNCE
                || (type == MovementType.GLIDE && regionId != null && !regionId.isBlank()
                && (terrainCellX != 0 || terrainCellY != 0));
        if (usesTerrain && terrainTracker != null && regionId != null && !regionId.isBlank()) {
            WorldMutabilityService.TerrainKind kind = type == MovementType.BOUNCE
                    ? WorldMutabilityService.TerrainKind.BOUNCE_MUSHROOM
                    : WorldMutabilityService.TerrainKind.WIND_FIELD;
            Map<String, Object> terrain = terrainTracker.tryConsume(
                    regionId, terrainCellX, terrainCellY, kind, terrainTtlMs, nowMs);
            if (!Boolean.TRUE.equals(terrain.get("ok"))) {
                body.putAll(terrain);
                body.put("ok", false);
                body.put("retcode", RetCode.TERRAIN_EXHAUSTED);
                body.put("staminaConsumed", 0f);
                body.put("stamina", stamina.current(playerId));
                return body;
            }
            body.put("terrain", terrain);
        }

        if (type == MovementType.BOUNCE) {
            // 弹射成功：不额外扣大额体力（蘑菇本身提供动量）
            body.put("ok", true);
            body.put("bounce", true);
            body.put("stamina", stamina.current(playerId));
            body.put("consumed", 0f);
            return body;
        }

        if (MoveFlags.has(cmd.moveFlags(), MoveFlags.MID_AIR_DASH) || type == MovementType.AIR_DASH) {
            Map<String, Object> cost = stamina.consumeFixed(playerId, MovementType.AIR_DASH, 20f, nowMs);
            body.putAll(cost);
            if (!Boolean.TRUE.equals(cost.get("ok"))) {
                return body;
            }
            body.put("ok", true);
            body.put("flag", "MID_AIR_DASH");
            return body;
        }

        if (MoveFlags.has(cmd.moveFlags(), MoveFlags.WALL_RUN) || type == MovementType.WALL_RUN) {
            float[] normal = new float[]{cmd.wallNormalX(), cmd.wallNormalY(), cmd.wallNormalZ()};
            if (!isValidWallNormal(normal, cmd.speed(), cmd.targetX() - fromX, cmd.targetZ() - fromZ)) {
                return Map.of("ok", false, "error", "wall_angle_invalid",
                        "maxAngleDeg", WALL_RUN_MAX_ANGLE_DEG, "movementType", type.name());
            }
            Map<String, Object> cost = stamina.consume(playerId, MovementType.WALL_RUN, durationMs, 1f, nowMs);
            body.putAll(cost);
            if (!Boolean.TRUE.equals(cost.get("ok"))) {
                return body;
            }
            body.put("wallNormal", Map.of("x", normal[0], "y", normal[1], "z", normal[2]));
            body.put("ok", true);
            return body;
        }

        if (type == MovementType.GRAPPLE) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.HOOK)
                    && !hasTraverseMode(playerId, TraverseModeService.Mode.GRAPPLE)) {
                return Map.of("ok", false, "error", "grapple_locked", "movementType", type.name());
            }
            Map<String, Object> g = grappleNodes.admitGrapple(
                    playerId, cmd.grappleNodeId(),
                    fromX, fromY, fromZ,
                    cmd.targetX(), cmd.targetY(), cmd.targetZ(), nowMs);
            body.putAll(g);
            if (!Boolean.TRUE.equals(g.get("ok"))) {
                return body;
            }
            Map<String, Object> cost = stamina.consume(playerId, MovementType.GRAPPLE, durationMs, 1f, nowMs);
            body.putAll(cost);
            return body;
        }

        if (type == MovementType.RIDE) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.VEHICLE)
                    && !hasTraverseMode(playerId, TraverseModeService.Mode.RIDE)) {
                return Map.of("ok", false, "error", "ride_locked", "movementType", type.name());
            }
            if (cmd.mountCreatureUid() != null && !cmd.mountCreatureUid().isBlank()) {
                body.put("mountCreatureUid", cmd.mountCreatureUid());
            }
            Map<String, Object> cost = stamina.consume(playerId, MovementType.RIDE, durationMs, 0.6f, nowMs);
            body.putAll(cost);
            return body;
        }

        if (type == MovementType.CLIMB) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.CLIMB)) {
                return Map.of("ok", false, "error", "climb_locked", "movementType", type.name());
            }
            ClimbableMesh mesh = resolveClimbMesh(cmd);
            if (mesh == null) {
                return Map.of("ok", false, "error", "climbable_mesh_required",
                        "movementType", type.name());
            }
            float dx = cmd.targetX() - mesh.x();
            float dy = cmd.targetY() - mesh.y();
            float dz = cmd.targetZ() - mesh.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > mesh.radius()) {
                return Map.of("ok", false, "error", "out_of_climb_mesh",
                        "climbableMeshId", mesh.meshId(), "movementType", type.name());
            }
            body.put("climbableMeshId", mesh.meshId());
            body.put("staminaCostPerSec", mesh.staminaCostPerSec());
            if (climbRestPoints != null) {
                Map<String, Object> rest = climbRestPoints.tryRest(
                        playerId, mesh.meshId(), cmd.targetX(), cmd.targetY(), cmd.targetZ(), stamina);
                if (Boolean.TRUE.equals(rest.get("onRestPoint"))) {
                    body.putAll(rest);
                    body.put("ok", true);
                    body.put("consumed", 0f);
                    body.put("stamina", stamina.current(playerId));
                    return body;
                }
            }
            float scale = mesh.staminaCostPerSec() / 10f;
            if (climbRestPoints != null && climbRestPoints.hasParkourSkill(playerId)) {
                scale *= 0.75f;
            }
            boolean climbJump = MoveFlags.has(cmd.moveFlags(), MoveFlags.WALL_KICK);
            if (climbJump && climbRestPoints != null) {
                Map<String, Object> restJump = climbRestPoints.consumeRestJump(playerId, stamina, nowMs);
                if (Boolean.TRUE.equals(restJump.get("restJump"))) {
                    body.putAll(restJump);
                    body.put("ok", true);
                    body.put("movementType", type.name());
                    return body;
                }
            }
            if (climbRestPoints != null && climbRestPoints.inNegativeStaminaWindow(playerId, nowMs)) {
                float cost = scale * Math.max(0.016f, durationMs / 1000f) * 10f;
                stamina.consumeAllowNegative(playerId, cost);
                body.put("ok", true);
                body.put("negativeStaminaAllowed", true);
                body.put("stamina", stamina.current(playerId));
                body.put("consumed", cost);
                return body;
            }
            Map<String, Object> cost = stamina.consume(playerId, type, durationMs, scale, nowMs);
            body.putAll(cost);
            if (Boolean.TRUE.equals(cost.get("ok")) && stamina.current(playerId) < CLIMB_HANG_STAMINA_THRESHOLD
                    && cmd.speed() < CLIMB_HANG_SPEED_THRESHOLD) {
                body.put("suggestClimbHang", true);
                body.put("movementType", MovementType.CLIMB_HANG.name());
            }
            return body;
        }

        if (type == MovementType.GLIDE) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.GLIDE)
                    && !hasTraverseMode(playerId, TraverseModeService.Mode.WIND_FIELD)) {
                return Map.of("ok", false, "error", "glide_locked", "movementType", type.name());
            }
            boolean inWind = isInWindField(cmd.targetX(), cmd.targetY(), cmd.targetZ());
            float cur = stamina.current(playerId);
            boolean glideOk = inWind || cur >= 15f;
            body.put("isGlidingAvailable", glideOk);
            body.put("inWindField", inWind);
            if (!glideOk) {
                body.put("ok", false);
                body.put("error", "glide_unavailable");
                body.put("stamina", cur);
                return body;
            }
            Map<String, Object> cost = stamina.consume(playerId, type, durationMs,
                    inWind ? 0.5f : 1f, nowMs);
            body.putAll(cost);
            return body;
        }

        if (type == MovementType.SWIM) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.SWIM)) {
                return Map.of("ok", false, "error", "swim_locked", "movementType", type.name());
            }
            Map<String, Object> cost = stamina.consume(playerId, type, durationMs, 1f, nowMs);
            body.putAll(cost);
            return body;
        }

        if (type == MovementType.SWING) {
            if (!hasTraverseMode(playerId, TraverseModeService.Mode.HOOK)) {
                return Map.of("ok", false, "error", "swing_locked", "movementType", type.name());
            }
            Map<String, Object> cost = stamina.consume(playerId, type, durationMs, 1f, nowMs);
            body.putAll(cost);
            return body;
        }

        if (type == MovementType.DASH) {
            Map<String, Object> cost = stamina.consume(playerId, type, durationMs, 1f, nowMs);
            body.putAll(cost);
            if (!Boolean.TRUE.equals(cost.get("ok")) && cost.containsKey("ok")) {
                return body;
            }
            float requested = Math.max(1f, Math.abs(cmd.speed()) * Math.max(1f, durationMs / 1000f));
            if (requested < 1f) {
                requested = 8f;
            }
            Map<String, Object> dashScale = underwater.scaleDashDistance(playerId, requested);
            body.put("dashDistance", dashScale);
            body.put("underwater", underwater.isUnderwater(playerId));
            if (!body.containsKey("ok")) {
                body.put("ok", true);
            }
            return body;
        }
        body.put("ok", true);
        body.put("stamina", stamina.current(playerId));
        body.put("consumed", 0f);
        return body;
    }

    /** 壁走：墙面法线与水平速度方向夹角须 &lt; 30°。 */
    static boolean isValidWallNormal(float[] normal, float speedHint, float velX, float velZ) {
        float nx = normal[0];
        float ny = normal[1];
        float nz = normal[2];
        double nLen = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nLen < 0.1) {
            return false;
        }
        nx /= (float) nLen;
        ny /= (float) nLen;
        nz /= (float) nLen;
        // 接近竖直墙（法线接近水平）
        if (Math.abs(ny) > 0.5f) {
            return false;
        }
        double vLen = Math.sqrt(velX * velX + velZ * velZ);
        if (vLen < 0.01 && Math.abs(speedHint) < 0.01) {
            return false;
        }
        if (vLen < 0.01) {
            return true; // 仅法线合格时由客户端速度补齐
        }
        double vx = velX / vLen;
        double vz = velZ / vLen;
        // 速度应近似垂直于法线（沿墙），夹角于法线应接近 90°，即与墙切向夹角小
        double dot = nx * vx + nz * vz;
        double angleToNormalDeg = Math.toDegrees(Math.acos(Math.min(1, Math.max(-1, Math.abs(dot)))));
        // 与法线夹角应 > 60°（即沿墙夹角 < 30°）
        return angleToNormalDeg >= (90.0 - WALL_RUN_MAX_ANGLE_DEG);
    }

    public ClimbableMesh climbMesh(String meshId) {
        return climbMeshes.get(meshId);
    }

    public GrappleNodeService grappleNodes() {
        return grappleNodes;
    }

    private ClimbableMesh resolveClimbMesh(SceneMoveCmd cmd) {
        if (cmd.climbableMeshId() != null && !cmd.climbableMeshId().isBlank()) {
            return climbMeshes.get(cmd.climbableMeshId());
        }
        ClimbableMesh best = null;
        double bestDist = Double.MAX_VALUE;
        for (ClimbableMesh m : climbMeshes.values()) {
            float dx = cmd.targetX() - m.x();
            float dy = cmd.targetY() - m.y();
            float dz = cmd.targetZ() - m.z();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= m.radius() && d < bestDist) {
                best = m;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean isInWindField(float x, float y, float z) {
        for (WindField f : windFields.values()) {
            float dx = x - f.x();
            float dy = y - f.y();
            float dz = z - f.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= f.radius()) {
                return true;
            }
        }
        return false;
    }

    public StaminaConsumeService stamina() {
        return stamina;
    }

    public TraverseModeService traverseModes() {
        return traverseModes;
    }

    public UnderwaterPhysicsService underwater() {
        return underwater;
    }

    /**
     * ASM 分层校验：空中技能仅 AIR_NORMAL；空中普攻消耗额外体力；1.5s 内最多 3 次空中轻击。
     */
    public Map<String, Object> validateActionState(long playerId, SceneMoveCmd cmd, long nowMs) {
        ActionState state = cmd.actionState();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("actionState", state.name());
        if (state == ActionState.AIR_HEAVY
                && cmd.movementType() != MovementType.GLIDE
                && cmd.movementType() != MovementType.AIR_DASH
                && !MoveFlags.has(cmd.moveFlags(), MoveFlags.MID_AIR_DASH)) {
            return Map.of("ok", false, "error", "air_heavy_requires_air",
                    "retcode", RetCode.ACTION_STATE_REJECTED);
        }
        if (state == ActionState.CLIMB_ATTACK && cmd.movementType() != MovementType.CLIMB
                && cmd.movementType() != MovementType.CLIMB_HANG) {
            return Map.of("ok", false, "error", "climb_attack_requires_climb",
                    "retcode", RetCode.ACTION_STATE_REJECTED);
        }
        if (state == ActionState.SWIM_ATTACK && cmd.movementType() != MovementType.SWIM) {
            return Map.of("ok", false, "error", "swim_attack_requires_swim",
                    "retcode", RetCode.ACTION_STATE_REJECTED);
        }
        if (state.isAirborne()) {
            Map<String, Object> airCost = stamina.consumeAirCombat(playerId, nowMs);
            if (!Boolean.TRUE.equals(airCost.get("ok"))) {
                airCost.put("retcode", RetCode.ACTION_STATE_REJECTED);
                return airCost;
            }
            body.put("airCombatCost", airCost);
            if (state == ActionState.AIR_NORMAL) {
                Deque<Long> hits = airComboHits.computeIfAbsent(playerId, id -> new ArrayDeque<>());
                synchronized (hits) {
                    while (!hits.isEmpty() && nowMs - hits.peekFirst() > AIR_COMBO_WINDOW_MS) {
                        hits.pollFirst();
                    }
                    if (hits.size() >= AIR_COMBO_MAX_HITS) {
                        body.put("airComboCapped", true);
                    } else {
                        hits.addLast(nowMs);
                        body.put("airComboHit", hits.size());
                        body.put("airComboWindowMs", AIR_COMBO_WINDOW_MS);
                        body.put("airComboFloaty", true);
                    }
                }
            }
        }
        return body;
    }

    /** 摄像机相对移动：将 moveIntent 映射为世界坐标目标点；返回 [x,y,z, dashFlag]。 */
    static float[] remapByCamera(SceneMoveCmd cmd, float fromX, float fromY, float fromZ) {
        MoveIntent intent = cmd.moveIntent();
        float yawRad = (float) Math.toRadians(cmd.cameraYaw());
        float fwdX = (float) Math.sin(yawRad);
        float fwdZ = (float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) -Math.sin(yawRad);
        float step = Math.max(0.5f, Math.abs(cmd.speed()) * 0.1f);
        float dx = 0f;
        float dz = 0f;
        float dashFlag = 0f;
        switch (intent) {
            case FORWARD -> {
                dx = fwdX * step;
                dz = fwdZ * step;
            }
            case BACKWARD -> {
                dx = -fwdX * step;
                dz = -fwdZ * step;
                if (cmd.movementType() == MovementType.DASH) {
                    dashFlag = -1f;
                }
            }
            case LEFT -> {
                dx = -rightX * step;
                dz = -rightZ * step;
            }
            case RIGHT -> {
                dx = rightX * step;
                dz = rightZ * step;
            }
            default -> {
                dx = cmd.targetX() - fromX;
                dz = cmd.targetZ() - fromZ;
            }
        }
        if (intent == MoveIntent.FORWARD && cmd.movementType() == MovementType.DASH) {
            dashFlag = 1f;
        }
        if (intent == MoveIntent.FORWARD || intent == MoveIntent.BACKWARD
                || intent == MoveIntent.LEFT || intent == MoveIntent.RIGHT) {
            return new float[]{fromX + dx, fromY, fromZ + dz, dashFlag};
        }
        return new float[]{cmd.targetX(), cmd.targetY(), cmd.targetZ(), dashFlag};
    }

    private Map<String, Object> admitClimbHang(
            long playerId, SceneMoveCmd cmd, long durationMs, long nowMs, Map<String, Object> body) {
        Long since = climbHangSince.get(playerId);
        if (since == null) {
            climbHangSince.put(playerId, nowMs);
            since = nowMs;
        }
        if (nowMs - since > CLIMB_HANG_MAX_MS) {
            climbHangSince.remove(playerId);
            return Map.of("ok", false, "error", "hang_timeout", "movementType", MovementType.CLIMB_HANG.name());
        }
        Map<String, Object> recover = stamina.tickHangRecover(playerId, durationMs);
        body.putAll(recover);
        body.put("ok", true);
        body.put("movementType", MovementType.CLIMB_HANG.name());
        body.put("hangRemainMs", CLIMB_HANG_MAX_MS - (nowMs - since));
        body.put("hangRecoverRate", StaminaConsumeService.HANG_RECOVER_RATE);
        return body;
    }

    private Map<String, Object> admitClimbVault(
            long playerId, SceneMoveCmd cmd, float fromX, float fromY, float fromZ,
            Map<String, Object> body) {
        ClimbableMesh mesh = resolveClimbMesh(cmd);
        if (mesh == null) {
            return Map.of("ok", false, "error", "climbable_mesh_required",
                    "movementType", MovementType.CLIMB_VAULT.name());
        }
        float topY = mesh.y() + 2f;
        float edgeDist = Math.abs(cmd.targetY() - topY);
        if (edgeDist > VAULT_EDGE_DISTANCE_M) {
            return Map.of("ok", false, "error", "vault_edge_too_far", "edgeDist", edgeDist);
        }
        climbHangSince.remove(playerId);
        body.put("ok", true);
        body.put("retcode", RetCode.VAULT_SUCCESS);
        body.put("movementType", MovementType.CLIMB_VAULT.name());
        body.put("standX", mesh.x());
        body.put("standY", topY);
        body.put("standZ", mesh.z());
        body.put("cancelStiffness", true);
        body.put("vaultTeleport", true);
        return body;
    }
}
