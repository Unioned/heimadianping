package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringTemplate) {
        this.stringTemplate = stringTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //session获取对应用户信息
//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");
        //获取请求头的token
        String token = request.getHeader("authorization");
        if (token == null){
            return true;
        }
        //基于token获取redis中用户
        Map<Object, Object> userMap = stringTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }
        //查询到的数据转换为Hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(), false);
        //存在就保存到threadLocal
        UserHolder.saveUser(userDTO);
        //刷新token
        stringTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行请求
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
