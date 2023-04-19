package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SendPhoneMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //验证码存session
//        session.setAttribute("code",code);
//        session.setAttribute("phone",phone);
        //验证码存redis
        stringTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送
        boolean sendSuccess = SendPhoneMessage.SendMessage(phone,code);
        if (!sendSuccess){
            log.error("验证码发送操作出现异常");
            return Result.fail("服务器异常,请稍后重新发送验证码");
        }
        log.debug("发送成功，验证码为{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
//        session方式进行校验
//        String sessionPhone = session.getAttribute("phone").toString();
//        if (phone == null || !phone.equals(sessionPhone)){
//            return Result.fail("手机号格式或内容不符");
//        }
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式或内容不符");
        }
        //校验验证码
        String code = loginForm.getCode();
//        session方式进行校验
//        String validCode = session.getAttribute("code").toString();
        //通过手机号在redis中获取验证码
        String validCode = stringTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(validCode)){
            return Result.fail("验证码错误!");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        //判断新老用户
        if (user == null) {
            //不存在则创建新用户保存到数据库
            user = createUserWithPhone(phone);
        }
        //存储用户到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //生成随机token
        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //用户存到redis
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)-> fieldValue.toString()));
        stringTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        //设置有效期
        stringTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {

        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));

        save(user);

        return user;
    }
}
