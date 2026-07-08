/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/BuffConfig.java
 * 类型：类
 * 职责：定义 BuffConfig，供各业务模块复用与扩展。
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
 * 对应表 buff_config。
 */
@Entity
@Table(name = "buff_config")
public class BuffConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Integer） */
    private Integer id;
    @Column(name = "name", nullable = false, length = 64)
    /** 名称（类型：String） */
    private String name;
    /** 持续时间（毫秒）?1 表示永久 */
    @Column(name = "duration", nullable = false)
    /** duration（类型：Integer） */
    private Integer duration = -1;
    @Column(name = "periodic_interval")
    /** periodicinterval（类型：Integer） */
    private Integer periodicInterval;
    @Column(name = "stack_limit", nullable = false)
    /** stacklimit（类型：Integer） */
    private Integer stackLimit = 1;
    @Column(name = "effect_type", nullable = false)
    /** effect类型（类型：Integer） */
    private Integer effectType;
    @Column(name = "effect_params", length = 255)
    /** effectparams（类型：String） */
    private String effectParams;
    @Column(name = "description", length = 255)
    /** description（类型：String） */
    private String description;/**
     * 获取标识属性值
     */
    public Integer getId() {
        return id;
    }

    /**
     * 设置标识属性值
     */
    public void setId(Integer id) {
        this.id = id;  // 访问或赋值当前实例字段
    }

    /**
     * 获取名称属性值
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称属性值
     */
    public void setName(String name) {
        this.name = name;  // 访问或赋值当前实例字段
    }

    /**
     * 获取duration属性值
     */
    public Integer getDuration() {
        return duration;
    }

    /**
     * 设置duration属性值
     */
    public void setDuration(Integer duration) {
        this.duration = duration;  // 访问或赋值当前实例字段
    }

    /**
     * 获取periodicinterval属性值
     */
    public Integer getPeriodicInterval() {
        return periodicInterval;
    }

    /**
     * 设置periodicinterval属性值
     */
    public void setPeriodicInterval(Integer periodicInterval) {
        this.periodicInterval = periodicInterval;  // 访问或赋值当前实例字段
    }

    /**
     * 获取stacklimit属性值
     */
    public Integer getStackLimit() {
        return stackLimit;
    }

    /**
     * 设置stacklimit属性值
     */
    public void setStackLimit(Integer stackLimit) {
        this.stackLimit = stackLimit;  // 访问或赋值当前实例字段
    }

    /**
     * 获取effect类型属性值
     */
    public Integer getEffectType() {
        return effectType;
    }

    /**
     * 设置effect类型属性值
     */
    public void setEffectType(Integer effectType) {
        this.effectType = effectType;  // 访问或赋值当前实例字段
    }

    /**
     * 获取effectparams属性值
     */
    public String getEffectParams() {
        return effectParams;
    }

    /**
     * 设置effectparams属性值
     */
    public void setEffectParams(String effectParams) {
        this.effectParams = effectParams;  // 访问或赋值当前实例字段
    }

    /**
     * 获取description属性值
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置description属性值
     */
    public void setDescription(String description) {
        this.description = description;  // 访问或赋值当前实例字段
    }
}
