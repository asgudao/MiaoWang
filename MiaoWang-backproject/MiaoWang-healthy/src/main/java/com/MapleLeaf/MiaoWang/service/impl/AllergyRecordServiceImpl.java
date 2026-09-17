package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.dao.entity.AllergyRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AllergyRecordMapper;
import com.MapleLeaf.MiaoWang.dto.req.AllergySaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AllergyRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthSeverityEnum;
import com.MapleLeaf.MiaoWang.service.AllergyRecordService;
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
 * 过敏史实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllergyRecordServiceImpl implements AllergyRecordService {

    private final AllergyRecordMapper allergyRecordMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    @Override
    public Long create(Long pid, AllergySaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        if (StrUtil.isBlank(requestParam.getAllergen())) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.ALLERGY, countByPid(pid));
        AllergyRecordDO record = new AllergyRecordDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setAllergen(requestParam.getAllergen());
        record.setSeverity(requestParam.getSeverity() == null ? 1 : requestParam.getSeverity());
        record.setReaction(requestParam.getReaction());
        record.setNote(requestParam.getNote());
        if (allergyRecordMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<AllergyRecordRespDTO> list(Long pid) {
        petOwnershipChecker.requireOwnedPet(pid);
        // 重度在前：过敏史是「随时要能一眼看到」的信息，别让它淹在列表底部
        return allergyRecordMapper.selectList(Wrappers.lambdaQuery(AllergyRecordDO.class)
                        .eq(AllergyRecordDO::getPid, pid)
                        .eq(AllergyRecordDO::getDelFlag, 0)
                        .orderByDesc(AllergyRecordDO::getSeverity)
                        .orderByDesc(AllergyRecordDO::getId)).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, AllergySaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        if (StrUtil.isBlank(requestParam.getAllergen())) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        allergyRecordMapper.update(null, Wrappers.lambdaUpdate(AllergyRecordDO.class)
                .eq(AllergyRecordDO::getId, id)
                .eq(AllergyRecordDO::getUid, uid)
                .eq(AllergyRecordDO::getDelFlag, 0)
                .set(AllergyRecordDO::getAllergen, requestParam.getAllergen())
                .set(AllergyRecordDO::getSeverity, requestParam.getSeverity() == null ? 1 : requestParam.getSeverity())
                .set(AllergyRecordDO::getReaction, requestParam.getReaction())
                .set(AllergyRecordDO::getNote, requestParam.getNote()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        allergyRecordMapper.update(null, Wrappers.lambdaUpdate(AllergyRecordDO.class)
                .eq(AllergyRecordDO::getId, id)
                .eq(AllergyRecordDO::getUid, uid)
                .eq(AllergyRecordDO::getDelFlag, 0)
                .set(AllergyRecordDO::getDelFlag, 1));
    }

    private long countByPid(Long pid) {
        Long count = allergyRecordMapper.selectCount(Wrappers.lambdaQuery(AllergyRecordDO.class)
                .eq(AllergyRecordDO::getPid, pid)
                .eq(AllergyRecordDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private AllergyRecordDO requireOwnedRecord(Long id, Long uid) {
        AllergyRecordDO record = allergyRecordMapper.selectOne(Wrappers.lambdaQuery(AllergyRecordDO.class)
                .eq(AllergyRecordDO::getId, id)
                .eq(AllergyRecordDO::getUid, uid)
                .eq(AllergyRecordDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private AllergyRecordRespDTO toResp(AllergyRecordDO record) {
        AllergyRecordRespDTO resp = new AllergyRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        resp.setSeverityDesc(HealthSeverityEnum.descOf(record.getSeverity()));
        return resp;
    }
}