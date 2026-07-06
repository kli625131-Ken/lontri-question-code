package com.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResultVO<T> {
    private long total;
    private long page;
    private long pageSize;
    private List<T> items;
}
