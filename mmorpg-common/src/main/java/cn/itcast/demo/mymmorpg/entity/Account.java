/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/Account.java
 * 类型：类
 * 职责：定义 Account，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;

import jakarta.persistence.GenerationType;

import jakarta.persistence.Id;

import jakarta.persistence.PrePersist;

import jakarta.persistence.Table;


import java.time.LocalDateTime;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Long） */
    private Long id;
    @Column(name = "account_name", nullable = false, unique = true, length = 64)
    /** 账号名称（类型：String） */
    private String accountName;
    @Column(name = "password", nullable = false, length = 128)
    /** password（类型：String） */
    private String password;
    @Column(name = "create_time", nullable = false)
    /** createtime（类型：LocalDateTime） */
    private LocalDateTime createTime;
    @Column(name = "last_login_time")
    /** last登录time（类型：LocalDateTime） */
    private LocalDateTime lastLoginTime;@PrePersist
    /**
     * prepersist；无参数
     */
    void prePersist() {
        if (createTime == null) { // 条件分支判断
            createTime = LocalDateTime.now();  // 局部变量赋值
        }
    }

    /**
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
     * 获取账号名称属性值
     */
    public String getAccountName() {
        return accountName;
    }

    /**
     * 设置账号名称属性值
     */
    public void setAccountName(String accountName) {
        this.accountName = accountName;  // 访问或赋值当前实例字段
    }

    /**
     * 获取password属性值
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置password属性值
     */
    public void setPassword(String password) {
        this.password = password;  // 访问或赋值当前实例字段
    }

    /**
     * 获取createtime属性值
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置createtime属性值
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;  // 访问或赋值当前实例字段
    }

    /**
     * 获取last登录time属性值
     */
    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    /**
     * 设置last登录time属性值
     */
    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;  // 访问或赋值当前实例字段
    }
}
