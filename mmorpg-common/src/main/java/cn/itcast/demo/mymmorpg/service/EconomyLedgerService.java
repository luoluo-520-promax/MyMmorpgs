package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ItemLedger;
import cn.itcast.demo.mymmorpg.entity.WalletLedger;
import cn.itcast.demo.mymmorpg.repository.ItemLedgerRepository;
import cn.itcast.demo.mymmorpg.repository.WalletLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 货币 / 道具流水账本写入（幂等键唯一）。
 */
@Service
@ConditionalOnBean(WalletLedgerRepository.class)
public class EconomyLedgerService {

    private static final Logger log = LoggerFactory.getLogger(EconomyLedgerService.class);

    private final WalletLedgerRepository walletLedgerRepository;
    private final ItemLedgerRepository itemLedgerRepository;

    public EconomyLedgerService(WalletLedgerRepository walletLedgerRepository,
                                ItemLedgerRepository itemLedgerRepository) {
        this.walletLedgerRepository = walletLedgerRepository;
        this.itemLedgerRepository = itemLedgerRepository;
    }

    @Transactional
    public void recordWallet(long playerId,
                             String currency,
                             long delta,
                             long balanceBefore,
                             long balanceAfter,
                             String bizType,
                             String bizNo,
                             String idempotencyKey,
                             String operator) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        if (walletLedgerRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        WalletLedger row = new WalletLedger();
        row.setPlayerId(playerId);
        row.setCurrency(currency == null || currency.isBlank() ? "GOLD" : currency);
        row.setDelta(delta);
        row.setBalanceBefore(balanceBefore);
        row.setBalanceAfter(balanceAfter);
        row.setBizType(bizType == null ? "" : bizType);
        row.setBizNo(bizNo == null ? "" : bizNo);
        row.setIdempotencyKey(idempotencyKey.trim());
        row.setOperator(operator == null || operator.isBlank() ? "system" : operator);
        row.setCreatedAt(System.currentTimeMillis());
        try {
            walletLedgerRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            log.debug("wallet ledger idempotent hit key={}", idempotencyKey);
        }
    }

    @Transactional
    public void recordItem(long playerId,
                           int itemConfigId,
                           int delta,
                           int countBefore,
                           int countAfter,
                           String bizType,
                           String bizNo,
                           String idempotencyKey,
                           String operator) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        if (itemLedgerRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        ItemLedger row = new ItemLedger();
        row.setPlayerId(playerId);
        row.setItemConfigId(itemConfigId);
        row.setDelta(delta);
        row.setCountBefore(countBefore);
        row.setCountAfter(countAfter);
        row.setBizType(bizType == null ? "" : bizType);
        row.setBizNo(bizNo == null ? "" : bizNo);
        row.setIdempotencyKey(idempotencyKey.trim());
        row.setOperator(operator == null || operator.isBlank() ? "system" : operator);
        row.setCreatedAt(System.currentTimeMillis());
        try {
            itemLedgerRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            log.debug("item ledger idempotent hit key={}", idempotencyKey);
        }
    }
}
