package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClientUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;
    @Resource
    CacheClientUtil cacheClientUtil;

    @Resource
    RedisIDWorker redisIDWorker;

    @Test
    void saveShopToRedisTest(){
        Shop shop = shopService.getById(1L);
        cacheClientUtil.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(),shop,10L, TimeUnit.SECONDS);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 200; i++) {
                long order = redisIDWorker.nextId("order");
                System.out.println("id:"+order);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("useTime:"+(end - start));
    }
}