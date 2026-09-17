package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 体重记录
 */
@Data
public class WeightRecordRespDTO {

    private Long id;

    private Long pid;

    private BigDecimal weightKg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date measuredAt;

    private String note;
}