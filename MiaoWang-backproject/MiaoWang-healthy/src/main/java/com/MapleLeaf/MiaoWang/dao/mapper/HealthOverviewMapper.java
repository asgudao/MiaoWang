package com.MapleLeaf.MiaoWang.dao.mapper;


import com.MapleLeaf.MiaoWang.dto.resp.RecordTypeCountRespDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 概览聚合查询
 *
 * <p>概览要拿六类记录各自的条数。原来是对六张表各发一次 {@code selectCount}，
 * 也就是六次数据库往返；这里合成一条 {@code UNION ALL}，一次往返拿回全部计数。
 *
 * <p>之所以能这么合：六张表的计数条件完全同构（{@code pid + del_flag}），
 * 且 pid 上都有索引，六段子查询各自都是索引计数，合起来仍然很快。
 */
@Mapper
public interface HealthOverviewMapper {

    /**
     * 一次查出某个宠物六类记录的条数
     *
     * @param pid 宠物ID
     */
    @Select("""
            SELECT 'preventive' AS recordType, COUNT(*) AS total FROM miaowang_pet_preventive_record WHERE pid = #{pid} AND del_flag = 0
            UNION ALL SELECT 'medical', COUNT(*) FROM miaowang_pet_medical_record WHERE pid = #{pid} AND del_flag = 0
            UNION ALL SELECT 'medication', COUNT(*) FROM miaowang_pet_medication_record WHERE pid = #{pid} AND del_flag = 0
            UNION ALL SELECT 'allergy', COUNT(*) FROM miaowang_pet_allergy_record WHERE pid = #{pid} AND del_flag = 0
            UNION ALL SELECT 'symptom', COUNT(*) FROM miaowang_pet_symptom_log WHERE pid = #{pid} AND del_flag = 0
            UNION ALL SELECT 'weight', COUNT(*) FROM miaowang_pet_weight_record WHERE pid = #{pid} AND del_flag = 0
            """)
    List<RecordTypeCountRespDTO> countByRecordType(@Param("pid") Long pid);
}