package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 疫苗/驱虫记录保存请求
 * 新增与修改共用（修改时 pid 走路径参数）
 */
@Data
public class PreventiveSaveReqDTO {

    /**
     * 类型：VACCINE=疫苗 DEWORM_IN=体内驱虫 DEWORM_OUT=体外驱虫
     */
    private String type;

    /**
     * 项目名，如：猫三联、狂犬疫苗、大宠爱
     */
    private String itemName;

    /**
     * 接种/驱虫时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date happenedAt;

    /**
     * 下次到期时间（不填则不会生成到期提醒）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date nextDueAt;

    /**
     * 医院/机构
     */
    private String hospital;

    /**
     * 疫苗批号
     */
    private String batchNo;

    /**
     * 备注
     */
    private String remark;
}