/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/MessageMeta.java
 * 类型：注解
 * 职责：在消息类上标记模块内命令号 cmd，供路由与反射解析 msgId。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 协议注解定义包

import java.lang.annotation.ElementType; // 指定注解可标注的程序元素类型（类、方法等）

import java.lang.annotation.Retention; // 指定注解保留到哪个生命周期阶段

import java.lang.annotation.RetentionPolicy; // 保留策略枚举：SOURCE / CLASS / RUNTIME

import java.lang.annotation.Target; // 元注解：声明本注解能用在哪些地方

/**
 * 标记「模块内命令 ID」（cmd）。
 * <p>全局消息 ID 计算公式：{@code msgId = module * 100 + cmd}，其中 module 由路由层根据消息类所在模块确定。</p>
 * <p>典型用法：{@link GamePackets} 中各嵌套请求类上的 {@code @MessageMeta(cmd = n)}。</p>
 */
@Target(ElementType.TYPE) // 仅允许标注在类、接口、枚举等类型上，不能标在字段或方法
@Retention(RetentionPolicy.RUNTIME) // 编译后保留到运行时，便于反射读取 cmd 值
public @interface MessageMeta { // 自定义注解类型，编译期生成元数据

    /**
     * 模块内命令序号（与 {@link MessageId} 中末两位或约定 cmd 一致）。
     *
     * @return 非负整数，与同模块下其他消息 cmd 不重复
     */
    int cmd(); // 注解唯一必填属性，无默认值
}
