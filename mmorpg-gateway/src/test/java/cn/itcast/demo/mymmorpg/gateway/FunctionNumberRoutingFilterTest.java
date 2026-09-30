package cn.itcast.demo.mymmorpg.gateway;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionNumberRoutingFilterTest {

    @Test
    public void resolvesSceneAndBagRanges() {
        FunctionNumberRoutingFilter filter = new FunctionNumberRoutingFilter();
        assertThat(filter.resolve(105)).isEqualTo("SCENE");
        assertThat(filter.resolve(301)).isEqualTo("BAG");
        assertThat(filter.resolve(201)).isEqualTo("BATTLE");
        assertThat(filter.resolve(10)).isEqualTo("PLAYER");
    }
}
