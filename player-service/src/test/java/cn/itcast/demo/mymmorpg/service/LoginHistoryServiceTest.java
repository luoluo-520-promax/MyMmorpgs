package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.LoginHistory;
import cn.itcast.demo.mymmorpg.repository.LoginHistoryRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录历史风控：新 IP / 新设备判定。
 */
public class LoginHistoryServiceTest {

    private LoginHistoryRepository repository;
    private LoginHistoryService service;

    @BeforeMethod
    public void setUp() {
        repository = mock(LoginHistoryRepository.class);
        service = new LoginHistoryService(repository);
    }

    @Test
    public void evaluateRisk_firstLogin_noRisk() {
        when(repository.findTop20ByAccountIdAndSuccessOrderByCreatedAtDesc(1L, true)).thenReturn(List.of());
        assertThat(service.evaluateRisk(1L, new AuthTokenService.LoginDeviceContext("d", "PC", "1.1.1.1", "")))
                .isNull();
    }

    @Test
    public void evaluateRisk_newIpAndDevice() {
        LoginHistory old = new LoginHistory();
        old.setClientIp("2.2.2.2");
        old.setDeviceId("old-dev");
        when(repository.findTop20ByAccountIdAndSuccessOrderByCreatedAtDesc(1L, true)).thenReturn(List.of(old));
        assertThat(service.evaluateRisk(1L, new AuthTokenService.LoginDeviceContext("new-dev", "PC", "9.9.9.9", "")))
                .isEqualTo("NEW_IP_AND_DEVICE");
    }

    @Test
    public void evaluateRisk_knownIpAndDevice_ok() {
        LoginHistory old = new LoginHistory();
        old.setClientIp("1.1.1.1");
        old.setDeviceId("d1");
        when(repository.findTop20ByAccountIdAndSuccessOrderByCreatedAtDesc(1L, true)).thenReturn(List.of(old));
        assertThat(service.evaluateRisk(1L, new AuthTokenService.LoginDeviceContext("d1", "PC", "1.1.1.1", "")))
                .isNull();
    }

    @Test
    public void record_persists() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LoginHistory h = service.record(7L, "hero",
                new AuthTokenService.LoginDeviceContext("d", "MOBILE", "3.3.3.3", "ua"),
                true, "NEW_IP");
        assertThat(h.getAccountId()).isEqualTo(7L);
        assertThat(h.getClientType()).isEqualTo("MOBILE");
        assertThat(h.isSuccess()).isTrue();
        assertThat(h.getRiskFlag()).isEqualTo("NEW_IP");
    }
}
