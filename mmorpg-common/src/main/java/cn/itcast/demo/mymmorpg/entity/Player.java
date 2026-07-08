/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/Player.java
 * 类型：类
 * 职责：定义 Player，供各业务模块复用与扩展。
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
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Long） */
    private Long id;@Column(name = "account_id", nullable = false)
    /** 账号标识（类型：Long） */
    private Long accountId;@Column(name = "name", nullable = false, unique = true, length = 64)
    /** 名称（类型：String） */
    private String name;@Column(name = "level", nullable = false)
    /** 等级（类型：Integer） */
    private Integer level = 1;@Column(name = "vip_right", nullable = false)
    /** vipright（类型：Integer） */
    private Integer vipRight = 0;
    /** 金币（出售等）*/
    @Column(name = "gold", nullable = false)
    /** 金币（类型：Long） */
    private Long gold = 0L;
    /** 累计经验 */
    @Column(name = "exp", nullable = false)
    /** 经验（类型：Long） */
    private Long exp = 0L;/**
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
     * 获取账号标识属性值
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * 设置账号标识属性值
     */
    public void setAccountId(Long accountId) {
        this.accountId = accountId;  // 访问或赋值当前实例字段
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
     * 获取等级属性值
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置等级属性值
     */
    public void setLevel(Integer level) {
        this.level = level;  // 访问或赋值当前实例字段
    }

    /**
     * 获取vipright属性值
     */
    public Integer getVipRight() {
        return vipRight;
    }

    /**
     * 设置vipright属性值
     */
    public void setVipRight(Integer vipRight) {
        this.vipRight = vipRight;  // 访问或赋值当前实例字段
    }

    /**
     * 获取金币属性值
     */
    public Long getGold() {
        return gold;
    }

    /**
     * 设置金币属性值
     */
    public void setGold(Long gold) {
        this.gold = gold;  // 访问或赋值当前实例字段
    }

    /**
     * 获取经验属性值
     */
    public Long getExp() {
        return exp;
    }

    /**
     * 设置经验属性值
     */
    public void setExp(Long exp) {
        this.exp = exp;  // 访问或赋值当前实例字段
    }
}
