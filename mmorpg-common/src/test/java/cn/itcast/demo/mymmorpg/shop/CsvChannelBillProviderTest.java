package cn.itcast.demo.mymmorpg.shop;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CsvChannelBillProviderTest {

    private static final String CSV = """
            channelOrderId,merchantOrderId,amountFen,status,paidAtMs
            ch-1,M1,100,SUCCESS,1000
            ch-2,M2,200,SUCCESS,2000
            ch-3,M3,300,SUCCESS,3000
            """;

    @Test
    public void fetchBills_filtersByPaidAtMs() {
        CsvChannelBillProvider provider = new CsvChannelBillProvider(CSV);
        assertThat(provider.channel()).isEqualTo("CSV");

        List<ChannelBillProvider.ChannelBillLine> mid = provider.fetchBills("CSV", 1500, 2500);
        assertThat(mid).hasSize(1);
        assertThat(mid.get(0).channelOrderId()).isEqualTo("ch-2");
        assertThat(mid.get(0).amountFen()).isEqualTo(200L);

        assertThat(provider.fetchBills("CSV", 1000, 3000)).hasSize(3);
        assertThat(provider.fetchBills("CSV", 4000, 5000)).isEmpty();
    }

    @Test
    public void constructor_acceptsCustomChannelAndPath() throws Exception {
        Path tmp = Files.createTempFile("bills", ".csv");
        try {
            Files.writeString(tmp, CSV);
            CsvChannelBillProvider provider = new CsvChannelBillProvider(tmp, "WECHAT_CSV");
            assertThat(provider.channel()).isEqualTo("WECHAT_CSV");
            assertThat(provider.fetchBills(provider.channel(), 1000, 1000)).hasSize(1);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
