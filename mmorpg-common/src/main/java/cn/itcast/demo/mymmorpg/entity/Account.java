/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/Account.java
 * 类型：类
 * 职责：定义 Account，供各业务模块复用与扩展。
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
    private Long id;

    @Column(name = "account_name", nullable = false, unique = true, length = 64)
    private String accountName;

    @Column(name = "password", nullable = false, length = 128)
    private String password;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    /** 封账号：禁止该账号下所有角色登录/选角 */
    @Column(name = "banned", nullable = false)
    private Boolean banned = false;

    @Column(name = "ban_reason", length = 255)
    private String banReason;

    @Column(name = "ban_until")
    private LocalDateTime banUntil;

    @PrePersist
    void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (banned == null) {
            banned = false;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Boolean getBanned() {
        return banned;
    }

    public void setBanned(Boolean banned) {
        this.banned = banned;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public LocalDateTime getBanUntil() {
        return banUntil;
    }

    public void setBanUntil(LocalDateTime banUntil) {
        this.banUntil = banUntil;
    }
}
