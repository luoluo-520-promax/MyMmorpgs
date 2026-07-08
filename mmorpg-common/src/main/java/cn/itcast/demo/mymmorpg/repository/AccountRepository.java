/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/AccountRepository.java
 * 类型：接口
 * 职责：账号表（account）的 Spring Data JPA 仓储，供登录与鉴权使用。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 持久层接口统一包名，各服务模块均可扫描注入

import cn.itcast.demo.mymmorpg.entity.Account; // 账号实体，映射数据库 account 表

import org.springframework.data.jpa.repository.JpaRepository; // 提供 save/findById/delete 等 CRUD 及分页能力

import java.util.Optional; // 可能查不到账号时用 Optional 代替 null

/**
 * 账号数据访问接口。
 * <p>继承 {@link JpaRepository} 后自动具备按主键增删改查；自定义方法由方法名派生 SQL。</p>
 */
public interface AccountRepository extends JpaRepository<Account, Long> { // 实体类型 Account，主键类型 Long

    /**
     * 按登录名查询账号（登录流程第一步：校验账号是否存在）。
     *
     * @param accountName 用户输入的账号名，对应实体字段 accountName
     * @return 存在则 {@code Optional.of(account)}，不存在则 {@code Optional.empty()}
     */
    Optional<Account> findByAccountName(String accountName); // Spring Data 解析为 WHERE account_name = ?
}
