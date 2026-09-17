package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.dao.entity.MedicalRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.MedicalRecordMapper;
import com.MapleLeaf.MiaoWang.dto.req.MedicalSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicalRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.service.MedicalRecordService;
import com.MapleLeaf.MiaoWang.support.HealthBenefitResolver;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import com.MapleLeaf.MiaoWang.support.PetOwnershipChecker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 就诊记录实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordMapper medicalRecordMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    @Override
    public Long create(Long pid, MedicalSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        if (requestParam.getVisitAt() == null || StrUtil.isBlank(requestParam.getDiagnosis())) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.MEDICAL, countByPid(pid));
        MedicalRecordDO record = new MedicalRecordDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setVisitAt(requestParam.getVisitAt());
        record.setHospital(requestParam.getHospital());
        record.setDoctor(requestParam.getDoctor());
        record.setDiagnosis(requestParam.getDiagnosis());
        record.setTreatment(requestParam.getTreatment());
        record.setCost(requestParam.getCost());
        record.setAttachments(requestParam.getAttachments());
        record.setRemark(requestParam.getRemark());
        if (medicalRecordMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<MedicalRecordRespDTO> list(Long pid) {
        petOwnershipChecker.requireOwnedPet(pid);
        return medicalRecordMapper.selectList(Wrappers.lambdaQuery(MedicalRecordDO.class)
                        .eq(MedicalRecordDO::getPid, pid)
                        .eq(MedicalRecordDO::getDelFlag, 0)
                        .orderByDesc(MedicalRecordDO::getVisitAt)).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, MedicalSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        if (requestParam.getVisitAt() == null || StrUtil.isBlank(requestParam.getDiagnosis())) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        medicalRecordMapper.update(null, Wrappers.lambdaUpdate(MedicalRecordDO.class)
                .eq(MedicalRecordDO::getId, id)
                .eq(MedicalRecordDO::getUid, uid)
                .eq(MedicalRecordDO::getDelFlag, 0)
                .set(MedicalRecordDO::getVisitAt, requestParam.getVisitAt())
                .set(MedicalRecordDO::getHospital, requestParam.getHospital())
                .set(MedicalRecordDO::getDoctor, requestParam.getDoctor())
                .set(MedicalRecordDO::getDiagnosis, requestParam.getDiagnosis())
                .set(MedicalRecordDO::getTreatment, requestParam.getTreatment())
                .set(MedicalRecordDO::getCost, requestParam.getCost())
                .set(MedicalRecordDO::getAttachments, requestParam.getAttachments())
                .set(MedicalRecordDO::getRemark, requestParam.getRemark()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        medicalRecordMapper.update(null, Wrappers.lambdaUpdate(MedicalRecordDO.class)
                .eq(MedicalRecordDO::getId, id)
                .eq(MedicalRecordDO::getUid, uid)
                .eq(MedicalRecordDO::getDelFlag, 0)
                .set(MedicalRecordDO::getDelFlag, 1));
    }

    private long countByPid(Long pid) {
        Long count = medicalRecordMapper.selectCount(Wrappers.lambdaQuery(MedicalRecordDO.class)
                .eq(MedicalRecordDO::getPid, pid)
                .eq(MedicalRecordDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private MedicalRecordDO requireOwnedRecord(Long id, Long uid) {
        MedicalRecordDO record = medicalRecordMapper.selectOne(Wrappers.lambdaQuery(MedicalRecordDO.class)
                .eq(MedicalRecordDO::getId, id)
                .eq(MedicalRecordDO::getUid, uid)
                .eq(MedicalRecordDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private MedicalRecordRespDTO toResp(MedicalRecordDO record) {
        MedicalRecordRespDTO resp = new MedicalRecordRespDTO();
        BeanUtil.copyProperties(record, resp, "attachments");
        resp.setAttachments(StrUtil.isBlank(record.getAttachments())
                ? Collections.emptyList()
                : StrUtil.splitTrim(record.getAttachments(), ','));
        return resp;
    }
}