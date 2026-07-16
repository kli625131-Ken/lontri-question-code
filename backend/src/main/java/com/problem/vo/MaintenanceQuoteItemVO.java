package com.problem.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceQuoteItemVO {
    private Long id;
    private Long visitId;
    private String areaName;
    private String itemName;
    private BigDecimal quantity;
    private String unitName;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String notes;
    private String sourceFilePath;
    private String sourceSheet;
    private Integer sourceRowNumber;
    private String sourceHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
