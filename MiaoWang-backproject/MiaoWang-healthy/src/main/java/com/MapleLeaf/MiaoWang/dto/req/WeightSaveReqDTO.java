package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 体重记录保存请求
 */
@Data
public class WeightSaveReqDTO {

    /**
     * 体重（kg）
     */
    private BigDecimal weightKg;

    /**
     * 测量时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date measuredAt;

    /**
     * 备注
     */
    private String note;
}