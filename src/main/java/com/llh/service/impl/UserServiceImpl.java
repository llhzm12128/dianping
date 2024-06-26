package com.llh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llh.dto.LoginFormDTO;
import com.llh.dto.Result;
import com.llh.dto.UserDTO;
import com.llh.entity.User;
import com.llh.mapper.UserMapper;
import com.llh.service.IUserService;
import com.llh.utils.RegexUtils;
import com.llh.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.llh.utils.RedisConstants.*;
import static com.llh.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service

public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        System.out.println("发送验证码成功，验证码:"+code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检验手机号
        String phone = loginForm.getPhone();
        if(phone == null || RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误..");
        }
        //校验验证码,从redis获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(cacheCode==null || !cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        //用户是否存在
        if(user == null){
            //不存在则创建用户并保存
            user = createWithPhoe(phone);
        }
        //保存用户到redis
        ///随即生成token作为令牌
        String token = UUID.randomUUID().toString(true);
        ///user转map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName,fieldValue)-> fieldValue.toString()));
        ///存储
        String tokenKey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        ///设置token有效时间
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截至今天为值所有签到记录，返回的是一个十进制数
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0));
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        //循环遍历
        int count = 0;
        while (true){
            //让这个数字和 1 做与运算，得到最后一个bit位;//判断是否为零
            if((num & 1) == 0){
                //为零，说明未签到，结束
                break;
            }else{
                //不为零，说明已签到，计数器加一
                count++;
            }
            //数字右移一位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createWithPhoe(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName( USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
