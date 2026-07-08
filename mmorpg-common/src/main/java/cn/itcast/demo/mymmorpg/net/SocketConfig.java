/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/SocketConfig.java
 * 类型：类
 * 职责：定义 SocketConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;

public class SocketConfig {
    /** socket 服务 ID */
    private int id = 0;
    /** 服务器ip（类型：String） */
    private String serverIp = "";
    /** 客户端 TCP 连接端口 */
    private int port = 0;

    /**
     * 获取标识属性值
     */
    public int getId() {
        return id;
    }

    /**
     * 设置标识属性值
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * 获取服务器ip属性值
     */
    public String getServerIp() {
        return serverIp;
    }

    /**
     * 设置服务器ip属性值
     */
    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    /**
     * 获取端口属性值
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置端口属性值
     */
    public void setPort(int port) {
        this.port = port;
    }
}
