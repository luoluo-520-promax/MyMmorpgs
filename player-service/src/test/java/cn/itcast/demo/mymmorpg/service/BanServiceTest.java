package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 封账号 / 封角色校验与落库。
 */
public class BanServiceTest {

    private AccountRepository accountRepository;
    private PlayerRepository playerRepository;
    private BanService banService;

    @BeforeMethod
    public void setUp() {
        accountRepository = mock(AccountRepository.class);
        playerRepository = mock(PlayerRepository.class);
        banService = new BanService(accountRepository, playerRepository);
    }

    @Test
    public void checkAccount_bannedPermanent() {
        Account a = new Account();
        a.setBanned(true);
        a.setBanUntil(null);
        assertThat(banService.checkAccount(a)).isEqualTo(RetCode.ACCOUNT_BANNED);
    }

    @Test
    public void checkAccount_banExpired_allows() {
        Account a = new Account();
        a.setBanned(true);
        a.setBanUntil(LocalDateTime.now().minusDays(1));
        assertThat(banService.checkAccount(a)).isEqualTo(RetCode.OK);
    }

    @Test
    public void checkPlayer_banned() {
        Player p = new Player();
        p.setBanned(true);
        p.setBanUntil(LocalDateTime.now().plusDays(1));
        assertThat(banService.checkPlayer(p)).isEqualTo(RetCode.PLAYER_BANNED);
    }

    @Test
    public void banAccount_persistsFlags() {
        Account a = new Account();
        a.setId(1L);
        a.setBanned(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(a));
        LocalDateTime until = LocalDateTime.now().plusDays(7);
        banService.banAccount(1L, "cheat", until);
        assertThat(a.getBanned()).isTrue();
        assertThat(a.getBanReason()).isEqualTo("cheat");
        assertThat(a.getBanUntil()).isEqualTo(until);
        verify(accountRepository).save(a);
    }

    @Test
    public void unbanPlayer_clearsFlags() {
        Player p = new Player();
        p.setId(9L);
        p.setBanned(true);
        p.setBanReason("t");
        when(playerRepository.findById(9L)).thenReturn(Optional.of(p));
        banService.unbanPlayer(9L);
        assertThat(p.getBanned()).isFalse();
        assertThat(p.getBanReason()).isNull();
        verify(playerRepository).save(p);
    }
}
