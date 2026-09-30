package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 养成流程：查体力 → 消耗进本 → 购买补充 → 掉落装备随机词条落 BLOB。
 */
public class ResinEquipFlowTest {

    @Test
    @SuppressWarnings("unchecked")
    public void resinGateThenEquipAffixBlob() {
        ObjectProvider redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        ResinService resin = new ResinService(redis);
        EquipRandomizer randomizer = new EquipRandomizer(new ObjectMapper());

        Map<String, Object> before = resin.status(1001L);
        assertThat(before.get("current")).isEqualTo(ResinService.MAX_RESIN);

        // 深渊/副本消耗 40 点
        Map<String, Object> afterDungeon = resin.consume(1001L, 40);
        assertThat(afterDungeon.get("ok")).isEqualTo(true);
        assertThat(afterDungeon.get("current")).isEqualTo(ResinService.MAX_RESIN - 40);

        // 体力不足拒绝
        assertThat(resin.consume(1001L, 200).get("error")).isEqualTo("resin_not_enough");

        // 购买补充（演示：不扣费，只计次数）
        Map<String, Object> bought = resin.buy(1001L);
        assertThat(bought.get("ok")).isEqualTo(true);
        int afterBuy = (Integer) bought.get("current");
        assertThat(afterBuy).isGreaterThan(ResinService.MAX_RESIN - 40);
        assertThat(bought.get("buyCountToday")).isEqualTo(1);

        // 掉落 4 星装备 → 随机词条 → BLOB round-trip（模拟入库 affix_blob）
        EquipRandomizer.AffixRoll roll = randomizer.roll(5001, 4);
        byte[] blob = randomizer.toBlob(roll);
        EquipRandomizer.AffixRoll loaded = randomizer.fromBlob(blob);
        assertThat(loaded.mainStat()).isEqualTo(roll.mainStat());
        assertThat(loaded.mainValue()).isEqualTo(roll.mainValue());
        assertThat(loaded.subStats()).hasSize(roll.subStats().size());
        assertThat(blob.length).isGreaterThan(10);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dailyBuyLimitBlocksExtraPurchases() {
        ObjectProvider redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        ResinService resin = new ResinService(redis);
        resin.status(77L);
        for (int i = 0; i < ResinService.DAILY_BUY_LIMIT; i++) {
            assertThat(resin.buy(77L).get("ok")).isEqualTo(true);
        }
        assertThat(resin.buy(77L).get("error")).isEqualTo("buy_limit");
    }
}
