package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryType() {
        List<ShopType> typeList = new ArrayList<>();
        Long size = stringRedisTemplate.opsForList().size(CACHE_SHOP_TYPE_KEY);
        if (size != null && size != 0){
            List<String> shopTypes = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, size);
            if (shopTypes != null && shopTypes.size() != 0){
                for (String shopType : shopTypes) {
                    typeList.add(JSONUtil.toBean(shopType,ShopType.class));
                }
                return Result.ok(typeList);
            }
        }
        typeList = query().orderByAsc("sort").list();
        List<String> typeJson = new ArrayList<>();
        for (ShopType shopType : typeList) {
            typeJson.add(JSONUtil.toJsonStr(shopType));
        }
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,typeJson);
        return Result.ok(typeList);
    }
}
