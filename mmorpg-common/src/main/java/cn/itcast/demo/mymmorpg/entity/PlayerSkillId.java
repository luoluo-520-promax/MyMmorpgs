/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/PlayerSkillId.java
 * 类型：类
 * 职责：定义 PlayerSkillId，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

import java.util.Objects;

@Embeddable
public class PlayerSkillId implements Serializable {

    @Column(name = "player_id", nullable = false)
    /** 玩家标识（类型：Long） */
    private Long playerId;@Column(name = "skill_id", nullable = false)
    /** 技能标识（类型：Integer） */
    private Integer skillId;/**
     * 构造 PlayerSkillId 实例
     */
    public PlayerSkillId() {
    }

    /**
     * 构造 PlayerSkillId 实例
     */
    public PlayerSkillId(Long playerId, Integer skillId) {
        this.playerId = playerId;  // 访问或赋值当前实例字段
        this.skillId = skillId;  // 访问或赋值当前实例字段
    }

    /**
     * 获取玩家标识属性值
     */
    public Long getPlayerId() {
        return playerId;
    }

    /**
     * 设置玩家标识属性值
     */
    public void setPlayerId(Long playerId) {
        this.playerId = playerId;  // 访问或赋值当前实例字段
    }

    /**
     * 获取技能标识属性值
     */
    public Integer getSkillId() {
        return skillId;
    }

    /**
     * 设置技能标识属性值
     */
    public void setSkillId(Integer skillId) {
        this.skillId = skillId;  // 访问或赋值当前实例字段
    }

    @Override
    /**
     * equals；参数：Object o
     */
    public boolean equals(Object o) {
        if (this == o) { // 条件分支判断
            return true; // 判定为在线
        }
        if (o == null || getClass() != o.getClass()) { // 条件分支判断
            return false; // 判定为离线或不可用
        }
        PlayerSkillId that = (PlayerSkillId) o;
        return Objects.equals(playerId, that.playerId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    /**
     * hash码；无参数
     */
    public int hashCode() {
        return Objects.hash(playerId, skillId);
    }
}
