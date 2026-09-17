package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dao.entity.SymptomLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.SymptomLogMapper;
import com.MapleLeaf.MiaoWang.dto.req.AiRagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiRagAskRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiRagSourceRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthAiAnalyzeRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthSeverityEnum;
import com.MapleLeaf.MiaoWang.openfegin.ai.AiAnalyzeFeign;
import com.MapleLeaf.MiaoWang.service.HealthAiAnalysisService;
import com.MapleLeaf.MiaoWang.support.HealthBenefitResolver;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import com.MapleLeaf.MiaoWang.support.PetOwnershipChecker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * AI 症状解读实现
 *
 * <p>职责边界：本模块负责「取数 + 拼问题 + 权限」，「检索 + 生成」交给 ai-knowledge，
 * 这样知识库/模型怎么换都不影响健康档案。
 *
 * <p>为什么不让它去调 ai-knowledge 的公开问答接口：那个接口要登录态、还要扣 RAG 次数。
 * AI 解读属于档位功能（{@code healthAiAnalysis}），不该再吃 RAG 配额 ——
 * 有解读权限的档位 RAG 本来就是不限次数，扣了也等于没扣，反而让账单看起来像"点一次扣两次"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthAiAnalysisServiceImpl implements HealthAiAnalysisService {

    /**
     * 参与解读的最近症状条数：太多会稀释重点、也白烧 token
     */
    private static final int MAX_SYMPTOM_LOGS = 10;

    /**
     * 免责声明（前端必须展示）：这是辅助参考，不是诊断
     */
    private static final String DISCLAIMER =
            "以上内容由 AI 结合你记录的症状与知识库生成，仅供辅助参考，不能替代兽医面诊；症状持续或加重请尽快就医。";

    private final SymptomLogMapper symptomLogMapper;

    private final HealthBenefitResolver healthBenefitResolver;

    private final PetOwnershipChecker petOwnershipChecker;

    private final AiAnalyzeFeign aiAnalyzeFeign;

    @Override
    public HealthAiAnalyzeRespDTO analyze(Long pid) {
        Long uid = HealthUserHelper.currentUid();
        // 归属校验放最前面：既保证权限，也顺手拿到物种与名字用于拼问题
        PetFeignRespDTO pet = petOwnershipChecker.requireOwnedPet(pid);
        // 付费闸门：AI 解读是 34.9 及以上档位的功能
        if (!healthBenefitResolver.aiAnalysisAllowed(uid)) {
            log.info("用户 {} 的当前档位不支持 AI 解读症状", uid);
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_AI_NOT_ALLOWED);
        }
        List<SymptomLogDO> symptomLogs = recentSymptomLogs(uid, pid);
        if (symptomLogs.isEmpty()) {
            // 没症状记录就没有解读的输入，直接提示去记一条，别白跑一次大模型
            throw new ClientException(HealthyErrorCodeEnum.HEALTH_AI_NO_SYMPTOM);
        }
        AiRagAskRespDTO aiAnswer = askAi(buildQuestion(pet, symptomLogs), speciesCode(pet.getSpecies()), uid);
        HealthAiAnalyzeRespDTO respDTO = new HealthAiAnalyzeRespDTO();
        respDTO.setPid(pid);
        respDTO.setPetName(pet.getPName());
        respDTO.setSymptomCount(symptomLogs.size());
        respDTO.setAnalysis(aiAnswer.getAnswer());
        respDTO.setHasKnowledge(aiAnswer.getHasKnowledge());
        respDTO.setSources(aiAnswer.getSources());
        respDTO.setDisclaimer(DISCLAIMER);
        respDTO.setGeneratedAt(new Date());
        log.info("用户 {} 完成宠物 {} 的 AI 症状解读（用了最近 {} 条症状，命中知识库={}）",
                uid, pid, symptomLogs.size(), aiAnswer.getHasKnowledge());
        return respDTO;
    }

    /**
     * 取最近若干条症状记录（按出现时间倒序；逻辑删除的不算）
     */
    private List<SymptomLogDO> recentSymptomLogs(Long uid, Long pid) {
        List<SymptomLogDO> logs = symptomLogMapper.selectList(Wrappers.lambdaQuery(SymptomLogDO.class)
                .eq(SymptomLogDO::getPid, pid)
                .eq(SymptomLogDO::getUid, uid)
                .eq(SymptomLogDO::getDelFlag, 0)
                .orderByDesc(SymptomLogDO::getOccurredAt)
                .last("limit " + MAX_SYMPTOM_LOGS));
        return logs == null ? List.of() : logs;
    }

    /**
     * 把症状记录拼成问题
     *
     * <p>要求里刻意写死"不要给用药名称和剂量"：模型给剂量是典型的越界建议，
     * 出了事没人担得起。
     */
    private String buildQuestion(PetFeignRespDTO pet, List<SymptomLogDO> symptomLogs) {
        StringBuilder question = new StringBuilder();
        question.append("我家的")
                .append(StrUtil.blankToDefault(pet.getSpecies(), "宠物"))
                .append(StrUtil.blankToDefault(pet.getPName(), ""))
                .append("最近出现了这些症状，请结合常见病因帮我分析：\n");
        for (SymptomLogDO symptomLog : symptomLogs) {
            question.append("- ").append(StrUtil.blankToDefault(symptomLog.getSymptomName(), "未命名症状"));
            if (symptomLog.getSeverity() != null) {
                question.append("（").append(HealthSeverityEnum.descOf(symptomLog.getSeverity())).append("）");
            }
            if (symptomLog.getOccurredAt() != null) {
                question.append("，发生时间 ").append(DateUtil.formatDate(symptomLog.getOccurredAt()));
            }
            if (StrUtil.isNotBlank(symptomLog.getDescription())) {
                question.append("，补充描述：").append(symptomLog.getDescription());
            }
            question.append("\n");
        }
        question.append("请按下面三点回答：1) 可能的原因（常见在前、少见在后）；")
                .append("2) 现在需要重点观察什么；3) 出现哪些情况必须尽快就医。")
                .append("不要给出具体用药名称和剂量。");
        return question.toString();
    }

    private AiRagAskRespDTO askAi(String question, Integer species, Long uid) {
        Result<AiRagAskRespDTO> result;
        try {
            result = aiAnalyzeFeign.analyze(AiRagAskReqDTO.of(question, species, uid));
        } catch (Exception e) {
            log.error("调用 AI 解读服务失败，问题长度={}", question.length(), e);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_AI_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("AI 解读服务返回异常结果：{}", result);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_AI_SERVICE_ERROR);
        }
        return result.getData();
    }

    /**
     * 物种编码转换：知识库的物种字段是 1=猫 2=狗，而 pet 侧存的是中文大类名。
     * 其他物种（兔/鸟/…）暂时不做物种过滤，让它检索全库，避免筛成空结果。
     */
    private Integer speciesCode(String species) {
        if (species == null) {
            return null;
        }
        return switch (species) {
            case "猫" -> 1;
            case "狗" -> 2;
            default -> null;
        };
    }
}