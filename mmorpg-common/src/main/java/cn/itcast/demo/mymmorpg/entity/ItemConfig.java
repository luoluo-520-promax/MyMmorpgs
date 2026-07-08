/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/ItemConfig.java
 * 类型：类
 * 职责：定义 ItemConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;

import jakarta.persistence.GenerationType;

import jakarta.persistence.Id;

import jakarta.persistence.Table;

@Entity
@Table(name = "item_config")
public class ItemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Integer） */
    private Integer id;@Column(name = "name", nullable = false, length = 64)
    /** 名称（类型：String） */
    private String name;@Column(name = "kind", nullable = false)
    /** kind（类型：Integer） */
    private Integer kind;@Column(name = "stack_limit", nullable = false)
    /** stacklimit（类型：Integer） */
    private Integer stackLimit = 1;@Column(name = "level_required", nullable = false)
    /** 等级required（类型：Integer） */
    private Integer levelRequired = 0;@Column(name = "description", length = 255)
    /** description（类型：String） */
    private String description;@Column(name = "price", nullable = false)
    /** price（类型：Integer） */
    private Integer price = 0;@Column(name = "sell_price", nullable = false)
    /** sellprice（类型：Integer） */
    private Integer sellPrice = 0;@Column(name = "effect_params", length = 255)
    /** effectparams（类型：String） */
    private String effectParams;/**
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
     * 获取kind属性值
     */
    public Integer getKind() {
        return kind;
    }

    /**
     * 设置kind属性值
     */
    public void setKind(Integer kind) {
        this.kind = kind;  // 访问或赋值当前实例字段
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
     * 获取等级required属性值
     */
    public Integer getLevelRequired() {
        return levelRequired;
    }

    /**
     * 设置等级required属性值
     */
    public void setLevelRequired(Integer levelRequired) {
        this.levelRequired = levelRequired;  // 访问或赋值当前实例字段
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

    /**
     * 获取price属性值
     */
    public Integer getPrice() {
        return price;
    }

    /**
     * 设置price属性值
     */
    public void setPrice(Integer price) {
        this.price = price;  // 访问或赋值当前实例字段
    }

    /**
     * 获取sellprice属性值
     */
    public Integer getSellPrice() {
        return sellPrice;
    }

    /**
     * 设置sellprice属性值
     */
    public void setSellPrice(Integer sellPrice) {
        this.sellPrice = sellPrice;  // 访问或赋值当前实例字段
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
}
