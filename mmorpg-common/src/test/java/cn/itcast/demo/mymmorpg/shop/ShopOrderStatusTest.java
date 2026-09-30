package cn.itcast.demo.mymmorpg.shop;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ShopOrderStatusTest {

    @Test
    public void deliveredEqualsFulfilledForOps() {
        assertThat(ShopOrderStatus.isDelivered("DELIVERED")).isTrue();
        assertThat(ShopOrderStatus.isDelivered("FULFILLED")).isTrue();
        assertThat(ShopOrderStatus.isDelivered("PAID")).isFalse();
        assertThat(ShopOrderStatus.canRefund("PAID")).isTrue();
        assertThat(ShopOrderStatus.canRefund("DELIVERED")).isTrue();
        assertThat(ShopOrderStatus.canRefund("CREATED")).isFalse();
    }
}
