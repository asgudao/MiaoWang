package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.VipAdminDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipAdminOpLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.VipAdminMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipAdminOpLogMapper;
import com.MapleLeaf.MiaoWang.enums.VipAdminRoleEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Date;

/**
 * 后台鉴权与审计底座（登录校验 / token 发放与续期 / 角色校验 / 操作日志）
 *
 * <h3>为什么 token 放 Redis 而不是签 JWT</h3>
 * 后台最需要的不是"无状态"，而是<b>可即时吊销</b>：禁用某人、改密码、"踢下线"都要立刻生效。
 * Redis 里存 token → adminId，删 key 即失效；再叠加每请求查库，禁用账号是秒级生效的。
 * 后台 QPS 极低，这点开销完全不值得省。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipAdminAuthSupport {

    /**
     * token key 前缀，值为 adminId
     */
    private static final String TOKEN_KEY_PREFIX = "MiaoWang:vip:admin:token:";

    /**
     * token 有效期（分钟），每次请求滑动续期
     */
    public static final int TOKEN_TTL_MINUTES = 120;

    /**
     * 操作日志详情长度上限（防止把整段 JSON 塞进库里）
     */
    private static final int DETAIL_MAX_LENGTH = 500;

    private final VipAdminMapper vipAdminMapper;

    private final VipAdminOpLogMapper vipAdminOpLogMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 登录校验：账号不存在与密码错误返回<b>同一个</b>错误码，不告诉攻击者"这个账号存在"
     *
     * <p>失败次数限制/锁定暂未做（挂账）：真实后台需要，本项目当前优先级低。
     */
    public VipAdminDO authenticate(String username, String rawPassword, String ip) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(rawPassword)) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_LOGIN_FAILED);
        }
        VipAdminDO adminDO = findByUsername(username.trim());
        if (adminDO == null || StrUtil.isBlank(adminDO.getPasswordHash())
                || !BCrypt.checkpw(rawPassword, adminDO.getPasswordHash())) {
            log.warn("后台登录失败：username={}，ip={}", username, ip);
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_LOGIN_FAILED);
        }
        if (adminDO.getStatus() == null || adminDO.getStatus() != 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_DISABLED);
        }
        VipAdminDO updateDO = new VipAdminDO();
        updateDO.setId(adminDO.getId());
        updateDO.setLastLoginTime(new Date());
        updateDO.setLastLoginIp(ip);
        vipAdminMapper.updateById(updateDO);
        adminDO.setLastLoginTime(updateDO.getLastLoginTime());
        adminDO.setLastLoginIp(ip);
        return adminDO;
    }

    /**
     * 发放 token（Redis 里有记录才算登录态）
     */
    public String issueToken(Long adminId) {
        String token = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, String.valueOf(adminId),
                Duration.ofMinutes(TOKEN_TTL_MINUTES));
        return token;
    }

    /**
     * 注销 token（立即失效）
     */
    public void revokeToken(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + token);
        }
    }

    /**
     * 用 token 取管理员：token 不存在/过期 → 未登录；账号被禁用/删除 → 立即失效
     */
    public VipAdminDO requireAdminByToken(String token) {
        if (StrUtil.isBlank(token)) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        String key = TOKEN_KEY_PREFIX + token;
        String adminId = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(adminId)) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        Long id;
        try {
            id = Long.valueOf(adminId.trim());
        } catch (NumberFormatException e) {
            // Redis 里存了非数字（人为改坏/串数据）：当未登录处理，别让它变成 500
            log.error("后台 token {} 对应的 adminId 非法：{}", token, adminId);
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        VipAdminDO adminDO = vipAdminMapper.selectById(id);
        if (adminDO == null || (adminDO.getDelFlag() != null && adminDO.getDelFlag() != 0)) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        if (adminDO.getStatus() == null || adminDO.getStatus() != 1) {
            // 禁用立即生效：token 还在也没用
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_DISABLED);
        }
        // 滑动续期：一直在用就一直有效
        stringRedisTemplate.expire(key, Duration.ofMinutes(TOKEN_TTL_MINUTES));
        return adminDO;
    }

    /**
     * 要求超管角色（管理管理员账号、看审计日志这类操作只有超管能做）
     */
    public void requireSuper() {
        VipAdminDO adminDO = VipAdminContext.get();
        if (adminDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        if (VipAdminRoleEnum.of(adminDO.getRole()) != VipAdminRoleEnum.SUPER) {
            log.warn("管理员 {}（{}）尝试执行超管操作，已拒绝", adminDO.getUsername(), adminDO.getRole());
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_FORBIDDEN);
        }
    }

    public VipAdminDO findByUsername(String username) {
        return vipAdminMapper.selectOne(Wrappers.lambdaQuery(VipAdminDO.class)
                .eq(VipAdminDO::getUsername, username)
                .eq(VipAdminDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 记录后台操作日志（审计底线：谁在什么时候把哪一单改成了什么）
     */
    public void log(String action, String targetType, String targetId, String detail) {
        try {
            VipAdminOpLogDO logDO = new VipAdminOpLogDO();
            logDO.setAdminId(VipAdminContext.currentAdminId());
            logDO.setAdminUsername(VipAdminContext.currentAdminName());
            logDO.setAction(action);
            logDO.setTargetType(targetType);
            logDO.setTargetId(targetId);
            logDO.setDetail(StrUtil.maxLength(detail, DETAIL_MAX_LENGTH));
            logDO.setIp(currentIp());
            logDO.setDelFlag(0);
            vipAdminOpLogMapper.insert(logDO);
        } catch (Exception e) {
            // 审计日志写失败不能让业务操作回滚 —— 但必须告警，否则等于没有审计
            log.error("后台操作日志写入失败：action={}，target={}，detail={}", action, targetId, detail, e);
        }
    }

    /**
     * 取请求来源 IP：网关转发后 remoteAddr 是网关地址，所以优先读转发头
     */
    public String currentIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwarded)) {
            // X-Forwarded-For 可能是 "client, proxy1, proxy2"，第一个才是客户端
            return StrUtil.subBefore(forwarded, ",", false).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StrUtil.isNotBlank(realIp) ? realIp.trim() : request.getRemoteAddr();
    }
}