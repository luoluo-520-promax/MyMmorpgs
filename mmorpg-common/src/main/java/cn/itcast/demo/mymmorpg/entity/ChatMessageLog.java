/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/ChatMessageLog.java
 * 类型：类
 * 职责：定义 ChatMessageLog，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;

import jakarta.persistence.GenerationType;

import jakarta.persistence.Id;

import jakarta.persistence.Table;

/**
 * 聊天消息落库（审计），表 chat_message_log。
 */
@Entity
@Table(name = "chat_message_log")
public class ChatMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Long） */
    private Long id;
    @Column(name = "sender_id", nullable = false)
    /** 发送器标识（类型：Long） */
    private Long senderId;
    @Column(name = "channel", nullable = false)
    /** channel（类型：Integer） */
    private Integer channel;
    @Column(name = "target_id")
    /** target标识（类型：Long） */
    private Long targetId;
    @Column(name = "msg_type", nullable = false)
    /** msg类型（类型：Integer） */
    private Integer msgType;
    @Column(name = "content", nullable = false, length = 512)
    /** content（类型：String） */
    private String content;
    @Column(name = "server_ts", nullable = false)
    /** 服务器ts（类型：Long） */
    private Long serverTs;/**
     * 获取标识属性值
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置标识属性值
     */
    public void setId(Long id) {
        this.id = id;  // 访问或赋值当前实例字段
    }

    /**
     * 获取发送器标识属性值
     */
    public Long getSenderId() {
        return senderId;
    }

    /**
     * 设置发送器标识属性值
     */
    public void setSenderId(Long senderId) {
        this.senderId = senderId;  // 访问或赋值当前实例字段
    }

    /**
     * 获取channel属性值
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * 设置channel属性值
     */
    public void setChannel(Integer channel) {
        this.channel = channel;  // 访问或赋值当前实例字段
    }

    /**
     * 获取target标识属性值
     */
    public Long getTargetId() {
        return targetId;
    }

    /**
     * 设置target标识属性值
     */
    public void setTargetId(Long targetId) {
        this.targetId = targetId;  // 访问或赋值当前实例字段
    }

    /**
     * 获取msg类型属性值
     */
    public Integer getMsgType() {
        return msgType;
    }

    /**
     * 设置msg类型属性值
     */
    public void setMsgType(Integer msgType) {
        this.msgType = msgType;  // 访问或赋值当前实例字段
    }

    /**
     * 获取content属性值
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置content属性值
     */
    public void setContent(String content) {
        this.content = content;  // 访问或赋值当前实例字段
    }

    /**
     * 获取服务器ts属性值
     */
    public Long getServerTs() {
        return serverTs;
    }

    /**
     * 设置服务器ts属性值
     */
    public void setServerTs(Long serverTs) {
        this.serverTs = serverTs;  // 访问或赋值当前实例字段
    }
}
