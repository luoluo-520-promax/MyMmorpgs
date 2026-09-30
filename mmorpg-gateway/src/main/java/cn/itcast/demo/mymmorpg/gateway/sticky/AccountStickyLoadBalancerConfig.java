package cn.itcast.demo.mymmorpg.gateway.sticky;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按 X-Account-Id / Authorization / X-Auth-Token 哈希粘滞到同一后端实例。
 */
@Configuration
public class AccountStickyLoadBalancerConfig {

    @Bean
    ReactorServiceInstanceLoadBalancer reactorServiceInstanceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new AccountStickyLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name);
    }

    static final class AccountStickyLoadBalancer implements ReactorServiceInstanceLoadBalancer {
        private final org.springframework.beans.factory.ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
        private final AtomicInteger position = new AtomicInteger();

        AccountStickyLoadBalancer(
                org.springframework.beans.factory.ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
                String ignoredServiceId) {
            this.supplierProvider = supplierProvider;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Mono<Response<ServiceInstance>> choose(Request request) {
            ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
            if (supplier == null) {
                return Mono.just(new EmptyResponse());
            }
            return supplier.get(request).next().map(instances -> pick(instances, request));
        }

        @SuppressWarnings("rawtypes")
        private Response<ServiceInstance> pick(List<ServiceInstance> instances, Request request) {
            if (instances == null || instances.isEmpty()) {
                return new EmptyResponse();
            }
            String key = stickyKey(request);
            if (key == null || key.isBlank()) {
                int idx = Math.floorMod(position.getAndIncrement(), instances.size());
                return new DefaultResponse(instances.get(idx));
            }
            int idx = Math.floorMod(key.hashCode(), instances.size());
            return new DefaultResponse(instances.get(idx));
        }

        @SuppressWarnings("rawtypes")
        private static String stickyKey(Request request) {
            if (request == null || !(request.getContext() instanceof RequestDataContext ctx)
                    || ctx.getClientRequest() == null) {
                return null;
            }
            HttpHeaders headers = ctx.getClientRequest().getHeaders();
            if (headers == null) {
                return null;
            }
            String accountId = headers.getFirst("X-Account-Id");
            if (accountId != null && !accountId.isBlank()) {
                return accountId.trim();
            }
            String auth = headers.getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                return auth.trim();
            }
            String token = headers.getFirst("X-Auth-Token");
            if (token != null && !token.isBlank()) {
                return token.trim();
            }
            return null;
        }
    }
}
