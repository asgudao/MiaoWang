package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.math.BigDecimal;

/**
 * 体重曲线上的一个点
 */
@Data
public class WeightCurvePointRespDTO {

    /**
     * 日期，格式 yyyy-MM-dd
     */
    private String date;

    /**
     * 当天最后一次测量的体重（kg）
     */
    private BigDecimal weightKg;

    /**
     * 当天测量次数（同一天多次测量时用来提示前端）
     */
    private Integer count;
}