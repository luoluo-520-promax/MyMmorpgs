package cn.itcast.demo.mymmorpg.world.time;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 大世界时序与天气：全局 Tick 推进昼夜，广播天气变化。
 */
@Service
public class WorldTimeService {

    public enum Weather {
        CLEAR,
        CLOUDY,
        RAIN,
        THUNDER,
        SNOW
    }

    public record WorldClock(
            long tick,
            int dayOfYear,
            int hourOfDay,
            int minuteOfHour,
            Weather weather,
            float timeScale) {
    }

    public record WeatherChangeEvent(Weather from, Weather to, long tick, int hourOfDay) {
    }

    /** 游戏内 1 天 = realDayMillis / timeScale */
    private volatile float timeScale = 24f; // 现实 1 小时 ≈ 游戏 1 天
    private volatile long epochRealMs = System.currentTimeMillis();
    private volatile long tick;
    private volatile Weather weather = Weather.CLEAR;
    /** GM/运维强制天气；非 null 时 tick 不再用自然算法覆盖 */
    private volatile Weather forcedWeather;
    private final CopyOnWriteArrayList<Consumer<WeatherChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    public void configure(float timeScale, long epochRealMs) {
        this.timeScale = Math.max(0.1f, timeScale);
        this.epochRealMs = epochRealMs;
    }

    public void onWeatherChange(Consumer<WeatherChangeEvent> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** 推进到 now；返回当前时钟。 */
    public WorldClock tick(long nowMs) {
        long elapsed = Math.max(0L, nowMs - epochRealMs);
        // 现实 ms * timeScale → 游戏分钟
        long gameMinutes = (long) (elapsed / 60_000.0 * timeScale);
        this.tick = gameMinutes;
        int dayOfYear = (int) ((gameMinutes / (24L * 60L)) % 365L) + 1;
        int minuteOfDay = (int) (gameMinutes % (24L * 60L));
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        Weather next = forcedWeather != null ? forcedWeather : resolveWeather(dayOfYear, hour);
        if (next != weather) {
            WeatherChangeEvent ev = new WeatherChangeEvent(weather, next, tick, hour);
            weather = next;
            for (Consumer<WeatherChangeEvent> l : listeners) {
                try {
                    l.accept(ev);
                } catch (Exception ignored) {
                    // 广播失败不影响主时钟
                }
            }
        }
        return new WorldClock(tick, dayOfYear, hour, minute, weather, timeScale);
    }

    public WorldClock current(long nowMs) {
        return tick(nowMs);
    }

    public void forceWeather(Weather weather) {
        Weather target = weather == null ? Weather.CLEAR : weather;
        this.forcedWeather = target;
        Weather from = this.weather;
        this.weather = target;
        if (from != this.weather) {
            WeatherChangeEvent ev = new WeatherChangeEvent(from, this.weather, tick, hourOf(tick));
            for (Consumer<WeatherChangeEvent> l : listeners) {
                l.accept(ev);
            }
        }
    }

    /** 清除强制天气，恢复自然昼夜天气算法。 */
    public void clearForcedWeather() {
        this.forcedWeather = null;
    }

    public Map<String, Object> toView(long nowMs) {
        WorldClock c = current(nowMs);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("tick", c.tick());
        m.put("dayOfYear", c.dayOfYear());
        m.put("hourOfDay", c.hourOfDay());
        m.put("minuteOfHour", c.minuteOfHour());
        m.put("weather", c.weather().name());
        m.put("timeScale", c.timeScale());
        m.put("elementVisionBonus", elementVisionBonus(c.weather()));
        return m;
    }

    /** 雨天/雷暴影响元素视野（简化系数）。 */
    public static double elementVisionBonus(Weather w) {
        return switch (w) {
            case RAIN -> 1.15;
            case THUNDER -> 1.25;
            case SNOW -> 0.9;
            case CLOUDY -> 1.05;
            default -> 1.0;
        };
    }

    public List<Map<String, Object>> recentListenerCountView() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("weatherListeners", listeners.size()));
        return out;
    }

    private Weather resolveWeather(int dayOfYear, int hour) {
        int seed = (dayOfYear * 31 + hour) % 100;
        if (seed < 55) {
            return Weather.CLEAR;
        }
        if (seed < 70) {
            return Weather.CLOUDY;
        }
        if (seed < 85) {
            return Weather.RAIN;
        }
        if (seed < 95) {
            return Weather.THUNDER;
        }
        return Weather.SNOW;
    }

    private static int hourOf(long tickMinutes) {
        return (int) ((tickMinutes % (24L * 60L)) / 60L);
    }
}
