package cn.itcast.demo.mymmorpg.gateway.sticky;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gateway Sticky：同 X-Account-Id 粘滞到同一实例。
 */
public class AccountStickyLoadBalancerTest {

    private AccountStickyLoadBalancerConfig.AccountStickyLoadBalancer balancer;
    private List<ServiceInstance> instances;

    @BeforeMethod
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setUp() {
        instances = List.of(
                new DefaultServiceInstance("i1", "player-service", "10.0.0.1", 8080, false),
                new DefaultServiceInstance("i2", "player-service", "10.0.0.2", 8080, false),
                new DefaultServiceInstance("i3", "player-service", "10.0.0.3", 8080, false));
        ServiceInstanceListSupplier supplier = mock(ServiceInstanceListSupplier.class);
        when(supplier.get(any(Request.class))).thenReturn(Flux.just(instances));
        ObjectProvider<ServiceInstanceListSupplier> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(supplier);
        balancer = new AccountStickyLoadBalancerConfig.AccountStickyLoadBalancer(provider, "player-service");
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void sameAccount_stickyToSameInstance() {
        Request request = requestWithHeader("X-Account-Id", "account-42");
        Response<ServiceInstance> first = balancer.choose(request).block();
        Response<ServiceInstance> second = balancer.choose(request).block();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.hasServer()).isTrue();
        assertThat(first.getServer().getInstanceId()).isEqualTo(second.getServer().getInstanceId());

        int expectedIdx = Math.floorMod("account-42".hashCode(), instances.size());
        assertThat(first.getServer().getInstanceId()).isEqualTo(instances.get(expectedIdx).getInstanceId());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void noStickyKey_roundRobin() {
        Request request = requestWithHeader("X-Other", "x");
        ServiceInstance a = balancer.choose(request).block().getServer();
        ServiceInstance b = balancer.choose(request).block().getServer();
        // 无 sticky 时使用 position 递增，连续两次应不同（实例数>1）
        assertThat(a.getInstanceId()).isNotEqualTo(b.getInstanceId());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void emptyInstances_emptyResponse() {
        ServiceInstanceListSupplier supplier = mock(ServiceInstanceListSupplier.class);
        when(supplier.get(any(Request.class))).thenReturn(Flux.just(List.of()));
        ObjectProvider<ServiceInstanceListSupplier> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(supplier);
        var emptyBalancer = new AccountStickyLoadBalancerConfig.AccountStickyLoadBalancer(provider, "x");

        StepVerifier.create(emptyBalancer.choose(requestWithHeader("X-Account-Id", "1")))
                .assertNext(r -> assertThat(r.hasServer()).isFalse())
                .verifyComplete();
    }

    @SuppressWarnings("rawtypes")
    private static Request requestWithHeader(String name, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(name, value);
        RequestData data = new RequestData(HttpMethod.GET, URI.create("http://player-service/api"), headers, null, null);
        RequestDataContext ctx = new RequestDataContext(data, null);
        Request request = mock(Request.class);
        when(request.getContext()).thenReturn(ctx);
        return request;
    }
}
