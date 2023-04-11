package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
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

    @Override
    public Result secKill(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        int re = result != null ? result.intValue() : 1;
        if (re != 0){
            return Result.fail(re == 1 ? "库存不足" : "一人只能一单！");
        }
        // 2.2 re==0表示有资格,保存到阻塞队列中让数据库进行异步处理
        long order = redisIDWorker.nextId("order");
        // TODO 保存到阻塞队列里

        return Result.ok(order);
    }

/*

    @Override
    public Result secKill(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断是否到时间
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        //判断库存
        if (voucher.getStock() < 1){
            return Result.fail("库存已经为空，请下次再来...");
        }

        Long userId = UserHolder.getUser().getId();
//      这里注释的是通过redis自己设置锁 SimpleRedisLock为自制工具类
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        boolean lockIt = simpleRedisLock.tryLock(5);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean lockIt = lock.tryLock();

        if (!lockIt){
            return Result.fail("每人仅限一单！");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.CreateVoucherOrder(voucherId);
        } finally {
            lock.unlock();
//            lock.unLock();
        }

//        此处是不使用redis对每人一单的业务进行的一个事务管理和加锁操作。
        */
/*
        synchronized (userId.toString().intern()){
            //在方法调用之前加锁可以保证在事务执行之后再释放锁,可能在数据库数据提交之前有新线程进来进行订单查询,此时订单数据未提交，导致一人两票。
            //给userid加锁可以保证同一个用户id才会进行串行,这样保证了运行效率,也保证了每人一单的安全性。
            //intern()保证了相同字符串内容的字符串为唯一字符串(字符串池中拿到),就不会有相同字符串内容是不同字符串对象的情况。
            //获取代理对象(用于事务管理)，同时需要再启动项加上@EnableAspectJAutoProxy(exposeProxy = true)注解暴露代理对象才能通过AopContext.currentProxy()拿到代理对象。
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.CreateVoucherOrder(voucherId);
        }
        *//*

    }
*/


    @Transactional
    public Result CreateVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock",0)//where id = ? and stock > 0 (内部利用了数据库的行锁,让每次只能一个线程取到数据。)
//                .eq("stock",voucher.getStock())//where id = ? and stock = ? (可以控制超卖，但是失败率过高)
                .update();
        if (!success){
            return Result.fail("库存已经为空，请下次再来...");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
