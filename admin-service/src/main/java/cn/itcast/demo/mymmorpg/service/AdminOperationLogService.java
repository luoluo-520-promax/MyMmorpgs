package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.AdminOperationLog;
import cn.itcast.demo.mymmorpg.repository.AdminOperationLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminOperationLogService {

    private final AdminOperationLogRepository operationLogRepository;

    public AdminOperationLogService(AdminOperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional
    public AdminOperationLog record(Long adminUserId, String action, Long targetId, String detail) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(adminUserId);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setDetail(detail);
        return operationLogRepository.save(log);
    }
}
