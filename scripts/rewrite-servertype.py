#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path

content = """/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/ServerType.java
 * 类型：枚举
 * 职责：定义分布式部署中的服务器类型及编码映射。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
// 包声明：cn.itcast.demo.mymmorpg.net
package cn.itcast.demo.mymmorpg.net;

/**
 * 分布式部署中的服务器类型（与 {@code server.yml} / 文档 server.type 对应）。
 * <p>文档约定：GATE (0)、GAME (1)、FIGHT (2)、CENTRE (10) 等。</p>
 */
public enum ServerType {
    /** 网关：客户端连接与初始路由。 */
    GATE(0),
    /** 游戏逻辑：玩家会话、游戏逻辑。 */
    GAME(1),
    /** 战斗：战斗实例、战斗计算。 */
    FIGHT(2),
    /** 中心：目录服务、服务器注册、协调。 */
    CENTRE(10);

    /** 类型编码，与配置文件 server.type 对应 */
    private final int code;

    /**
     * 构造枚举常量并绑定编码。
     *
     * @param code 类型编码
     */
    ServerType(int code) {
        this.code = code; // 保存编码
    }

    /**
     * 获取类型编码。
     *
     * @return 编码值
     */
    public int getCode() {
        return code; // 返回编码
    }

    /**
     * 根据编码解析服务器类型，未知编码默认 GAME。
     *
     * @param code 配置中的类型编码
     * @return 匹配的 ServerType
     */
    public static ServerType fromCode(int code) {
        for (ServerType t : values()) { // 遍历所有枚举值
            if (t.code == code) { // 编码匹配
                return t; // 返回对应类型
            }
        }
        return GAME; // 默认游戏服
    }
}
"""

path = Path(__file__).resolve().parent.parent / "mmorpg-common" / "src" / "main" / "java" / "cn" / "itcast" / "demo" / "mymmorpg" / "net" / "ServerType.java"
with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("ok")
