package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.dao.entity.WeightRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.WeightRecordMapper;
import com.MapleLeaf.MiaoWang.dto.req.WeightSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightCurvePointRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.service.WeightRecordService;
import com.MapleLeaf.MiaoWang.support.HealthBenefitResolver;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import com.MapleLeaf.MiaoWang.support.PetOwnershipChecker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 体重记录实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeightRecordServiceImpl implements WeightRecordService {

    /**
     * 曲线默认回溯月数
     */
    private static final int DEFAULT_MONTHS = 6;

    private static final int MAX_MONTHS = 24;

    /**
     * 体重合理区间（kg）：挡住手滑输成 420 这类脏数据，比后期修补数据便宜得多
     */
    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.05");

    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("200");

    private final WeightRecordMapper weightRecordMapper;

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    @Override
    public Long create(Long pid, WeightSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        petOwnershipChecker.requireOwnedPet(pid);
        validateWeight(requestParam.getWeightKg());
        if (requestParam.getMeasuredAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        healthBenefitResolver.requireRecordQuota(uid, HealthRecordTypeEnum.WEIGHT, countByPid(pid));
        WeightRecordDO record = new WeightRecordDO();
        record.setPid(pid);
        record.setUid(uid);
        record.setUsername(HealthUserHelper.currentUsername());
        record.setWeightKg(requestParam.getWeightKg());
        record.setMeasuredAt(requestParam.getMeasuredAt());
        record.setNote(requestParam.getNote());
        if (weightRecordMapper.insert(record) < 1) {
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_RECORD_SAVE_ERROR);
        }
        return record.getId();
    }

    @Override
    public List<WeightRecordRespDTO> list(Long pid) {
        petOwnershipChecker.requireOwnedPet(pid);
        return weightRecordMapper.selectList(Wrappers.lambdaQuery(WeightRecordDO.class)
                        .eq(WeightRecordDO::getPid, pid)
                        .eq(WeightRecordDO::getDelFlag, 0)
                        .orderByDesc(WeightRecordDO::getMeasuredAt)).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Long id, WeightSaveReqDTO requestParam) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        validateWeight(requestParam.getWeightKg());
        if (requestParam.getMeasuredAt() == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_PARAM_ERROR);
        }
        weightRecordMapper.update(null, Wrappers.lambdaUpdate(WeightRecordDO.class)
                .eq(WeightRecordDO::getId, id)
                .eq(WeightRecordDO::getUid, uid)
                .eq(WeightRecordDO::getDelFlag, 0)
                .set(WeightRecordDO::getWeightKg, requestParam.getWeightKg())
                .set(WeightRecordDO::getMeasuredAt, requestParam.getMeasuredAt())
                .set(WeightRecordDO::getNote, requestParam.getNote()));
    }

    @Override
    public void delete(Long id) {
        Long uid = HealthUserHelper.currentUid();
        requireOwnedRecord(id, uid);
        weightRecordMapper.update(null, Wrappers.lambdaUpdate(WeightRecordDO.class)
                .eq(WeightRecordDO::getId, id)
                .eq(WeightRecordDO::getUid, uid)
                .eq(WeightRecordDO::getDelFlag, 0)
                .set(WeightRecordDO::getDelFlag, 1));
    }

    @Override
    public List<WeightCurvePointRespDTO> curve(Long pid, Integer months) {
        petOwnershipChecker.requireOwnedPet(pid);
        int range = months == null ? DEFAULT_MONTHS : Math.min(Math.max(months, 1), MAX_MONTHS);
        Date from = DateUtil.offsetMonth(new Date(), -range);
        List<WeightRecordDO> records = weightRecordMapper.selectList(Wrappers.lambdaQuery(WeightRecordDO.class)
                .eq(WeightRecordDO::getPid, pid)
                .eq(WeightRecordDO::getDelFlag, 0)
                .ge(WeightRecordDO::getMeasuredAt, from)
                .orderByAsc(WeightRecordDO::getMeasuredAt));
        // 按天聚合：同一天量了多次，曲线上只画最后一次，同时把次数带上让前端可以提示
        Map<String, WeightCurvePointRespDTO> pointsByDate = new LinkedHashMap<>();
        for (WeightRecordDO record : records) {
            if (record.getMeasuredAt() == null) {
                continue;
            }
            String date = DateUtil.formatDate(record.getMeasuredAt());
            WeightCurvePointRespDTO point = pointsByDate.get(date);
            if (point == null) {
                point = new WeightCurvePointRespDTO();
                point.setDate(date);
                point.setWeightKg(record.getWeightKg());
                point.setCount(1);
                pointsByDate.put(date, point);
            } else {
                point.setWeightKg(record.getWeightKg());
                point.setCount(point.getCount() + 1);
            }
        }
        return new ArrayList<>(pointsByDate.values());
    }

    private void validateWeight(BigDecimal weightKg) {
        if (weightKg == null || weightKg.compareTo(MIN_WEIGHT_KG) < 0 || weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            throw new ClientException("体重需在 0.05 ~ 200 kg 之间");
        }
    }

    private long countByPid(Long pid) {
        Long count = weightRecordMapper.selectCount(Wrappers.lambdaQuery(WeightRecordDO.class)
                .eq(WeightRecordDO::getPid, pid)
                .eq(WeightRecordDO::getDelFlag, 0));
        return count == null ? 0L : count;
    }

    private WeightRecordDO requireOwnedRecord(Long id, Long uid) {
        WeightRecordDO record = weightRecordMapper.selectOne(Wrappers.lambdaQuery(WeightRecordDO.class)
                .eq(WeightRecordDO::getId, id)
                .eq(WeightRecordDO::getUid, uid)
                .eq(WeightRecordDO::getDelFlag, 0));
        if (record == null) {
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private WeightRecordRespDTO toResp(WeightRecordDO record) {
        WeightRecordRespDTO resp = new WeightRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        return resp;
    }
}