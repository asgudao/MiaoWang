package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.dao.entity.PreventiveRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.PreventiveRecordMapper;
import com.MapleLeaf.MiaoWang.dto.req.PreventiveSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PreventiveRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.enums.PreventiveTypeEnum;
import com.MapleLeaf.MiaoWang.service.PreventiveRecordService;
import com.MapleLeaf.MiaoWang.support.HealthBenefitResolver;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import com.MapleLeaf.MiaoWang.support.PetOwnershipChecker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 疫苗/驱虫记录实现
 * 归属于「记录类 CRUD」，六个实现的结构都一致：
 * 取身份 → 校验宠物归属 → 校验记录额度 → 落库；改/删则先按 id + uid 查出来，查不到即无权
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreventiveRecordServiceImpl implements PreventiveRecordService {

    private static final long MILLIS_PER_DAY = 86400000L;

    private final PreventiveRecordMapper preventiveRecordMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    @Override
    public Long create(Long pid, PreventiveSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        PreventiveTypeEnum typeEnum = PreventiveTypeEnum.of(requestParam.getType());
        if (typeEnum == null || StrUtil.isBlank(requestParam.getItemName()) || requestParam.getHappenedAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.PREVENTIVE, countByPid(pid));
        PreventiveRecordDO record = new PreventiveRecordDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setType(typeEnum.getType());
        record.setItemName(requestParam.getItemName());
        record.setHappenedAt(requestParam.getHappenedAt());
        record.setNextDueAt(requestParam.getNextDueAt());
        record.setHospital(requestParam.getHospital());
        record.setBatchNo(requestParam.getBatchNo());
        record.setRemark(requestParam.getRemark());
        if (preventiveRecordMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<PreventiveRecordRespDTO> list(Long pid, String type) {
        petOwnershipChecker.requireOwnedPet(pid);
        var wrapper = Wrappers.lambdaQuery(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getPid, pid)
                .eq(PreventiveRecordDO::getDelFlag, 0)
                .orderByDesc(PreventiveRecordDO::getHappenedAt);
        PreventiveTypeEnum typeEnum = PreventiveTypeEnum.of(type);
        if (typeEnum != null) {
            wrapper.eq(PreventiveRecordDO::getType, typeEnum.getType());
        }
        return preventiveRecordMapper.selectList(wrapper).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, PreventiveSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        PreventiveTypeEnum typeEnum = PreventiveTypeEnum.of(requestParam.getType());
        if (typeEnum == null || StrUtil.isBlank(requestParam.getItemName()) || requestParam.getHappenedAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        // 用 set(...) 逐字段覆盖而不是 updateById(entity)：后者默认忽略 null，
        // 会导致「把下次到期时间清空」这种操作静默失效
        preventiveRecordMapper.update(null, Wrappers.lambdaUpdate(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getId, id)
                .eq(PreventiveRecordDO::getUid, uid)
                .eq(PreventiveRecordDO::getDelFlag, 0)
                .set(PreventiveRecordDO::getType, typeEnum.getType())
                .set(PreventiveRecordDO::getItemName, requestParam.getItemName())
                .set(PreventiveRecordDO::getHappenedAt, requestParam.getHappenedAt())
                .set(PreventiveRecordDO::getNextDueAt, requestParam.getNextDueAt())
                .set(PreventiveRecordDO::getHospital, requestParam.getHospital())
                .set(PreventiveRecordDO::getBatchNo, requestParam.getBatchNo())
                .set(PreventiveRecordDO::getRemark, requestParam.getRemark()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        // BaseDO 的 delFlag 没有 @TableLogic，删除要自己写成逻辑删除，否则会把历史数据真删掉
        preventiveRecordMapper.update(null, Wrappers.lambdaUpdate(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getId, id)
                .eq(PreventiveRecordDO::getUid, uid)
                .eq(PreventiveRecordDO::getDelFlag, 0)
                .set(PreventiveRecordDO::getDelFlag, 1));
    }

    private long countByPid(Long pid) {
        Long count = preventiveRecordMapper.selectCount(Wrappers.lambdaQuery(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getPid, pid)
                .eq(PreventiveRecordDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private PreventiveRecordDO requireOwnedRecord(Long id, Long uid) {
        PreventiveRecordDO record = preventiveRecordMapper.selectOne(Wrappers.lambdaQuery(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getId, id)
                .eq(PreventiveRecordDO::getUid, uid)
                .eq(PreventiveRecordDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private PreventiveRecordRespDTO toResp(PreventiveRecordDO record) {
        PreventiveRecordRespDTO resp = new PreventiveRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        resp.setTypeDesc(PreventiveTypeEnum.descOf(record.getType()));
        if (record.getNextDueAt() != null) {
            resp.setNextDueDays((record.getNextDueAt().getTime() - System.currentTimeMillis()) / MILLIS_PER_DAY);
        }
        return resp;
    }
}