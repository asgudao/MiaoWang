package com.MapleLeaf.MiaoWang.support;


import cn.hutool.json.JSONUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.pet.PetServiceFeign;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 宠物归属校验
 *
 * <p>健康档案的六张表都以 pid 为键，如果只按 pid 查询而不校验归属，
 * 任何人拼一个 pid 就能读写别人的宠物病历 —— 这是本模块最要紧的一道闸。
 *
 * <p>实现上没有让 pet 模块新增内部接口，而是复用它的「查我的宠物」接口：
 * 该接口按登录态（username + token → UserContext）返回当前用户的宠物列表，
 * 所以只要目标 pid 不在列表里就说明无权访问。登录头由
 * {@code FeignHeaderPropagationConfig} 透传过去。
 *
 * <p>性能上：归属校验原本是每个请求一次跨服务调用（实测 18ms），是首屏热路径上的固定开销。
 * 现在按 uid 缓存宠物列表（L1 进程内 + L2 Redis），命中即放行、零远程调用。
 *
 * <p><b>关键取舍：缓存只用来加速「放行」，不参与「拒绝」。</b>
 * 缓存未命中（列表里没这个 pid）时会绕过缓存回源确认一次，因为那可能是
 * 「缓存还没刷新」而不是「这只宠物不是你的」—— 用户刚建好宠物就录第一条记录
 * 正是这个场景，用缓存拒绝会直接把人挡在门外。代价是被拒时多一次远程调用，
 * 而被拒本来就该是低频路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetOwnershipChecker {

    private final PetServiceFeign petServiceFeign;

    private final StringRedisTemplate stringRedisTemplate;

    private final HealthCacheStats cacheStats;

    /**
     * L1：进程内缓存「某个用户的宠物列表」。
     *
     * <p>用 Caffeine 的 {@code get(key, fn)} 而不是自己加锁：同一个 key 只会有一个线程回源，
     * 其余线程等它填好再拿结果 —— 这正是缓存击穿的解法（热点 key 失效瞬间不让请求同时打下游）。
     */
    private final Cache<Long, List<PetFeignRespDTO>> ownedPetsL1 = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(HealthCacheKeys.OWNERSHIP_L1_SECONDS))
            .build();

    /**
     * 校验宠物属于当前登录用户，并返回宠物信息（顺手拿到物种，便于后续按物种取症状选项）
     *
     * @param pid 宠物ID
     */
    public PetFeignRespDTO requireOwnedPet(Long pid) {
        Long uid = HealthUserHelper.currentUid();
        if (pid == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        PetFeignRespDTO cached = findIn(listMyPets(uid), pid);
        if (cached != null) {
            return cached;
        }
        // 走到这里说明「缓存里没有」，而不是「确定无权访问」，所以必须回源复核一次
        cacheStats.ownershipRecheck();
        PetFeignRespDTO fresh = findIn(listMyPetsFresh(uid), pid);
        if (fresh != null) {
            return fresh;
        }
        // 刻意不区分「宠物不存在」与「不是你的宠物」：否则可以拿报错差异探测别人有哪些宠物ID
        log.warn("宠物归属校验未通过，uid={}，pid={}", uid, pid);
        throw new ClientException(HealthyErrorCodeEnum.HEALTH_PET_NOT_FOUND);
    }

    /**
     * 当前登录用户的宠物列表（走缓存）
     */
    public List<PetFeignRespDTO> listMyPets(Long uid) {
        List<PetFeignRespDTO> l1 = ownedPetsL1.getIfPresent(uid);
        if (l1 != null) {
            cacheStats.ownershipL1Hit();
            return l1;
        }
        return ownedPetsL1.get(uid, key -> {
            List<PetFeignRespDTO> fromRedis = readFromRedis(key);
            if (fromRedis != null) {
                cacheStats.ownershipL2Hit();
                return fromRedis;
            }
            cacheStats.ownershipLoad();
            return refresh(key);
        });
    }

    /**
     * 绕过缓存回源，并刷新两级缓存
     */
    public List<PetFeignRespDTO> listMyPetsFresh(Long uid) {
        cacheStats.ownershipLoad();
        List<PetFeignRespDTO> pets = refresh(uid);
        ownedPetsL1.put(uid, pets);
        return pets;
    }

    private PetFeignRespDTO findIn(List<PetFeignRespDTO> pets, Long pid) {
        for (PetFeignRespDTO pet : pets) {
            if (pid.equals(pet.getPid())) {
                return pet;
            }
        }
        return null;
    }

    private List<PetFeignRespDTO> refresh(Long uid) {
        List<PetFeignRespDTO> pets = fetchFromPetService();
        writeToRedis(uid, pets);
        return pets;
    }

    private List<PetFeignRespDTO> fetchFromPetService() {
        Result<List<PetFeignRespDTO>> result;
        try {
            result = petServiceFeign.listMyPets();
        } catch (Exception e) {
            // 远程服务不可用时宁可挡住写操作，也不能让归属校验形同虚设
            log.error("调用宠物服务查询宠物列表失败", e);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_PET_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("宠物服务返回异常结果：{}", result);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_PET_SERVICE_ERROR);
        }
        return result.getData();
    }

    /**
     * 读 L2。空数组也是有效值（表示"确认没有宠物"），所以刻意缓存空结果防穿透；
     * 返回 null 只代表"缓存里没有"或"缓存不可用"
     */
    private List<PetFeignRespDTO> readFromRedis(Long uid) {
        try {
            String json = stringRedisTemplate.opsForValue().get(HealthCacheKeys.ownership(uid));
            if (json == null) {
                return null;
            }
            return JSONUtil.toList(json, PetFeignRespDTO.class);
        } catch (Exception e) {
            // 缓存故障不该影响主流程：当作未命中，继续回源（可用性优先于省这一次调用）
            log.warn("读取归属缓存失败，降级为回源，uid={}", uid, e);
            return null;
        }
    }

    private void writeToRedis(Long uid, List<PetFeignRespDTO> pets) {
        try {
            stringRedisTemplate.opsForValue().set(HealthCacheKeys.ownership(uid), JSONUtil.toJsonStr(pets),
                    HealthCacheKeys.jitteredTtl(HealthCacheKeys.OWNERSHIP_L2_SECONDS));
        } catch (Exception e) {
            log.warn("写入归属缓存失败，忽略，uid={}", uid, e);
        }
    }
}