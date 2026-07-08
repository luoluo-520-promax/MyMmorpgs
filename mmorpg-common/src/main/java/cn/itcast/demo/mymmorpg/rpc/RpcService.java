/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcService.java
 * 类型：注解
 * 职责：标记可被 RPC 服务端扫描并注册为远程可调用的服务类（Protostuff RPC 扩展占位）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记可被 RPC 服务端扫描注册的远程服务类。
 * <p>标注在 Spring Bean 类上，RPC 启动时扫描并暴露为跨服可调用的服务端点（Protostuff RPC 扩展占位）。
 */
@Target(ElementType.TYPE) // 仅可标注在类/接口/枚举类型上
@Retention(RetentionPolicy.RUNTIME) // 运行时保留，供 RPC 框架反射扫描
public @interface RpcService {
}
