package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MqInboxEvent;
import cn.itcast.demo.mymmorpg.repository.MqInboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MqInboxService：claim / 重复跳过。
 */
public class MqInboxServiceTest {

    private MqInboxEventRepository inboxRepository;
    private MqInboxService service;

    @BeforeMethod
    public void setUp() {
        inboxRepository = mock(MqInboxEventRepository.class);
        service = new MqInboxService(inboxRepository);
    }

    @Test
    public void tryClaim_blankKey_allows() {
        assertThat(service.tryClaim("g", "", "T", "p")).isTrue();
        assertThat(service.tryClaim(" ", "k", "T", "p")).isTrue();
        verify(inboxRepository, never()).existsByConsumerGroupAndMessageKey(any(), any());
    }

    @Test
    public void tryClaim_firstTime_savesAndReturnsTrue() {
        when(inboxRepository.existsByConsumerGroupAndMessageKey("activity", "battle:end:1")).thenReturn(false);
        when(inboxRepository.saveAndFlush(any(MqInboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.tryClaim("activity", "battle:end:1", "BATTLE_EVENTS", "body")).isTrue();
        verify(inboxRepository).saveAndFlush(any(MqInboxEvent.class));
    }

    @Test
    public void tryClaim_alreadyExists_returnsFalse() {
        when(inboxRepository.existsByConsumerGroupAndMessageKey(eq("activity"), eq("battle:end:1")))
                .thenReturn(true);
        assertThat(service.tryClaim("activity", "battle:end:1", "BATTLE_EVENTS", "body")).isFalse();
        verify(inboxRepository, never()).saveAndFlush(any());
    }

    @Test
    public void tryClaim_uniqueViolation_returnsFalse() {
        when(inboxRepository.existsByConsumerGroupAndMessageKey(any(), any())).thenReturn(false);
        when(inboxRepository.saveAndFlush(any(MqInboxEvent.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.tryClaim("activity", "battle:end:1", "BATTLE_EVENTS", "body")).isFalse();
    }
}
