package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.MESSAGE_QUEUE_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //获取消息队列消息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), //XREADGROUP GROUP g1 c1
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), //COUNT 1 BLOCK 2000
                            StreamOffset.create(MESSAGE_QUEUE_PREFIX, ReadOffset.lastConsumed()) //STREAMS stream.orders >
                    );
                    //判断是否获取到消息
                    if (list == null || list.isEmpty()){
                        continue;
                    }
                    //解析消息订单信息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ack确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(MESSAGE_QUEUE_PREFIX,"g1",entries.getId());
                } catch (Exception e) {
                    log.error("订单处理异常",e);
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                //获取pending-list消息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"), //XREADGROUP GROUP g1 c1
                        StreamReadOptions.empty().count(1), //COUNT 1
                        StreamOffset.create(MESSAGE_QUEUE_PREFIX, ReadOffset.from("0")) //STREAMS stream.orders >
                );
                //判断是否获取到消息
                if (list == null || list.isEmpty()) {
                    // 如果获取失败说明pending-list没有消息，结束循环
                    break;
                }
                //解析消息订单信息
                MapRecord<String, Object, Object> entries = list.get(0);
                Map<Object, Object> value = entries.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //创建订单
                handleVoucherOrder(voucherOrder);
                //ack确认 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(MESSAGE_QUEUE_PREFIX, "g1", entries.getId());
            } catch (Exception e) {
                log.error("订单处理pending-list异常", e);
            }
        }
    }

    private IVoucherOrderService proxy;

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean lockIt = lock.tryLock();

        if (!lockIt){
            log.error("不能重复下单");
            return;
        }
        try {
            proxy.CreateVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result secKill(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIDWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int re = result != null ? result.intValue() : 1;
        if (re != 0){
            return Result.fail(re == 1 ? "库存不足" : "一人只能一单！");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void CreateVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0 ){
            log.error("一人一单！");
            return;
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock",0)//where id = ? and stock > 0 (内部利用了数据库的行锁,让每次只能一个线程取到数据。)
                .update();
        if (!success){
            log.error("库存已经为空，请下次再来...");
            return;
        }
        save(voucherOrder);
    }

}
