package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.dao.entity.MedicationRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.MedicationRecordMapper;
import com.MapleLeaf.MiaoWang.dto.req.MedicationSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicationRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.service.MedicationRecordService;
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
 * 用药记录实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationRecordServiceImpl implements MedicationRecordService {

    /**
     * 用药中
     */
    private static final int STATUS_TAKING = 0;

    /**
     * 已结束
     */
    private static final int STATUS_FINISHED = 1;

    private final MedicationRecordMapper medicationRecordMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    @Override
    public Long create(Long pid, MedicationSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        if (StrUtil.isBlank(requestParam.getMedicineName()) || requestParam.getStartAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.MEDICATION, countByPid(pid));
        MedicationRecordDO record = new MedicationRecordDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setMedicalId(requestParam.getMedicalId());
        record.setMedicineName(requestParam.getMedicineName());
        record.setDosage(requestParam.getDosage());
        record.setFrequency(requestParam.getFrequency());
        record.setStartAt(requestParam.getStartAt());
        record.setEndAt(requestParam.getEndAt());
        // 不传状态时按「有没有结束时间」推断，避免出现「有结束时间却还显示用药中」
        record.setStatus(requestParam.getStatus() == null
                ? (requestParam.getEndAt() == null ? STATUS_TAKING : STATUS_FINISHED)
                : requestParam.getStatus());
        record.setRemark(requestParam.getRemark());
        if (medicationRecordMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<MedicationRecordRespDTO> list(Long pid, Integer status) {
        petOwnershipChecker.requireOwnedPet(pid);
        var wrapper = Wrappers.lambdaQuery(MedicationRecordDO.class)
                .eq(MedicationRecordDO::getPid, pid)
                .eq(MedicationRecordDO::getDelFlag, 0)
                .orderByDesc(MedicationRecordDO::getStartAt);
        if (status != null) {
            wrapper.eq(MedicationRecordDO::getStatus, status);
        }
        return medicationRecordMapper.selectList(wrapper).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, MedicationSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        if (StrUtil.isBlank(requestParam.getMedicineName()) || requestParam.getStartAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        medicationRecordMapper.update(null, Wrappers.lambdaUpdate(MedicationRecordDO.class)
                .eq(MedicationRecordDO::getId, id)
                .eq(MedicationRecordDO::getUid, uid)
                .eq(MedicationRecordDO::getDelFlag, 0)
                .set(MedicationRecordDO::getMedicalId, requestParam.getMedicalId())
                .set(MedicationRecordDO::getMedicineName, requestParam.getMedicineName())
                .set(MedicationRecordDO::getDosage, requestParam.getDosage())
                .set(MedicationRecordDO::getFrequency, requestParam.getFrequency())
                .set(MedicationRecordDO::getStartAt, requestParam.getStartAt())
                .set(MedicationRecordDO::getEndAt, requestParam.getEndAt())
                .set(MedicationRecordDO::getStatus, requestParam.getStatus() == null
                        ? (requestParam.getEndAt() == null ? STATUS_TAKING : STATUS_FINISHED)
                        : requestParam.getStatus())
                .set(MedicationRecordDO::getRemark, requestParam.getRemark()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        medicationRecordMapper.update(null, Wrappers.lambdaUpdate(MedicationRecordDO.class)
                .eq(MedicationRecordDO::getId, id)
                .eq(MedicationRecordDO::getUid, uid)
                .eq(MedicationRecordDO::getDelFlag, 0)
                .set(MedicationRecordDO::getDelFlag, 1));
    }

    private long countByPid(Long pid) {
        Long count = medicationRecordMapper.selectCount(Wrappers.lambdaQuery(MedicationRecordDO.class)
                .eq(MedicationRecordDO::getPid, pid)
                .eq(MedicationRecordDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private MedicationRecordDO requireOwnedRecord(Long id, Long uid) {
        MedicationRecordDO record = medicationRecordMapper.selectOne(Wrappers.lambdaQuery(MedicationRecordDO.class)
                .eq(MedicationRecordDO::getId, id)
                .eq(MedicationRecordDO::getUid, uid)
                .eq(MedicationRecordDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private MedicationRecordRespDTO toResp(MedicationRecordDO record) {
        MedicationRecordRespDTO resp = new MedicationRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        resp.setStatusDesc(statusDesc(record.getStatus()));
        return resp;
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STATUS_TAKING -> "用药中";
            case STATUS_FINISHED -> "已结束";
            case 2 -> "已停用";
            default -> "未知";
        };
    }
}