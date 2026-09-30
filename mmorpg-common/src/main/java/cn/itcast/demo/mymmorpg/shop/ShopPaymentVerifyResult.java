package cn.itcast.demo.mymmorpg.shop;

public record ShopPaymentVerifyResult(boolean ok, int retcode, String message, String channelOrderId) {

    public static ShopPaymentVerifyResult success(String channelOrderId) {
        return new ShopPaymentVerifyResult(true, 0, "ok", channelOrderId);
    }

    public static ShopPaymentVerifyResult fail(int retcode, String message) {
        return new ShopPaymentVerifyResult(false, retcode, message, "");
    }
}
