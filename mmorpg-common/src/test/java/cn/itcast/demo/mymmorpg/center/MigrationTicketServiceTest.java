package cn.itcast.demo.mymmorpg.center;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationTicketServiceTest {

    @Test
    public void issueAndConsume_once() {
        MigrationTicketService service = new MigrationTicketService();
        String ticket = service.issue(1001L, 3, 1, 0, 1.5f, 0f, -2f);
        MigrationTicketService.TicketPayload payload = service.consume(ticket);
        assertThat(payload).isNotNull();
        assertThat(payload.playerId()).isEqualTo(1001L);
        assertThat(payload.sceneId()).isEqualTo(3);
        assertThat(service.consume(ticket)).isNull();
    }

    @Test
    public void consume_blankOrUnknown_returnsNull() {
        MigrationTicketService service = new MigrationTicketService();
        assertThat(service.consume(null)).isNull();
        assertThat(service.consume("")).isNull();
        assertThat(service.consume("missing")).isNull();
    }

    @Test
    public void issueSeamless_carriesVelocityAndZone() {
        MigrationTicketService service = new MigrationTicketService();
        String ticket = service.issueSeamless(7L, 2, 1, 0, 10f, 0f, 20f, 3f, -1f, 90f, 42);
        MigrationTicketService.TicketPayload payload = service.consume(ticket);
        assertThat(payload).isNotNull();
        assertThat(payload.seamless()).isTrue();
        assertThat(payload.zoneId()).isEqualTo(42);
        assertThat(payload.velocityX()).isEqualTo(3f);
        assertThat(payload.facingYaw()).isEqualTo(90f);
    }

    @Test
    public void renew_extendsTtlWithoutConsuming() {
        MigrationTicketService service = new MigrationTicketService();
        String ticket = service.issue(55L, 1, 1, 0, 0f, 0f, 0f);
        MigrationTicketService.TicketPayload renewed = service.renew(ticket);
        assertThat(renewed).isNotNull();
        assertThat(renewed.playerId()).isEqualTo(55L);
        assertThat(renewed.expireAtMillis()).isGreaterThan(System.currentTimeMillis());
        // renew 后仍可消费一次
        assertThat(service.consume(ticket)).isNotNull();
        assertThat(service.consume(ticket)).isNull();
    }

    @Test
    public void renew_unknownOrBlank_returnsNull() {
        MigrationTicketService service = new MigrationTicketService();
        assertThat(service.renew(null)).isNull();
        assertThat(service.renew("")).isNull();
        assertThat(service.renew("missing")).isNull();
    }
}
