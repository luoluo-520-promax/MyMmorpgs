package cn.itcast.demo.mymmorpg.world.time;

import cn.itcast.demo.mymmorpg.world.narrative.ServerEpochService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 游戏逻辑时间守护：1 游戏小时 = 2 分钟现实时间，配合 ServerEpochService 绑定昼夜循环。
 */
@Service
public class GameTimeKeeper {

    /** 1 游戏小时 = 120_000 ms 现实时间 */
    public static final long REAL_MS_PER_GAME_HOUR = 120_000L;
    public static final float GAME_HOURS_PER_REAL_MINUTE = 0.5f;

    private final WorldTimeService worldTime;
    private ServerEpochService serverEpoch;

    public GameTimeKeeper() {
        this(new WorldTimeService());
    }

    public GameTimeKeeper(WorldTimeService worldTime) {
        this.worldTime = worldTime == null ? new WorldTimeService() : worldTime;
        // 1 游戏天 = 48 现实分钟 → timeScale = 24 * 60 / 48 = 30
        this.worldTime.configure(30f, System.currentTimeMillis());
    }

    public void bindServerEpoch(ServerEpochService epoch) {
        this.serverEpoch = epoch;
    }

    public WorldTimeService worldTime() {
        return worldTime;
    }

    public Map<String, Object> tick(long nowMs) {
        WorldTimeService.WorldClock clock = worldTime.tick(nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("tick", clock.tick());
        body.put("hourOfDay", clock.hourOfDay());
        body.put("minuteOfHour", clock.minuteOfHour());
        body.put("weather", clock.weather().name());
        body.put("realMsPerGameHour", REAL_MS_PER_GAME_HOUR);
        body.put("isNight", clock.hourOfDay() >= 22 || clock.hourOfDay() < 6);
        body.put("isDawn", clock.hourOfDay() >= 5 && clock.hourOfDay() < 7);
        body.put("isDusk", clock.hourOfDay() >= 17 && clock.hourOfDay() < 19);
        if (serverEpoch != null) {
            body.put("epochVersion", serverEpoch.epochVersion());
        }
        return body;
    }

    public boolean isStoreHours(int hourOfDay) {
        return hourOfDay >= 8 && hourOfDay < 22;
    }

    public Map<String, Object> view(long nowMs) {
        return tick(nowMs);
    }
}
