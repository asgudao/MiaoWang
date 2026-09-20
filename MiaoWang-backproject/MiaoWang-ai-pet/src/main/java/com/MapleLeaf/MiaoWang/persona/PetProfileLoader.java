package com.MapleLeaf.MiaoWang.persona;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiPetErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.pet.PetServiceFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 宠物档案装载器：从 pet 服务取档案，校验归属，并翻译成 {@link PetProfile}。
 *
 * <h3>归属校验为什么不用新接口</h3>
 * 直接复用 pet 已有的「查我的宠物」——它按登录态（username + token → UserContext）返回当前用户的宠物列表
 * （common 的 {@code FeignHeaderPropagationConfig} 会把两个登录头原样透传给下游）。
 * 目标 pid 不在这个列表里，就说明这只宠物不是当前用户的：越权与不存在返回<b>同一个</b>错误码，
 * 不给攻击者留一个"这个 pid 真实存在"的探测信号。
 *
 * <h3>为什么必须判 Result.code</h3>
 * 下游抛业务异常时 Feign 仍然是一次成功的 HTTP 200，只是信封里 {@code code != "0"}、{@code data = null}。
 * 只看 data 为不为空，会把"用户未登录"误报成"宠物服务挂了"，把排查引到完全错误的方向。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetProfileLoader {

    /**
     * 主人没设置称呼时的兜底。绝大多数用户不会填，给个通用称呼比让宠物说"null"强。
     */
    private static final String DEFAULT_OWNER_CALL = "主人";

    /**
     * 宠物没名字时的占位名（库里确实允许空名，见挂账：pet 的 pName 没有服务端校验）
     */
    private static final String UNNAMED = "未命名";

    private final PetServiceFeign petServiceFeign;

    /**
     * 取当前用户的宠物档案并组装人设
     *
     * @param uid 当前用户（只用于日志归因；真正的身份来自请求头透传）
     * @param pid 目标宠物；为 null 时取「第一只」（默认宠物）
     */
    public PetProfile load(Long uid, Long pid) {
        List<PetFeignRespDTO> pets = listMyPets(uid);
        if (pets.isEmpty()) {
            // 不是错误，是"还没建档案"：文案要指向下一步动作，而不是报一串技术错误
            throw new ClientException(AiPetErrorCodeEnum.PET_PROFILE_EMPTY);
        }
        PetFeignRespDTO target = pid == null ? pets.get(0) : find(pets, pid);
        return toProfile(target);
    }

    private List<PetFeignRespDTO> listMyPets(Long uid) {
        Result<List<PetFeignRespDTO>> result;
        try {
            result = petServiceFeign.listMyPets();
        } catch (Exception e) {
            log.error("调用宠物档案服务失败，uid={}", uid, e);
            throw new ServiceException(AiPetErrorCodeEnum.PET_SERVICE_ERROR);
        }
        if (result == null) {
            log.error("宠物档案服务返回 null，uid={}", uid);
            throw new ServiceException(AiPetErrorCodeEnum.PET_SERVICE_ERROR);
        }
        if (!result.isSuccess() || result.getData() == null) {
            // 下游把"未登录"也塞在 A000001 里，只能按消息识别；识别出来就还它本来面目
            String message = StrUtil.blankToDefault(result.getMessage(), "");
            if (message.contains("未登录") || message.contains("登录已过期") || message.contains("登录失效")) {
                log.warn("宠物档案服务认为当前请求未登录，uid={}，message={}", uid, message);
                throw new ClientException(AiPetErrorCodeEnum.PET_CHAT_NOT_LOGIN);
            }
            log.error("宠物档案服务返回失败，uid={}，code={}，message={}", uid, result.getCode(), message);
            throw new ServiceException(AiPetErrorCodeEnum.PET_SERVICE_ERROR);
        }
        return result.getData();
    }

    private PetFeignRespDTO find(List<PetFeignRespDTO> pets, Long pid) {
        for (PetFeignRespDTO pet : pets) {
            if (pid.equals(pet.getPid())) {
                return pet;
            }
        }
        // 越权与不存在同码：不区分，避免通过错误码探测别人的宠物ID
        throw new ClientException(AiPetErrorCodeEnum.PET_NOT_FOUND);
    }

    /**
     * 把库里的形状翻译成人话
     */
    private PetProfile toProfile(PetFeignRespDTO pet) {
        boolean named = StrUtil.isNotBlank(pet.getPName());
        String species = StrUtil.blankToDefault(pet.getSpecies(), "宠物");
        return new PetProfile(
                pet.getPid(),
                named ? pet.getPName().trim() : UNNAMED,
                named,
                species,
                StrUtil.blankToDefault(pet.getSpecificSpecies(), ""),
                StrUtil.blankToDefault(pet.getOwnerCallName(), DEFAULT_OWNER_CALL),
                ageText(pet.getBirth()),
                genderText(pet.getGender()),
                sterilizedText(pet.getSterilizedState()));
    }

    /**
     * 生日 → 年龄说法。解析不了就返回空串（宁可不提年龄，也不要编一个）
     */
    private String ageText(String birth) {
        if (StrUtil.isBlank(birth)) {
            return "";
        }
        LocalDate date;
        try {
            // 对端只回日期（2020-05-01）；万一带了时间部分，截前 10 位再解析
            date = LocalDate.parse(birth.length() > 10 ? birth.substring(0, 10) : birth);
        } catch (DateTimeParseException e) {
            log.warn("宠物生日解析失败，按未知处理：{}", birth);
            return "";
        }
        Period period = Period.between(date, LocalDate.now());
        if (period.isNegative()) {
            // 生日填成了未来时间（脏数据）：不猜，当作未知
            return "";
        }
        int months = period.getYears() * 12 + period.getMonths();
        if (months <= 0) {
            return "不到一个月";
        }
        if (months < 12) {
            return months + "个月";
        }
        return period.getYears() + "岁";
    }

    /**
     * 性别 → 「母」/「公」。未知返回空串（库中该列可为空）
     */
    private String genderText(Integer gender) {
        if (gender == null) {
            return "";
        }
        if (gender == 0) {
            return "母";
        }
        if (gender == 1) {
            return "公";
        }
        return "";
    }

    /**
     * 绝育状态 → 一句话。未知返回空串
     */
    private String sterilizedText(Integer sterilizedState) {
        if (sterilizedState == null) {
            return "";
        }
        return sterilizedState == 1 ? "已经绝育" : "还没绝育";
    }
}