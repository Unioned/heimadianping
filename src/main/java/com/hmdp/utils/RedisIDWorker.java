package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {

    private static final long BEGIN_DATETIME = 1679961600;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String prefix){
        //1.生成时间戳
        LocalDateTime nowDateTime = LocalDateTime.now();
        long NowDateSecond = nowDateTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = NowDateSecond - BEGIN_DATETIME;
        //2.生成序列号
        //获取当前天数用于生成当天key
        String dateKey = nowDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //获得自增数量
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + dateKey);
        //3.拼接并返回
        return timeStamp << 32 | increment;
    }
}
