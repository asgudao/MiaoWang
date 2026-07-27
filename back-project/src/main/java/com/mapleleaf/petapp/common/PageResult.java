package com.mapleleaf.petapp.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private long pages;
    private long current;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long pages, long current, List<T> records) {
        return new PageResult<>(total, pages, current, records);
    }
}
