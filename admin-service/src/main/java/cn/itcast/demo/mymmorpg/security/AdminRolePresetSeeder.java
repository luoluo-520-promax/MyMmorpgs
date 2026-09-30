package cn.itcast.demo.mymmorpg.security;

import cn.itcast.demo.mymmorpg.entity.AdminPermission;
import cn.itcast.demo.mymmorpg.entity.AdminRole;
import cn.itcast.demo.mymmorpg.entity.AdminRolePermission;
import cn.itcast.demo.mymmorpg.repository.AdminPermissionRepository;
import cn.itcast.demo.mymmorpg.repository.AdminRolePermissionRepository;
import cn.itcast.demo.mymmorpg.repository.AdminRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 将 {@link AdminRbacPresets} 幂等写入 admin_role / admin_permission / admin_role_permission。
 * 默认关闭；开发/首次部署设 {@code admin.rbac.seed-presets=true}。
 */
@Component
@ConditionalOnProperty(name = "admin.rbac.seed-presets", havingValue = "true")
public class AdminRolePresetSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRolePresetSeeder.class);

    private final AdminRoleRepository roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final AdminRolePermissionRepository rolePermissionRepository;

    public AdminRolePresetSeeder(AdminRoleRepository roleRepository,
                                 AdminPermissionRepository permissionRepository,
                                 AdminRolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String code : AdminRbacPresets.allPermissionCodes()) {
            ensurePermission(code);
        }
        for (AdminRbacPresets.RolePreset preset : AdminRbacPresets.all()) {
            AdminRole role = ensureRole(preset);
            for (String permCode : preset.permissions()) {
                AdminPermission perm = ensurePermission(permCode);
                linkIfAbsent(role.getId(), perm.getId());
            }
            log.info("RBAC preset seeded role={} permissions={}", preset.code(), preset.permissions());
        }
    }

    private AdminRole ensureRole(AdminRbacPresets.RolePreset preset) {
        return roleRepository.findByCode(preset.code()).orElseGet(() -> {
            AdminRole role = new AdminRole();
            role.setCode(preset.code());
            role.setName(preset.displayName());
            return roleRepository.save(role);
        });
    }

    private AdminPermission ensurePermission(String code) {
        return permissionRepository.findByCode(code).orElseGet(() -> {
            AdminPermission p = new AdminPermission();
            p.setCode(code);
            p.setDescription(code);
            return permissionRepository.save(p);
        });
    }

    private void linkIfAbsent(Long roleId, Long permissionId) {
        boolean exists = rolePermissionRepository.findByRoleIdIn(List.of(roleId)).stream()
                .anyMatch(rp -> permissionId.equals(rp.getPermissionId()));
        if (!exists) {
            AdminRolePermission link = new AdminRolePermission();
            link.setRoleId(roleId);
            link.setPermissionId(permissionId);
            rolePermissionRepository.save(link);
        }
    }
}
