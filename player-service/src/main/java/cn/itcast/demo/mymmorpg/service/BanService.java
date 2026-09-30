package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 封禁：支持封账号（全角色）与封角色（单角色）。
 */
@Service
public class BanService {

    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;

    public BanService(AccountRepository accountRepository, PlayerRepository playerRepository) {
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
    }

    public int checkAccount(Account account) {
        if (account == null) {
            return RetCode.ACCOUNT_NOT_FOUND;
        }
        if (Boolean.TRUE.equals(account.getBanned())) {
            LocalDateTime until = account.getBanUntil();
            if (until == null || until.isAfter(LocalDateTime.now())) {
                return RetCode.ACCOUNT_BANNED;
            }
        }
        return RetCode.OK;
    }

    public int checkPlayer(Player player) {
        if (player == null) {
            return RetCode.PLAYER_NOT_FOUND;
        }
        if (Boolean.TRUE.equals(player.getBanned())) {
            LocalDateTime until = player.getBanUntil();
            if (until == null || until.isAfter(LocalDateTime.now())) {
                return RetCode.PLAYER_BANNED;
            }
        }
        return RetCode.OK;
    }

    @Transactional
    public void banAccount(long accountId, String reason, LocalDateTime until) {
        accountRepository.findById(accountId).ifPresent(a -> {
            a.setBanned(true);
            a.setBanReason(reason);
            a.setBanUntil(until);
            accountRepository.save(a);
        });
    }

    @Transactional
    public void unbanAccount(long accountId) {
        accountRepository.findById(accountId).ifPresent(a -> {
            a.setBanned(false);
            a.setBanReason(null);
            a.setBanUntil(null);
            accountRepository.save(a);
        });
    }

    @Transactional
    public void banPlayer(long playerId, String reason, LocalDateTime until) {
        playerRepository.findById(playerId).ifPresent(p -> {
            p.setBanned(true);
            p.setBanReason(reason);
            p.setBanUntil(until);
            playerRepository.save(p);
        });
    }

    @Transactional
    public void unbanPlayer(long playerId) {
        playerRepository.findById(playerId).ifPresent(p -> {
            p.setBanned(false);
            p.setBanReason(null);
            p.setBanUntil(null);
            playerRepository.save(p);
        });
    }
}
