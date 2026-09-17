package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dao.mapper.PetMapper;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PetUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.MapleLeaf.MiaoWang.enums.PetErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.PetSpeciesEnum;
import com.MapleLeaf.MiaoWang.service.PetService;
import com.MapleLeaf.MiaoWang.support.PetBenefitResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetServiceImpl extends ServiceImpl<PetMapper,PetDO> implements PetService {

    private final PetBenefitResolver petBenefitResolver;

    @Override
    public List<PetRespDTO> getMyPets() {
        // 当前用户身份从上下文获取，按稳定键 uid 查询（username 可被修改，不能作为归属键）
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        List<PetDO> petDOList = baseMapper.selectList(queryWrapper);
        // 空列表直接返回空数组，前端渲染"还没有宠物"的空状态，不做客户端错误处理
        if (petDOList == null || petDOList.isEmpty()) {
            return List.of();
        }
        return petDOList.stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PetRespDTO> getPetsByUid(Long uid) {
        // 公开读接口：不看当前登录身份，归属完全由入参 uid 决定（社区个人主页用）
        if (uid == null) {
            return List.of();
        }
        List<PetDO> petDOList = baseMapper.selectList(Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getUid, uid)
                .eq(PetDO::getDelFlag, 0));
        if (petDOList == null || petDOList.isEmpty()) {
            return List.of();
        }
        return petDOList.stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PetRespDTO createPetByUsername(PetCreateReqDTO requestParam) {
        // 用户身份一律从上下文获取（由网关/过滤器鉴权后注入），不信任前端传入的身份
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StrUtil.isAllNotBlank(userId, username)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        PetDO petDO = new PetDO();
        // birth 在 API 层是 LocalDate（只收日期），入库转 datetime（当天 00:00:00）
        BeanUtil.copyProperties(requestParam, petDO, "birth");
        petDO.setBirth(requestParam.getBirth() == null ? null : requestParam.getBirth().atStartOfDay());
        petDO.setPid(null);
        // 数据库关联用 uid，username 冗余存储便于排查展示
        petDO.setUid(Long.parseLong(userId));
        petDO.setUsername(username);
        // 大类是库中非空字段且取值受限，前置校验，避免脏数据和 SQL 异常
        checkSpecies(petDO.getSpecies(), true);
        // 数量上限属于会员权益（免费每种 1 只、会员每种 5 只…），规则由 vip 模块判定
        checkPetLimit(petDO.getUid());
        // 0/1 两态字段校验（可不填，填了就必须合法）
        checkBinaryState(petDO.getGender(), "性别");
        checkBinaryState(petDO.getSterilizedState(), "绝育状态");
        baseMapper.insert(petDO);
        // MP 会把自增主键回填到 petDO.pid，直接返回新宠物对象，前端无需二次查询
        return toRespDTO(petDO);
    }

    @Override
    public Void updatePetByPid(Long pid, PetUpdateReqDTO requestParam) {
        // 当前用户身份从上下文获取
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getPid, pid)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        PetDO petDO = baseMapper.selectOne(queryWrapper);
        if (petDO == null) {
            throw new ClientException("宠物不存在或已被删除");
        }

        // 大类传了才校验（null 表示本次不改动该字段）
        checkSpecies(requestParam.getSpecies(), false);
        checkBinaryState(requestParam.getGender(), "性别");
        checkBinaryState(requestParam.getSterilizedState(), "绝育状态");
        // 只复制 DTO 中存在的字段（pName/species/specificSpecies/ownerCallName/gender/sterilizedState），pid/uid/username 保持不变
        BeanUtil.copyProperties(requestParam, petDO, "birth");
        // birth 单独处理：API 层是 LocalDate，入库转 datetime（当天 00:00:00）；null 表示不改动
        if (requestParam.getBirth() != null) {
            petDO.setBirth(requestParam.getBirth().atStartOfDay());
        }
        baseMapper.updateById(petDO);
        return null;
    }

    @Override
    public Void deletePetByPid(Long pid) {
        // 当前用户身份从上下文获取
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        // 存在性 + 归属 + 未删除 一次查询完成
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getPid, pid)
                .eq(PetDO::getUid, Long.parseLong(userId))
                .eq(PetDO::getDelFlag, 0);
        PetDO petDO = baseMapper.selectOne(queryWrapper);
        if (petDO == null) {
            throw new ClientException("宠物不存在或无权删除");
        }
        // 逻辑删除：置删除标记并记录删除时间，保留数据（delFlag：未删除0/已删除1）
        petDO.setDelFlag(1);
        petDO.setDeletionTime(System.currentTimeMillis());
        baseMapper.updateById(petDO);
        return null;
    }

    /**
     * 校验宠物大类取值
     *
     * @param species  待校验的大类
     * @param required true=创建场景必须传；false=更新场景，null 表示不修改该字段
     */
    private void checkSpecies(String species, boolean required) {
        if (species == null) {
            if (required) {
                throw new ClientException("请选择宠物大类");
            }
            return;
        }
        if (StrUtil.isBlank(species) || !PetSpeciesEnum.isValid(species)) {
            throw new ClientException("宠物大类不合法，可选值：" + PetSpeciesEnum.allowedValues());
        }
    }

    /**
     * 校验某物种的宠物数量是否已达当前档位上限
     *
     * <p>口径：<b>按宠物总数计数，不区分物种</b> —— 免费档一共 2 只、会员档一共 5 只
     * （上限值来自 vip 的档位表，-1 表示不限），本模块不写死任何数字；
     * 改权益只需要改 vip 的档位数据，这里一行都不用动。
     *
     * <p>早期版本是"每个物种各算一份"（免费档 9 种物种各 1 只 = 实际能养 9 只），
     * 与产品口径不符，已改为总数口径。
     *
     * <p>注意这是「先查后写」：并发下极端情况可能多建 1 只（见模块 README 待办 ——
     * 可用 Redisson 按 uid 加锁，或改成 Redis 计数器 + 落库补偿）。
     */
    private void checkPetLimit(Long uid) {
        int limit = petBenefitResolver.petLimitTotal(uid);
        if (petBenefitResolver.isUnlimited(limit)) {
            return;
        }
        long owned = baseMapper.selectCount(Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getUid, uid)
                .eq(PetDO::getDelFlag, 0));
        if (owned >= limit) {
            throw new ClientException("最多只能养 " + limit + " 只宠物，开通会员可提升数量上限",
                    PetErrorCodeEnum.PET_LIMIT_EXCEED);
        }
    }

    /**
     * 校验 0/1 两态字段取值（null 表示未填写/不修改）
     *
     * @param value     待校验取值
     * @param fieldName 字段中文名，用于错误提示
     */
    private void checkBinaryState(Integer value, String fieldName) {
        if (value != null && value != 0 && value != 1) {
            throw new ClientException(fieldName + "取值不合法，只能是 0 或 1");
        }
    }

    /**
     * DO 转响应对象（birth 只回日期，年龄由前端按出生日期自行计算）
     *
     * @param petDO 宠物数据对象
     * @return 响应对象
     */
    private PetRespDTO toRespDTO(PetDO petDO) {
        PetRespDTO respDTO = new PetRespDTO();
        // birth 只回日期部分（DO 里是 datetime，类型不同需单独转换）
        BeanUtil.copyProperties(petDO, respDTO, "birth");
        respDTO.setBirth(petDO.getBirth() == null ? null : petDO.getBirth().toLocalDate());
        return respDTO;
    }
}
