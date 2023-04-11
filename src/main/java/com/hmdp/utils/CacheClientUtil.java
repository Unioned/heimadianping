package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Component
public class CacheClientUtil {


    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    CacheClientUtil(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透
     */
    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isNotBlank(json)){//判断null或者空字符串
            //命中,直接从redis缓存返回数据
            return JSONUtil.toBean(json, type);
        }
        if(json != null){
            return null;
        }
        //未命中,从数据库查询数据5
        R r = dbFallBack.apply(id);
        //数据库无数据,返回错误
        if (r == null){
            //写空值到redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //数据库有数据,先存到redis缓存,再返回数据
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 逻辑过期解决缓存穿透,默认一定在缓存中,一般先手动载入缓存。
     */
    public <R,ID> R queryWithLogicExpire(String prefix,String lockPrefix,Function<ID,R> dbFallBack,ID id,Class<R> type,Long time,TimeUnit unit) {
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isBlank(json)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        String lockKey = lockPrefix + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R rr = dbFallBack.apply(id);
                    setWithLogicExpire(lockKey,rr,time,unit);
                }finally {
                    UnLock(lockKey);
                }
            });
        }
        return r;
    }

    /**
     * 互斥锁解决缓存穿透
     */
    public <R,ID> R queryWithMutex(String prefix,String lockPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,
                                   Long nullTime,TimeUnit nullUnit,Long dataTime,TimeUnit dataUnit) {
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isNotBlank(json)){//判断null或者空字符串
            //命中,直接从redis缓存返回数据
            return JSONUtil.toBean(json, type);
        }
        if(json != null){
            return null;
        }
        //实行缓存重建
        //获取互斥锁
        String lockKey = lockPrefix + id;
        R r;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock){
                Thread.sleep(50);
                return queryWithMutex(prefix,lockPrefix,id,type,dbFallBack,nullTime,nullUnit,dataTime,dataUnit);
            }
            //失败,等待一段时间
            //成功,构建缓存
            //未命中,从数据库查询数据5
            r = dbFallBack.apply(id);
            //数据库无数据,返回错误
            if (r == null){
                //写空值到redis
                stringRedisTemplate.opsForValue().set(key,"",nullTime,nullUnit);
                return null;
            }
            //数据库有数据,先存到redis缓存,再返回数据
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),dataTime,dataUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            UnLock(lockKey);
        }
        return r;
    }

    private boolean tryLock(String key){
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lock);
    }

    private void UnLock(String key){
        stringRedisTemplate.delete(key);
    }
}
