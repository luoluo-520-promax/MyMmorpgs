/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/PlayerSkill.java
 * 类型：类
 * 职责：定义 PlayerSkill，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.EmbeddedId;

import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 对应表 player_skill。
 */
@Entity
@Table(name = "player_skill")
public class PlayerSkill {

    @EmbeddedId
    /** 标识（类型：PlayerSkillId） */
    private PlayerSkillId id;@Column(name = "learn_time", nullable = false)
    /** learntime（类型：Instant） */
    private Instant learnTime;/**
     * 获取标识属性值
     */
    public PlayerSkillId getId() {
        return id;
    }

    /**
     * 设置标识属性值
     */
    public void setId(PlayerSkillId id) {
        this.id = id;  // 访问或赋值当前实例字段
    }

    /**
     * 获取learntime属性值
     */
    public Instant getLearnTime() {
        return learnTime;
    }

    /**
     * 设置learntime属性值
     */
    public void setLearnTime(Instant learnTime) {
        this.learnTime = learnTime;  // 访问或赋值当前实例字段
    }
}
