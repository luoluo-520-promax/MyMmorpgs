package cn.itcast.demo.mymmorpg.observability;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LogDesensitizerTest {

    @Test
    public void maskPhoneKeepsHeadAndTail() {
        assertThat(LogDesensitizer.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(LogDesensitizer.maskPhone("138-1234-5678")).isEqualTo("138****5678");
    }

    @Test
    public void maskTokenHidesMiddle() {
        assertThat(LogDesensitizer.maskToken("abcd")).isEqualTo("****");
        assertThat(LogDesensitizer.maskToken("payment-ticket-abcdef12"))
                .startsWith("paym")
                .contains("****")
                .endsWith("ef12");
    }

    @Test
    public void desensitizeMapMasksSensitiveKeys() {
        Map<String, Object> masked = LogDesensitizer.desensitize(Map.of(
                "phone", "13900001111",
                "paymentTicket", "TICKET-SECRET-9999",
                "idCard", "110101199001011234",
                "bankCard", "6222021234567890123",
                "sessionToken", "sess-abcdef0123456789",
                "password", "SuperSecret!",
                "playerId", 42L));

        assertThat(masked.get("phone")).isEqualTo("139****1111");
        assertThat(String.valueOf(masked.get("paymentTicket"))).contains("****");
        assertThat(String.valueOf(masked.get("idCard"))).contains("****");
        assertThat(String.valueOf(masked.get("bankCard"))).contains("****");
        assertThat(String.valueOf(masked.get("sessionToken"))).contains("****");
        assertThat(String.valueOf(masked.get("password"))).contains("****");
        assertThat(masked.get("playerId")).isEqualTo(42L);
    }

    @Test
    public void desensitizeJsonMasksFieldsAndBarePhones() {
        String json = "{\"phone\":\"13812345678\",\"password\":\"pwd12345\",\"note\":\"call 13900001111\"}";
        String out = LogDesensitizer.desensitizeJson(json);
        assertThat(out).contains("138****5678");
        assertThat(out).doesNotContain("pwd12345");
        assertThat(out).contains("139****1111");
        assertThat(out).contains("****");
    }
}
