package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportBatchDetailVO {
    private ImportBatchVO batch;
    private List<ImportRowReviewVO> rows;
}
