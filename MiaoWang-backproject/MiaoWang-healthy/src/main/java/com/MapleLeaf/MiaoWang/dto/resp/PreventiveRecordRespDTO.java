package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 疫苗/驱虫记录
 */
@Data
public class PreventiveRecordRespDTO {

    private Long id;

    private Long pid;

    /**
     * VACCINE=疫苗 DEWORM_IN=体内驱虫 DEWORM_OUT=体外驱虫
     */
    private String type;

    /**
     * 类型中文名，前端不用自己维护映射表
     */
    private String typeDesc;

    /**
     * 项目名
     */
    private String itemName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date happenedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date nextDueAt;

    /**
     * 距下次到期还有多少天：负数=已过期，null=没填到期时间
     */
    private Long nextDueDays;

    private String hospital;

    private String batchNo;

    private String remark;
}