package cn.itcast.demo.mymmorpg.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 RBAC 角色预设与权限码文档。
 * <p>
 * 权限码约定（与 {@code AdminPermissionService.hasPermission} 匹配）：
 * <ul>
 *   <li>{@code ops:reload} — 热更新 / 配置重载</li>
 *   <li>{@code ops:rollback} — 配置回滚</li>
 *   <li>{@code ops:gray} — 灰度发布开关</li>
 *   <li>{@code config:edit} — 配置编辑</li>
 *   <li>{@code config:import} — 配置导入</li>
 *   <li>{@code config:preview} — 配置预览（干跑）</li>
 *   <li>{@code complaint:handle} — 客诉处理</li>
 *   <li>{@code player:query} — 玩家查询</li>
 *   <li>{@code *} — 超级管理员通配（拥有全部权限）</li>
 * </ul>
 * 角色编码写入 {@code admin_role.code}，权限写入 {@code admin_permission.code}。
 */
public final class AdminRbacPresets {

    public static final String ROLE_OPS = "OPS";
    public static final String ROLE_PLANNER = "PLANNER";
    public static final String ROLE_CS = "CS";
    public static final String ROLE_SUPERADMIN = "SUPERADMIN";

    public static final String PERM_ALL = "*";
    public static final String PERM_OPS_RELOAD = "ops:reload";
    public static final String PERM_OPS_ROLLBACK = "ops:rollback";
    public static final String PERM_OPS_GRAY = "ops:gray";
    public static final String PERM_CONFIG_EDIT = "config:edit";
    public static final String PERM_CONFIG_IMPORT = "config:import";
    public static final String PERM_CONFIG_PREVIEW = "config:preview";
    public static final String PERM_COMPLAINT_HANDLE = "complaint:handle";
    public static final String PERM_PLAYER_QUERY = "player:query";

    public record RolePreset(String code, String displayName, Set<String> permissions) {
    }

    private AdminRbacPresets() {
    }

    public static RolePreset ops() {
        return new RolePreset(ROLE_OPS, "运维", Set.of(
                PERM_OPS_RELOAD, PERM_OPS_ROLLBACK, PERM_OPS_GRAY));
    }

    public static RolePreset planner() {
        return new RolePreset(ROLE_PLANNER, "策划", Set.of(
                PERM_CONFIG_EDIT, PERM_CONFIG_IMPORT, PERM_CONFIG_PREVIEW));
    }

    public static RolePreset cs() {
        return new RolePreset(ROLE_CS, "客服", Set.of(
                PERM_COMPLAINT_HANDLE, PERM_PLAYER_QUERY));
    }

    public static RolePreset superAdmin() {
        return new RolePreset(ROLE_SUPERADMIN, "超级管理员", Set.of(PERM_ALL));
    }

    /** 全部预设，顺序稳定便于种子脚本。 */
    public static List<RolePreset> all() {
        return List.of(ops(), planner(), cs(), superAdmin());
    }

    /** roleCode → permission codes */
    public static Map<String, Set<String>> asMap() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (RolePreset p : all()) {
            map.put(p.code(), new LinkedHashSet<>(p.permissions()));
        }
        return map;
    }

    /** 文档用：全部已知权限码（含通配）。 */
    public static Set<String> allPermissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(PERM_OPS_RELOAD);
        codes.add(PERM_OPS_ROLLBACK);
        codes.add(PERM_OPS_GRAY);
        codes.add(PERM_CONFIG_EDIT);
        codes.add(PERM_CONFIG_IMPORT);
        codes.add(PERM_CONFIG_PREVIEW);
        codes.add(PERM_COMPLAINT_HANDLE);
        codes.add(PERM_PLAYER_QUERY);
        codes.add(PERM_ALL);
        return codes;
    }
}
