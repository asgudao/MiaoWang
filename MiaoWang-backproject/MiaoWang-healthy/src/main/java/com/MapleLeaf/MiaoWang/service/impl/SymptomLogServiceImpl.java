package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dao.entity.SymptomLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.SymptomLogMapper;
import com.MapleLeaf.MiaoWang.dto.req.SymptomSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomLogRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthSeverityEnum;
import com.MapleLeaf.MiaoWang.openfegin.knowledge.KnowledgeServiceFeign;
import com.MapleLeaf.MiaoWang.service.SymptomLogService;
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
 * 症状日记实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymptomLogServiceImpl implements SymptomLogService {

    private final SymptomLogMapper symptomLogMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    private final KnowledgeServiceFeign knowledgeServiceFeign;

    @Override
    public Long create(Long pid, SymptomSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        if (requestParam.getOccurredAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        String symptomName = resolveSymptomName(requestParam);
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.SYMPTOM, countByPid(pid));
        SymptomLogDO record = new SymptomLogDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setSymptomId(requestParam.getSymptomId());
        record.setSymptomName(symptomName);
        record.setDescription(requestParam.getDescription());
        record.setSeverity(requestParam.getSeverity() == null ? 1 : requestParam.getSeverity());
        record.setOccurredAt(requestParam.getOccurredAt());
        record.setImages(requestParam.getImages());
        record.setRemark(requestParam.getRemark());
        if (symptomLogMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<SymptomLogRespDTO> list(Long pid) {
        petOwnershipChecker.requireOwnedPet(pid);
        return symptomLogMapper.selectList(Wrappers.lambdaQuery(SymptomLogDO.class)
                        .eq(SymptomLogDO::getPid, pid)
                        .eq(SymptomLogDO::getDelFlag, 0)
                        .orderByDesc(SymptomLogDO::getOccurredAt)).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, SymptomSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        if (requestParam.getOccurredAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        String symptomName = resolveSymptomName(requestParam);
        symptomLogMapper.update(null, Wrappers.lambdaUpdate(SymptomLogDO.class)
                .eq(SymptomLogDO::getId, id)
                .eq(SymptomLogDO::getUid, uid)
                .eq(SymptomLogDO::getDelFlag, 0)
                .set(SymptomLogDO::getSymptomId, requestParam.getSymptomId())
                .set(SymptomLogDO::getSymptomName, symptomName)
                .set(SymptomLogDO::getDescription, requestParam.getDescription())
                .set(SymptomLogDO::getSeverity, requestParam.getSeverity() == null ? 1 : requestParam.getSeverity())
                .set(SymptomLogDO::getOccurredAt, requestParam.getOccurredAt())
                .set(SymptomLogDO::getImages, requestParam.getImages())
                .set(SymptomLogDO::getRemark, requestParam.getRemark()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        symptomLogMapper.update(null, Wrappers.lambdaUpdate(SymptomLogDO.class)
                .eq(SymptomLogDO::getId, id)
                .eq(SymptomLogDO::getUid, uid)
                .eq(SymptomLogDO::getDelFlag, 0)
                .set(SymptomLogDO::getDelFlag, 1));
    }

    /**
     * 症状名以知识库条目为准：前端传什么都会被覆盖
     * 这样同一个症状不会出现「呕吐 / 吐了 / 想吐」三种写法，统计与后续 AI 解读才有意义；
     * 同时把名称快照存下来，知识库以后改文案也不会让历史记录变得对不上
     */
    private String resolveSymptomName(SymptomSaveReqDTO requestParam) {
        if (requestParam.getSymptomId() == null) {
            // 允许自由填写（比如症状不在选项里），但至少要有个名字
            if (StrUtil.isBlank(requestParam.getSymptomName())) {
                throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
            }
            return requestParam.getSymptomName().trim();
        }
        Result<KnowledgeFragmentRespDTO> result;
        try {
            result = knowledgeServiceFeign.getFragmentById(requestParam.getSymptomId());
        } catch (Exception e) {
            log.error("调用知识库查询症状条目失败，symptomId={}", requestParam.getSymptomId(), e);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_KNOWLEDGE_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_SYMPTOM_NOT_FOUND);
        }
        return result.getData().getTitle();
    }

    private long countByPid(Long pid) {
        Long count = symptomLogMapper.selectCount(Wrappers.lambdaQuery(SymptomLogDO.class)
                .eq(SymptomLogDO::getPid, pid)
                .eq(SymptomLogDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private SymptomLogDO requireOwnedRecord(Long id, Long uid) {
        SymptomLogDO record = symptomLogMapper.selectOne(Wrappers.lambdaQuery(SymptomLogDO.class)
                .eq(SymptomLogDO::getId, id)
                .eq(SymptomLogDO::getUid, uid)
                .eq(SymptomLogDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private SymptomLogRespDTO toResp(SymptomLogDO record) {
        SymptomLogRespDTO resp = new SymptomLogRespDTO();
        BeanUtil.copyProperties(record, resp, "images");
        resp.setSeverityDesc(HealthSeverityEnum.descOf(record.getSeverity()));
        resp.setImages(StrUtil.isBlank(record.getImages())
                ? Collections.emptyList()
                : StrUtil.splitTrim(record.getImages(), ','));
        return resp;
    }
}