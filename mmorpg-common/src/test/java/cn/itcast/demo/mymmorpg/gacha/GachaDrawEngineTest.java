package cn.itcast.demo.mymmorpg.gacha;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GachaDrawEngineTest {

    @Test
    public void softPity_forcesFiveStarAt90() {
        GachaDrawEngine engine = new GachaDrawEngine();
        GachaBannerConfig banner = new GachaBannerConfig();
        banner.setBannerType(GachaBannerType.NORMAL);
        banner.setRateUpItems5(List.of(10001));
        banner.setRateUpItems4(List.of(20001));

        GachaDrawEngine.DrawResult r = engine.doOneDraw(
                GachaBannerType.NORMAL, banner, banner, 89, 0, 0);
        assertThat(r.rarity()).isEqualTo(5);
        assertThat(r.pity5After()).isZero();
        assertThat(r.itemId()).isEqualTo(10001);
    }

    @Test
    public void upBanner_guaranteeAfterFailedUp() {
        GachaDrawEngine engine = new GachaDrawEngine() {
            @Override
            boolean rollPercent(double percent) {
                return percent >= 0.6; // 强制出 5 星路径以外不干扰：本测用 pity5=89
            }

            @Override
            int rollInt(int bound) {
                return 0;
            }
        };
        GachaBannerConfig up = new GachaBannerConfig();
        up.setBannerType(GachaBannerType.AVATAR_UP);
        up.setRateUpItems5(List.of(11001));
        GachaBannerConfig normal = new GachaBannerConfig();
        normal.setRateUpItems5(List.of(10001));

        GachaDrawEngine.DrawResult r = engine.doOneDraw(
                GachaBannerType.AVATAR_UP, up, normal, 89, 0, 1);
        assertThat(r.rarity()).isEqualTo(5);
        assertThat(r.itemId()).isEqualTo(11001);
        assertThat(r.failedUpCountAfter()).isZero();
    }
}
