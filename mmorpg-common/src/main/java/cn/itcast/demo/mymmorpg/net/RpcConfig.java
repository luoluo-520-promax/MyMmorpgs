/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/RpcConfig.java
 * 类型：类
 * 职责：定义 RpcConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;

public class RpcConfig {
    /** 端口（类型：int） */
    private int port = 0;
    /** 中心服配置 */
    private CenterConfig center = new CenterConfig();
    /** 签名key（类型：String） */
    private String signKey = "";
    /** RANDOM 或 ROUND，见 {@link cn.itcast.demo.mymmorpg.rpc.BalanceStrategy}；文档默认轮询。 */
    private String balanceStrategy = "ROUND";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public CenterConfig getCenter() {
        return center;
    }

    public void setCenter(CenterConfig center) {
        this.center = center;
    }

    public String getSignKey() {
        return signKey;
    }

    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }

    public String getBalanceStrategy() {
        return balanceStrategy;
    }

    public void setBalanceStrategy(String balanceStrategy) {
        this.balanceStrategy = balanceStrategy;
    }
}
