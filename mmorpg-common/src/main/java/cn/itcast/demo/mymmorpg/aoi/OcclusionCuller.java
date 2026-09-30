package cn.itcast.demo.mymmorpg.aoi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 障碍物视距裁剪：简化 Raycast（线段采样高度阻挡）。
 * 生产可替换为导航网格/碰撞体查询；此处提供可注入的高度场接口。
 */
public final class OcclusionCuller {

    @FunctionalInterface
    public interface HeightField {
        /** 返回世界坐标 (x,z) 处阻挡高度；无阻挡返回 Float.NEGATIVE_INFINITY。 */
        float blockerHeight(float x, float z);
    }

    private final HeightField heightField;
    private final int samples;
    private final float clearance;

    public OcclusionCuller(HeightField heightField, int samples, float clearance) {
        this.heightField = heightField == null ? (x, z) -> Float.NEGATIVE_INFINITY : heightField;
        this.samples = Math.max(2, samples);
        this.clearance = Math.max(0f, clearance);
    }

    public OcclusionCuller() {
        this(null, 8, 1.5f);
    }

    /**
     * @param fromTo 长度 6：[fx,fy,fz,tx,ty,tz]
     * @return true 表示视线通畅，应同步
     */
    public boolean isVisible(float[] fromTo) {
        if (fromTo == null || fromTo.length < 6) {
            return true;
        }
        float fx = fromTo[0];
        float fy = fromTo[1];
        float fz = fromTo[2];
        float tx = fromTo[3];
        float ty = fromTo[4];
        float tz = fromTo[5];
        for (int i = 1; i < samples; i++) {
            float t = (float) i / samples;
            float x = fx + (tx - fx) * t;
            float y = fy + (ty - fy) * t;
            float z = fz + (tz - fz) * t;
            float blocker = heightField.blockerHeight(x, z);
            if (blocker > Float.NEGATIVE_INFINITY && y + clearance < blocker) {
                return false;
            }
        }
        return true;
    }

    public Predicate<float[]> asPredicate() {
        return this::isVisible;
    }

    /** 悬崖场景：高低差超过 maxDeltaY 且水平距离近则互相不可见。 */
    public static Predicate<float[]> cliffFilter(float maxDeltaY) {
        float limit = Math.max(0.1f, maxDeltaY);
        return fromTo -> {
            if (fromTo == null || fromTo.length < 6) {
                return true;
            }
            return Math.abs(fromTo[1] - fromTo[4]) <= limit;
        };
    }

    public static HeightField walls(List<float[]> wallBoxes) {
        List<float[]> boxes = wallBoxes == null ? List.of() : new ArrayList<>(wallBoxes);
        return (x, z) -> {
            float maxH = Float.NEGATIVE_INFINITY;
            for (float[] b : boxes) {
                // b: minX, minZ, maxX, maxZ, height
                if (b.length < 5) {
                    continue;
                }
                if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) {
                    maxH = Math.max(maxH, b[4]);
                }
            }
            return maxH;
        };
    }
}
