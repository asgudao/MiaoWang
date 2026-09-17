package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.file.FileStorageService;
import com.MapleLeaf.MiaoWang.common.biz.file.FileUploadRespDTO;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.biz.user.UserInfoDTO;
import com.MapleLeaf.MiaoWang.common.constant.RedisCacheConstant;
import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.enums.UserErrorCodeEnum;
import com.MapleLeaf.MiaoWang.common.jwt.JwtTokenUtil;
import com.MapleLeaf.MiaoWang.dao.entity.UserDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserMapper;
import com.MapleLeaf.MiaoWang.dto.req.PhoneLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.SmsSendReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserRegisterReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPasswordUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPhoneUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserRespDTO;
import com.MapleLeaf.MiaoWang.enums.SmsSceneEnum;
import com.MapleLeaf.MiaoWang.service.CaptchaService;
import com.MapleLeaf.MiaoWang.service.SmsSender;
import com.MapleLeaf.MiaoWang.service.UserService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.MapleLeaf.MiaoWang.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.MapleLeaf.MiaoWang.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;
import static com.MapleLeaf.MiaoWang.common.enums.UserErrorCodeEnum.USER_SAVE_ERROR;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileStorageService fileStorageService;
    private final SmsSender smsSender;
    private final CaptchaService captchaService;

    /**
     * JWT token 有效期，单位毫秒，默认 1 小时
     */
    @Value("${jwt.token.expired:3600000}")
    private Integer tokenExpired;

    /**
     * 短信验证码有效期（分钟）
     */
    @Value("${sms.code-expire-minutes:5}")
    private Integer smsCodeExpireMinutes;

    /**
     * 同一手机号两次发送短信的最小间隔（秒）
     */
    @Value("${sms.send-interval-seconds:60}")
    private Integer smsSendIntervalSeconds;

    /**
     * 同一手机号每日发送短信上限（条）
     */
    @Value("${sms.daily-limit:10}")
    private Integer smsDailyLimit;

    /**
     * 短信验证码连续校验失败上限
     * <p>
     * 6 位数字码只有 100 万种组合，不封顶就能被脚本穷举出来
     */
    @Value("${sms.max-fail-count:5}")
    private Integer smsMaxFailCount;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO == null){
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO,result);
        return result;
    }

    @Override
    public Boolean hasUserName(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if(!hasUserName(requestParam.getUsername())){
            throw new ClientException(USER_NAME_EXIST);
        }
        // 注册同样要"真人 + 真实手机号"两件事：
        // 1) 手机号格式；2) 该手机号收到的注册短信验证码（scene=register，登录场景的码不能拿来注册）
        if (!Validator.isMobile(requestParam.getPhone())) {
            throw new ClientException(UserErrorCodeEnum.PHONE_FORMAT_ERROR);
        }
        checkSmsCode(SmsSceneEnum.REGISTER, requestParam.getPhone(), requestParam.getSmsCode());
        // 一个手机号只能绑一个账号：手机号登录是按 phone 查用户，不挡的话查到多条就登错账号
        Long phoneCount = baseMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getPhone, requestParam.getPhone()));
        if (phoneCount != null && phoneCount > 0) {
            throw new ClientException(UserErrorCodeEnum.USER_PHONE_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY+requestParam.getUsername());
        boolean locked = false;
        try{
            locked = lock.tryLock();
            if(!locked){
                throw new ClientException(USER_NAME_EXIST);
            }
            // 锁内二次校验：并发下两个请求都能通过锁外那道布隆检查（check-then-act 的经典漏洞），
            // 只有一个能抢到锁，后进来的那个必须在这里被拦住，否则会插出两个同名用户
            if(!hasUserName(requestParam.getUsername())){
                throw new ClientException(USER_NAME_EXIST);
            }
            int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            if(inserted < 1){
                throw new ClientException(USER_SAVE_ERROR);
            }

            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            return;
        } finally {
            // 没抢到锁还去 unlock 会抛 IllegalMonitorStateException（原来就是这么写的）
            if(locked){
                lock.unlock();
            }
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        // 改谁不认前端传的 username，而是认请求头还原出来的登录身份（和头像上传同一套思路）：
        // 否则任何人带上别人的 username 就能改掉别人的资料——密码、手机号也在可改字段里
        String loginUsername = UserContext.getUsername();
        if (StrUtil.isBlank(loginUsername)) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
        if (!StrUtil.equals(loginUsername, requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_UPDATE_ONLY_SELF);
        }
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDelFlag, 0);
        baseMapper.update(BeanUtil.toBean(requestParam,UserDO.class),updateWrapper);
    }

    @Override
    public void updatePassword(UserPasswordUpdateReqDTO requestParam) {
        String loginUsername = UserContext.getUsername();
        if (StrUtil.isBlank(loginUsername)) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
        if (StrUtil.isBlank(requestParam.getOldPassword()) || StrUtil.isBlank(requestParam.getNewPassword())) {
            throw new ClientException(UserErrorCodeEnum.PASSWORD_EMPTY);
        }
        // 先比新旧再查库：新密码和原密码一样就没必要往下走（也避免"改了但没改"的误报成功）
        if (StrUtil.equals(requestParam.getOldPassword(), requestParam.getNewPassword())) {
            throw new ClientException(UserErrorCodeEnum.PASSWORD_SAME);
        }
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, loginUsername)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        // 密码目前是明文存库 + 明文比对（安全债），这里的比对口径与登录保持一致，不能一个用 MD5 一个用明文
        if (!StrUtil.equals(userDO.getPassword(), requestParam.getOldPassword())) {
            throw new ClientException(UserErrorCodeEnum.PASSWORD_OLD_ERROR);
        }
        UserDO updateDO = new UserDO();
        updateDO.setPassword(requestParam.getNewPassword());
        baseMapper.update(updateDO, Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUid, userDO.getUid()));
        // 密码换了，基于"旧密码"建立的登录态就该作废——连当前这个 token 一起清，必须重新登录。
        // 不清的话，被盗号者手里那个 token 在用户改完密码后依然有效，"改密码把人踢下线"就成了空话。
        stringRedisTemplate.delete(RedisCacheConstant.USER_LOGIN_KEY + loginUsername);
    }

    @Override
    public void updatePhone(UserPhoneUpdateReqDTO requestParam) {
        String loginUsername = UserContext.getUsername();
        if (StrUtil.isBlank(loginUsername)) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
        String newPhone = requestParam.getPhone();
        if (!Validator.isMobile(newPhone)) {
            throw new ClientException(UserErrorCodeEnum.PHONE_FORMAT_ERROR);
        }
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, loginUsername)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        if (StrUtil.equals(userDO.getPhone(), newPhone)) {
            throw new ClientException(UserErrorCodeEnum.PHONE_SAME);
        }
        // 先查占用、再验短信码：号已经被别人占了就没必要让用户白搭一个验证码
        Long phoneUsed = baseMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getPhone, newPhone));
        if (phoneUsed != null && phoneUsed > 0) {
            throw new ClientException(UserErrorCodeEnum.USER_PHONE_EXIST);
        }
        // 关键一步：新号必须是"本人能收到短信"的号，否则等于任意改绑（scene=change_phone 与登录/注册的码不通用）
        checkSmsCode(SmsSceneEnum.CHANGE_PHONE, newPhone, requestParam.getSmsCode());
        UserDO updateDO = new UserDO();
        updateDO.setPhone(newPhone);
        try {
            baseMapper.update(updateDO, Wrappers.lambdaUpdate(UserDO.class)
                    .eq(UserDO::getUid, userDO.getUid()));
        } catch (DuplicateKeyException ex) {
            // uk_phone 兜底：上面的"查占用"和这次写入之间，号可能被并发请求抢走 → 这里翻译成友好错误码，别甩 500
            throw new ClientException(UserErrorCodeEnum.USER_PHONE_EXIST);
        }
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO == null){
            throw new ClientException("用户不存在");
        }
        return issueTokenAndSession(userDO);
    }

    @Override
    public Boolean checkLogin(String username,String token) {
        return stringRedisTemplate.opsForHash().get("MiaoWang_login_" + username, token) != null;
    }

    @Override
    public void logout(String username, String token) {
        if(checkLogin(username,token)){
            stringRedisTemplate.delete("MiaoWang_login_" + username);
            return ;
        }
        throw new ClientException("用户Token不存在或者用户未登录");


    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        // 归属只认 UserContext（请求头 username+token → UserTransmitFilter 还原），不接收前端传的用户名
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
        // 1. 上传到 MinIO 头像桶（minio.buckets.avatar，与其他业务分桶）
        FileUploadRespDTO uploadResult = fileStorageService.uploadImage(file, FileBizTypeEnum.AVATAR);
        // 2. 回写头像 URL；传 entity 才会触发 MetaObjectHandler 的 updateTime 自动填充
        UserDO updateDO = new UserDO();
        updateDO.setAvatar(uploadResult.getUrl());
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, username);
        baseMapper.update(updateDO, updateWrapper);
        return uploadResult.getUrl();
    }

    @Override
    public void sendSmsCode(SmsSendReqDTO requestParam) {
        SmsSceneEnum scene = SmsSceneEnum.of(requestParam.getScene());
        if (scene == null) {
            throw new ClientException(UserErrorCodeEnum.SMS_SCENE_ERROR);
        }
        String phone = requestParam.getPhone();
        if (!Validator.isMobile(phone)) {
            throw new ClientException(UserErrorCodeEnum.PHONE_FORMAT_ERROR);
        }
        // 第 1 道闸门：图形验证码（真人校验，校验后即作废）
        // 没有它，脚本拿一个手机号就能把短信额度刷爆——短信是真金白银
        captchaService.validate(requestParam.getCaptchaKey(), requestParam.getCaptchaCode());
        // 第 2 道闸门：发送间隔。setIfAbsent 是原子操作，两个并发请求只有一个能成功，
        // 所以它同时挡住了"脚本并发打接口绕过间隔限制"这种情况
        String limitKey = RedisCacheConstant.SMS_SEND_LIMIT_KEY + phone;
        Boolean firstSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", smsSendIntervalSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstSend)) {
            throw new ClientException(UserErrorCodeEnum.SMS_SEND_TOO_FREQUENT);
        }
        // 第 3 道闸门：每日上限。INCR 计数，首次计数时把过期时间定到当天 24 点（自然日自动重置）
        String dailyKey = RedisCacheConstant.SMS_DAILY_LIMIT_KEY + phone;
        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            long secondsToMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();
            stringRedisTemplate.expire(dailyKey, secondsToMidnight, TimeUnit.SECONDS);
        }
        if (dailyCount != null && dailyCount > smsDailyLimit) {
            throw new ClientException(UserErrorCodeEnum.SMS_DAILY_LIMIT);
        }
        // 落 Redis：key 带场景，注册的码不能拿去登录
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue()
                .set(buildSmsCodeKey(scene, phone), code, smsCodeExpireMinutes, TimeUnit.MINUTES);
        // 重新发码就清掉上一轮的失败次数，否则上一轮输错几次会把新码也一起锁死
        stringRedisTemplate.delete(buildSmsFailKey(scene, phone));
        smsSender.send(phone, code, scene);
    }

    @Override
    public UserLoginRespDTO loginByPhone(PhoneLoginReqDTO requestParam) {
        String phone = requestParam.getPhone();
        if (!Validator.isMobile(phone)) {
            throw new ClientException(UserErrorCodeEnum.PHONE_FORMAT_ERROR);
        }
        checkSmsCode(SmsSceneEnum.LOGIN, phone, requestParam.getSmsCode());
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getPhone, phone)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null) {
            // 手机号没注册过就自动注册：注册与登录共用一个入口，用户不用先走一遍注册表单
            userDO = autoRegisterByPhone(phone);
        }
        return issueTokenAndSession(userDO);
    }

    /**
     * 校验短信验证码：一次性（用掉即删）+ 失败次数封顶
     */
    private void checkSmsCode(SmsSceneEnum scene, String phone, String inputCode) {
        if (StrUtil.isBlank(inputCode)) {
            throw new ClientException(UserErrorCodeEnum.SMS_CODE_ERROR);
        }
        String codeKey = buildSmsCodeKey(scene, phone);
        String rightCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (rightCode == null) {
            throw new ClientException(UserErrorCodeEnum.SMS_CODE_ERROR);
        }
        if (rightCode.equals(inputCode.trim())) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(buildSmsFailKey(scene, phone));
            return;
        }
        // 错了：记一次失败；达到上限就把码作废，逼用户重新获取（重新获取前还要再过一次图形验证码）
        String failKey = buildSmsFailKey(scene, phone);
        Long failCount = stringRedisTemplate.opsForValue().increment(failKey);
        if (failCount != null && failCount == 1L) {
            stringRedisTemplate.expire(failKey, smsCodeExpireMinutes, TimeUnit.MINUTES);
        }
        if (failCount != null && failCount >= smsMaxFailCount) {
            stringRedisTemplate.delete(codeKey);
            throw new ClientException(UserErrorCodeEnum.SMS_CODE_TOO_MANY_ATTEMPTS);
        }
        throw new ClientException(UserErrorCodeEnum.SMS_CODE_ERROR);
    }

    private String buildSmsCodeKey(SmsSceneEnum scene, String phone) {
        return RedisCacheConstant.SMS_CODE_KEY + scene.getScene() + ":" + phone;
    }

    private String buildSmsFailKey(SmsSceneEnum scene, String phone) {
        return RedisCacheConstant.SMS_FAIL_KEY + scene.getScene() + ":" + phone;
    }

    /**
     * 手机号自动注册
     * <p>
     * 用户名随机生成而不是直接用手机号：username 会被用户信息接口/个人主页暴露出去，
     * 拿手机号当用户名等于把手机号公开了。
     */
    private UserDO autoRegisterByPhone(String phone) {
        String username = null;
        for (int i = 0; i < 3; i++) {
            String candidate = "mw_" + phone.substring(phone.length() - 4) + RandomUtil.randomNumbers(4);
            Long count = baseMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                    .eq(UserDO::getUsername, candidate));
            if (!userRegisterCachePenetrationBloomFilter.contains(candidate) && count != null && count == 0) {
                username = candidate;
                break;
            }
        }
        if (username == null) {
            throw new ClientException("自动注册失败：用户名生成冲突，请重试");
        }
        UserDO userDO = new UserDO();
        userDO.setUsername(username);
        // password 列是 NOT NULL：给随机值占位（以后可走"设置密码/重置密码"覆盖）。
        // 不能用固定值，否则所有自动注册的账号等于共用一把万能钥匙
        userDO.setPassword(RandomUtil.randomString(16));
        userDO.setPhone(phone);
        if (baseMapper.insert(userDO) < 1) {
            throw new ClientException(USER_SAVE_ERROR);
        }
        userRegisterCachePenetrationBloomFilter.add(username);
        // insert 后回查一次再返回：不依赖 MyBatis-Plus 的主键回填行为（uid 是自增 INT 列）
        UserDO registered = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0));
        if (registered == null) {
            throw new ClientException(USER_SAVE_ERROR);
        }
        return registered;
    }

    /**
     * 签发 JWT + 写 Redis 会话（密码登录 / 手机号登录共用，避免两套登录各写一份会话逻辑）
     */
    private UserLoginRespDTO issueTokenAndSession(UserDO userDO) {
        // 单会话策略：不拦截重复登录，新登录直接顶掉旧会话（会话有效期与 JWT 保持一致）
        String sessionKey = RedisCacheConstant.USER_LOGIN_KEY + userDO.getUsername();
        stringRedisTemplate.delete(sessionKey);

        /**
         * Hash
         * Key：MiaoWang_login_用户名
         * Value:
         *  Key:JWT token
         *  Val:JSON 字符串（用户信息）
         *
         * 存 UserInfoDTO 而非 UserDO：凭据（密码）不进入 Redis，
         * 且与 UserTransmitFilter 读取的 UserInfoDTO 类型完全对齐
         */
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userDO.getUid());
        claims.put("username", userDO.getUsername());
        String token = JwtTokenUtil.generateToken(claims, "user", tokenExpired);
        UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                .userId(String.valueOf(userDO.getUid()))
                .username(userDO.getUsername())
                .realName(userDO.getRealName())
                .build();
        stringRedisTemplate.opsForHash().put(sessionKey, token, JSON.toJSONString(userInfoDTO));
        stringRedisTemplate.expire(sessionKey, tokenExpired, TimeUnit.MILLISECONDS);
        return new UserLoginRespDTO(token);
    }
}
