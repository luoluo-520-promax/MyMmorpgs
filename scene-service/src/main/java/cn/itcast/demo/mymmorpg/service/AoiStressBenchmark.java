package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.aoi.AoiGrid;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 千人同屏级 AOI 查询吞吐模拟（单进程基准，非真实集群压测）。
 */
@Service
public class AoiStressBenchmark {

    public Map<String, Object> run(int entityCount, int queryCount, int gridSize, float radius) {
        int entities = Math.max(100, Math.min(entityCount, 50_000));
        int queries = Math.max(100, Math.min(queryCount, 100_000));
        int grid = Math.max(16, gridSize);
        float r = radius > 0 ? radius : 300f;
        AoiGrid aoi = new AoiGrid(grid);
        long millis = aoi.benchmarkQueryMillis(entities, queries, r);
        double qps = millis <= 0 ? queries : (queries * 1000.0 / millis);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("entityCount", entities);
        out.put("queryCount", queries);
        out.put("gridSize", grid);
        out.put("radius", r);
        out.put("elapsedMs", millis);
        out.put("approxQps", Math.round(qps));
        out.put("cellCount", aoi.cellCount());
        out.put("message", "single-process AOI grid benchmark; use for regression, not cluster capacity");
        return out;
    }
}
