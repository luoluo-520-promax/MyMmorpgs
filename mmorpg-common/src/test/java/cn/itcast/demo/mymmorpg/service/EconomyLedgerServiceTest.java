package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ItemLedger;
import cn.itcast.demo.mymmorpg.entity.WalletLedger;
import cn.itcast.demo.mymmorpg.repository.ItemLedgerRepository;
import cn.itcast.demo.mymmorpg.repository.WalletLedgerRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 货币/道具流水账本幂等写入。
 */
public class EconomyLedgerServiceTest {

    private WalletLedgerRepository walletRepo;
    private ItemLedgerRepository itemRepo;
    private EconomyLedgerService service;

    @BeforeMethod
    public void setUp() {
        walletRepo = mock(WalletLedgerRepository.class);
        itemRepo = mock(ItemLedgerRepository.class);
        service = new EconomyLedgerService(walletRepo, itemRepo);
    }

    @Test
    public void recordWallet_blankKey_skips() {
        service.recordWallet(1L, "GOLD", 10, 0, 10, "SHOP", "o1", "  ", "system");
        verify(walletRepo, never()).save(any());
    }

    @Test
    public void recordWallet_existingKey_skips() {
        when(walletRepo.findByIdempotencyKey("k1")).thenReturn(Optional.of(new WalletLedger()));
        service.recordWallet(1L, "GOLD", 10, 0, 10, "SHOP", "o1", "k1", "system");
        verify(walletRepo, never()).save(any());
    }

    @Test
    public void recordWallet_firstTime_saves() {
        when(walletRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        service.recordWallet(1L, "GOLD", 10, 0, 10, "SHOP", "o1", "k1", "system");
        verify(walletRepo).save(any(WalletLedger.class));
    }

    @Test
    public void recordItem_existingKey_skips() {
        when(itemRepo.findByIdempotencyKey("ik")).thenReturn(Optional.of(new ItemLedger()));
        service.recordItem(1L, 1001, 2, 0, 2, "GRANT", "b1", "ik", "system");
        verify(itemRepo, never()).save(any());
    }

    @Test
    public void recordItem_firstTime_saves() {
        when(itemRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        service.recordItem(1L, 1001, 2, 0, 2, "GRANT", "b1", "ik", "system");
        verify(itemRepo).save(any(ItemLedger.class));
    }
}
