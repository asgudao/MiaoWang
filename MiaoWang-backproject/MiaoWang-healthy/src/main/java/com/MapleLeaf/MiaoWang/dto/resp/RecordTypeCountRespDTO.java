package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 按记录类型统计条数的查询结果（内部使用，不直接对外返回）
 */
@Data
public class RecordTypeCountRespDTO {

    /**
     * 记录类型，取值同 {@code HealthRecordTypeEnum#getType()}
     */
    private String recordType;

    /**
     * 条数
     */
    private Long total;
}