package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ShopConfigService {

    private static final Logger log = LoggerFactory.getLogger(ShopConfigService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String productsPath;
    private final String tabsPath;

    private volatile List<ShopProductConfig> products = List.of();
    private volatile List<ShopTabConfig> tabs = List.of();
    private volatile int version = 1;

    public ShopConfigService(ObjectMapper objectMapper,
                             ResourceLoader resourceLoader,
                             @Value("${game.shop.products-path:config/shop/products.json}") String productsPath,
                             @Value("${game.shop.tabs-path:config/shop/tabs.json}") String tabsPath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.productsPath = productsPath;
        this.tabsPath = tabsPath;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public boolean reload() {
        try {
            ProductsFile pf = readJson(productsPath, ProductsFile.class, "config/shop/products.json");
            TabsFile tf = readJson(tabsPath, TabsFile.class, "config/shop/tabs.json");
            List<ShopProductConfig> nextProducts = pf == null || pf.products == null
                    ? List.of() : List.copyOf(pf.products);
            List<ShopTabConfig> nextTabs = tf == null || tf.tabs == null
                    ? List.of() : List.copyOf(tf.tabs);
            if (!nextProducts.isEmpty()) {
                List<String> errors = ShopImportValidator.validate(nextProducts);
                if (!errors.isEmpty()) {
                    log.error("Shop products validation failed: {}", errors);
                    return false;
                }
            }
            products = nextProducts;
            tabs = nextTabs;
            version = nextProducts.stream().mapToInt(p -> p.version).max().orElse(1);
            log.info("Shop config loaded: products={} tabs={} version={}", products.size(), tabs.size(), version);
            return true;
        } catch (Exception e) {
            log.error("Shop config reload failed", e);
            return false;
        }
    }

    /**
     * 导入覆盖内存配置（Admin apply 后调用）；不强制写盘。
     */
    public boolean applyProducts(List<ShopProductConfig> incoming) {
        List<String> errors = ShopImportValidator.validate(incoming);
        if (!errors.isEmpty()) {
            log.warn("Shop apply rejected: {}", errors);
            return false;
        }
        products = List.copyOf(incoming);
        version = incoming.stream().mapToInt(p -> p.version).max().orElse(version + 1);
        return true;
    }

    public int version() {
        return version;
    }

    public List<ShopTabConfig> listTabs() {
        return tabs;
    }

    public List<ShopProductConfig> listOnSale(String tabId, long nowMs) {
        List<ShopProductConfig> out = new ArrayList<>();
        for (ShopProductConfig p : products) {
            if (p == null || !p.isOnSale(nowMs)) {
                continue;
            }
            if (tabId != null && !tabId.isBlank() && !tabId.equals(p.tabId)) {
                continue;
            }
            if (p.typeEnum() == ShopProductType.DISCOUNT_PACK && !p.isDiscountWindowActive(nowMs)) {
                continue;
            }
            out.add(p);
        }
        out.sort(Comparator.comparingInt((ShopProductConfig p) -> p.sort)
                .thenComparingInt(p -> p.productId));
        return out;
    }

    public Optional<ShopProductConfig> findById(int productId) {
        for (ShopProductConfig p : products) {
            if (p != null && p.productId == productId) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public List<ShopProductConfig> allProducts() {
        return products;
    }

    private <T> T readJson(String configPath, Class<T> type, String fallback) throws Exception {
        Path path = Path.of(configPath);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                return objectMapper.readValue(in, type);
            }
        }
        Resource resource = resourceLoader.getResource("classpath:" + configPath);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, type);
            }
        }
        Path alt = Path.of(fallback);
        if (Files.isRegularFile(alt)) {
            try (InputStream in = Files.newInputStream(alt)) {
                return objectMapper.readValue(in, type);
            }
        }
        log.warn("{} not found", configPath);
        return type.getDeclaredConstructor().newInstance();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductsFile {
        public List<ShopProductConfig> products = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TabsFile {
        public List<ShopTabConfig> tabs = new ArrayList<>();
    }
}
