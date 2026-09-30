package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerMail;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerMailRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.RankingScoreStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 深渊发奖依赖的大厅系统邮件路径。
 */
public class HallSystemMailFlowTest {

    private PlayerMailRepository mailRepository;
    private HallService hallService;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        mailRepository = mock(PlayerMailRepository.class);
        when(mailRepository.save(any(PlayerMail.class))).thenAnswer(inv -> {
            PlayerMail mail = inv.getArgument(0);
            mail.setId(42L);
            return mail;
        });
        ObjectProvider empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        hallService = new HallService(
                mock(PlayerRepository.class),
                mock(PlayerFriendRepository.class),
                mailRepository,
                empty,
                empty,
                empty,
                mock(PlayerPresenceQuery.class),
                mock(RankingScoreStore.class),
                new ObjectMapper());
    }

    @Test
    public void sendSystemMail_persistsAbyssAttachments() {
        String attachments = "[{\"itemId\":10002,\"count\":50}]";
        PlayerMail mail = hallService.sendSystemMail(1001L, "深渊里程碑 9 星", "奖励", attachments);
        assertThat(mail.getId()).isEqualTo(42L);
        assertThat(mail.getPlayerId()).isEqualTo(1001L);
        assertThat(mail.getClaimed()).isFalse();
        assertThat(mail.getAttachmentsJson()).isEqualTo(attachments);
        assertThat(mail.getTitle()).contains("9");
    }
}
