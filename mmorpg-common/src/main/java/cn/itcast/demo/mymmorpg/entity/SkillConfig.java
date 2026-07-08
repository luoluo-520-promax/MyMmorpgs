/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/SkillConfig.java
 * 类型：类
 * 职责：定义 SkillConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;

import jakarta.persistence.GenerationType;

import jakarta.persistence.Id;

import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 对应表 skill_config。
 */
@Entity
@Table(name = "skill_config")
public class SkillConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Integer） */
    private Integer id;@Column(name = "name", nullable = false, length = 64)
    /** 名称（类型：String） */
    private String name;@Column(name = "effect", length = 255)
    /** effect（类型：String） */
    private String effect;@Column(name = "need_level", nullable = false)
    /** need等级（类型：Integer） */
    private Integer needLevel = 0;@Column(name = "cooldown", nullable = false)
    /** cooldown（类型：Integer） */
    private Integer cooldown = 0;@Column(name = "mana_cost", nullable = false)
    /** manacost（类型：Integer） */
    private Integer manaCost = 0;@Column(name = "cast_time", nullable = false, precision = 5, scale = 2)
    /** casttime（类型：BigDecimal） */
    private BigDecimal castTime = BigDecimal.ZERO;@Column(name = "skill_type", nullable = false)
    /** 技能类型（类型：Integer） */
    private Integer skillType = 1;@Column(name = "target_type", nullable = false)
    /** target类型（类型：Integer） */
    private Integer targetType = 1;@Column(name = "skill_range", nullable = false)
    /** range（类型：Integer） */
    private Integer range = 0;@Column(name = "shape", nullable = false)
    /** shape（类型：Integer） */
    private Integer shape = 1;@Column(name = "shape_params", columnDefinition = "json")
    /** shapeparams（类型：String） */
    private String shapeParams;/**
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
     * 获取effect属性值
     */
    public String getEffect() {
        return effect;
    }

    /**
     * 设置effect属性值
     */
    public void setEffect(String effect) {
        this.effect = effect;  // 访问或赋值当前实例字段
    }

    /**
     * 获取need等级属性值
     */
    public Integer getNeedLevel() {
        return needLevel;
    }

    /**
     * 设置need等级属性值
     */
    public void setNeedLevel(Integer needLevel) {
        this.needLevel = needLevel;  // 访问或赋值当前实例字段
    }

    /**
     * 获取cooldown属性值
     */
    public Integer getCooldown() {
        return cooldown;
    }

    /**
     * 设置cooldown属性值
     */
    public void setCooldown(Integer cooldown) {
        this.cooldown = cooldown;  // 访问或赋值当前实例字段
    }

    /**
     * 获取manacost属性值
     */
    public Integer getManaCost() {
        return manaCost;
    }

    /**
     * 设置manacost属性值
     */
    public void setManaCost(Integer manaCost) {
        this.manaCost = manaCost;  // 访问或赋值当前实例字段
    }

    /**
     * 获取casttime属性值
     */
    public BigDecimal getCastTime() {
        return castTime;
    }

    /**
     * 设置casttime属性值
     */
    public void setCastTime(BigDecimal castTime) {
        this.castTime = castTime;  // 访问或赋值当前实例字段
    }

    /**
     * 获取技能类型属性值
     */
    public Integer getSkillType() {
        return skillType;
    }

    /**
     * 设置技能类型属性值
     */
    public void setSkillType(Integer skillType) {
        this.skillType = skillType;  // 访问或赋值当前实例字段
    }

    /**
     * 获取target类型属性值
     */
    public Integer getTargetType() {
        return targetType;
    }

    /**
     * 设置target类型属性值
     */
    public void setTargetType(Integer targetType) {
        this.targetType = targetType;  // 访问或赋值当前实例字段
    }

    /**
     * 获取range属性值
     */
    public Integer getRange() {
        return range;
    }

    /**
     * 设置range属性值
     */
    public void setRange(Integer range) {
        this.range = range;  // 访问或赋值当前实例字段
    }

    /**
     * 获取shape属性值
     */
    public Integer getShape() {
        return shape;
    }

    /**
     * 设置shape属性值
     */
    public void setShape(Integer shape) {
        this.shape = shape;  // 访问或赋值当前实例字段
    }

    /**
     * 获取shapeparams属性值
     */
    public String getShapeParams() {
        return shapeParams;
    }

    /**
     * 设置shapeparams属性值
     */
    public void setShapeParams(String shapeParams) {
        this.shapeParams = shapeParams;  // 访问或赋值当前实例字段
    }
}
