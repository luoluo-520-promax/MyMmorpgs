package cn.itcast.demo.mymmorpg.world.time;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class WorldTimeServiceTest {

    @Test
    public void tickAdvancesAndBroadcastsWeather() {
        WorldTimeService svc = new WorldTimeService();
        svc.configure(24f, 0L);
        AtomicInteger changes = new AtomicInteger();
        svc.onWeatherChange(ev -> changes.incrementAndGet());
        svc.forceWeather(WorldTimeService.Weather.CLEAR);
        svc.forceWeather(WorldTimeService.Weather.RAIN);
        assertThat(changes.get()).isGreaterThanOrEqualTo(1);
        assertThat(svc.toView(3_600_000L).get("weather")).isEqualTo("RAIN");
        svc.clearForcedWeather();
        var clock = svc.tick(3_600_000L);
        assertThat(clock.hourOfDay()).isBetween(0, 23);
        assertThat(WorldTimeService.elementVisionBonus(WorldTimeService.Weather.THUNDER))
                .isGreaterThan(1.0);
    }
}
