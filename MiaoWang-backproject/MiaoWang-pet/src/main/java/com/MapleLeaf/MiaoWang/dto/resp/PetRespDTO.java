package com.MapleLeaf.MiaoWang.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;


@Data
public class PetRespDTO {
    /**
     * 宠物id
     */
    private Long pid;

    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（猫/狗/蛇等）
     */
    private String species;

    /**
     * 具体品种（可为空）
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期（只回日期，格式 2020-05-01）
     */
    private LocalDate birth;

    /**
     * 性别（0=雌性，1=雄性）
     */
    private Integer gender;

    /**
     * 绝育状态（0=未绝育，1=已绝育）
     */
    private Integer sterilizedState;

    /**
     * 生存状态（1=在世，2=已离世；库中默认 1，所以正常不会是 null）
     *
     * <p><b>前端一般情况下不展示它</b>：这是给"要不要继续给这只宠物派活"这类判断用的
     * （例如首页的照护提醒不该给已离世的宠物算待办、以后要归到「回忆」区而不是在世主列表）。
     * 不参与创建 / 编辑入参 —— 离世只能由后续的申报流程改写，客户端传不了这个字段。
     */
    private Integer lifeStatus;
}
