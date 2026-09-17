package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.errorcode.BaseErrorCode;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dao.entity.AllergyRecordDO;
import com.MapleLeaf.MiaoWang.dao.entity.MedicalRecordDO;
import com.MapleLeaf.MiaoWang.dao.entity.MedicationRecordDO;
import com.MapleLeaf.MiaoWang.dao.entity.PreventiveRecordDO;
import com.MapleLeaf.MiaoWang.dao.entity.SymptomLogDO;
import com.MapleLeaf.MiaoWang.dao.entity.WeightRecordDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AllergyRecordMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.HealthOverviewMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.MedicalRecordMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.MedicationRecordMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.PreventiveRecordMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.SymptomLogMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.WeightRecordMapper;
import com.MapleLeaf.MiaoWang.dto.resp.AllergyRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthExportRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthOverviewRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthTimelineItemRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicalRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicationRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PreventiveRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RecordTypeCountRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomLogRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomOptionRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipBenefitFeignRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightRecordRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthSeverityEnum;
import com.MapleLeaf.MiaoWang.enums.PreventiveTypeEnum;
import com.MapleLeaf.MiaoWang.openfegin.knowledge.KnowledgeServiceFeign;
import com.MapleLeaf.MiaoWang.service.HealthOverviewService;
import com.MapleLeaf.MiaoWang.support.HealthBenefitResolver;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import com.MapleLeaf.MiaoWang.support.PetOwnershipChecker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 健康档案聚合实现
 *
 * <p>三个「读」场景共用一套查询：
 * <ul>
 *   <li>概览：进页面首屏一次拿全，避免发六七个请求</li>
 *   <li>时间线：六类记录拍平成同一种形状，按时间倒序</li>
 *   <li>导出：把全部记录结构化输出（付费权益）</li>
 * </ul>
 * 注意：宠物归属只校验一次（远程调用开销最大），权益也只查一次 —— 聚合接口最容易
 * 犯的错就是「顺手多调几个服务」，把一次请求放大成十几次远程调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthOverviewServiceImpl implements HealthOverviewService {

    private static final int DEFAULT_TIMELINE_LIMIT = 50;

    private static final int MAX_TIMELINE_LIMIT = 200;

    /**
     * 体重变化对比窗口：最近一次体重 vs 30 天前
     */
    private static final int WEIGHT_TREND_DAYS = 30;

    /**
     * 概览里最多展示的过敏项与在用药物条数
     */
    private static final int OVERVIEW_ITEM_LIMIT = 10;

    /**
     * 知识库中「健康自查」的分类编码
     */
    private static final String KNOWLEDGE_CATEGORY_HEALTH = "health";

    private final PetOwnershipChecker petOwnershipChecker;

    private final HealthBenefitResolver healthBenefitResolver;

    private final KnowledgeServiceFeign knowledgeServiceFeign;

    private final PreventiveRecordMapper preventiveRecordMapper;

    private final MedicalRecordMapper medicalRecordMapper;

    private final MedicationRecordMapper medicationRecordMapper;

    private final AllergyRecordMapper allergyRecordMapper;

    private final SymptomLogMapper symptomLogMapper;

    private final WeightRecordMapper weightRecordMapper;

    private final HealthOverviewMapper healthOverviewMapper;

    /**
     * 概览里几笔互不依赖的小查询并行执行用的线程池
     */
    private final ExecutorService healthQueryExecutor;

    @Override
    public HealthOverviewRespDTO overview(Long pid) {
        Long uid = HealthUserHelper.currentUid();
        PetFeignRespDTO pet = petOwnershipChecker.requireOwnedPet(pid);
        VipBenefitFeignRespDTO benefit = healthBenefitResolver.benefitOf(uid);
        Map<String, Long> counts = countRecords(pid);

        // 下面几笔查询彼此没有依赖，串行执行等于把各自的往返时间相加，
        // 并行之后墙钟时间约等于最慢的那一笔（每个任务自己从连接池拿连接）
        CompletableFuture<PreventiveRecordDO> nextPreventiveFuture = supplyAsync(() -> selectNextPreventive(pid));
        CompletableFuture<WeightRecordDO> latestWeightFuture = supplyAsync(() -> selectLatestWeight(pid));
        // 体重变化要基于"最新的那条"，所以它是对上一个 future 的依赖阶段，而不是另起一笔
        CompletableFuture<java.math.BigDecimal> weightChangeFuture =
                latestWeightFuture.thenApplyAsync(latest -> resolveWeightChange(pid, latest), healthQueryExecutor);
        CompletableFuture<List<AllergyRecordDO>> allergiesFuture = supplyAsync(() ->
                allergyRecordMapper.selectList(Wrappers.lambdaQuery(AllergyRecordDO.class)
                        .eq(AllergyRecordDO::getPid, pid)
                        .eq(AllergyRecordDO::getDelFlag, 0)
                        .orderByDesc(AllergyRecordDO::getSeverity)
                        .orderByDesc(AllergyRecordDO::getId)
                        .last("limit " + OVERVIEW_ITEM_LIMIT)));
        CompletableFuture<List<MedicationRecordDO>> medicationsFuture = supplyAsync(() ->
                medicationRecordMapper.selectList(Wrappers.lambdaQuery(MedicationRecordDO.class)
                        .eq(MedicationRecordDO::getPid, pid)
                        .eq(MedicationRecordDO::getDelFlag, 0)
                        .eq(MedicationRecordDO::getStatus, 0)
                        .orderByDesc(MedicationRecordDO::getStartAt)
                        .last("limit " + OVERVIEW_ITEM_LIMIT)));

        HealthOverviewRespDTO resp = new HealthOverviewRespDTO();
        resp.setPid(pid);
        resp.setPetName(pet == null ? null : pet.getPName());
        resp.setSpecies(pet == null ? null : pet.getSpecies());
        resp.setRecordCounts(counts);
        resp.setTotalRecords(counts.values().stream().mapToInt(Long::intValue).sum());
        Integer limit = benefit.getHealthRecordLimit();
        resp.setRecordLimit(limit);
        resp.setLimitReachedTypes(resolveLimitReachedTypes(counts, limit));
        resp.setNextPreventive(toPreventiveResp(await(nextPreventiveFuture)));
        resp.setLatestWeight(toWeightResp(await(latestWeightFuture)));
        resp.setWeightChangeKg(await(weightChangeFuture));
        resp.setAllergies(await(allergiesFuture).stream()
                .map(this::toAllergyResp)
                .collect(Collectors.toList()));
        resp.setActiveMedications(await(medicationsFuture).stream()
                .map(this::toMedicationResp)
                .collect(Collectors.toList()));
        resp.setExportAllowed(isFlagOn(benefit.getHealthExport()));
        resp.setAiAnalysisAllowed(isFlagOn(benefit.getHealthAiAnalysis()));
        return resp;
    }

    @Override
    public List<HealthTimelineItemRespDTO> timeline(Long pid, String types, Integer limit) {
        petOwnershipChecker.requireOwnedPet(pid);
        int size = limit == null ? DEFAULT_TIMELINE_LIMIT : Math.min(Math.max(limit, 1), MAX_TIMELINE_LIMIT);
        Set<String> wantedTypes = resolveTypes(types);
        List<HealthTimelineItemRespDTO> items = new ArrayList<>();
        // 每类各取前 size 条：合并后的前 size 条一定落在各类的前 size 条里，
        // 所以不用把所有历史记录都查出来再排序
        if (wantedTypes.contains(HealthRecordTypeEnum.PREVENTIVE.getType())) {
            for (PreventiveRecordDO record : preventiveRecordMapper.selectList(Wrappers.lambdaQuery(PreventiveRecordDO.class)
                    .eq(PreventiveRecordDO::getPid, pid)
                    .eq(PreventiveRecordDO::getDelFlag, 0)
                    .orderByDesc(PreventiveRecordDO::getHappenedAt)
                    .last("limit " + size))) {
                items.add(buildItem(HealthRecordTypeEnum.PREVENTIVE, record.getId(),
                        PreventiveTypeEnum.descOf(record.getType()) + "：" + record.getItemName(),
                        StrUtil.blankToDefault(record.getHospital(), record.getRemark()),
                        record.getHappenedAt(), null));
            }
        }
        if (wantedTypes.contains(HealthRecordTypeEnum.MEDICAL.getType())) {
            for (MedicalRecordDO record : medicalRecordMapper.selectList(Wrappers.lambdaQuery(MedicalRecordDO.class)
                    .eq(MedicalRecordDO::getPid, pid)
                    .eq(MedicalRecordDO::getDelFlag, 0)
                    .orderByDesc(MedicalRecordDO::getVisitAt)
                    .last("limit " + size))) {
                items.add(buildItem(HealthRecordTypeEnum.MEDICAL, record.getId(),
                        StrUtil.blankToDefault(record.getDiagnosis(), "就诊记录"),
                        StrUtil.blankToDefault(record.getTreatment(), record.getHospital()),
                        record.getVisitAt(), null));
            }
        }
        if (wantedTypes.contains(HealthRecordTypeEnum.MEDICATION.getType())) {
            for (MedicationRecordDO record : medicationRecordMapper.selectList(Wrappers.lambdaQuery(MedicationRecordDO.class)
                    .eq(MedicationRecordDO::getPid, pid)
                    .eq(MedicationRecordDO::getDelFlag, 0)
                    .orderByDesc(MedicationRecordDO::getStartAt)
                    .last("limit " + size))) {
                items.add(buildItem(HealthRecordTypeEnum.MEDICATION, record.getId(),
                        "用药：" + record.getMedicineName(),
                        StrUtil.blankToDefault(record.getDosage(), record.getFrequency()),
                        record.getStartAt(), null));
            }
        }
        if (wantedTypes.contains(HealthRecordTypeEnum.ALLERGY.getType())) {
            for (AllergyRecordDO record : allergyRecordMapper.selectList(Wrappers.lambdaQuery(AllergyRecordDO.class)
                    .eq(AllergyRecordDO::getPid, pid)
                    .eq(AllergyRecordDO::getDelFlag, 0)
                    .orderByDesc(AllergyRecordDO::getId)
                    .last("limit " + size))) {
                // 过敏史没有「发生时间」这一列，用录入时间作为时间线锚点
                items.add(buildItem(HealthRecordTypeEnum.ALLERGY, record.getId(),
                        "过敏：" + record.getAllergen(), record.getReaction(),
                        record.getCreateTime(), record.getSeverity()));
            }
        }
        if (wantedTypes.contains(HealthRecordTypeEnum.SYMPTOM.getType())) {
            for (SymptomLogDO record : symptomLogMapper.selectList(Wrappers.lambdaQuery(SymptomLogDO.class)
                    .eq(SymptomLogDO::getPid, pid)
                    .eq(SymptomLogDO::getDelFlag, 0)
                    .orderByDesc(SymptomLogDO::getOccurredAt)
                    .last("limit " + size))) {
                items.add(buildItem(HealthRecordTypeEnum.SYMPTOM, record.getId(),
                        StrUtil.blankToDefault(record.getSymptomName(), "症状记录"),
                        record.getDescription(), record.getOccurredAt(), record.getSeverity()));
            }
        }
        if (wantedTypes.contains(HealthRecordTypeEnum.WEIGHT.getType())) {
            for (WeightRecordDO record : weightRecordMapper.selectList(Wrappers.lambdaQuery(WeightRecordDO.class)
                    .eq(WeightRecordDO::getPid, pid)
                    .eq(WeightRecordDO::getDelFlag, 0)
                    .orderByDesc(WeightRecordDO::getMeasuredAt)
                    .last("limit " + size))) {
                items.add(buildItem(HealthRecordTypeEnum.WEIGHT, record.getId(),
                        "体重 " + record.getWeightKg() + " kg", record.getNote(),
                        record.getMeasuredAt(), null));
            }
        }
        // 时间倒序，null 时间排到最后（正常不会有，防御式处理）
        items.sort((left, right) -> {
            Date leftTime = left.getHappenedAt();
            Date rightTime = right.getHappenedAt();
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });
        return items.size() > size ? new ArrayList<>(items.subList(0, size)) : items;
    }

    @Override
    public List<SymptomOptionRespDTO> symptomOptions(Integer species) {
        Result<List<KnowledgeFragmentRespDTO>> result;
        try {
            result = knowledgeServiceFeign.listFragments(species, KNOWLEDGE_CATEGORY_HEALTH);
        } catch (Exception e) {
            log.error("调用知识库查询健康自查条目失败，species={}", species, e);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_KNOWLEDGE_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("知识库返回异常结果：{}", result);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_KNOWLEDGE_SERVICE_ERROR);
        }
        return result.getData().stream()
                .map(fragment -> {
                    SymptomOptionRespDTO option = new SymptomOptionRespDTO();
                    option.setSymptomId(fragment.getId());
                    option.setName(fragment.getTitle());
                    option.setDescription(fragment.getContent());
                    option.setRemark(fragment.getRemark());
                    option.setSpecies(fragment.getSpecies());
                    return option;
                })
                .collect(Collectors.toList());
    }

    @Override
    public HealthExportRespDTO export(Long pid) {
        Long uid = HealthUserHelper.currentUid();
        PetFeignRespDTO pet = petOwnershipChecker.requireOwnedPet(pid);
        // 导出是付费权益，先过闸再取数据
        healthBenefitResolver.requireExportAllowed(uid);
        Map<String, Long> counts = countRecords(pid);

        HealthExportRespDTO resp = new HealthExportRespDTO();
        resp.setPid(pid);
        resp.setPetName(pet == null ? null : pet.getPName());
        resp.setSpecies(pet == null ? null : pet.getSpecies());
        resp.setExportedAt(new Date());
        resp.setRecordLimit(healthBenefitResolver.recordLimit(uid));
        resp.setRecordCounts(counts);
        resp.setPreventives(preventiveRecordMapper.selectList(Wrappers.lambdaQuery(PreventiveRecordDO.class)
                        .eq(PreventiveRecordDO::getPid, pid)
                        .eq(PreventiveRecordDO::getDelFlag, 0)
                        .orderByAsc(PreventiveRecordDO::getHappenedAt)).stream()
                .map(this::toPreventiveResp)
                .collect(Collectors.toList()));
        resp.setMedicals(medicalRecordMapper.selectList(Wrappers.lambdaQuery(MedicalRecordDO.class)
                        .eq(MedicalRecordDO::getPid, pid)
                        .eq(MedicalRecordDO::getDelFlag, 0)
                        .orderByAsc(MedicalRecordDO::getVisitAt)).stream()
                .map(this::toMedicalResp)
                .collect(Collectors.toList()));
        resp.setMedications(medicationRecordMapper.selectList(Wrappers.lambdaQuery(MedicationRecordDO.class)
                        .eq(MedicationRecordDO::getPid, pid)
                        .eq(MedicationRecordDO::getDelFlag, 0)
                        .orderByAsc(MedicationRecordDO::getStartAt)).stream()
                .map(this::toMedicationResp)
                .collect(Collectors.toList()));
        resp.setAllergies(allergyRecordMapper.selectList(Wrappers.lambdaQuery(AllergyRecordDO.class)
                        .eq(AllergyRecordDO::getPid, pid)
                        .eq(AllergyRecordDO::getDelFlag, 0)
                        .orderByDesc(AllergyRecordDO::getSeverity)).stream()
                .map(this::toAllergyResp)
                .collect(Collectors.toList()));
        resp.setSymptoms(symptomLogMapper.selectList(Wrappers.lambdaQuery(SymptomLogDO.class)
                        .eq(SymptomLogDO::getPid, pid)
                        .eq(SymptomLogDO::getDelFlag, 0)
                        .orderByAsc(SymptomLogDO::getOccurredAt)).stream()
                .map(this::toSymptomResp)
                .collect(Collectors.toList()));
        resp.setWeights(weightRecordMapper.selectList(Wrappers.lambdaQuery(WeightRecordDO.class)
                        .eq(WeightRecordDO::getPid, pid)
                        .eq(WeightRecordDO::getDelFlag, 0)
                        .orderByAsc(WeightRecordDO::getMeasuredAt)).stream()
                .map(this::toWeightResp)
                .collect(Collectors.toList()));
        return resp;
    }

    private Map<String, Long> countRecords(Long pid) {
        // 先按枚举铺底，保证返回结构稳定：某类一条都没有时前端也要拿到 0，而不是缺字段
        Map<String, Long> counts = new LinkedHashMap<>();
        for (HealthRecordTypeEnum recordType : HealthRecordTypeEnum.values()) {
            counts.put(recordType.getType(), 0L);
        }
        // 六类计数合成一条 UNION ALL 查询：六次数据库往返压成一次
        for (RecordTypeCountRespDTO row : healthOverviewMapper.countByRecordType(pid)) {
            if (row.getRecordType() != null) {
                counts.put(row.getRecordType(), countOf(row.getTotal()));
            }
        }
        return counts;
    }

    /**
     * 哪些类别已经记满（-1=不限则永不记满）
     */
    private List<String> resolveLimitReachedTypes(Map<String, Long> counts, Integer limit) {
        if (limit == null || limit == HealthBenefitResolver.UNLIMITED) {
            return Collections.emptyList();
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Set<String> resolveTypes(String types) {
        Set<String> result = new LinkedHashSet<>();
        if (StrUtil.isBlank(types)) {
            for (HealthRecordTypeEnum item : HealthRecordTypeEnum.values()) {
                result.add(item.getType());
            }
            return result;
        }
        for (String type : StrUtil.splitTrim(types, ',')) {
            HealthRecordTypeEnum item = HealthRecordTypeEnum.of(type);
            if (item == null) {
                throw new ClientException("不支持的记录类型：" + type);
            }
            result.add(item.getType());
        }
        return result;
    }

    private PreventiveRecordDO selectNextPreventive(Long pid) {
        return preventiveRecordMapper.selectOne(Wrappers.lambdaQuery(PreventiveRecordDO.class)
                .eq(PreventiveRecordDO::getPid, pid)
                .eq(PreventiveRecordDO::getDelFlag, 0)
                .isNotNull(PreventiveRecordDO::getNextDueAt)
                .ge(PreventiveRecordDO::getNextDueAt, new Date())
                .orderByAsc(PreventiveRecordDO::getNextDueAt)
                .last("limit 1"));
    }

    private WeightRecordDO selectLatestWeight(Long pid) {
        return weightRecordMapper.selectOne(Wrappers.lambdaQuery(WeightRecordDO.class)
                .eq(WeightRecordDO::getPid, pid)
                .eq(WeightRecordDO::getDelFlag, 0)
                .orderByDesc(WeightRecordDO::getMeasuredAt)
                .last("limit 1"));
    }

    private java.math.BigDecimal resolveWeightChange(Long pid, WeightRecordDO latestWeight) {
        if (latestWeight == null || latestWeight.getWeightKg() == null) {
            return null;
        }
        WeightRecordDO baseline = weightRecordMapper.selectOne(Wrappers.lambdaQuery(WeightRecordDO.class)
                .eq(WeightRecordDO::getPid, pid)
                .eq(WeightRecordDO::getDelFlag, 0)
                .le(WeightRecordDO::getMeasuredAt, DateUtil.offsetDay(new Date(), -WEIGHT_TREND_DAYS))
                .orderByDesc(WeightRecordDO::getMeasuredAt)
                .last("limit 1"));
        if (baseline == null || baseline.getWeightKg() == null) {
            return null;
        }
        return latestWeight.getWeightKg().subtract(baseline.getWeightKg());
    }

    /**
     * 把一笔查询丢给聚合查询线程池
     */
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, healthQueryExecutor);
    }

    /**
     * 等并行查询结果，并把包装异常还原成业务异常：
     * 线程池里的异常会被包成 CompletionException，直接抛出去前端只能看到笼统的系统出错
     */
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.error("概览并行查询失败", cause);
            throw new ServiceException(BaseErrorCode.SERVICE_ERROR);
        }
    }

    private boolean isFlagOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private long countOf(Long count) {
        return count == null ? 0L : count;
    }

    private HealthTimelineItemRespDTO buildItem(HealthRecordTypeEnum recordType, Long recordId, String title,
                                                String summary, Date happenedAt, Integer severity) {
        HealthTimelineItemRespDTO item = new HealthTimelineItemRespDTO();
        item.setRecordType(recordType.getType());
        item.setRecordTypeDesc(recordType.getDesc());
        item.setRecordId(recordId);
        item.setTitle(title);
        item.setSummary(summary);
        item.setHappenedAt(happenedAt);
        item.setSeverity(severity);
        return item;
    }

    private PreventiveRecordRespDTO toPreventiveResp(PreventiveRecordDO record) {
        if (record == null) {
            return null;
        }
        PreventiveRecordRespDTO resp = new PreventiveRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        resp.setTypeDesc(PreventiveTypeEnum.descOf(record.getType()));
        if (record.getNextDueAt() != null) {
            resp.setNextDueDays((record.getNextDueAt().getTime() - System.currentTimeMillis()) / 86400000L);
        }
        return resp;
    }

    private MedicalRecordRespDTO toMedicalResp(MedicalRecordDO record) {
        MedicalRecordRespDTO resp = new MedicalRecordRespDTO();
        BeanUtil.copyProperties(record, resp, "attachments");
        resp.setAttachments(StrUtil.isBlank(record.getAttachments())
                ? Collections.emptyList()
                : StrUtil.splitTrim(record.getAttachments(), ','));
        return resp;
    }

    private MedicationRecordRespDTO toMedicationResp(MedicationRecordDO record) {
        MedicationRecordRespDTO resp = new MedicationRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        Integer status = record.getStatus();
        if (status != null) {
            resp.setStatusDesc(switch (status) {
                case 0 -> "用药中";
                case 1 -> "已结束";
                case 2 -> "已停用";
                default -> "未知";
            });
        }
        return resp;
    }

    private AllergyRecordRespDTO toAllergyResp(AllergyRecordDO record) {
        AllergyRecordRespDTO resp = new AllergyRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        resp.setSeverityDesc(HealthSeverityEnum.descOf(record.getSeverity()));
        return resp;
    }

    private SymptomLogRespDTO toSymptomResp(SymptomLogDO record) {
        SymptomLogRespDTO resp = new SymptomLogRespDTO();
        BeanUtil.copyProperties(record, resp, "images");
        resp.setSeverityDesc(HealthSeverityEnum.descOf(record.getSeverity()));
        resp.setImages(StrUtil.isBlank(record.getImages())
                ? Collections.emptyList()
                : StrUtil.splitTrim(record.getImages(), ','));
        return resp;
    }

    private WeightRecordRespDTO toWeightResp(WeightRecordDO record) {
        if (record == null) {
            return null;
        }
        WeightRecordRespDTO resp = new WeightRecordRespDTO();
        BeanUtil.copyProperties(record, resp);
        return resp;
    }
}