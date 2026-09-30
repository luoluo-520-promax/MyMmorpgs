package cn.itcast.demo.mymmorpg.shop;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonChannelBillProviderTest {

    private static final String JSON = """
            [
              {"channelOrderId":"ch-1","merchantOrderId":"M1","amountFen":100,"status":"SUCCESS","paidAtMs":1000},
              {"channelOrderId":"ch-2","merchantOrderId":"M2","amountFen":200,"status":"SUCCESS","paidAtMs":2000},
              {"channelOrderId":"ch-3","merchantOrderId":"M3","amountFen":300,"status":"SUCCESS","paidAtMs":3000}
            ]
            """;

    @Test
    public void fetchBills_filtersByPaidAtMs() {
        JsonChannelBillProvider provider = new JsonChannelBillProvider(JSON);
        assertThat(provider.channel()).isEqualTo("JSON");

        List<ChannelBillProvider.ChannelBillLine> mid = provider.fetchBills("JSON", 1500, 2500);
        assertThat(mid).hasSize(1);
        assertThat(mid.get(0).channelOrderId()).isEqualTo("ch-2");
        assertThat(mid.get(0).merchantOrderId()).isEqualTo("M2");

        assertThat(provider.fetchBills("JSON", 1000, 3000)).hasSize(3);
        assertThat(provider.fetchBills("JSON", 0, 999)).isEmpty();
    }

    @Test
    public void constructor_acceptsCustomChannelAndPath() throws Exception {
        Path tmp = Files.createTempFile("bills", ".json");
        try {
            Files.writeString(tmp, JSON);
            JsonChannelBillProvider provider = new JsonChannelBillProvider(tmp, "ALIPAY_JSON");
            assertThat(provider.channel()).isEqualTo("ALIPAY_JSON");
            assertThat(provider.fetchBills(provider.channel(), 2000, 2000)).hasSize(1);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
