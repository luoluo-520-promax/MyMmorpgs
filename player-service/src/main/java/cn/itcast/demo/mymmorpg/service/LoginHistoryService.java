package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.LoginHistory;
import cn.itcast.demo.mymmorpg.repository.LoginHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录历史与简易异地/新设备风控。
 */
@Service
public class LoginHistoryService {

    private final LoginHistoryRepository repository;

    public LoginHistoryService(LoginHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LoginHistory record(long accountId, String accountName, AuthTokenService.LoginDeviceContext device,
                               boolean success, String riskFlag) {
        LoginHistory h = new LoginHistory();
        h.setAccountId(accountId);
        h.setAccountName(accountName);
        if (device != null) {
            h.setClientIp(device.clientIp());
            h.setDeviceId(device.deviceId());
            h.setClientType(device.clientType());
            h.setUserAgent(device.userAgent());
        }
        h.setSuccess(success);
        h.setRiskFlag(riskFlag);
        h.setCreatedAt(LocalDateTime.now());
        return repository.save(h);
    }

    /**
     * 若 IP 或设备相对近期成功登录发生显著变化，返回风险标记；否则 null。
     */
    public String evaluateRisk(long accountId, AuthTokenService.LoginDeviceContext device) {
        List<LoginHistory> recent = repository.findTop20ByAccountIdAndSuccessOrderByCreatedAtDesc(accountId, true);
        if (recent.isEmpty()) {
            return null;
        }
        String ip = device == null ? "" : nullToEmpty(device.clientIp());
        String did = device == null ? "" : nullToEmpty(device.deviceId());
        boolean ipSeen = ip.isBlank();
        boolean deviceSeen = did.isBlank();
        for (LoginHistory h : recent) {
            if (!ip.isBlank() && ip.equals(nullToEmpty(h.getClientIp()))) {
                ipSeen = true;
            }
            if (!did.isBlank() && did.equals(nullToEmpty(h.getDeviceId()))) {
                deviceSeen = true;
            }
        }
        if (!ipSeen && !deviceSeen) {
            return "NEW_IP_AND_DEVICE";
        }
        if (!ipSeen) {
            return "NEW_IP";
        }
        if (!deviceSeen) {
            return "NEW_DEVICE";
        }
        return null;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
