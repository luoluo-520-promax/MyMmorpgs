package cn.itcast.demo.mymmorpg.shop;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ShopCsvImporterTest {

    @Test
    public void parse_readsThreeProductTypes() throws Exception {
        String header = "productId,productType,name,channelSku,price,originalPrice,discountBegin,discountEnd,"
                + "saleBegin,saleEnd,rewardsJson,limitType,limitCount,tags,tabId,sort,opened";
        String row1 = "10001,DIRECT_TOPUP,60钻石,mmorpg.diamond.60,600,600,,,,,"
                + "\"[{\"\"itemId\"\":9001,\"\"count\"\":60}]\",NONE,0,diamond,topup,10,true";
        String row2 = "20001,DISCOUNT_PACK,周末特惠,mmorpg.pack.weekend,1200,3000,"
                + "2026-08-01T00:00:00+08:00,2027-08-03T23:59:59+08:00,,,"
                + "\"[{\"\"itemId\"\":9001,\"\"count\"\":300}]\",WEEKLY,1,weekend,discount,10,true";
        String row3 = "30001,OTHER_PACK,首充礼包,mmorpg.pack.first,600,600,,,,,"
                + "\"[{\"\"itemId\"\":9001,\"\"count\"\":120}]\",LIFETIME,1,first_charge,pack,10,true";
        String csv = header + "\n" + row1 + "\n" + row2 + "\n" + row3 + "\n";

        List<ShopProductConfig> products = ShopCsvImporter.parse(csv);
        assertThat(products).hasSize(3);
        assertThat(ShopImportValidator.validate(products)).isEmpty();
        assertThat(products.get(0).productType).isEqualTo("DIRECT_TOPUP");
        assertThat(products.get(0).rewards.get(0).itemId).isEqualTo(9001);
        assertThat(products.get(1).discountBegin).contains("2026-08-01");
        assertThat(products.get(2).tags).contains("first_charge");
    }
}
