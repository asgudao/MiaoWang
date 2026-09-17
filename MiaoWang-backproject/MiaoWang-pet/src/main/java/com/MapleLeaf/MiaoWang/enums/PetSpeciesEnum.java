package com.MapleLeaf.MiaoWang.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 宠物大类枚举（species 字段白名单）
 * <p>
 * 前端下拉选项与本枚举保持一致；新增大类只需在此追加一行。
 * 注意：若数据库中 species 列是 MySQL ENUM 类型，新增取值需同时 ALTER TABLE。
 */
@Getter
@AllArgsConstructor
public enum PetSpeciesEnum {

    CAT("猫"),
    DOG("狗"),
    SNAKE("蛇"),
    RABBIT("兔"),
    BIRD("鸟"),
    HAMSTER("仓鼠"),
    TORTOISE("龟"),
    FISH("鱼"),
    OTHER("其他");

    /**
     * 存库值（中文大类名）
     */
    private final String value;

    /**
     * 校验是否属于允许的大类
     *
     * @param value 待校验的大类
     * @return true 表示合法
     */
    public static boolean isValid(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.value.equals(value));
    }

    /**
     * 允许的大类列表（用「、」连接），用于错误提示
     *
     * @return 允许的大类，如「猫、狗、蛇…」
     */
    public static String allowedValues() {
        return Arrays.stream(values())
                .map(PetSpeciesEnum::getValue)
                .collect(Collectors.joining("、"));
    }
}