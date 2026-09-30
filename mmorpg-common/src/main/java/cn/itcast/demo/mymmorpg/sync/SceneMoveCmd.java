package cn.itcast.demo.mymmorpg.sync;



/**

 * 服务端移动指令：坐标 + MovementType + moveFlags / 钩锁 / 骑乘 + 物理哈希快照 + ASM / 摄像机相对输入。

 */

public record SceneMoveCmd(

        float targetX,

        float targetY,

        float targetZ,

        float speed,

        long timestamp,

        MovementType movementType,

        String climbableMeshId,

        int moveFlags,

        String mountCreatureUid,

        String grappleNodeId,

        float wallNormalX,

        float wallNormalY,

        float wallNormalZ,

        float inheritedVelocityX,

        float inheritedVelocityY,

        float inheritedVelocityZ,

        /** 客户端物理引擎关键参数 MD5（速度矢量、刚体碰撞法线等） */

        String physicsStateHash,

        /** 动作状态机分层（空中/地面/攀爬攻击） */

        ActionState actionState,

        /** 摄像机相对移动意图 */

        MoveIntent moveIntent,

        /** 摄像机水平朝向（度） */

        float cameraYaw,

        /** 本次输入距上一帧耗时（ms），用于动态输入窗口 */

        int clientDeltaMs,

        /** 地表材质：Grass/Snow/Mud 等，用于脚印/车辙 */

        SurfaceType surfaceType) {



    public SceneMoveCmd {

        movementType = movementType == null ? MovementType.WALK : movementType;
        surfaceType = surfaceType == null ? SurfaceType.PLAIN : surfaceType;

        climbableMeshId = climbableMeshId == null ? "" : climbableMeshId.trim();

        mountCreatureUid = mountCreatureUid == null ? "" : mountCreatureUid.trim();

        grappleNodeId = grappleNodeId == null ? "" : grappleNodeId.trim();

        physicsStateHash = physicsStateHash == null ? "" : physicsStateHash.trim();

        actionState = actionState == null ? ActionState.GROUND_IDLE : actionState;

        moveIntent = moveIntent == null ? MoveIntent.FORWARD : moveIntent;

    }



    public SceneMoveCmd(

            float targetX, float targetY, float targetZ, float speed, long timestamp,

            MovementType movementType, String climbableMeshId) {

        this(targetX, targetY, targetZ, speed, timestamp, movementType, climbableMeshId,

                MoveFlags.NONE, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",

                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16, SurfaceType.PLAIN);

    }



    public SceneMoveCmd(

            float targetX, float targetY, float targetZ, float speed, long timestamp,

            MovementType movementType, String climbableMeshId, int moveFlags,

            String mountCreatureUid, String grappleNodeId,

            float wallNormalX, float wallNormalY, float wallNormalZ) {

        this(targetX, targetY, targetZ, speed, timestamp, movementType, climbableMeshId,

                moveFlags, mountCreatureUid, grappleNodeId,

                wallNormalX, wallNormalY, wallNormalZ, 0f, 0f, 0f, "",

                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16, SurfaceType.PLAIN);

    }



    public SceneMoveCmd(

            float targetX, float targetY, float targetZ, float speed, long timestamp,

            MovementType movementType, String climbableMeshId, int moveFlags,

            String mountCreatureUid, String grappleNodeId,

            float wallNormalX, float wallNormalY, float wallNormalZ,

            float inheritedVelocityX, float inheritedVelocityY, float inheritedVelocityZ) {

        this(targetX, targetY, targetZ, speed, timestamp, movementType, climbableMeshId,

                moveFlags, mountCreatureUid, grappleNodeId,

                wallNormalX, wallNormalY, wallNormalZ,

                inheritedVelocityX, inheritedVelocityY, inheritedVelocityZ, "",

                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16, SurfaceType.PLAIN);

    }



    public SceneMoveCmd(

            float targetX, float targetY, float targetZ, float speed, long timestamp,

            MovementType movementType, String climbableMeshId, int moveFlags,

            String mountCreatureUid, String grappleNodeId,

            float wallNormalX, float wallNormalY, float wallNormalZ,

            float inheritedVelocityX, float inheritedVelocityY, float inheritedVelocityZ,

            String physicsStateHash) {

        this(targetX, targetY, targetZ, speed, timestamp, movementType, climbableMeshId,

                moveFlags, mountCreatureUid, grappleNodeId,

                wallNormalX, wallNormalY, wallNormalZ,

                inheritedVelocityX, inheritedVelocityY, inheritedVelocityZ, physicsStateHash,

                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16, SurfaceType.PLAIN);

    }



    public SceneMoveCmd(

            float targetX, float targetY, float targetZ, float speed, long timestamp,

            MovementType movementType, String climbableMeshId, int moveFlags,

            String mountCreatureUid, String grappleNodeId,

            float wallNormalX, float wallNormalY, float wallNormalZ,

            float inheritedVelocityX, float inheritedVelocityY, float inheritedVelocityZ,

            String physicsStateHash,

            ActionState actionState, MoveIntent moveIntent, float cameraYaw, int clientDeltaMs) {

        this(targetX, targetY, targetZ, speed, timestamp, movementType, climbableMeshId,

                moveFlags, mountCreatureUid, grappleNodeId,

                wallNormalX, wallNormalY, wallNormalZ,

                inheritedVelocityX, inheritedVelocityY, inheritedVelocityZ, physicsStateHash,

                actionState, moveIntent, cameraYaw, clientDeltaMs, SurfaceType.PLAIN);

    }



    public static SceneMoveCmd walk(float x, float y, float z, float speed, long ts) {

        return new SceneMoveCmd(x, y, z, speed, ts, MovementType.WALK, "");

    }

}


