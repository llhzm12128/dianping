package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private VoucherServiceImpl voucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1,shop,10L, TimeUnit.SECONDS);
    }

    @Test
    void testSaveSeckillVoucher(){
        Voucher voucher = voucherService.getById(7L);
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY+voucher.getId(),
                "100");
    }
    @Test
    void loadShopData(){
        //查询店铺信息
        List<Shop> shopList = shopService.list();
        //把店铺按照typeId分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        //分批完成写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<Shop> shops = entry.getValue();
            ArrayList<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
               geoLocations.add(new RedisGeoCommands.GeoLocation(
                       shop.getId().toString(),
                       new Point(shop.getX(), shop.getY())));
            }

            stringRedisTemplate.opsForGeo().add(key, geoLocations);


        }

    }

    @Test
    void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for(int i = 0;i<1000000;i++){
            j = i%1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl1",values);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hl1");
        System.out.println(size);
    }

}
