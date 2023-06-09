package com.hmdp;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.CacheClientUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SendPhoneMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.PasswordEncoder.encode;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    UserMapper userMapper;
    @Resource
    ShopServiceImpl shopService;
    @Resource
    CacheClientUtil cacheClientUtil;

    @Resource
    RedisIDWorker redisIDWorker;

    @Test
    void initPassword(){
        List<User> list = userMapper.selectList(null);
        String s = "123456789";
        for (User user : list) {
            System.out.println(user);
            String encodePassword = encode(s);
            user.setPassword(encodePassword);
            userMapper.updateById(user);
        }
    }
    @Test
    void sendMessage() {
        String phone = "18374894528";
        String code = "784264";
        boolean isSuccess = SendPhoneMessage.SendMessage(phone, code);
        System.out.println(isSuccess);
    }
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
