package cn.itcast.demo.mymmorpg.gateway;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RegionAwareRoutingFilterTest {

    @Test
    public void normalizeRegionMapsCountryCodes() {
        assertThat(RegionAwareRoutingFilter.normalizeRegion("CN")).isEqualTo("cn-east");
        assertThat(RegionAwareRoutingFilter.normalizeRegion("us")).isEqualTo("us-west");
        assertThat(RegionAwareRoutingFilter.normalizeRegion("eu-west")).isEqualTo("eu-west");
    }
}
