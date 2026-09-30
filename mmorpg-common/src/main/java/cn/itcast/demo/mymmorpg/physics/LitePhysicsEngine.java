package cn.itcast.demo.mymmorpg.physics;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量物理引擎：2D 高度图 + 胶囊体 AABB 粗筛，不做 3D 精细碰撞。
 * 服务端仅校验落点高度与水平范围，细节由客户端 Seed 回放。
 */
@Component
public class LitePhysicsEngine {

    public static final float CAPSULE_RADIUS = 0.4f;
    public static final float CAPSULE_HEIGHT = 1.8f;

    private float[] heightMap = new float[256 * 256];
    private int mapWidth = 256;
    private int mapHeight = 256;
    private float cellSize = 1f;
    private float originX;
    private float originZ;

    public void loadHeightMap(int width, int height, float cellSize,
                              float originX, float originZ, float[] heights) {
        this.mapWidth = Math.max(1, width);
        this.mapHeight = Math.max(1, height);
        this.cellSize = Math.max(0.1f, cellSize);
        this.originX = originX;
        this.originZ = originZ;
        this.heightMap = Arrays.copyOf(heights, width * height);
    }

    public float sampleHeight(float x, float z) {
        int gx = worldToGrid(x, true);
        int gz = worldToGrid(z, false);
        if (gx < 0 || gz < 0 || gx >= mapWidth || gz >= mapHeight) {
            return 0f;
        }
        return heightMap[gz * mapWidth + gx];
    }

    /**
     * 胶囊体粗筛：AABB 与高度差是否在允许范围。
     */
    public boolean capsuleOnGround(float x, float y, float z, float maxHeightDelta) {
        float ground = sampleHeight(x, z);
        float footY = y - CAPSULE_HEIGHT * 0.5f;
        return Math.abs(footY - ground) <= maxHeightDelta;
    }

    /**
     * 钩锁落点校验：仅检查是否在 GrappleNode 半径内 + 高度合理。
     */
    public boolean grappleLandingValid(
            float landX, float landY, float landZ,
            float nodeX, float nodeY, float nodeZ, float nodeRadius) {
        float dx = landX - nodeX;
        float dy = landY - nodeY;
        float dz = landZ - nodeZ;
        float horiz = (float) Math.sqrt(dx * dx + dz * dz);
        if (horiz > nodeRadius) {
            return false;
        }
        float ground = sampleHeight(landX, landZ);
        return Math.abs(landY - ground) <= 2f && Math.abs(landY - nodeY) <= nodeRadius * 2f;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "mapWidth", mapWidth,
                "mapHeight", mapHeight,
                "cellSize", cellSize,
                "cells", heightMap.length);
    }

    private int worldToGrid(float coord, boolean xAxis) {
        float origin = xAxis ? originX : originZ;
        return (int) Math.floor((coord - origin) / cellSize);
    }
}
