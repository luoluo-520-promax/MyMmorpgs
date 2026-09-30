package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ShopService;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopCsvImporter;
import cn.itcast.demo.mymmorpg.shop.ShopImportValidator;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import cn.itcast.demo.mymmorpg.shop.ShopProductConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城内部 API：导入/热更/Mock 支付/验签确认/退款/运维补发。
 * 在 player（嵌入）与 shop-service（独立）进程均可暴露。
 */
@RestController
@RequestMapping("/internal/shop")
@ConditionalOnBean(ShopService.class)
public class InternalShopController {

    private final ShopService shopService;
    private final ShopConfigService shopConfigService;
    private final ObjectMapper objectMapper;

    public InternalShopController(ShopService shopService,
                                  ShopConfigService shopConfigService,
                                  ObjectMapper objectMapper) {
        this.shopService = shopService;
        this.shopConfigService = shopConfigService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/orders/reconcile")
    public Map<String, Object> reconcile(@RequestParam(defaultValue = "0") long fromMs,
                                         @RequestParam(defaultValue = "0") long toMs) {
        long end = toMs > 0 ? toMs : System.currentTimeMillis();
        long start = fromMs > 0 ? fromMs : end - 86_400_000L;
        return shopService.reconcile(start, end);
    }

    @PostMapping("/mock-pay/{orderId}")
    public Map<String, Object> mockPay(@PathVariable String orderId,
                                       @RequestParam(required = false) String channelOrderId) {
        return shopService.mockPay(orderId, channelOrderId);
    }

    @PostMapping("/orders/{orderId}/confirm-pay")
    public Map<String, Object> confirmPay(@PathVariable String orderId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        ShopPaymentReceipt receipt = new ShopPaymentReceipt();
        receipt.orderId = orderId;
        if (body != null) {
            receipt.channel = body.get("channel") == null ? null : String.valueOf(body.get("channel"));
            receipt.channelOrderId = body.get("channelOrderId") == null ? null : String.valueOf(body.get("channelOrderId"));
            receipt.receiptData = body.get("receiptData") == null ? null : String.valueOf(body.get("receiptData"));
            if (body.get("payAmount") instanceof Number n) {
                receipt.payAmount = n.longValue();
            }
        }
        return shopService.confirmPay(receipt);
    }

    @PostMapping("/orders/{orderId}/fulfill")
    public Map<String, Object> fulfill(@PathVariable String orderId) {
        return shopService.fulfillByOps(orderId);
    }

    @PostMapping("/orders/{orderId}/refund")
    public Map<String, Object> refund(@PathVariable String orderId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        boolean reclaim = body != null && Boolean.TRUE.equals(body.get("reclaimItems"));
        return shopService.refundOrder(orderId, reason, reclaim);
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        boolean ok = shopConfigService.reload();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", ok);
        body.put("service", "shop");
        body.put("version", shopConfigService.version());
        body.put("productCount", shopConfigService.allProducts().size());
        return body;
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importProducts(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) throws Exception {
        ShopConfigService.ProductsFile file = objectMapper.readValue(body, ShopConfigService.ProductsFile.class);
        List<ShopProductConfig> products = file.products == null ? List.of() : file.products;
        return respondImport(dryRun, products);
    }

    @PostMapping(value = "/import", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> importProductsCsv(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) throws Exception {
        return respondImport(dryRun, ShopCsvImporter.parse(body));
    }

    private ResponseEntity<Map<String, Object>> respondImport(boolean dryRun, List<ShopProductConfig> products) {
        List<String> errors = ShopImportValidator.validate(products);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dryRun", dryRun);
        resp.put("count", products.size());
        resp.put("errors", errors);
        if (!errors.isEmpty()) {
            resp.put("ok", false);
            return ResponseEntity.badRequest().body(resp);
        }
        if (dryRun) {
            resp.put("ok", true);
            return ResponseEntity.ok(resp);
        }
        boolean applied = shopConfigService.applyProducts(products);
        resp.put("ok", applied);
        resp.put("version", shopConfigService.version());
        return applied ? ResponseEntity.ok(resp) : ResponseEntity.badRequest().body(resp);
    }
}
