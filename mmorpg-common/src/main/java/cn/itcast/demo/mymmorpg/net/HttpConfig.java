/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/HttpConfig.java
 * 类型：类
 * 职责：定义 HttpConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;

public class HttpConfig {
    private int port = 0;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
